package net.magiccode.maven.docker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.magiccode.maven.Volume;

/**
 * Integration test for volume configuration as used in real multi-module projects
 */
public class RealWorldVolumeIntegrationTest {

    @Test
    @DisplayName("Multi-module project with volume configuration generates volumes in docker-compose")
    void multiModuleProjectWithVolumeConfiguration(@TempDir Path tempDir) throws IOException {
        // Create a realistic multi-module project structure
        createMultiModuleProject(tempDir);
        
        // Test volume configuration as the user provided it
        List<Volume> configuredVolumes = new ArrayList<>();
        
        // This simulates the volume configuration from the user's pom.xml:
        // <volume external="../ssl" internal="/opt/ssl" />
        Volume sslVolume = new Volume();
        sslVolume.setExternal("../ssl");
        sslVolume.setInternal("/opt/ssl");
        configuredVolumes.add(sslVolume);
        
        // Create services for both modules with the same volume configuration
        List<DockerService> services = new ArrayList<>();
        
        // Service A (user-service module)
        DockerService serviceA = DockerService.builder()
                .name("user-service")
                .version("1.0.0")
                .imagePrefix("nexus.riskcontrollimited.com:8891/rcl/")
                .specificVolumes(convertVolumesToMappings(configuredVolumes))
                .dockerEnvVars(java.util.Map.of("SERVER_PORT", "8080"))
                .build();
        
        // Service B (order-service module) 
        DockerService serviceB = DockerService.builder()
                .name("order-service")
                .version("1.0.0")
                .imagePrefix("nexus.riskcontrollimited.com:8891/rcl/")
                .specificVolumes(convertVolumesToMappings(configuredVolumes))
                .dockerEnvVars(java.util.Map.of("SERVER_PORT", "8081"))
                .build();
                
        services.add(serviceA);
        services.add(serviceB);
        
        // Simulate the commonization logic from DockerComposePlugin.compileCommonVolumes()
        List<VolumeMapping> commonVolumes = findCommonVolumes(services);
        
        // Apply commonization
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
        }
        
        // Generate docker-compose file
        Path outDir = tempDir.resolve("output");
        Files.createDirectories(outDir);
        
        ComposeFileGenerator.builder()
                .services(services)
                .moduleName("ecommerce-system")
                .commonVolumes(commonVolumes)
                .outputDir(outDir.toString())
                .createEnvironmentFile(false)
                .build()
                .generateDockerCompose();
        
        // Verify the generated docker-compose.yml file
        Path dockerComposeFile = outDir.resolve("docker-compose.yml");
        assertThat(dockerComposeFile).exists();
        
        String content = Files.readString(dockerComposeFile);
        System.out.println("Generated docker-compose.yml:");
        System.out.println(content);
        
        // Assertions - this is what should happen but might not be working
        assertThat(content).contains("x-ecommerce-system-common:");
        assertThat(content).contains("volumes:");
        assertThat(content).contains("&ecommerce-system-volumes");
        assertThat(content).contains("- ../ssl:/opt/ssl");
        
        // Both services should use common volumes (no additional volumes in this case)
        // They should NOT have individual volumes sections since all volumes are common
        long volumesSectionCount = content.lines()
                .filter(line -> line.trim().equals("volumes:"))
                .count();
        assertThat(volumesSectionCount).isEqualTo(1); // Only in the common anchor
        
        // Verify services don't have individual volumes sections
        assertThat(content).doesNotContainPattern("user-service:\n(?s).*volumes:");
        assertThat(content).doesNotContainPattern("order-service:\n(?s).*volumes:");
    }
    
    @Test
    @DisplayName("Single module project with volume configuration generates volumes in docker-compose")
    void singleModuleProjectWithVolumeConfiguration(@TempDir Path tempDir) throws IOException {
        // Test the exact configuration the user provided
        Volume sslVolume = new Volume();
        sslVolume.setExternal("../ssl");
        sslVolume.setInternal("/opt/ssl");
        
        List<VolumeMapping> volumeMappings = convertVolumesToMappings(List.of(sslVolume));
        
        DockerService service = DockerService.builder()
                .name("my-app")
                .version("1.0.0")
                .imagePrefix("nexus.riskcontrollimited.com:8891/rcl/")
                .specificVolumes(volumeMappings)
                .dockerEnvVars(java.util.Map.of("SERVER_PORT", "8080"))
                .build();
        
        Path outDir = tempDir.resolve("output");
        Files.createDirectories(outDir);
        
        ComposeFileGenerator.builder()
                .services(List.of(service))
                .moduleName("my-app")
                .outputDir(outDir.toString())
                .createEnvironmentFile(false)
                .build()
                .generateDockerCompose();
        
        Path dockerComposeFile = outDir.resolve("docker-compose.yml");
        assertThat(dockerComposeFile).exists();
        
        String content = Files.readString(dockerComposeFile);
        System.out.println("Single module docker-compose.yml:");
        System.out.println(content);
        
        // For single module, volumes should be directly in the service
        assertThat(content).contains("my-app:");
        assertThat(content).contains("volumes:");
        assertThat(content).contains("- ../ssl:/opt/ssl");
    }
    
    private void createMultiModuleProject(Path baseDir) throws IOException {
        // Create a realistic multi-module structure
        Path userServiceDir = baseDir.resolve("user-service");
        Path orderServiceDir = baseDir.resolve("order-service");
        
        Files.createDirectories(userServiceDir.resolve("src/main/java/com/example/user"));
        Files.createDirectories(orderServiceDir.resolve("src/main/java/com/example/order"));
        
        // Create Spring Boot applications
        String userAppContent = """
                package com.example.user;
                
                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                
                @SpringBootApplication
                public class UserServiceApplication {
                    public static void main(String[] args) {
                        SpringApplication.run(UserServiceApplication.class, args);
                    }
                }
                """;
        Files.writeString(userServiceDir.resolve("src/main/java/com/example/user/UserServiceApplication.java"), userAppContent);
        
        String orderAppContent = """
                package com.example.order;
                
                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                
                @SpringBootApplication
                public class OrderServiceApplication {
                    public static void main(String[] args) {
                        SpringApplication.run(OrderServiceApplication.class, args);
                    }
                }
                """;
        Files.writeString(orderServiceDir.resolve("src/main/java/com/example/order/OrderServiceApplication.java"), orderAppContent);
    }
    
    private List<VolumeMapping> convertVolumesToMappings(List<Volume> volumes) {
        List<VolumeMapping> mappings = new ArrayList<>();
        if (volumes != null) {
            for (Volume volume : volumes) {
                if (volume.getExternal() != null && volume.getInternal() != null) {
                    VolumeMapping mapping = VolumeMapping.builder()
                            .external(volume.getExternal())
                            .internal(volume.getInternal())
                            .build();
                    mappings.add(mapping);
                }
            }
        }
        return mappings;
    }
    
    private List<VolumeMapping> findCommonVolumes(List<DockerService> services) {
        List<VolumeMapping> commonVolumes = new ArrayList<>();
        java.util.Map<VolumeMapping, Integer> volumeCounts = new java.util.HashMap<>();
        
        // Count occurrences of each volume across services
        for (DockerService service : services) {
            for (VolumeMapping volume : service.getSpecificVolumes()) {
                volumeCounts.put(volume, volumeCounts.getOrDefault(volume, 0) + 1);
            }
        }
        
        // Find volumes that appear in at least 2 services
        for (java.util.Map.Entry<VolumeMapping, Integer> entry : volumeCounts.entrySet()) {
            if (entry.getValue() >= 2) {
                commonVolumes.add(entry.getKey());
            }
        }
        
        return commonVolumes;
    }
}