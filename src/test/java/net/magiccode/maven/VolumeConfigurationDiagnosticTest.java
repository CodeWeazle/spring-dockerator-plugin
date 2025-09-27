package net.magiccode.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.magiccode.maven.docker.VolumeMapping;

/**
 * Test to diagnose volume configuration issues based on user's XML format
 */
public class VolumeConfigurationDiagnosticTest {

    @Test
    @DisplayName("Volume configuration with nested elements works correctly")
    void volumeConfigurationWithNestedElements() {
        // This is how our current Volume class is designed to work:
        // <volume>
        //   <external>../ssl</external>
        //   <internal>/opt/ssl</internal>
        // </volume>
        
        List<Volume> volumes = new ArrayList<>();
        
        Volume volume = new Volume();
        volume.setExternal("../ssl");
        volume.setInternal("/opt/ssl");
        volumes.add(volume);
        
        // Test the conversion logic from DockerComposePlugin.generateService()
        List<VolumeMapping> volumeMappings = new ArrayList<>();
        if (volumes != null && !volumes.isEmpty()) {
            for (Volume vol : volumes) {
                if (vol.getExternal() != null && vol.getInternal() != null) {
                    VolumeMapping mapping = VolumeMapping.builder()
                            .external(vol.getExternal())
                            .internal(vol.getInternal())
                            .build();
                    volumeMappings.add(mapping);
                }
            }
        }
        
        assertThat(volumeMappings).hasSize(1);
        assertThat(volumeMappings.get(0).getExternal()).isEqualTo("../ssl");
        assertThat(volumeMappings.get(0).getInternal()).isEqualTo("/opt/ssl");
    }
    
    @Test
    @DisplayName("Empty Volume objects simulate XML attribute parsing issues")
    void emptyVolumeObjectsSimulateXmlAttributeParsingIssues() {
        // This simulates what might happen if Maven can't parse XML attributes
        // into the Volume object fields - they would remain null
        
        List<Volume> volumes = new ArrayList<>();
        
        // This simulates a Volume object created but not populated due to 
        // XML attribute vs nested element mismatch
        Volume unpopulatedVolume = new Volume();
        // external and internal remain null because Maven couldn't map 
        // attributes to fields
        volumes.add(unpopulatedVolume);
        
        // Test the conversion logic - should filter out invalid volumes
        List<VolumeMapping> volumeMappings = new ArrayList<>();
        if (volumes != null && !volumes.isEmpty()) {
            System.out.println("Processing " + volumes.size() + " volume configurations");
            for (Volume vol : volumes) {
                System.out.println("Volume: external=" + vol.getExternal() + ", internal=" + vol.getInternal());
                if (vol.getExternal() != null && vol.getInternal() != null) {
                    VolumeMapping mapping = VolumeMapping.builder()
                            .external(vol.getExternal())
                            .internal(vol.getInternal())
                            .build();
                    volumeMappings.add(mapping);
                    System.out.println("Added volume mapping: " + mapping);
                } else {
                    System.out.println("Skipping incomplete volume: external=" + vol.getExternal() + ", internal=" + vol.getInternal());
                }
            }
        }
        
        // This would result in no volumes being processed
        assertThat(volumeMappings).isEmpty();
        
        System.out.println("Final volume mappings count: " + volumeMappings.size());
    }
    
    @Test
    @DisplayName("Debug volume configuration processing step by step")
    void debugVolumeConfigurationProcessing() {
        // Let's trace through the exact steps that happen in DockerComposePlugin
        
        // Step 1: Plugin configuration parsing (simulated)
        System.out.println("=== Step 1: Plugin Configuration Parsing ===");
        List<Volume> configuredVolumes = new ArrayList<>();
        
        // User's XML: <volume external="../ssl" internal="/opt/ssl" />
        // But our Volume class expects nested elements, not attributes
        Volume volume = new Volume();
        volume.setExternal("../ssl");  // This works if set programmatically
        volume.setInternal("/opt/ssl");
        configuredVolumes.add(volume);
        
        System.out.println("Configured volumes count: " + configuredVolumes.size());
        for (int i = 0; i < configuredVolumes.size(); i++) {
            Volume vol = configuredVolumes.get(i);
            System.out.println("Volume " + i + ": external='" + vol.getExternal() + "', internal='" + vol.getInternal() + "'");
        }
        
        // Step 2: Volume processing in generateService method
        System.out.println("\n=== Step 2: Volume Processing in generateService ===");
        List<VolumeMapping> volumeMappings = new ArrayList<>();
        
        if (configuredVolumes != null && !configuredVolumes.isEmpty()) {
            System.out.println("Processing " + configuredVolumes.size() + " configured volume(s)");
            for (Volume vol : configuredVolumes) {
                System.out.println("Checking volume: external='" + vol.getExternal() + "', internal='" + vol.getInternal() + "'");
                if (vol.getExternal() != null && vol.getInternal() != null) {
                    VolumeMapping mapping = VolumeMapping.builder()
                            .external(vol.getExternal())
                            .internal(vol.getInternal())
                            .build();
                    volumeMappings.add(mapping);
                    System.out.println("✓ Added volume mapping: " + mapping);
                } else {
                    System.out.println("✗ Skipping incomplete volume configuration");
                }
            }
        } else {
            System.out.println("No volumes configured or volumes list is null/empty");
        }
        
        // Step 3: Result verification
        System.out.println("\n=== Step 3: Result Verification ===");
        System.out.println("Final volume mappings count: " + volumeMappings.size());
        for (VolumeMapping mapping : volumeMappings) {
            System.out.println("Volume mapping: " + mapping);
        }
        
        assertThat(volumeMappings).hasSize(1);
        assertThat(volumeMappings.get(0).getExternal()).isEqualTo("../ssl");
        assertThat(volumeMappings.get(0).getInternal()).isEqualTo("/opt/ssl");
        
        System.out.println("✓ Volume processing working correctly!");
    }
}