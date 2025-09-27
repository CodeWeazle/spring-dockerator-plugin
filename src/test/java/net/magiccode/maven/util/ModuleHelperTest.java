package net.magiccode.maven.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link ModuleHelper}
 */
public class ModuleHelperTest {

    @Test
    @DisplayName("getModules: throws exception when parent pom.xml doesn't exist")
    void getModulesNonExistentPom(@TempDir Path tempDir) {
        ModuleHelper helper = ModuleHelper.builder()
                .basedir(tempDir.toFile())
                .build();

        assertThatThrownBy(() -> helper.getModules())
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Parent pom.xml not found");
    }

    @Test
    @DisplayName("getModules: returns empty list when no modules defined")
    void getModulesNoModulesDefined(@TempDir Path tempDir) throws Exception {
        // Create a minimal pom.xml without modules
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>test</groupId>
                    <artifactId>test</artifactId>
                    <version>1.0.0</version>
                </project>
                """;
        Files.writeString(tempDir.resolve("pom.xml"), pomContent);

        ModuleHelper helper = ModuleHelper.builder()
                .basedir(tempDir.toFile())
                .build();

        List<File> modules = helper.getModules();
        assertThat(modules).isEmpty();
    }

    @Test
    @DisplayName("getModules: returns modules that exist with pom.xml files")
    void getModulesValidModules(@TempDir Path tempDir) throws Exception {
        // Create parent pom.xml with modules
        String pomContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>test</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                    
                    <modules>
                        <module>module-a</module>
                        <module>module-b</module>
                        <module>nonexistent</module>
                    </modules>
                </project>
                """;
        Files.writeString(tempDir.resolve("pom.xml"), pomContent);

        // Create module directories with pom.xml files
        Path moduleA = tempDir.resolve("module-a");
        Files.createDirectories(moduleA);
        Files.writeString(moduleA.resolve("pom.xml"), "<project><artifactId>module-a</artifactId></project>");

        Path moduleB = tempDir.resolve("module-b");  
        Files.createDirectories(moduleB);
        Files.writeString(moduleB.resolve("pom.xml"), "<project><artifactId>module-b</artifactId></project>");

        // Note: nonexistent module directory is not created

        ModuleHelper helper = ModuleHelper.builder()
                .basedir(tempDir.toFile())
                .build();

        List<File> modules = helper.getModules();
        assertThat(modules).hasSize(2);
        assertThat(modules.stream().map(File::getName)).containsExactlyInAnyOrder("module-a", "module-b");
    }

    @Test
    @DisplayName("isRunnableModule: returns false when src/main/java doesn't exist")
    void isRunnableModuleNoSrcMainJava(@TempDir Path tempDir) throws Exception {
        ModuleHelper helper = ModuleHelper.builder()
                .basedir(tempDir.getParent().toFile())
                .build();

        boolean isRunnable = helper.isRunnableModule(tempDir.toFile());
        assertThat(isRunnable).isFalse();
    }

    @Test
    @DisplayName("isRunnableModule: returns true when @SpringBootApplication annotation found")
    void isRunnableModuleSpringBootApp(@TempDir Path tempDir) throws Exception {
        // Create src/main/java structure
        Path srcMainJava = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcMainJava);

        // Create class with @SpringBootApplication
        String javaContent = """
                package com.example;
                
                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                
                @SpringBootApplication
                public class Application {
                    public static void main(String[] args) {
                        SpringApplication.run(Application.class, args);
                    }
                }
                """;
        Files.writeString(srcMainJava.resolve("Application.java"), javaContent);

        ModuleHelper helper = ModuleHelper.builder()
                .basedir(tempDir.getParent().toFile())
                .build();

        boolean isRunnable = helper.isRunnableModule(tempDir.toFile());
        assertThat(isRunnable).isTrue();
    }

    @Test
    @DisplayName("isRunnableModule: returns true when main method found without SpringBoot")
    void isRunnableModuleMainMethod(@TempDir Path tempDir) throws Exception {
        // Create src/main/java structure
        Path srcMainJava = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcMainJava);

        // Create class with main method but no @SpringBootApplication
        String javaContent = """
                package com.example;
                
                public class MainApp {
                    public static void main(String[] args) {
                        System.out.println("Hello World");
                    }
                }
                """;
        Files.writeString(srcMainJava.resolve("MainApp.java"), javaContent);

        ModuleHelper helper = ModuleHelper.builder()
                .basedir(tempDir.getParent().toFile())
                .build();

        boolean isRunnable = helper.isRunnableModule(tempDir.toFile());
        assertThat(isRunnable).isTrue();
    }

    @Test
    @DisplayName("isRunnableModule: returns false when no main method or SpringBoot annotation")
    void isRunnableModuleNotRunnable(@TempDir Path tempDir) throws Exception {
        // Create src/main/java structure
        Path srcMainJava = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcMainJava);

        // Create class without main method or SpringBoot annotation
        String javaContent = """
                package com.example;
                
                public class Utility {
                    public void doSomething() {
                        // utility method
                    }
                }
                """;
        Files.writeString(srcMainJava.resolve("Utility.java"), javaContent);

        ModuleHelper helper = ModuleHelper.builder()
                .basedir(tempDir.getParent().toFile())
                .build();

        boolean isRunnable = helper.isRunnableModule(tempDir.toFile());
        assertThat(isRunnable).isFalse();
    }

    @Test
    @DisplayName("isRunnableModule: handles multiple files correctly")
    void isRunnableModuleMultipleFiles(@TempDir Path tempDir) throws Exception {
        // Create src/main/java structure
        Path srcMainJava = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcMainJava);

        // Create utility class (not runnable)
        String utilContent = """
                package com.example;
                public class Utility {
                    public void doSomething() {}
                }
                """;
        Files.writeString(srcMainJava.resolve("Utility.java"), utilContent);

        // Create service class (not runnable)
        String serviceContent = """
                package com.example;
                public class Service {
                    public String process() { return ""; }
                }
                """;
        Files.writeString(srcMainJava.resolve("Service.java"), serviceContent);

        // Create application class (runnable)
        String appContent = """
                package com.example;
                
                @SpringBootApplication
                public class Application {
                    public static void main(String[] args) {}
                }
                """;
        Files.writeString(srcMainJava.resolve("Application.java"), appContent);

        ModuleHelper helper = ModuleHelper.builder()
                .basedir(tempDir.getParent().toFile())
                .build();

        boolean isRunnable = helper.isRunnableModule(tempDir.toFile());
        assertThat(isRunnable).isTrue();
    }
}