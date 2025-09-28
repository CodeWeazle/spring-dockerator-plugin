package net.magiccode.maven.docker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for volume handling in {@link ComposeFileGenerator} / {@link DockerService}.
 */
public class ComposeFileGeneratorVolumesTest {

    private DockerService baseService(String name) {
        Map<String,String> env = new HashMap<>();
        env.put("SERVER_PORT", "8080");
        return DockerService.builder()
                .name(name)
                .version("1.0.0")
                .imagePrefix("demo/")
                .dockerEnvVars(env)
                .build();
    }

    @Test
    @DisplayName("Single module compose contains direct volumes section when service has volumes")
    void singleModuleVolumesPresent() throws IOException {
        DockerService service = baseService("app");
        service.getSpecificVolumes().add(VolumeMapping.builder().external("./ssl").internal("/opt/ssl").build());

        Path outDir = Files.createTempDirectory("compose-test-single");
        ComposeFileGenerator.builder()
                .services(List.of(service))
                .moduleName("app")
                .outputDir(outDir.toString())
                .createEnvironmentFile(false)
                .build()
                .generateDockerCompose();

        String content = Files.readString(outDir.resolve("docker-compose.yml"));
        assertThat(content).contains("volumes:")
                           .contains("- ./ssl:/opt/ssl");
    }

    @Test
    @DisplayName("Multi-module: shared volume listed once in common anchor; services without extra volumes omit volumes key")
    void multiModuleAllCommonVolumes() throws IOException {
        DockerService svcA = baseService("svc-a");
        DockerService svcB = baseService("svc-b");
        VolumeMapping shared = VolumeMapping.builder().external("./ssl").internal("/opt/ssl").build();
        svcA.getSpecificVolumes().add(shared);
        svcB.getSpecificVolumes().add(shared);

        // simulate plugin commonization
        List<VolumeMapping> common = new ArrayList<>();
        common.add(shared);
        svcA.getSpecificVolumes().clear();
        svcB.getSpecificVolumes().clear();

        Path outDir = Files.createTempDirectory("compose-test-multi-common");
        ComposeFileGenerator.builder()
                .services(List.of(svcA, svcB))
                .moduleName("demo-system")
                .commonVolumes(common)
                .outputDir(outDir.toString())
                .createEnvironmentFile(false)
                .build()
                .generateDockerCompose();

        String content = Files.readString(outDir.resolve("docker-compose.yml"));
        // anchor has common volume
        assertThat(content).contains("x-demo-system-common:")
                           .contains("volumes:")
                           .contains("- ./ssl:/opt/ssl");
        // services must not have their own volumes: sections (only one occurrence overall: the anchor)
        long volumesOccurrences = content.lines().filter(l -> l.trim().equals("volumes:")).count();
        assertThat(volumesOccurrences).isEqualTo(1); // only the common anchor
        assertThat(content).contains("svc-a:").doesNotContainPattern("svc-a:\n(?s).*volumes:");
        assertThat(content).contains("svc-b:").doesNotContainPattern("svc-b:\n(?s).*volumes:");
    }

    @Test
    @DisplayName("Multi-module: service with extra volumes emits merged list (common first then specific)")
    void multiModuleMixedCommonAndSpecific() throws IOException {
        DockerService svcA = baseService("svc-a");
        DockerService svcB = baseService("svc-b");
        VolumeMapping commonVol = VolumeMapping.builder().external("./ssl").internal("/opt/ssl").build();
        VolumeMapping specificVol = VolumeMapping.builder().external("./data").internal("/var/data").build();
        svcA.getSpecificVolumes().add(commonVol);
        svcA.getSpecificVolumes().add(specificVol);
        svcB.getSpecificVolumes().add(commonVol);

        // compute common set (simulate plugin) -> only commonVol
        List<VolumeMapping> common = List.of(commonVol);
        // remove common from services
        svcA.getSpecificVolumes().removeIf(v -> v.equals(commonVol));
        svcB.getSpecificVolumes().removeIf(v -> v.equals(commonVol));
        // svcA retains only specificVol, svcB empty

        Path outDir = Files.createTempDirectory("compose-test-multi-mixed");
        ComposeFileGenerator.builder()
                .services(List.of(svcA, svcB))
                .moduleName("demo-system")
                .commonVolumes(common)
                .outputDir(outDir.toString())
                .createEnvironmentFile(false)
                .build()
                .generateDockerCompose();

        String content = Files.readString(outDir.resolve("docker-compose.yml"));
        // anchor contains common volume only
        assertThat(content).containsSubsequence("x-demo-system-common:", "volumes:", "- ./ssl:/opt/ssl");
        // svc-a must have volumes section with both entries
        assertThat(content).containsPattern("svc-a:\n(?s).*volumes:\n(?s).* - ./ssl:/opt/ssl\n(?s).* - ./data:/var/data");
        // svc-b must not have its own volumes section
        assertThat(content).contains("svc-b:").doesNotContainPattern("svc-b:\n(?s).*volumes:");
    }

    @Test
    @DisplayName("Single module: parent directory volume path '../ssl' is preserved")
    void singleModuleParentDirVolumePreserved() throws IOException {
        DockerService service = baseService("app");
        service.getSpecificVolumes().add(VolumeMapping.builder().external("../ssl").internal("/opt/ssl").build());
        Path outDir = Files.createTempDirectory("compose-test-single-parent");
        ComposeFileGenerator.builder()
                .services(List.of(service))
                .moduleName("app")
                .outputDir(outDir.toString())
                .createEnvironmentFile(false)
                .build()
                .generateDockerCompose();
        String content = Files.readString(outDir.resolve("docker-compose.yml"));
        assertThat(content).contains("volumes:")
                           .contains("- ../ssl:/opt/ssl");
    }

    @Test
    @DisplayName("Multi-module: '../ssl' common volume preserved in anchor")
    void multiModuleParentDirCommonVolume() throws IOException {
        DockerService svcA = baseService("svc-a");
        DockerService svcB = baseService("svc-b");
        VolumeMapping shared = VolumeMapping.builder().external("../ssl").internal("/opt/ssl").build();
        svcA.getSpecificVolumes().add(shared);
        svcB.getSpecificVolumes().add(shared);
        List<VolumeMapping> common = List.of(shared);
        svcA.getSpecificVolumes().clear();
        svcB.getSpecificVolumes().clear();
        Path outDir = Files.createTempDirectory("compose-test-multi-parent-common");
        ComposeFileGenerator.builder()
                .services(List.of(svcA, svcB))
                .moduleName("demo-system")
                .commonVolumes(common)
                .outputDir(outDir.toString())
                .createEnvironmentFile(false)
                .build()
                .generateDockerCompose();
        String content = Files.readString(outDir.resolve("docker-compose.yml"));
        assertThat(content).contains("x-demo-system-common:")
                           .contains("volumes:")
                           .contains("- ../ssl:/opt/ssl");
    }

    @Test
    @DisplayName("Multi-module: no volumes at all -> no volumes key anywhere")
    void multiModuleNoVolumesProducesNoSection() throws IOException {
        DockerService svcA = baseService("svc-a");
        DockerService svcB = baseService("svc-b");
        Path outDir = Files.createTempDirectory("compose-test-multi-novol");
        ComposeFileGenerator.builder()
                .services(List.of(svcA, svcB))
                .moduleName("demo-system")
                .outputDir(outDir.toString())
                .createEnvironmentFile(false)
                .build()
                .generateDockerCompose();
        String content = Files.readString(outDir.resolve("docker-compose.yml"));
        // ensure no stray 'volumes:' appears
        assertThat(content).doesNotContain("volumes:");
    }
}