package net.magiccode.maven;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration class for volume definitions in Maven plugin configuration.
 * Supports both nested elements and XML attributes:
 * <pre>
 * &lt;volumes&gt;
 *   &lt;!-- Nested elements format --&gt;
 *   &lt;volume&gt;
 *     &lt;external&gt;../ssl&lt;/external&gt;
 *     &lt;internal&gt;/opt/ssl&lt;/internal&gt;
 *   &lt;/volume&gt;
 *   &lt;!-- Attribute format --&gt;
 *   &lt;volume external="../ssl" internal="/opt/ssl"/&gt;
 * &lt;/volumes&gt;
 * </pre>
 */
@Data
@NoArgsConstructor
public class Volume {
    
    /**
     * The external host path (e.g. ../ssl or ./data).
     * Can be configured as XML attribute or nested element.
     */
    private String external;
    
    /**
     * The internal container path (e.g. /opt/ssl).
     * Can be configured as XML attribute or nested element.
     */
    private String internal;
}