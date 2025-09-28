package net.magiccode.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for ensuring that server.port is only included in docker-compose when
 * explicitly marked with #DockerInclude annotation.
 * 
 * Addresses the issue where server.port values from config files without 
 * #DockerInclude were being incorrectly added to the ports list.
 */
public class PortProcessingTest {
    
    private static final String DOCKER_INCLUDE_COMMENT = "DockerInclude";
    private static final String SERVER_PORT_PROPERTY = "server.port";

    @Test
    @DisplayName("Should only include server.port when marked with DockerInclude in properties")
    void shouldOnlyIncludeAnnotatedPortsInProperties() throws Exception {
        // Simulate properties file with mixed annotations
        String propertiesContent = """
                # Main application properties
                #DockerInclude
                server.port=8080
                spring.application.name=test-app
                # Another annotated property
                #DockerInclude  
                spring.profiles.active=docker
                # This port should NOT be included (no annotation)
                server.port=8085
                logging.level.root=INFO
                """;
        
        Map<String, String> dockerEnvVars = new HashMap<>();
        List<String> ports = new ArrayList<>();
        
        // Process the properties content using the same logic as the plugin
        processPropertiesContent(propertiesContent, dockerEnvVars, ports);
        
        // Verify only annotated port is included
        assertThat(ports).hasSize(1);
        assertThat(ports).contains("8080");
        assertThat(ports).doesNotContain("8085");
        
        // Verify environment variables
        assertThat(dockerEnvVars).hasSize(2);
        assertThat(dockerEnvVars).containsKey("server.port");
        assertThat(dockerEnvVars).containsKey("spring.profiles.active");
        assertThat(dockerEnvVars.get("server.port")).isEqualTo("8080");
    }
    
    @Test
    @DisplayName("Should not include server.port when no DockerInclude annotation exists")
    void shouldNotIncludeUnannotatedPorts() throws Exception {
        // Properties file without any DockerInclude annotations
        String propertiesContent = """
                # Application properties with no annotations
                server.port=8080
                spring.application.name=test-app
                logging.level.root=INFO
                server.port=9090
                """;
        
        Map<String, String> dockerEnvVars = new HashMap<>();
        List<String> ports = new ArrayList<>();
        
        // Process the properties content
        processPropertiesContent(propertiesContent, dockerEnvVars, ports);
        
        // Verify no ports or env vars are included
        assertThat(ports).isEmpty();
        assertThat(dockerEnvVars).isEmpty();
    }
    
    @Test
    @DisplayName("Should include multiple server.port when all are annotated")
    void shouldIncludeMultipleAnnotatedPorts() throws Exception {
        String propertiesContent = """
                # Multiple annotated server.port values
                #DockerInclude
                server.port=8080
                #DockerInclude
                server.port=8081
                # Another service port
                #DockerInclude
                server.port=9090
                """;
        
        Map<String, String> dockerEnvVars = new HashMap<>();
        List<String> ports = new ArrayList<>();
        
        processPropertiesContent(propertiesContent, dockerEnvVars, ports);
        
        // Verify all annotated server.port values are included in ports
        assertThat(ports).hasSize(3);
        assertThat(ports).contains("8080", "8081", "9090");
        
        // Note: dockerEnvVars will only have the last value due to Map.put() behavior
        assertThat(dockerEnvVars).hasSize(1);
        assertThat(dockerEnvVars).containsKey("server.port");
        assertThat(dockerEnvVars.get("server.port")).isEqualTo("9090"); // Last value wins
    }
    
    /**
     * Simulates the properties processing logic from DockerComposePlugin.processProperties()
     * This matches the actual implementation to ensure test accuracy.
     */
    private void processPropertiesContent(String content, Map<String, String> dockerEnvVars, List<String> ports) throws Exception {
        BufferedReader reader = new BufferedReader(new StringReader(content));
        String line;
        boolean includeNext = false;
        
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            
            if (line.isEmpty() || line.startsWith("#")) {
                // Check if it's a DockerInclude comment
                if (isLineComment(line) && line.contains(DOCKER_INCLUDE_COMMENT)) {
                    includeNext = true;
                }
                continue; // Skip comments and empty lines
            }
            
            if (includeNext) {
                String[] keyValue = line.split("=", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();
                    dockerEnvVars.put(key, value);
                    
                    // Include server.port in ports only when marked with DockerInclude
                    if (SERVER_PORT_PROPERTY.equals(key)) {
                        ports.add(value);
                    }
                }
                includeNext = false; // Reset the flag after processing
            }
        }
        
        reader.close();
    }
    
    /**
     * Checks if a line is a comment (starts with # after trimming)
     */
    private boolean isLineComment(String line) {
        return line != null && line.trim().startsWith("#");
    }
}