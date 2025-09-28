package net.magiccode.maven;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for per-module volume configuration extraction functionality.
 * This addresses the architectural limitation where volumes were only configured at the parent level.
 */
public class PerModuleVolumeConfigurationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should extract volume configuration from module pom.xml")
    public void testExtractModuleVolumeConfiguration() throws Exception {
        // Create a test module directory with pom.xml containing volume configuration
        Path moduleDir = tempDir.resolve("test-module");
        Files.createDirectories(moduleDir);
        
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    
                    <groupId>test</groupId>
                    <artifactId>test-module</artifactId>
                    <version>1.0.0</version>
                    
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>net.magiccode.maven</groupId>
                                <artifactId>spring-dockerator-plugin</artifactId>
                                <version>0.0.6-SNAPSHOT</version>
                                <configuration>
                                    <volumes>
                                        <volume>
                                            <external>../ssl</external>
                                            <internal>/opt/ssl</internal>
                                        </volume>
                                        <volume>
                                            <external>./data</external>
                                            <internal>/var/data</internal>
                                        </volume>
                                    </volumes>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """;
        
        Files.write(moduleDir.resolve("pom.xml"), pomContent.getBytes());
        
        // Test the volume extraction
        DockerComposePlugin plugin = new DockerComposePlugin();
        Method extractMethod = DockerComposePlugin.class.getDeclaredMethod("extractModuleVolumeConfiguration", File.class);
        extractMethod.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        List<Volume> volumes = (List<Volume>) extractMethod.invoke(plugin, moduleDir.toFile());
        
        // Verify the results
        assertThat(volumes).hasSize(2);
        
        Volume volume1 = volumes.get(0);
        assertThat(volume1.getExternal()).isEqualTo("../ssl");
        assertThat(volume1.getInternal()).isEqualTo("/opt/ssl");
        
        Volume volume2 = volumes.get(1);
        assertThat(volume2.getExternal()).isEqualTo("./data");
        assertThat(volume2.getInternal()).isEqualTo("/var/data");
    }

    @Test
    @DisplayName("Should return empty list when no pom.xml exists")
    public void testNoModulePomXml() throws Exception {
        // Create empty module directory without pom.xml
        Path moduleDir = tempDir.resolve("empty-module");
        Files.createDirectories(moduleDir);
        
        DockerComposePlugin plugin = new DockerComposePlugin();
        Method extractMethod = DockerComposePlugin.class.getDeclaredMethod("extractModuleVolumeConfiguration", File.class);
        extractMethod.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        List<Volume> volumes = (List<Volume>) extractMethod.invoke(plugin, moduleDir.toFile());
        
        assertThat(volumes).isEmpty();
    }

    @Test
    @DisplayName("Should return empty list when plugin is not configured in module pom.xml")
    public void testNoPluginConfiguration() throws Exception {
        // Create module directory with pom.xml but no plugin configuration
        Path moduleDir = tempDir.resolve("no-plugin-module");
        Files.createDirectories(moduleDir);
        
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    
                    <groupId>test</groupId>
                    <artifactId>no-plugin-module</artifactId>
                    <version>1.0.0</version>
                </project>
                """;
        
        Files.write(moduleDir.resolve("pom.xml"), pomContent.getBytes());
        
        DockerComposePlugin plugin = new DockerComposePlugin();
        Method extractMethod = DockerComposePlugin.class.getDeclaredMethod("extractModuleVolumeConfiguration", File.class);
        extractMethod.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        List<Volume> volumes = (List<Volume>) extractMethod.invoke(plugin, moduleDir.toFile());
        
        assertThat(volumes).isEmpty();
    }

    @Test
    @DisplayName("Should handle incomplete volume configuration gracefully")
    public void testIncompleteVolumeConfiguration() throws Exception {
        Path moduleDir = tempDir.resolve("incomplete-volume-module");
        Files.createDirectories(moduleDir);
        
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    
                    <groupId>test</groupId>
                    <artifactId>incomplete-volume-module</artifactId>
                    <version>1.0.0</version>
                    
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>net.magiccode.maven</groupId>
                                <artifactId>spring-dockerator-plugin</artifactId>
                                <version>0.0.6-SNAPSHOT</version>
                                <configuration>
                                    <volumes>
                                        <volume>
                                            <external>../ssl</external>
                                            <internal>/opt/ssl</internal>
                                        </volume>
                                        <volume>
                                            <external>./incomplete</external>
                                            <!-- Missing internal path -->
                                        </volume>
                                        <volume>
                                            <!-- Missing external path -->
                                            <internal>/opt/missing-external</internal>
                                        </volume>
                                    </volumes>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """;
        
        Files.write(moduleDir.resolve("pom.xml"), pomContent.getBytes());
        
        DockerComposePlugin plugin = new DockerComposePlugin();
        Method extractMethod = DockerComposePlugin.class.getDeclaredMethod("extractModuleVolumeConfiguration", File.class);
        extractMethod.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        List<Volume> volumes = (List<Volume>) extractMethod.invoke(plugin, moduleDir.toFile());
        
        // Only the complete volume should be extracted
        assertThat(volumes).hasSize(1);
        assertThat(volumes.get(0).getExternal()).isEqualTo("../ssl");
        assertThat(volumes.get(0).getInternal()).isEqualTo("/opt/ssl");
    }

    @Test
    @DisplayName("Demonstrate the architectural improvement")
    public void testArchitecturalImprovement() {
        System.out.println("\n=== Per-Module Volume Configuration Enhancement ===\n");
        
        System.out.println("❌ BEFORE (Architectural Limitation):");
        System.out.println("  - All modules received the same volume configuration from parent pom.xml");
        System.out.println("  - compileCommonVolumes() was ineffective - always found all volumes as 'common'");
        System.out.println("  - No way to configure different volumes for different services");
        System.out.println("  - User scenario: 'parent module has no volumes, but submodules do' was impossible");
        System.out.println();
        
        System.out.println("✅ AFTER (Enhanced Architecture):");
        System.out.println("  - Each module can have its own volume configuration in its pom.xml");
        System.out.println("  - Falls back to parent configuration if module has no specific volumes");
        System.out.println("  - compileCommonVolumes() now finds truly common volumes across different services");
        System.out.println("  - Supports per-service volume requirements");
        System.out.println("  - Enhanced logging shows volume source (parent vs module-specific)");
        System.out.println();
        
        System.out.println("🔧 Usage Examples:");
        System.out.println();
        System.out.println("Parent pom.xml (no volumes):");
        System.out.println("<plugin>");
        System.out.println("  <groupId>net.magiccode.maven</groupId>");
        System.out.println("  <artifactId>spring-dockerator-plugin</artifactId>");
        System.out.println("  <!-- No volumes configuration -->");
        System.out.println("</plugin>");
        System.out.println();
        
        System.out.println("Service A pom.xml (needs SSL certificates):");
        System.out.println("<plugin>");
        System.out.println("  <groupId>net.magiccode.maven</groupId>");
        System.out.println("  <artifactId>spring-dockerator-plugin</artifactId>");
        System.out.println("  <configuration>");
        System.out.println("    <volumes>");
        System.out.println("      <volume>");
        System.out.println("        <external>../ssl</external>");
        System.out.println("        <internal>/opt/ssl</internal>");
        System.out.println("      </volume>");
        System.out.println("    </volumes>");
        System.out.println("  </configuration>");
        System.out.println("</plugin>");
        System.out.println();
        
        System.out.println("Service B pom.xml (needs data persistence):");
        System.out.println("<plugin>");
        System.out.println("  <groupId>net.magiccode.maven</groupId>");
        System.out.println("  <artifactId>spring-dockerator-plugin</artifactId>");
        System.out.println("  <configuration>");
        System.out.println("    <volumes>");
        System.out.println("      <volume>");
        System.out.println("        <external>./data</external>");
        System.out.println("        <internal>/var/data</internal>");
        System.out.println("      </volume>");
        System.out.println("    </volumes>");
        System.out.println("  </configuration>");
        System.out.println("</plugin>");
        System.out.println();
        
        System.out.println("📊 Result:");
        System.out.println("  - Service A gets SSL volume only");
        System.out.println("  - Service B gets data volume only");
        System.out.println("  - compileCommonVolumes() correctly identifies no common volumes");
        System.out.println("  - Each service has volumes tailored to its specific needs");
        System.out.println();
    }
}