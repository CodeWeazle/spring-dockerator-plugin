package net.magiccode.maven.docker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Core tests for existing ComposeFileGenerator functionality (excluding volumes which are covered elsewhere).
 */
public class ComposeFileGeneratorCoreTest {

    private DockerService service(String name, boolean createEnvFile, Map<String,String> env) {
        return DockerService.builder()
                .name(name)
                .version("1.0.0")
                .imagePrefix("demo/")
                .dockerEnvVars(env)
                .createEnvironmentFile(createEnvFile)
                .build();
    }

    @Test
    @DisplayName("Single module: environment list style with quoting for values containing spaces")
    void singleModuleEnvironmentListStyle() throws IOException {
        Map<String,String> env = new HashMap<>();
        env.put("KEY_ONE", "val1");
        env.put("KEY_TWO", "some value"); // requires quoting
        DockerService svc = service("app", false, env);

        Path outDir = Files.createTempDirectory("compose-core-single");
        ComposeFileGenerator.builder()
                .services(List.of(svc))
                .moduleName("app")
                .outputDir(outDir.toString())
                .createEnvironmentFile(false)
                .build()
                .generateDockerCompose();

        String content = Files.readString(outDir.resolve("docker-compose.yml"));
        assertThat(content).contains("- KEY_ONE=val1");
        assertThat(content).contains("- KEY_TWO='some value'");
    }

    @Test
    @DisplayName("Multi-module: common environment anchor plus service specific map entries")
    void multiModuleCommonEnvironment() throws IOException {
        Map<String,String> svcAEnv = new HashMap<>();
        svcAEnv.put("KEY_TWO", "val2");
        Map<String,String> svcBEnv = new HashMap<>();
        Map<String,String> common = new HashMap<>();
        common.put("KEY_ONE", "val1");
        DockerService svcA = service("svc-a", false, svcAEnv);
        DockerService svcB = service("svc-b", false, svcBEnv);

        Path outDir = Files.createTempDirectory("compose-core-multi-common");
        ComposeFileGenerator.builder()
                .services(List.of(svcA, svcB))
                .moduleName("demo")
                .commonEnvironment(common)
                .outputDir(outDir.toString())
                .createEnvironmentFile(false)
                .build()
                .generateDockerCompose();

        String content = Files.readString(outDir.resolve("docker-compose.yml"));
        // anchor section
        assertThat(content).contains("x-demo-common:")
                           .contains("&demo-common")
                           .contains("environment:")
                           .contains("&demo-env")
                           .contains("KEY_ONE: val1");
        // service A includes merge and specific
        assertThat(content).contains("svc-a:\n    <<: *demo-common")
                           .contains("svc-a:\n    <<: *demo-common\n    image:") // ensure sequence
                           .contains("environment:\n      <<: *demo-env\n      KEY_TWO: val2");
        // service B includes merge but no specific entries after merge
        assertThat(content).contains("svc-b:\n    <<: *demo-common")
                           .contains("svc-b:\n    <<: *demo-common\n    image:")
                           .contains("environment:\n      <<: *demo-env\n");
    }

    @Test
    @DisplayName("Single module with createEnvFile true uses placeholder references")
    void singleModuleEnvFilePlaceholders() throws IOException {
        Map<String,String> env = new HashMap<>();
        env.put("KEY_ONE", "val1");
        DockerService svc = service("app", true, env);

        Path outDir = Files.createTempDirectory("compose-core-single-envfile");
        ComposeFileGenerator.builder()
                .services(List.of(svc))
                .moduleName("app")
                .outputDir(outDir.toString())
                .createEnvironmentFile(true)
                .build()
                .generateDockerCompose();

        String content = Files.readString(outDir.resolve("docker-compose.yml"));
        // placeholder should use service name prefix (APP_)
        assertThat(content).contains("- KEY_ONE=${APP_KEY_ONE}");
    }

    @Test
    @DisplayName("Multi-module with createEnvFile true: common and service placeholders")
    void multiModuleEnvFilePlaceholders() throws IOException {
        Map<String,String> svcAEnv = new HashMap<>();
        svcAEnv.put("KEY_TWO", "val2");
        Map<String,String> common = new HashMap<>();
        common.put("KEY_ONE", "val1");
        DockerService svcA = service("svc-a", true, svcAEnv);
        DockerService svcB = service("svc-b", true, Map.of());

        Path outDir = Files.createTempDirectory("compose-core-multi-envfile");
        ComposeFileGenerator.builder()
                .services(List.of(svcA, svcB))
                .moduleName("demo")
                .commonEnvironment(common)
                .outputDir(outDir.toString())
                .createEnvironmentFile(true)
                .build()
                .generateDockerCompose();

        String content = Files.readString(outDir.resolve("docker-compose.yml"));
        // common anchor contains placeholder without service prefix
        assertThat(content).contains("KEY_ONE: ${KEY_ONE}");
        // service A specific env placeholder with service prefix (hyphen kept as implemented)
        assertThat(content).contains("KEY_TWO: ${SVC-A_KEY_TWO}");
    }

    @Test
    @DisplayName("Module specific compose file generation contains only target service")
    void moduleSpecificComposeGeneration() throws IOException {
        Map<String,String> envA = Map.of("A_KEY", "a");
        Map<String,String> envB = Map.of("B_KEY", "b");
        DockerService svcA = service("alpha", false, envA);
        DockerService svcB = service("beta", false, envB);

        Path outDir = Files.createTempDirectory("compose-core-module-file");
        ComposeFileGenerator generator = ComposeFileGenerator.builder()
                .services(List.of(svcA, svcB))
                .moduleName("alpha")
                .outputDir(outDir.toString())
                .createEnvironmentFile(false)
                .build();
        generator.generateModuleDockerCompose();

        Path moduleFile = outDir.resolve("docker-compose-alpha.yml");
        assertThat(Files.exists(moduleFile)).isTrue();
        String content = Files.readString(moduleFile);
        assertThat(content).contains("alpha:")
                           .contains("A_KEY=a")
                           .doesNotContain("beta:")
                           .doesNotContain("B_KEY");
    }

    @Test
    @DisplayName("Single module: volumes are correctly added to the service entry")
    void singleModuleVolumes() throws IOException {
        VolumeMapping volume = new VolumeMapping("../ssl", "/opt/ssl");
        DockerService svc = DockerService.builder()
                                     .name("app")
                                     .version("1.0.0")
                                     .imagePrefix("demo/")
                                     .specificVolumes(List.of(volume))
                                     .build();

        Path outDir = Files.createTempDirectory("compose-core-single-volumes");
        ComposeFileGenerator.builder()
                        .services(List.of(svc))
                        .moduleName("app")
                        .outputDir(outDir.toString())
                        .build()
                        .generateDockerCompose();

        String content = Files.readString(outDir.resolve("docker-compose.yml"));
        assertThat(content).contains("volumes:")
                       .contains("- ../ssl:/opt/ssl");
    }

    @Test
    @DisplayName("Multi-module: common and service-specific volumes")
    void multiModuleVolumes() throws IOException {
        VolumeMapping commonVolume = new VolumeMapping("../common", "/opt/common");
        VolumeMapping svcAVolume = new VolumeMapping("../svc-a", "/opt/svc-a");
        VolumeMapping svcBVolume = new VolumeMapping("../svc-b", "/opt/svc-b");

        DockerService svcA = DockerService.builder()
                                      .name("svc-a")
                                      .version("1.0.0")
                                      .imagePrefix("demo/")
                                      .specificVolumes(List.of(svcAVolume))
                                      .build();

        DockerService svcB = DockerService.builder()
                                      .name("svc-b")
                                      .version("1.0.0")
                                      .imagePrefix("demo/")
                                      .specificVolumes(List.of(svcBVolume))
                                      .build();

        Path outDir = Files.createTempDirectory("compose-core-multi-volumes");
        ComposeFileGenerator.builder()
                        .services(List.of(svcA, svcB))
                        .moduleName("demo")
                        .commonVolumes(List.of(commonVolume))
                        .outputDir(outDir.toString())
                        .build()
                        .generateDockerCompose();

        String content = Files.readString(outDir.resolve("docker-compose.yml"));
        // Common volumes
        assertThat(content).contains("x-demo-common:")
                       .contains("volumes:")
                       .contains("- ../common:/opt/common");
        
        // Service-specific volumes
        assertThat(content).contains("svc-a:")
                       .contains("- ../svc-a:/opt/svc-a");
        assertThat(content).contains("svc-b:")
                       .contains("- ../svc-b:/opt/svc-b");
    }
}