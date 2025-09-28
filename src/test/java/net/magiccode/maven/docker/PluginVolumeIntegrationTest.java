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

import net.magiccode.maven.Volume;

/**
 * Integration tests for volume configuration support
 */
public class PluginVolumeIntegrationTest {

    @Test
    @DisplayName("Plugin volume configuration creates proper volume mappings")
    void pluginVolumeConfiguration() throws IOException {
        // Test that Volume configuration objects are properly converted to VolumeMapping objects
        
        // Create volume configurations like they would be defined in pom.xml
        List<Volume> configuredVolumes = new ArrayList<>();
        
        Volume sslVolume = new Volume();
        sslVolume.setExternal("../ssl");
        sslVolume.setInternal("/opt/ssl");
        configuredVolumes.add(sslVolume);
        
        Volume dataVolume = new Volume();
        dataVolume.setExternal("./data");
        dataVolume.setInternal("/var/data");
        configuredVolumes.add(dataVolume);

        // Simulate the conversion logic from DockerComposePlugin.generateService()
        List<VolumeMapping> volumeMappings = new ArrayList<>();
        for (Volume volume : configuredVolumes) {
            if (volume.getExternal() != null && volume.getInternal() != null) {
                VolumeMapping mapping = VolumeMapping.builder()
                        .external(volume.getExternal())
                        .internal(volume.getInternal())
                        .build();
                volumeMappings.add(mapping);
            }
        }

        // Test that the mappings were created correctly
        assertThat(volumeMappings).hasSize(2);
        assertThat(volumeMappings.get(0).getExternal()).isEqualTo("../ssl");
        assertThat(volumeMappings.get(0).getInternal()).isEqualTo("/opt/ssl");
        assertThat(volumeMappings.get(1).getExternal()).isEqualTo("./data");
        assertThat(volumeMappings.get(1).getInternal()).isEqualTo("/var/data");

        // Test Docker Compose generation with these volumes
        DockerService service = DockerService.builder()
                .name("test-service")
                .version("1.0.0")
                .imagePrefix("demo/")
                .specificVolumes(volumeMappings)
                .dockerEnvVars(Map.of("SERVER_PORT", "8080"))
                .build();

        Path outDir = Files.createTempDirectory("plugin-volume-test");
        ComposeFileGenerator.builder()
                .services(List.of(service))
                .moduleName("test-app")
                .outputDir(outDir.toString())
                .createEnvironmentFile(false)
                .build()
                .generateDockerCompose();

        String content = Files.readString(outDir.resolve("docker-compose.yml"));
        assertThat(content).contains("volumes:");
        assertThat(content).contains("- ../ssl:/opt/ssl");
        assertThat(content).contains("- ./data:/var/data");
    }

    @Test 
    @DisplayName("Multi-module volume commonization works correctly")
    void multiModuleVolumeCommonization() throws IOException {
        // Create two services with some common and some specific volumes
        DockerService serviceA = DockerService.builder()
                .name("service-a")
                .version("1.0.0")
                .imagePrefix("demo/")
                .dockerEnvVars(Map.of("SERVER_PORT", "8080"))
                .specificVolumes(new ArrayList<>())
                .build();

        DockerService serviceB = DockerService.builder()
                .name("service-b") 
                .version("1.0.0")
                .imagePrefix("demo/")
                .dockerEnvVars(Map.of("SERVER_PORT", "8081"))
                .specificVolumes(new ArrayList<>())
                .build();

        // Add common volume to both services
        VolumeMapping commonVol = VolumeMapping.builder()
                .external("../ssl")
                .internal("/opt/ssl")
                .build();
        serviceA.getSpecificVolumes().add(commonVol);
        serviceB.getSpecificVolumes().add(commonVol);

        // Add specific volume only to serviceA
        VolumeMapping specificVol = VolumeMapping.builder()
                .external("./logs")
                .internal("/var/log")
                .build();
        serviceA.getSpecificVolumes().add(specificVol);

        List<DockerService> services = List.of(serviceA, serviceB);

        // Simulate the volume commonization logic
        List<VolumeMapping> commonVolumes = new ArrayList<>();
        Map<VolumeMapping, Integer> volumeCounts = new HashMap<>();
        
        for (DockerService service : services) {
            for (VolumeMapping volume : service.getSpecificVolumes()) {
                volumeCounts.put(volume, volumeCounts.getOrDefault(volume, 0) + 1);
            }
        }

        for (Map.Entry<VolumeMapping, Integer> entry : volumeCounts.entrySet()) {
            if (entry.getValue() >= 2) {
                commonVolumes.add(entry.getKey());
            }
        }

        // Apply the commonization logic
        for (DockerService service : services) {
            service.getSpecificVolumes().removeAll(commonVolumes);
            
            boolean hasAdditionalVolumes = !service.getSpecificVolumes().isEmpty();
            if (hasAdditionalVolumes) {
                // Services with additional volumes should list ALL volumes directly
                List<VolumeMapping> allVolumes = new ArrayList<>(commonVolumes);
                allVolumes.addAll(service.getSpecificVolumes());
                service.getSpecificVolumes().clear();
                service.getSpecificVolumes().addAll(allVolumes);
            }
            // If no additional volumes, specificVolumes remains empty (only common volumes via merge)
        }

        // Generate the compose file
        Path outDir = Files.createTempDirectory("multi-module-volume-test");
        ComposeFileGenerator.builder()
                .services(services)
                .moduleName("multi-app")
                .commonVolumes(commonVolumes)
                .outputDir(outDir.toString())
                .createEnvironmentFile(false)
                .build()
                .generateDockerCompose();

        String content = Files.readString(outDir.resolve("docker-compose.yml"));
        
        // Check common volume anchor
        assertThat(content).contains("x-multi-app-common:")
                           .contains("volumes:")
                           .contains("&multi-app-volumes")
                           .contains("- ../ssl:/opt/ssl");

        // Check service-a has both common and specific volumes
        assertThat(content).containsPattern("service-a:\n(?s).*volumes:\n(?s).*- ../ssl:/opt/ssl\n(?s).*- ./logs:/var/log");

        // Check service-b has no volumes section (no specific volumes)
        assertThat(content).contains("service-b:").doesNotContainPattern("service-b:\n(?s).*volumes:");
    }
}