package net.magiccode.maven;

import net.magiccode.maven.docker.ComposeFileGenerator;
import net.magiccode.maven.docker.DockerService;
import net.magiccode.maven.docker.VolumeMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to reproduce the issue where services don't get the common volume reference.
 */
class CommonVolumeReferenceTest {

    @TempDir
    Path tempDir;

    private List<VolumeMapping> commonVolumes;
    private List<DockerService> services;

    @BeforeEach
    void setUp() {
        // Create common volumes
        commonVolumes = List.of(new VolumeMapping("../ssl", "/opt/ssl"));
        
        // Create services with cleared specific volumes (as would happen after compileCommonVolumes)
        DockerService serviceA = DockerService.builder()
            .name("service-a")
            .version("1.0.0")
            .imagePrefix("test/")
            .specificVolumes(new ArrayList<>()) // Empty - should use common reference
            .dockerEnvVars(new HashMap<>())
            .ports(new ArrayList<>())
            .build();
            
        DockerService serviceB = DockerService.builder()
            .name("service-b")
            .version("1.0.0")
            .imagePrefix("test/")
            .specificVolumes(new ArrayList<>()) // Empty - should use common reference
            .dockerEnvVars(new HashMap<>())
            .ports(new ArrayList<>())
            .build();
            
        services = List.of(serviceA, serviceB);
    }

    @Test
    void testCommonVolumeReferenceInServices() throws IOException {
        System.out.println("=== Testing Common Volume Reference Generation ===");
        
        // Create ComposeFileGenerator
        ComposeFileGenerator generator = ComposeFileGenerator.builder()
            .services(services)
            .outputDir(tempDir.toString())
            .moduleName("test-project")
            .commonVolumes(commonVolumes)
            .commonEnvironment(new HashMap<>())
            .build();
            
        // Generate the compose file
        generator.generateDockerCompose();
        
        // Read the generated file
        Path composeFile = tempDir.resolve("docker-compose.yml");
        String content = Files.readString(composeFile);
        
        System.out.println("Generated docker-compose.yml:");
        System.out.println(content);
        
        // Verify that common volumes section exists
        assertThat(content).contains("x-test-project-common:");
        assertThat(content).contains("&test-project-common");
        assertThat(content).contains("volumes:");
        assertThat(content).contains("&test-project-volumes");
        assertThat(content).contains("- ../ssl:/opt/ssl");
        
        // Verify that services reference the common section
        assertThat(content).contains("service-a:");
        assertThat(content).contains("service-b:");
        assertThat(content).contains("<<: *test-project-common");
        
        // Count how many times the common reference appears (should be 2, once per service)
        long commonRefCount = content.lines()
            .filter(line -> line.contains("<<: *test-project-common"))
            .count();
            
        System.out.println("Common references found: " + commonRefCount);
        assertThat(commonRefCount).isEqualTo(2);
        
        // Verify that services don't have individual volume sections
        // (since they should use the common reference)
        long volumesSectionCount = content.lines()
            .filter(line -> line.trim().equals("volumes:"))
            .count();
            
        System.out.println("Volume sections found: " + volumesSectionCount);
        // Should be 1 (in x-common section only)
        assertThat(volumesSectionCount).isEqualTo(1);
    }
}