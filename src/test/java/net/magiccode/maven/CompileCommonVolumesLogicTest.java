package net.magiccode.maven;

import net.magiccode.maven.docker.DockerService;
import net.magiccode.maven.docker.VolumeMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the compileCommonVolumes method logic to ensure correct behavior
 * when multiple services share common volume configurations.
 */
class CompileCommonVolumesLogicTest {

    private DockerComposePlugin plugin;
    private Method compileCommonVolumesMethod;

    @BeforeEach
    void setUp() throws Exception {
        plugin = new DockerComposePlugin();
        
        // Make compileCommonVolumes accessible for testing
        compileCommonVolumesMethod = DockerComposePlugin.class.getDeclaredMethod("compileCommonVolumes", List.class);
        compileCommonVolumesMethod.setAccessible(true);
    }

    @Test
    void testScenarioTwoSubmodulesWithSameVolume() throws Exception {
        System.out.println("=== Testing Scenario: Two Submodules with Same Volume ===");
        
        // Create services with the same volume configuration
        DockerService serviceA = createServiceWithVolume("service-a", "../ssl", "/opt/ssl");
        DockerService serviceB = createServiceWithVolume("service-b", "../ssl", "/opt/ssl");
        
        List<DockerService> services = List.of(serviceA, serviceB);
        
        System.out.println("Before compileCommonVolumes:");
        System.out.println("ServiceA specificVolumes: " + serviceA.getSpecificVolumes());
        System.out.println("ServiceB specificVolumes: " + serviceB.getSpecificVolumes());
        
        // Execute compileCommonVolumes
        @SuppressWarnings("unchecked")
        List<VolumeMapping> commonVolumes = (List<VolumeMapping>) compileCommonVolumesMethod.invoke(plugin, services);
        
        System.out.println("After compileCommonVolumes:");
        System.out.println("Common volumes returned: " + commonVolumes);
        System.out.println("ServiceA specificVolumes: " + serviceA.getSpecificVolumes());
        System.out.println("ServiceB specificVolumes: " + serviceB.getSpecificVolumes());
        
        // Verify the common volume is found and returned
        assertThat(commonVolumes).hasSize(1);
        assertThat(commonVolumes.get(0).getExternal()).isEqualTo("../ssl");
        assertThat(commonVolumes.get(0).getInternal()).isEqualTo("/opt/ssl");
        
        // Verify both services have cleared their specific volumes (they'll use x-common reference)
        assertThat(serviceA.getSpecificVolumes()).isEmpty();
        assertThat(serviceB.getSpecificVolumes()).isEmpty();
        
        System.out.println("✅ CORRECT BEHAVIOR: Common volumes properly identified and services cleared");
    }

    @Test
    void testScenarioMixedVolumes() throws Exception {
        System.out.println("=== Testing Scenario: Mixed Volume Configurations ===");
        
        // ServiceA: Only common volume
        DockerService serviceA = createServiceWithVolume("service-a", "../ssl", "/opt/ssl");
        
        // ServiceB: Common volume + specific volume  
        DockerService serviceB = createServiceWithVolumes("service-b", 
            List.of(
                new VolumeMapping("../ssl", "/opt/ssl"),
                new VolumeMapping("./service-b-data", "/var/data")
            )
        );
        
        List<DockerService> services = List.of(serviceA, serviceB);
        
        System.out.println("Before compileCommonVolumes:");
        System.out.println("ServiceA specificVolumes: " + serviceA.getSpecificVolumes());
        System.out.println("ServiceB specificVolumes: " + serviceB.getSpecificVolumes());
        
        // Execute compileCommonVolumes
        @SuppressWarnings("unchecked")
        List<VolumeMapping> commonVolumes = (List<VolumeMapping>) compileCommonVolumesMethod.invoke(plugin, services);
        
        System.out.println("After compileCommonVolumes:");
        System.out.println("Common volumes returned: " + commonVolumes);
        System.out.println("ServiceA specificVolumes: " + serviceA.getSpecificVolumes());
        System.out.println("ServiceB specificVolumes: " + serviceB.getSpecificVolumes());
        
        // Verify common volume is identified
        assertThat(commonVolumes).hasSize(1);
        assertThat(commonVolumes.get(0).getExternal()).isEqualTo("../ssl");
        
        // ServiceA should be cleared (uses x-common reference)
        assertThat(serviceA.getSpecificVolumes()).isEmpty();
        
        // ServiceB should have ALL volumes (common + specific) listed directly
        assertThat(serviceB.getSpecificVolumes()).hasSize(2);
        assertThat(serviceB.getSpecificVolumes()).extracting("external").containsExactly("../ssl", "./service-b-data");
        
        System.out.println("✅ CORRECT BEHAVIOR: Mixed scenario handled properly");
    }

    @Test 
    void testScenarioNoCommonVolumes() throws Exception {
        System.out.println("=== Testing Scenario: No Common Volumes ===");
        
        // Services with completely different volumes
        DockerService serviceA = createServiceWithVolume("service-a", "./service-a-data", "/var/data");
        DockerService serviceB = createServiceWithVolume("service-b", "./service-b-cache", "/var/cache");
        
        List<DockerService> services = List.of(serviceA, serviceB);
        
        System.out.println("Before compileCommonVolumes:");
        System.out.println("ServiceA specificVolumes: " + serviceA.getSpecificVolumes());
        System.out.println("ServiceB specificVolumes: " + serviceB.getSpecificVolumes());
        
        // Execute compileCommonVolumes
        @SuppressWarnings("unchecked")
        List<VolumeMapping> commonVolumes = (List<VolumeMapping>) compileCommonVolumesMethod.invoke(plugin, services);
        
        System.out.println("After compileCommonVolumes:");
        System.out.println("Common volumes returned: " + commonVolumes);
        System.out.println("ServiceA specificVolumes: " + serviceA.getSpecificVolumes());
        System.out.println("ServiceB specificVolumes: " + serviceB.getSpecificVolumes());
        
        // No common volumes should be found
        assertThat(commonVolumes).isEmpty();
        
        // Services should keep their specific volumes
        assertThat(serviceA.getSpecificVolumes()).hasSize(1);
        assertThat(serviceB.getSpecificVolumes()).hasSize(1);
        
        System.out.println("✅ CORRECT BEHAVIOR: No false common volumes identified");
    }

    private DockerService createServiceWithVolume(String serviceName, String external, String internal) {
        DockerService service = DockerService.builder()
            .name(serviceName)
            .version("1.0.0")
            .imagePrefix("test/")
            .build();
        
        service.getSpecificVolumes().add(new VolumeMapping(external, internal));
        return service;
    }
    
    private DockerService createServiceWithVolumes(String serviceName, List<VolumeMapping> volumes) {
        DockerService service = DockerService.builder()
            .name(serviceName)
            .version("1.0.0")
            .imagePrefix("test/")
            .build();
        
        service.getSpecificVolumes().addAll(volumes);
        return service;
    }
}