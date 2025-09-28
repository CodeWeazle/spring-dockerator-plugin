package net.magiccode.maven.docker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simple value object describing a volume mapping for docker-compose.
 * external -> internal path.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VolumeMapping {
    /**
     * The external host path (e.g. ../ssl or ./data)
     */
    private String external;
    /**
     * The internal container path (e.g. /opt/ssl)
     */
    private String internal;

    @Override
    public String toString() {
        return external + ":" + internal;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (external==null?0:external.hashCode());
        result = 31 * result + (internal==null?0:internal.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        VolumeMapping other = (VolumeMapping) obj;
        return (external == null ? other.external == null : external.equals(other.external)) &&
               (internal == null ? other.internal == null : internal.equals(other.internal));
    }
}
