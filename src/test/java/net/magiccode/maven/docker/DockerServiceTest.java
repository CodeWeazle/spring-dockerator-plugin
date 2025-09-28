package net.magiccode.maven.docker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for {@link DockerService} class functionality.
 * Tests the builder pattern, data model, and service generation methods.
 * 
 * @author GitHub Copilot
 */
public class DockerServiceTest {

    @Test
    @DisplayName("Should create DockerService with builder pattern and default values")
    void shouldCreateDockerServiceWithDefaults() {
        // Given & When
        DockerService service = DockerService.builder()
                .name("test-service")
                .version("1.0.0")
                .imagePrefix("registry.example.com/")
                .build();

        // Then
        assertThat(service.getName()).isEqualTo("test-service");
        assertThat(service.getVersion()).isEqualTo("1.0.0");
        assertThat(service.getImagePrefix()).isEqualTo("registry.example.com/");
        assertThat(service.getDockerEnvVars()).isEmpty();
        assertThat(service.getJdbcConfigs()).isEmpty();
        assertThat(service.getVolumes()).isEmpty();
        assertThat(service.getPorts()).isEmpty();
        assertThat(service.getSpecificVolumes()).isEmpty();
        assertThat(service.isCreateEnvironmentFile()).isFalse();
    }

    @Test
    @DisplayName("Should create DockerService with custom environment variables")
    void shouldCreateDockerServiceWithEnvironmentVariables() {
        // Given
        Map<String, String> envVars = Map.of(
                "SERVER_PORT", "8080",
                "DATABASE_URL", "jdbc:postgresql://db:5432/app"
        );

        // When
        DockerService service = DockerService.builder()
                .name("web-service")
                .version("2.1.0")
                .imagePrefix("myregistry/")
                .dockerEnvVars(envVars)
                .build();

        // Then
        assertThat(service.getDockerEnvVars()).hasSize(2);
        assertThat(service.getDockerEnvVars()).containsEntry("SERVER_PORT", "8080");
        assertThat(service.getDockerEnvVars()).containsEntry("DATABASE_URL", "jdbc:postgresql://db:5432/app");
    }

    @Test
    @DisplayName("Should create DockerService with ports configuration")
    void shouldCreateDockerServiceWithPorts() {
        // Given
        List<String> ports = List.of("8080", "9090", "3000");

        // When
        DockerService service = DockerService.builder()
                .name("multi-port-service")
                .version("1.5.0")
                .imagePrefix("localhost:5000/")
                .ports(ports)
                .build();

        // Then
        assertThat(service.getPorts()).hasSize(3);
        assertThat(service.getPorts()).containsExactly("8080", "9090", "3000");
    }

    @Test
    @DisplayName("Should create DockerService with JDBC configurations")
    void shouldCreateDockerServiceWithJdbcConfigs() {
        // Given
        Map<String, String> jdbcConfigs = Map.of(
                "driver", "org.postgresql.Driver",
                "url", "jdbc:postgresql://localhost:5432/testdb",
                "username", "testuser"
        );

        // When
        DockerService service = DockerService.builder()
                .name("database-service")
                .version("3.0.0")
                .imagePrefix("db.example.com/")
                .jdbcConfigs(jdbcConfigs)
                .build();

        // Then
        assertThat(service.getJdbcConfigs()).hasSize(3);
        assertThat(service.getJdbcConfigs()).containsEntry("driver", "org.postgresql.Driver");
        assertThat(service.getJdbcConfigs()).containsEntry("url", "jdbc:postgresql://localhost:5432/testdb");
        assertThat(service.getJdbcConfigs()).containsEntry("username", "testuser");
    }

    @Test
    @DisplayName("Should create DockerService with volume mappings")
    void shouldCreateDockerServiceWithVolumeMapping() {
        // Given
        List<VolumeMapping> volumes = List.of(
                VolumeMapping.builder().external("./data").internal("/var/data").build(),
                VolumeMapping.builder().external("../ssl").internal("/opt/ssl").build()
        );

        // When
        DockerService service = DockerService.builder()
                .name("volume-service")
                .version("1.2.0")
                .imagePrefix("volumes.example.com/")
                .specificVolumes(volumes)
                .build();

        // Then
        assertThat(service.getSpecificVolumes()).hasSize(2);
        assertThat(service.getSpecificVolumes().get(0).getExternal()).isEqualTo("./data");
        assertThat(service.getSpecificVolumes().get(0).getInternal()).isEqualTo("/var/data");
        assertThat(service.getSpecificVolumes().get(1).getExternal()).isEqualTo("../ssl");
        assertThat(service.getSpecificVolumes().get(1).getInternal()).isEqualTo("/opt/ssl");
    }

    @Test
    @DisplayName("Should create DockerService with environment file enabled")
    void shouldCreateDockerServiceWithEnvironmentFile() {
        // Given & When
        DockerService service = DockerService.builder()
                .name("env-file-service")
                .version("1.0.0")
                .imagePrefix("envs.example.com/")
                .createEnvironmentFile(true)
                .build();

        // Then
        assertThat(service.isCreateEnvironmentFile()).isTrue();
    }

    @Test
    @DisplayName("Should generate basic service entry without common configuration")
    void shouldGenerateBasicServiceEntry() {
        // Given
        DockerService service = DockerService.builder()
                .name("simple-service")
                .version("1.0.0")
                .imagePrefix("registry.com/")
                .dockerEnvVars(Map.of("PORT", "8080", "ENV", "production"))
                .ports(List.of("8080"))
                .build();

        // When
        String serviceEntry = service.generateServiceEntry();

        // Then
        assertThat(serviceEntry).contains("simple-service:");
        assertThat(serviceEntry).contains("image: registry.com/simple-service:1.0.0");
        assertThat(serviceEntry).contains("environment:");
        assertThat(serviceEntry).contains("- ENV=production");
        assertThat(serviceEntry).contains("- PORT=8080");
        assertThat(serviceEntry).contains("ports:");
        assertThat(serviceEntry).contains("- \"8080:8080\"");
        // Should not contain common configuration references
        assertThat(serviceEntry).doesNotContain("<<:");
    }

    @Test
    @DisplayName("Should generate service entry with common configuration")
    void shouldGenerateServiceEntryWithCommonConfiguration() {
        // Given
        DockerService service = DockerService.builder()
                .name("common-service")
                .version("2.0.0")
                .imagePrefix("shared.registry.com/")
                .dockerEnvVars(Map.of("SERVICE_NAME", "common-service"))
                .build();

        // When
        String serviceEntry = service.generateServiceEntry("common-config", "common-env");

        // Then
        assertThat(serviceEntry).contains("common-service:");
        assertThat(serviceEntry).contains("<<: *common-config");
        assertThat(serviceEntry).contains("image: shared.registry.com/common-service:2.0.0");
        assertThat(serviceEntry).contains("environment:");
        assertThat(serviceEntry).contains("<<: *common-env");
        assertThat(serviceEntry).contains("SERVICE_NAME: common-service");
    }

    @Test
    @DisplayName("Should generate service entry with volumes")
    void shouldGenerateServiceEntryWithVolumes() {
        // Given
        List<VolumeMapping> volumes = List.of(
                VolumeMapping.builder().external("./logs").internal("/var/log").build(),
                VolumeMapping.builder().external("../config").internal("/etc/app").build()
        );
        
        DockerService service = DockerService.builder()
                .name("volume-service")
                .version("1.0.0")
                .imagePrefix("volumes/")
                .dockerEnvVars(Map.of("APP_NAME", "volume-service"))
                .specificVolumes(volumes)
                .build();

        // When
        String serviceEntry = service.generateServiceEntry();

        // Then
        assertThat(serviceEntry).contains("volume-service:");
        assertThat(serviceEntry).contains("volumes:");
        assertThat(serviceEntry).contains("- ./logs:/var/log");
        assertThat(serviceEntry).contains("- ../config:/etc/app");
    }

    @Test
    @DisplayName("Should generate service entry with multiple ports sorted")
    void shouldGenerateServiceEntryWithSortedPorts() {
        // Given
        DockerService service = DockerService.builder()
                .name("multi-port-service")
                .version("1.0.0")
                .imagePrefix("ports/")
                .dockerEnvVars(Map.of("SERVICE", "multi"))
                .ports(List.of("9090", "3000", "8080")) // Unsorted input
                .build();

        // When
        String serviceEntry = service.generateServiceEntry();

        // Then
        assertThat(serviceEntry).contains("ports:");
        String[] lines = serviceEntry.split("\n");
        
        // Find port lines and verify they are sorted
        boolean foundPortsSection = false;
        String previousPort = null;
        for (String line : lines) {
            if (line.trim().equals("ports:")) {
                foundPortsSection = true;
                continue;
            }
            if (foundPortsSection && line.contains("- \"")) {
                String portLine = line.trim();
                if (portLine.startsWith("- \"")) {
                    String currentPort = portLine.substring(3, portLine.indexOf(':'));
                    if (previousPort != null) {
                        assertThat(currentPort.compareTo(previousPort)).isGreaterThan(0);
                    }
                    previousPort = currentPort;
                }
            } else if (foundPortsSection && !line.trim().isEmpty() && !line.startsWith("      - \"")) {
                break; // End of ports section
            }
        }
        
        // Verify all ports are present
        assertThat(serviceEntry).contains("\"3000:3000\"");
        assertThat(serviceEntry).contains("\"8080:8080\"");
        assertThat(serviceEntry).contains("\"9090:9090\"");
    }

    @Test
    @DisplayName("Should generate service entry with sorted environment variables")
    void shouldGenerateServiceEntryWithSortedEnvironmentVariables() {
        // Given
        Map<String, String> envVars = Map.of(
                "ZEBRA_CONFIG", "last",
                "ALPHA_CONFIG", "first",
                "BETA_CONFIG", "middle"
        );
        
        DockerService service = DockerService.builder()
                .name("sorted-env-service")
                .version("1.0.0")
                .imagePrefix("sorted/")
                .dockerEnvVars(envVars)
                .build();

        // When
        String serviceEntry = service.generateServiceEntry();

        // Then
        String[] lines = serviceEntry.split("\n");
        
        // Find environment variable lines and verify they are sorted
        boolean foundEnvSection = false;
        String previousEnvVar = null;
        for (String line : lines) {
            if (line.trim().equals("environment:")) {
                foundEnvSection = true;
                continue;
            }
            if (foundEnvSection && line.contains("- ") && line.contains("=")) {
                String envLine = line.trim();
                String currentEnvVar = envLine.substring(2, envLine.indexOf('='));
                if (previousEnvVar != null) {
                    assertThat(currentEnvVar.compareTo(previousEnvVar)).isGreaterThan(0);
                }
                previousEnvVar = currentEnvVar;
            } else if (foundEnvSection && !line.trim().isEmpty() && !line.startsWith("      - ")) {
                break; // End of environment section
            }
        }
        
        // Verify all environment variables are present
        assertThat(serviceEntry).contains("ALPHA_CONFIG=first");
        assertThat(serviceEntry).contains("BETA_CONFIG=middle");
        assertThat(serviceEntry).contains("ZEBRA_CONFIG=last");
    }

    @Test
    @DisplayName("Should generate service entry with environment file references")
    void shouldGenerateServiceEntryWithEnvironmentFileReferences() {
        // Given
        DockerService service = DockerService.builder()
                .name("env-file-service")
                .version("1.0.0")
                .imagePrefix("envfile/")
                .dockerEnvVars(Map.of("DATABASE_URL", "jdbc:h2:mem:test"))
                .createEnvironmentFile(true)
                .build();

        // When
        String serviceEntry = service.generateServiceEntry();

        // Then
        assertThat(serviceEntry).contains("DATABASE_URL=${ENV-FILE-SERVICE_DATABASE_URL}");
        // Should contain environment variable reference format for env files
    }

    @Test
    @DisplayName("Should handle empty configurations gracefully")
    void shouldHandleEmptyConfigurationsGracefully() {
        // Given
        DockerService service = DockerService.builder()
                .name("minimal-service")
                .version("1.0.0")
                .imagePrefix("minimal/")
                .build();

        // When
        String serviceEntry = service.generateServiceEntry();

        // Then
        assertThat(serviceEntry).contains("minimal-service:");
        assertThat(serviceEntry).contains("image: minimal/minimal-service:1.0.0");
        assertThat(serviceEntry).contains("environment:");
        // Should not contain ports or volumes sections
        assertThat(serviceEntry).doesNotContain("ports:");
        assertThat(serviceEntry).doesNotContain("volumes:");
    }

    @Test
    @DisplayName("Should generate complete service entry with all features")
    void shouldGenerateCompleteServiceEntryWithAllFeatures() {
        // Given
        Map<String, String> envVars = Map.of(
                "APP_NAME", "complete-app",
                "VERSION", "1.0.0"
        );
        List<String> ports = List.of("8080", "9090");
        List<VolumeMapping> volumes = List.of(
                VolumeMapping.builder().external("./data").internal("/app/data").build()
        );
        
        DockerService service = DockerService.builder()
                .name("complete-service")
                .version("1.0.0")
                .imagePrefix("complete.registry.com/")
                .dockerEnvVars(envVars)
                .ports(ports)
                .specificVolumes(volumes)
                .createEnvironmentFile(false)
                .build();

        // When
        String serviceEntry = service.generateServiceEntry();

        // Then
        assertThat(serviceEntry).contains("complete-service:");
        assertThat(serviceEntry).contains("image: complete.registry.com/complete-service:1.0.0");
        assertThat(serviceEntry).contains("environment:");
        assertThat(serviceEntry).contains("- APP_NAME=complete-app");
        assertThat(serviceEntry).contains("- VERSION=1.0.0");
        assertThat(serviceEntry).contains("ports:");
        assertThat(serviceEntry).contains("- \"8080:8080\"");
        assertThat(serviceEntry).contains("- \"9090:9090\"");
        assertThat(serviceEntry).contains("volumes:");
        assertThat(serviceEntry).contains("- ./data:/app/data");
    }
}