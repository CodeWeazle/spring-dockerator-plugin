/**
 * Helper class for module handling
 * 
 * @author Volker Karlmeier
 * 
 */
package net.magiccode.maven.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.maven.plugin.MojoExecutionException;

import lombok.Builder;
import lombok.Data;

/**
 * 
 */
@Builder
@Data
public class ModuleHelper {

	private File basedir;
	
	/**
	 * find modules for the given project in case it is a multi-module project
	 * 
	 * @return all sub-directories of the given project that contain a file with the
	 *         name pom.xml
	 * @throws MojoExecutionException
	 */
	public List<File> getModules() throws MojoExecutionException {
		List<File> modules = new ArrayList<>();
		Path parentPom = basedir.toPath().resolve("pom.xml");

		if (!Files.exists(parentPom)) {
			throw new MojoExecutionException("Parent pom.xml not found at: " + parentPom);
		}

		try {
			List<String> lines = Files.readAllLines(parentPom);
			boolean inModulesSection = false;

			for (String line : lines) {
				line = line.trim();
				if (line.startsWith("<modules>")) {
					inModulesSection = true;
					continue;
				} else if (line.startsWith("</modules>")) {
					inModulesSection = false;
					continue;
				}

				if (inModulesSection && line.startsWith("<module>")) {
					String moduleName = line.replace("<module>", "").replace("</module>", "").trim();
					Path modulePath = basedir.toPath().resolve(moduleName);
					if (Files.exists(modulePath.resolve("pom.xml"))) {
						modules.add(modulePath.toFile());
					}
				}
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Error reading parent pom.xml", e);
		}

		return modules;
	}

	/**
	 * returns whether or not a module is 'runnable'. This method returns true if
	 * any java file in src/main/java (or a subdirectory of this) either contains a
	 * <i>main</i> method or a <i>@SpringBootApplication</i<> annotation.
	 * 
	 * @param moduleDir - the directory of the module for the project
	 * @return true|false
	 * @throws MojoExecutionException
	 */
	public boolean isRunnableModule(File moduleDir) throws MojoExecutionException {
		Path mainJavaDir = moduleDir.toPath().resolve("src/main/java");

		if (!Files.exists(mainJavaDir)) {
			return false;
		}

		try {
			// Check for @SpringBootApplication annotation
			Optional<Path> annotatedClass = Files.walk(mainJavaDir).filter(path -> path.toString().endsWith(".java"))
					.filter(this::containsSpringBootApplicationAnnotation).findFirst();

			if (annotatedClass.isPresent()) {
				return true;
			}

			// Check for a main() method
			Optional<Path> mainClass = Files.walk(mainJavaDir).filter(path -> path.toString().endsWith(".java"))
					.filter(this::containsMainMethod).findFirst();

			return mainClass.isPresent();

		} catch (IOException e) {
			throw new MojoExecutionException("Error scanning sources for module: " + moduleDir.getName(), e);
		}
	}

	/**
	 * checks the given filePath for a java source file which contains a
	 * <i>@SpringBootApplication</i> annotation
	 * 
	 * @param filePath - the path to check for java files
	 * @return whether or not a file containing the <i>@SpringBootApplication</i>
	 *         annotation exists in the given path.
	 */
	private boolean containsSpringBootApplicationAnnotation(Path filePath) {
		try {
			return Files.lines(filePath).anyMatch(line -> line.contains("@SpringBootApplication"));
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * checks the given filePath for a java source file which contains a <i>main</i>
	 * method
	 * 
	 * @param filePath - the path to check for java files
	 * @return whether or not any line in the given path match a main method
	 *         signature
	 */
	private boolean containsMainMethod(Path filePath) {
		try {
			return Files.lines(filePath).anyMatch(line -> line.contains("public static void main"));
		} catch (IOException e) {
			return false;
		}
	}

}
