package net.magiccode.maven;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Volume} Maven plugin configuration class
 */
public class VolumeTest {

    @Test
    @DisplayName("Default constructor creates empty Volume")
    void defaultConstructorCreatesEmpty() {
        Volume volume = new Volume();

        assertThat(volume.getExternal()).isNull();
        assertThat(volume.getInternal()).isNull();
    }

    @Test
    @DisplayName("Setters and getters work correctly")
    void settersAndGettersWork() {
        Volume volume = new Volume();
        
        volume.setExternal("../ssl");
        volume.setInternal("/opt/ssl");

        assertThat(volume.getExternal()).isEqualTo("../ssl");
        assertThat(volume.getInternal()).isEqualTo("/opt/ssl");
    }

    @Test
    @DisplayName("Volume supports various external path formats")
    void supportsVariousExternalPaths() {
        Volume volume = new Volume();

        // Relative path with parent directory
        volume.setExternal("../ssl");
        assertThat(volume.getExternal()).isEqualTo("../ssl");

        // Relative path current directory
        volume.setExternal("./data");
        assertThat(volume.getExternal()).isEqualTo("./data");

        // Absolute path
        volume.setExternal("/host/path");
        assertThat(volume.getExternal()).isEqualTo("/host/path");

        // Named volume
        volume.setExternal("my-volume");
        assertThat(volume.getExternal()).isEqualTo("my-volume");
    }

    @Test
    @DisplayName("Volume supports various internal path formats")
    void supportsVariousInternalPaths() {
        Volume volume = new Volume();

        // Standard container paths
        volume.setInternal("/opt/ssl");
        assertThat(volume.getInternal()).isEqualTo("/opt/ssl");

        volume.setInternal("/var/log");
        assertThat(volume.getInternal()).isEqualTo("/var/log");

        volume.setInternal("/app/data");
        assertThat(volume.getInternal()).isEqualTo("/app/data");
    }

    @Test
    @DisplayName("Volume handles null values gracefully")
    void handlesNullValues() {
        Volume volume = new Volume();

        volume.setExternal(null);
        volume.setInternal(null);

        assertThat(volume.getExternal()).isNull();
        assertThat(volume.getInternal()).isNull();
    }

    @Test
    @DisplayName("Volume handles empty strings")
    void handlesEmptyStrings() {
        Volume volume = new Volume();

        volume.setExternal("");
        volume.setInternal("");

        assertThat(volume.getExternal()).isEmpty();
        assertThat(volume.getInternal()).isEmpty();
    }

    @Test
    @DisplayName("Volume equals works correctly with Lombok @Data")
    void equalsWorksCorrectly() {
        Volume volume1 = new Volume();
        volume1.setExternal("../ssl");
        volume1.setInternal("/opt/ssl");

        Volume volume2 = new Volume();
        volume2.setExternal("../ssl");
        volume2.setInternal("/opt/ssl");

        Volume volume3 = new Volume();
        volume3.setExternal("../ssl");
        volume3.setInternal("/etc/ssl");

        assertThat(volume1).isEqualTo(volume2);
        assertThat(volume1).isNotEqualTo(volume3);
    }

    @Test
    @DisplayName("Volume hashCode is consistent with Lombok @Data")
    void hashCodeIsConsistent() {
        Volume volume1 = new Volume();
        volume1.setExternal("../ssl");
        volume1.setInternal("/opt/ssl");

        Volume volume2 = new Volume();
        volume2.setExternal("../ssl");
        volume2.setInternal("/opt/ssl");

        assertThat(volume1.hashCode()).isEqualTo(volume2.hashCode());
    }

    @Test
    @DisplayName("Volume toString contains field values with Lombok @Data")
    void toStringContainsFieldValues() {
        Volume volume = new Volume();
        volume.setExternal("../ssl");
        volume.setInternal("/opt/ssl");

        String toString = volume.toString();
        assertThat(toString).contains("../ssl");
        assertThat(toString).contains("/opt/ssl");
        assertThat(toString).contains("Volume");
    }

    @Test
    @DisplayName("Volume supports special characters in paths")
    void supportsSpecialCharacters() {
        Volume volume = new Volume();

        // Paths with spaces, hyphens, underscores
        volume.setExternal("../my-ssl config");
        volume.setInternal("/opt/ssl_certs/my-app");

        assertThat(volume.getExternal()).isEqualTo("../my-ssl config");
        assertThat(volume.getInternal()).isEqualTo("/opt/ssl_certs/my-app");
    }
}