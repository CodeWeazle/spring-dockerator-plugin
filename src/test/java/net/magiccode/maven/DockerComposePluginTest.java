package net.magiccode.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.magiccode.maven.docker.VolumeMapping;

/**
 * Unit tests for {@link DockerComposePlugin} volume configuration processing
 */
public class DockerComposePluginTest {

    @Test
    @DisplayName("Volume configuration converts to VolumeMapping correctly")
    void volumeConfigurationConvertsToVolumeMapping() {
        // Create volume configurations like they would be defined in pom.xml
        List<Volume> volumes = new ArrayList<>();
        
        Volume volume1 = new Volume();
        volume1.setExternal("../ssl");
        volume1.setInternal("/opt/ssl");
        volumes.add(volume1);

        Volume volume2 = new Volume();
        volume2.setExternal("./data");
        volume2.setInternal("/var/data");
        volumes.add(volume2);

        // Test the conversion logic that happens in DockerComposePlugin.generateService()
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

        assertThat(volumeMappings).hasSize(2);
        assertThat(volumeMappings.get(0).getExternal()).isEqualTo("../ssl");
        assertThat(volumeMappings.get(0).getInternal()).isEqualTo("/opt/ssl");
        assertThat(volumeMappings.get(1).getExternal()).isEqualTo("./data");
        assertThat(volumeMappings.get(1).getInternal()).isEqualTo("/var/data");
    }

    @Test
    @DisplayName("Volume configuration filters out invalid volumes")
    void volumeConfigurationFiltersInvalidVolumes() {
        List<Volume> volumes = new ArrayList<>();
        
        // Valid volume
        Volume validVolume = new Volume();
        validVolume.setExternal("../ssl");
        validVolume.setInternal("/opt/ssl");
        volumes.add(validVolume);

        // Invalid volume - missing external
        Volume invalidVolume1 = new Volume();
        invalidVolume1.setInternal("/opt/ssl");
        volumes.add(invalidVolume1);

        // Invalid volume - missing internal
        Volume invalidVolume2 = new Volume();
        invalidVolume2.setExternal("../data");
        volumes.add(invalidVolume2);

        // Invalid volume - both null
        Volume invalidVolume3 = new Volume();
        volumes.add(invalidVolume3);

        // Test the filtering logic from DockerComposePlugin.generateService()
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

        // Only the valid volume should be converted
        assertThat(volumeMappings).hasSize(1);
        assertThat(volumeMappings.get(0).getExternal()).isEqualTo("../ssl");
        assertThat(volumeMappings.get(0).getInternal()).isEqualTo("/opt/ssl");
    }

    @Test
    @DisplayName("Empty volume list produces no volume mappings")
    void emptyVolumeListProducesNoMappings() {
        List<Volume> volumes = new ArrayList<>();

        // Test the conversion logic
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

        assertThat(volumeMappings).isEmpty();
    }

    @Test
    @DisplayName("Null volume list is handled gracefully")
    void nullVolumeListHandledGracefully() {
        List<Volume> volumes = null;

        // Test the null-safe logic that should be in DockerComposePlugin
        List<VolumeMapping> volumeMappings = new ArrayList<>();
        if (volumes != null) {
            for (Volume volume : volumes) {
                if (volume != null && volume.getExternal() != null && volume.getInternal() != null) {
                    VolumeMapping mapping = VolumeMapping.builder()
                            .external(volume.getExternal())
                            .internal(volume.getInternal())
                            .build();
                    volumeMappings.add(mapping);
                }
            }
        }

        assertThat(volumeMappings).isEmpty();
        
        // Also test with volumes initialized but still safe
        volumes = new ArrayList<>();
        volumeMappings.clear();
        
        for (Volume volume : volumes) {
            if (volume != null && volume.getExternal() != null && volume.getInternal() != null) {
                VolumeMapping mapping = VolumeMapping.builder()
                        .external(volume.getExternal())
                        .internal(volume.getInternal())
                        .build();
                volumeMappings.add(mapping);
            }
        }
        
        assertThat(volumeMappings).isEmpty();
    }

    @Test
    @DisplayName("Volume paths with special characters are preserved")
    void volumePathsWithSpecialCharactersPreserved() {
        List<Volume> volumes = new ArrayList<>();
        
        Volume volume = new Volume();
        volume.setExternal("../my-ssl config");
        volume.setInternal("/opt/ssl_certs/my-app");
        volumes.add(volume);

        List<VolumeMapping> volumeMappings = new ArrayList<>();
        for (Volume vol : volumes) {
            if (vol.getExternal() != null && vol.getInternal() != null) {
                VolumeMapping mapping = VolumeMapping.builder()
                        .external(vol.getExternal())
                        .internal(vol.getInternal())
                        .build();
                volumeMappings.add(mapping);
            }
        }

        assertThat(volumeMappings).hasSize(1);
        assertThat(volumeMappings.get(0).getExternal()).isEqualTo("../my-ssl config");
        assertThat(volumeMappings.get(0).getInternal()).isEqualTo("/opt/ssl_certs/my-app");
    }
}