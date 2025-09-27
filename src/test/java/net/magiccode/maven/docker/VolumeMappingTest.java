package net.magiccode.maven.docker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VolumeMapping}
 */
public class VolumeMappingTest {

    @Test
    @DisplayName("Builder creates VolumeMapping with correct values")
    void builderCreatesCorrectMapping() {
        VolumeMapping mapping = VolumeMapping.builder()
                .external("../ssl")
                .internal("/opt/ssl")
                .build();

        assertThat(mapping.getExternal()).isEqualTo("../ssl");
        assertThat(mapping.getInternal()).isEqualTo("/opt/ssl");
    }

    @Test
    @DisplayName("AllArgsConstructor creates VolumeMapping with correct values")
    void allArgsConstructorCreatesCorrectMapping() {
        VolumeMapping mapping = new VolumeMapping("./data", "/var/data");

        assertThat(mapping.getExternal()).isEqualTo("./data");
        assertThat(mapping.getInternal()).isEqualTo("/var/data");
    }

    @Test
    @DisplayName("NoArgsConstructor creates empty VolumeMapping")
    void noArgsConstructorCreatesEmpty() {
        VolumeMapping mapping = new VolumeMapping();

        assertThat(mapping.getExternal()).isNull();
        assertThat(mapping.getInternal()).isNull();
    }

    @Test
    @DisplayName("toString returns external:internal format")
    void toStringFormat() {
        VolumeMapping mapping = new VolumeMapping("../logs", "/var/log");

        assertThat(mapping.toString()).isEqualTo("../logs:/var/log");
    }

    @Test
    @DisplayName("toString handles null values")
    void toStringHandlesNulls() {
        VolumeMapping mapping1 = new VolumeMapping(null, "/opt/ssl");
        VolumeMapping mapping2 = new VolumeMapping("../ssl", null);
        VolumeMapping mapping3 = new VolumeMapping(null, null);

        assertThat(mapping1.toString()).isEqualTo("null:/opt/ssl");
        assertThat(mapping2.toString()).isEqualTo("../ssl:null");
        assertThat(mapping3.toString()).isEqualTo("null:null");
    }

    @Test
    @DisplayName("equals returns true for same external and internal paths")
    void equalsReturnsTrueForSamePaths() {
        VolumeMapping mapping1 = new VolumeMapping("../ssl", "/opt/ssl");
        VolumeMapping mapping2 = new VolumeMapping("../ssl", "/opt/ssl");

        assertThat(mapping1).isEqualTo(mapping2);
    }

    @Test
    @DisplayName("equals returns false for different external paths")
    void equalsReturnsFalseForDifferentExternal() {
        VolumeMapping mapping1 = new VolumeMapping("../ssl", "/opt/ssl");
        VolumeMapping mapping2 = new VolumeMapping("./ssl", "/opt/ssl");

        assertThat(mapping1).isNotEqualTo(mapping2);
    }

    @Test
    @DisplayName("equals returns false for different internal paths")
    void equalsReturnsFalseForDifferentInternal() {
        VolumeMapping mapping1 = new VolumeMapping("../ssl", "/opt/ssl");
        VolumeMapping mapping2 = new VolumeMapping("../ssl", "/etc/ssl");

        assertThat(mapping1).isNotEqualTo(mapping2);
    }

    @Test
    @DisplayName("equals handles null values correctly")
    void equalsHandlesNulls() {
        VolumeMapping mapping1 = new VolumeMapping(null, "/opt/ssl");
        VolumeMapping mapping2 = new VolumeMapping(null, "/opt/ssl");
        VolumeMapping mapping3 = new VolumeMapping("../ssl", "/opt/ssl");

        assertThat(mapping1).isEqualTo(mapping2);
        assertThat(mapping1).isNotEqualTo(mapping3);
    }

    @Test
    @DisplayName("equals returns false for null object")
    void equalsReturnsFalseForNull() {
        VolumeMapping mapping = new VolumeMapping("../ssl", "/opt/ssl");

        assertThat(mapping).isNotEqualTo(null);
    }

    @Test
    @DisplayName("equals returns false for different object type")
    void equalsReturnsFalseForDifferentType() {
        VolumeMapping mapping = new VolumeMapping("../ssl", "/opt/ssl");
        String other = "not a VolumeMapping";

        assertThat(mapping).isNotEqualTo(other);
    }

    @Test
    @DisplayName("equals returns true for same object reference")
    void equalsReturnsTrueForSameReference() {
        VolumeMapping mapping = new VolumeMapping("../ssl", "/opt/ssl");

        assertThat(mapping).isEqualTo(mapping);
    }

    @Test
    @DisplayName("hashCode is consistent for equal objects")
    void hashCodeConsistentForEqualObjects() {
        VolumeMapping mapping1 = new VolumeMapping("../ssl", "/opt/ssl");
        VolumeMapping mapping2 = new VolumeMapping("../ssl", "/opt/ssl");

        assertThat(mapping1.hashCode()).isEqualTo(mapping2.hashCode());
    }

    @Test
    @DisplayName("hashCode handles null values")
    void hashCodeHandlesNulls() {
        VolumeMapping mapping1 = new VolumeMapping(null, null);
        VolumeMapping mapping2 = new VolumeMapping(null, "/opt/ssl");
        VolumeMapping mapping3 = new VolumeMapping("../ssl", null);

        // Should not throw exceptions
        assertThat(mapping1.hashCode()).isNotNull();
        assertThat(mapping2.hashCode()).isNotNull();
        assertThat(mapping3.hashCode()).isNotNull();
    }

    @Test
    @DisplayName("Setters work correctly with Lombok @Data")
    void settersWorkCorrectly() {
        VolumeMapping mapping = new VolumeMapping();
        
        mapping.setExternal("../config");
        mapping.setInternal("/etc/config");

        assertThat(mapping.getExternal()).isEqualTo("../config");
        assertThat(mapping.getInternal()).isEqualTo("/etc/config");
    }
}