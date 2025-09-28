package net.magiccode.maven;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Debug test to identify exactly what's happening with volume configuration parsing.
 */
public class VolumeConfigurationDebugTest {

    @Test
    @DisplayName("Debug volume configuration parsing - your exact scenario")
    void debugVolumeConfigurationParsingYourExactScenario() {
        System.out.println("=== Debugging Your Volume Configuration Issue ===");
        
        // Test 1: What happens with attribute format (your current approach)
        System.out.println("\n--- Test 1: Attribute Format Simulation ---");
        List<Volume> attributeVolumes = simulateAttributeParsing();
        processVolumes(attributeVolumes, "Attribute Format");
        
        // Test 2: What happens with nested element format (correct approach)
        System.out.println("\n--- Test 2: Nested Element Format Simulation ---");
        List<Volume> nestedVolumes = simulateNestedElementParsing();
        processVolumes(nestedVolumes, "Nested Element Format");
        
        // Test 3: Show the exact XML difference
        System.out.println("\n--- Test 3: XML Format Comparison ---");
        showXmlFormats();
    }
    
    private List<Volume> simulateAttributeParsing() {
        // When Maven parses: <volume external="../ssl" internal="/opt/ssl" />
        // It creates Volume objects but doesn't populate fields from attributes
        List<Volume> volumes = new ArrayList<>();
        Volume vol = new Volume(); // Fields remain null - this is the bug!
        volumes.add(vol);
        return volumes;
    }
    
    private List<Volume> simulateNestedElementParsing() {
        // When Maven parses: 
        // <volume>
        //   <external>../ssl</external>
        //   <internal>/opt/ssl</internal>
        // </volume>
        List<Volume> volumes = new ArrayList<>();
        Volume vol = new Volume();
        vol.setExternal("../ssl");     // This gets populated correctly
        vol.setInternal("/opt/ssl");   // This gets populated correctly
        volumes.add(vol);
        return volumes;
    }
    
    private void processVolumes(List<Volume> volumes, String formatType) {
        System.out.printf("=== Processing %s ===\n", formatType);
        
        if (volumes == null || volumes.isEmpty()) {
            System.out.println("❌ No volumes to process");
            return;
        }
        
        System.out.printf("Processing %d configured volume(s):\n", volumes.size());
        
        int processedCount = 0;
        for (int i = 0; i < volumes.size(); i++) {
            Volume volume = volumes.get(i);
            System.out.printf("  Volume %d: external='%s', internal='%s'\n", 
                    i, volume.getExternal(), volume.getInternal());
            
            if (volume.getExternal() != null && volume.getInternal() != null) {
                processedCount++;
                System.out.printf("    ✅ Would create volume mapping: %s:%s\n", 
                        volume.getExternal(), volume.getInternal());
            } else {
                System.out.printf("    ❌ Skipping incomplete volume - fields are null!\n");
            }
        }
        
        System.out.printf("Result: %d volume mappings would be created\n", processedCount);
        
        if (processedCount == 0) {
            System.out.println("🚨 THIS IS WHY NO VOLUMES APPEAR IN YOUR DOCKER-COMPOSE.YML!");
        }
    }
    
    private void showXmlFormats() {
        System.out.println("=== The Critical Difference ===\n");
        
        System.out.println("❌ YOUR CURRENT FORMAT (doesn't work):");
        System.out.println("<plugin>");
        System.out.println("  <configuration>");
        System.out.println("    <volumes>");
        System.out.println("      <volume external=\"../ssl\" internal=\"/opt/ssl\" />");
        System.out.println("    </volumes>");
        System.out.println("  </configuration>");
        System.out.println("</plugin>\n");
        
        System.out.println("✅ CORRECT FORMAT (will work):");
        System.out.println("<plugin>");
        System.out.println("  <configuration>");
        System.out.println("    <volumes>");
        System.out.println("      <volume>");
        System.out.println("        <external>../ssl</external>");
        System.out.println("        <internal>/opt/ssl</internal>");
        System.out.println("      </volume>");
        System.out.println("    </volumes>");
        System.out.println("  </configuration>");
        System.out.println("</plugin>\n");
        
        System.out.println("🔑 KEY POINT:");
        System.out.println("Maven plugin parameter binding ONLY maps nested XML elements to Java object fields.");
        System.out.println("XML attributes are NOT automatically mapped to object fields.");
        System.out.println("This is a Maven design limitation, not a bug in the plugin!");
    }
    
    @Test
    @DisplayName("Show log debugging commands for your project")
    void showLogDebuggingCommandsForYourProject() {
        System.out.println("=== Debug Commands for Your Project ===\n");
        
        System.out.println("1. Run your Maven plugin with debug logging to see volume processing:");
        System.out.println("   mvn net.magiccode.maven:spring-dockerator-plugin:generate-docker-compose -X\n");
        
        System.out.println("2. Look for these log messages in the output:");
        System.out.println("   - 'Processing X configured volume(s) for module: limit-designer'");
        System.out.println("   - 'Volume 0: external='null', internal='null'' (if using attribute format)");
        System.out.println("   - '✗ Skipping incomplete volume configuration' (confirms the issue)\n");
        
        System.out.println("3. If you see 'No volume configuration found (volumes parameter is null)',");
        System.out.println("   it means Maven isn't parsing your <volumes> configuration at all.\n");
        
        System.out.println("4. After fixing the XML format, you should see:");
        System.out.println("   - 'Volume 0: external='../ssl', internal='/opt/ssl''");
        System.out.println("   - '✓ Added volume mapping: ../ssl -> /opt/ssl'");
        System.out.println("   - Volumes appearing in your docker-compose.yml file!");
    }
}