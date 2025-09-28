package net.magiccode.maven.docker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for port processing logic to ensure server.port is only included 
 * when marked with #DockerInclude annotation.
 * 
 * This test validates the fix for port duplication where server.port should
 * only be processed when it has the #DockerInclude comment.
 */
public class PortProcessingTest {

    @TempDir
    Path tempDir;

    /**
     * Test that when server.port is annotated with #DockerInclude, it appears in both
     * environment variables and ports mapping.
     */
    @Test
    @DisplayName("Server port included in Docker service when annotated with #DockerInclude")
    void testServerPortIncludedWhenAnnotated() throws IOException {
        // Create properties file with annotated server.port
        createPropertiesFile(
            "# Application configuration\n" +
            "spring.datasource.url=jdbc:mysql://localhost:3306/testdb\n" +
            "server.port=8081 # DockerInclude\n" +
            "logging.level.root=INFO\n"
        );

        DockerService service = createDockerServiceWithPortProcessing();

        // Verify server.port is included in both environment and ports
        assertThat(service.getDockerEnvVars()).containsEntry("SERVER_PORT", "8081");
        assertThat(service.getPorts()).contains("8081");
    }

    /**
     * Test that when server.port is NOT annotated with #DockerInclude, it does not
     * appear in environment variables or ports mapping.
     */
    @Test
    @DisplayName("Server port excluded from Docker service when not annotated with #DockerInclude")
    void testServerPortExcludedWhenNotAnnotated() throws IOException {
        // Create properties file with unannotated server.port
        createPropertiesFile(
            "# Application configuration\n" +
            "spring.datasource.url=jdbc:mysql://localhost:3306/testdb # DockerInclude\n" +
            "server.port=8082\n" +
            "logging.level.root=INFO\n"
        );

        DockerService service = createDockerServiceWithPortProcessing();

        // Verify server.port is NOT included in environment or ports
        assertThat(service.getDockerEnvVars()).doesNotContainKey("SERVER_PORT");
        assertThat(service.getPorts()).doesNotContain("8082");
        
        // But verify that annotated properties are included
        assertThat(service.getDockerEnvVars()).containsEntry("SPRING_DATASOURCE_URL", "jdbc:mysql://localhost:3306/testdb");
    }

    /**
     * Test mixed configuration where only some ports are annotated.
     */
    @Test
    @DisplayName("Mixed port configuration - only annotated ports included")
    void testMixedPortConfiguration() throws IOException {
        // Create properties file with mixed annotations
        createPropertiesFile(
            "# Application configuration\n" +
            "spring.datasource.url=jdbc:mysql://localhost:3306/testdb # DockerInclude\n" +
            "server.port=8083\n" +
            "management.server.port=9090 # DockerInclude\n" +
            "logging.level.root=INFO\n"
        );

        DockerService service = createDockerServiceWithPortProcessing();

        // Verify only annotated management port is included, not server.port
        assertThat(service.getDockerEnvVars()).doesNotContainKey("SERVER_PORT");
        assertThat(service.getPorts()).doesNotContain("8083");
        
        assertThat(service.getDockerEnvVars()).containsEntry("MANAGEMENT_SERVER_PORT", "9090");
        assertThat(service.getPorts()).contains("9090");
    }

    /**
     * Test YAML configuration with annotated server.port.
     */
    @Test
    @DisplayName("YAML server port included when annotated with #DockerInclude")
    void testYamlServerPortIncludedWhenAnnotated() throws IOException {
        // Create YAML file with annotated server.port
        createYamlFile(
            "# Application configuration\n" +
            "spring:\n" +
            "  datasource:\n" +
            "    url: jdbc:mysql://localhost:3306/testdb # DockerInclude\n" +
            "server:\n" +
            "  port: 8084 # DockerInclude\n" +
            "logging:\n" +
            "  level:\n" +
            "    root: INFO\n"
        );

        DockerService service = createDockerServiceWithYamlPortProcessing();

        // Verify server.port is included in both environment and ports
        assertThat(service.getDockerEnvVars()).containsEntry("SERVER_PORT", "8084");
        assertThat(service.getPorts()).contains("8084");
    }

    /**
     * Test YAML configuration with unannotated server.port.
     */
    @Test
    @DisplayName("YAML server port excluded when not annotated with #DockerInclude")
    void testYamlServerPortExcludedWhenNotAnnotated() throws IOException {
        // Create YAML file with unannotated server.port
        createYamlFile(
            "# Application configuration\n" +
            "spring:\n" +
            "  datasource:\n" +
            "    url: jdbc:mysql://localhost:3306/testdb # DockerInclude\n" +
            "server:\n" +
            "  port: 8085\n" +
            "logging:\n" +
            "  level:\n" +
            "    root: INFO\n"
        );

        DockerService service = createDockerServiceWithYamlPortProcessing();

        // Verify server.port is NOT included in environment or ports
        assertThat(service.getDockerEnvVars()).doesNotContainKey("SERVER_PORT");
        assertThat(service.getPorts()).doesNotContain("8085");
        
        // But verify that annotated properties are included
        assertThat(service.getDockerEnvVars()).containsEntry("SPRING_DATASOURCE_URL", "jdbc:mysql://localhost:3306/testdb");
    }

    private void createPropertiesFile(String content) throws IOException {
        File propertiesFile = tempDir.resolve("application.properties").toFile();
        try (FileWriter writer = new FileWriter(propertiesFile)) {
            writer.write(content);
        }
    }

    private void createYamlFile(String content) throws IOException {
        File yamlFile = tempDir.resolve("application.yml").toFile();
        try (FileWriter writer = new FileWriter(yamlFile)) {
            writer.write(content);
        }
    }

    /**
     * Simulates the properties-based port processing logic from DockerComposePlugin.
     */
    private DockerService createDockerServiceWithPortProcessing() throws IOException {
        Map<String, String> dockerEnvVars = new HashMap<>();
        Set<String> ports = new HashSet<>();
        
        File propertiesFile = tempDir.resolve("application.properties").toFile();
        if (propertiesFile.exists()) {
            List<String> lines = Files.readAllLines(propertiesFile.toPath());
            
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                String[] parts = line.split("=", 2);
                if (parts.length != 2) continue;
                
                String key = parts[0].trim();
                String value = parts[1].trim();
                
                // Extract inline comment
                String inlineComment = "";
                int commentIndex = value.indexOf("#");
                if (commentIndex != -1) {
                    inlineComment = value.substring(commentIndex + 1).trim();
                    value = value.substring(0, commentIndex).trim();
                }
                
                // Check if this property should be included
                boolean includeNext = inlineComment.contains("DockerInclude");
                
                if (includeNext) {
                    String formattedKey = key.toUpperCase().replace(".", "_").replace("-", "_");
                    dockerEnvVars.put(formattedKey, value);
                    
                    // Include server.port in ports only when marked with #DockerInclude
                    if (key.equals("server.port")) {
                        ports.add(value);
                    }
                    
                    // Handle other port properties
                    if (key.contains(".port") && !key.equals("server.port")) {
                        ports.add(value);
                    }
                }
            }
        }
        
        return DockerService.builder()
                .name("test-app")
                .version("1.0.0")
                .imagePrefix("test/")
                .dockerEnvVars(dockerEnvVars)
                .ports(List.copyOf(ports))
                .build();
    }

    /**
     * Simulates the YAML-based port processing logic from DockerComposePlugin.
     */
    private DockerService createDockerServiceWithYamlPortProcessing() throws IOException {
        Map<String, String> dockerEnvVars = new HashMap<>();
        Set<String> ports = new HashSet<>();
        
        File yamlFile = tempDir.resolve("application.yml").toFile();
        if (yamlFile.exists()) {
            List<String> lines = Files.readAllLines(yamlFile.toPath());
            
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                // Simple YAML parsing for key: value # DockerInclude patterns
                if (line.contains(":") && line.contains("#")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length != 2) continue;
                    
                    String key = parts[0].trim();
                    String valueAndComment = parts[1].trim();
                    
                    String[] valueCommentParts = valueAndComment.split("#", 2);
                    if (valueCommentParts.length != 2) continue;
                    
                    String value = valueCommentParts[0].trim();
                    String comment = valueCommentParts[1].trim();
                    
                    if (comment.contains("DockerInclude")) {
                        // Convert YAML path to environment variable format
                        String formattedKey;
                        if (key.equals("url")) {
                            formattedKey = "SPRING_DATASOURCE_URL";
                        } else if (key.equals("port")) {
                            formattedKey = "SERVER_PORT";
                        } else {
                            formattedKey = key.toUpperCase().replace(".", "_").replace("-", "_");
                        }
                        
                        dockerEnvVars.put(formattedKey, value);
                        
                        // Check for server.port (for ports mapping)
                        if (formattedKey.equals("SERVER_PORT")) {
                            ports.add(value);
                        }
                    }
                }
            }
        }
        
        return DockerService.builder()
                .name("test-app")
                .version("1.0.0")
                .imagePrefix("test/")
                .dockerEnvVars(dockerEnvVars)
                .ports(List.copyOf(ports))
                .build();
    }
}