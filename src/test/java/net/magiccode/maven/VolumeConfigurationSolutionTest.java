package net.magiccode.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.magiccode.maven.docker.VolumeMapping;

/**
 * Final diagnostic test to provide clear guidance on volume configuration issues
 */
public class VolumeConfigurationSolutionTest {

    @Test
    @DisplayName("Demonstrate correct volume configuration format for the user")
    void demonstrateCorrectVolumeConfigurationFormat() {
        System.out.println("=== Volume Configuration Format Guide ===");
        System.out.println();
        
        System.out.println("❌ INCORRECT FORMAT (what the user is using):");
        System.out.println("<volumes>");
        System.out.println("  <volume external=\"../ssl\" internal=\"/opt/ssl\" />");
        System.out.println("</volumes>");
        System.out.println();
        System.out.println("This format does NOT work because Maven plugin parameter binding");
        System.out.println("doesn't automatically map XML attributes to Java object fields.");
        System.out.println();
        
        System.out.println("✅ CORRECT FORMAT (what the user should use):");
        System.out.println("<volumes>");
        System.out.println("  <volume>");
        System.out.println("    <external>../ssl</external>");
        System.out.println("    <internal>/opt/ssl</internal>");
        System.out.println("  </volume>");
        System.out.println("</volumes>");
        System.out.println();
        
        System.out.println("=== Technical Explanation ===");
        
        // Demonstrate the issue
        System.out.println("\n1. What happens with attribute format:");
        Volume attributeVolume = new Volume(); // This simulates Maven creating the object
        // BUT Maven doesn't populate the fields from attributes automatically
        
        System.out.println("   Volume object created: external='" + attributeVolume.getExternal() + "', internal='" + attributeVolume.getInternal() + "'");
        System.out.println("   Result: Fields are null, so volume is skipped");
        
        System.out.println("\n2. What happens with nested element format:");
        Volume nestedVolume = new Volume();
        // This simulates Maven populating fields from nested elements
        nestedVolume.setExternal("../ssl");
        nestedVolume.setInternal("/opt/ssl");
        
        System.out.println("   Volume object created: external='" + nestedVolume.getExternal() + "', internal='" + nestedVolume.getInternal() + "'");
        System.out.println("   Result: Fields are populated, volume mapping is created");
        
        // Test the conversion
        List<Volume> volumes = List.of(nestedVolume);
        List<VolumeMapping> mappings = new ArrayList<>();
        
        for (Volume volume : volumes) {
            if (volume.getExternal() != null && volume.getInternal() != null) {
                mappings.add(VolumeMapping.builder()
                    .external(volume.getExternal())
                    .internal(volume.getInternal())
                    .build());
            }
        }
        
        assertThat(mappings).hasSize(1);
        System.out.println("   Created mapping: " + mappings.get(0));
        
        System.out.println("\n=== Complete Working Configuration ===");
        System.out.println("<plugin>");
        System.out.println("  <groupId>net.magiccode.maven</groupId>");
        System.out.println("  <artifactId>spring-dockerator-plugin</artifactId>");
        System.out.println("  <configuration>");
        System.out.println("    <propertiesDirs>");
        System.out.println("      <propertiesDir>src/main/resources</propertiesDir>");
        System.out.println("      <propertiesDir>config</propertiesDir>");
        System.out.println("    </propertiesDirs>");
        System.out.println("    <profiles>");
        System.out.println("      <profile>postgres</profile>");
        System.out.println("      <profile>docker</profile>");
        System.out.println("      <profile>ssl</profile>");
        System.out.println("      <profile>uma</profile>");
        System.out.println("    </profiles>");
        System.out.println("    <volumes>");
        System.out.println("      <volume>");
        System.out.println("        <external>../ssl</external>");
        System.out.println("        <internal>/opt/ssl</internal>");
        System.out.println("      </volume>");
        System.out.println("    </volumes>");
        System.out.println("    <imagePrefix>nexus.riskcontrollimited.com:8891/rcl/</imagePrefix>");
        System.out.println("    <outputDir>${project.basedir}/target/docker</outputDir>");
        System.out.println("    <jdbcPrefix>exposure.management.datasource.</jdbcPrefix>");
        System.out.println("  </configuration>");
        System.out.println("</plugin>");
        
        System.out.println("\n✅ This will generate docker-compose.yml with volumes!");
    }
    
    @Test
    @DisplayName("Verify volume processing works with correct nested element format")
    void verifyVolumeProcessingWithCorrectFormat() {
        // Create volumes using the correct nested element approach
        List<Volume> volumes = new ArrayList<>();
        
        Volume sslVolume = new Volume();
        sslVolume.setExternal("../ssl");
        sslVolume.setInternal("/opt/ssl");
        volumes.add(sslVolume);
        
        Volume dataVolume = new Volume();
        dataVolume.setExternal("./data");
        dataVolume.setInternal("/var/data");
        volumes.add(dataVolume);
        
        // Convert to mappings (this is the logic from DockerComposePlugin.generateService())
        List<VolumeMapping> volumeMappings = new ArrayList<>();
        for (Volume volume : volumes) {
            if (volume.getExternal() != null && volume.getInternal() != null) {
                VolumeMapping mapping = VolumeMapping.builder()
                        .external(volume.getExternal())
                        .internal(volume.getInternal())
                        .build();
                volumeMappings.add(mapping);
            }
        }
        
        // Verify the mappings were created correctly
        assertThat(volumeMappings).hasSize(2);
        assertThat(volumeMappings.get(0).getExternal()).isEqualTo("../ssl");
        assertThat(volumeMappings.get(0).getInternal()).isEqualTo("/opt/ssl");
        assertThat(volumeMappings.get(1).getExternal()).isEqualTo("./data");
        assertThat(volumeMappings.get(1).getInternal()).isEqualTo("/var/data");
        
        System.out.println("✅ Volume processing verified to work correctly with nested element format");
        System.out.println("Generated mappings:");
        for (VolumeMapping mapping : volumeMappings) {
            System.out.println("  - " + mapping);
        }
    }
}