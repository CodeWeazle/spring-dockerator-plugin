package net.magiccode.maven;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.model.Profile;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.yaml.snakeyaml.Yaml;

import lombok.extern.log4j.Log4j2;
import net.magiccode.maven.docker.ComposeFileGenerator;
import net.magiccode.maven.docker.DockerService;
import net.magiccode.maven.util.ModuleHelper;

@Mojo(name = "generate-docker-compose", defaultPhase = LifecyclePhase.PREPARE_PACKAGE)

@Log4j2
public class DockerComposePlugin extends AbstractMojo {

	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject project;

	@Parameter(defaultValue = "nexus.riskcontrollimited.com:8891/rcl/", property = "imagePrefix")
	private String imagePrefix;

	@Parameter(defaultValue = "spring.datasource.", property = "jdbcPrefix")
	private String jdbcPrefix;

	@Parameter(defaultValue = "${project.basedir}/src/main/resources", property = "propertiesDirs")
	private List<String> propertiesDirs;

	@Parameter(property = "profiles")
	private List<String> profiles;

	@Parameter(property = "skipModules")
	private List<String> skipModules;

	@Parameter(defaultValue = "${project.basedir}/docker", property = "outputDir")
	private String outputDir;

	@Parameter(defaultValue = "${project.basedir}", property = "basedir")
	private File basedir;

	private static final String DOCKER_INCLUDE_COMMENT = "DockerInclude";
	private static final String SERVER_PORT_PROPERTY = "server.port";

	/**
	 * execute method
	 */
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		MavenProject mvnProject = (MavenProject) this.getPluginContext().get("project");
		List<?> activeProfiles = mvnProject.getActiveProfiles();

		List<String> profiles = new ArrayList<>();
		activeProfiles.stream().filter(profile -> (!((Profile) profile).getId().equals("maven-central")))
				.forEach(profile -> profiles.add(((Profile) profile).getId()));
		// In case no profile is specified, add an empty one as default
		if (profiles.isEmpty())
			profiles.add("");

		profiles.stream().forEach(profile -> {
			List<DockerService> services = new ArrayList<>();
			Map<String, String> commonEnvironment = new HashMap<>();

			ModuleHelper moduleHelper = ModuleHelper.builder().basedir(basedir).build();

			try {
				// if this is a multi-module project, we need to check for the modules
				List<File> modules = moduleHelper.getModules();

				Files.createDirectories(Paths.get(outputDir));

				// a map for jdbc configurations.
				Map<String, String> globalJdbcConfigs = new HashMap<>();
				// In multi-module projects, process the modules
				if (!modules.isEmpty()) {
					commonEnvironment = processModules(moduleHelper, modules, services, profile);
				} else {
					log.info("Single module project");
					DockerService dockerService = generateService(basedir);
					services.add(dockerService);
				}
				// Generate docker-compose.yml for all services
				if (!services.isEmpty()) {
					ComposeFileGenerator composeFileGenerator = ComposeFileGenerator.builder().services(services)
							.outputDir(outputDir).commonEnvironment(commonEnvironment).moduleName(project.getName())
							.activeProfile(profile).build();
					composeFileGenerator.generateDockerCompose();
				} else {
					log.warn("No runnable modules found; docker-compose.yml will not be generated.");
				}

				// Generate docker-compose-db.yml for database containers
				if (!globalJdbcConfigs.isEmpty()) {
					generateDatabaseCompose(globalJdbcConfigs);
				}

			} catch (IOException | MojoExecutionException e) {
				throw new RuntimeException(new MojoExecutionException("Error processing property files", e));
			}
		});
		cleanupTarget();
	}

	/**
	 * remove all annotations from properties/yml files in the target directory
	 * before packaging.
	 */
	private void cleanupTarget() {

		Path modulePropertiesDir = Paths.get(basedir + "/target/classes");
		try {
			if (Files.exists(modulePropertiesDir)) {
				Path applicationProperties = modulePropertiesDir.resolve("application.properties");
				if (Files.exists(applicationProperties)) {
					cleanupPropertiesFile(applicationProperties.toFile());
				}

				Path applicationYaml = modulePropertiesDir.resolve("application.yml");
				if (Files.exists(applicationYaml)) {
					cleanupPropertiesFile(applicationYaml.toFile());
				}

				// Process profile-specific files
				for (String profile : profiles) {
					Path propertiesFile = modulePropertiesDir.resolve("application-" + profile + ".properties");
					if (Files.exists(propertiesFile)) {
						cleanupPropertiesFile(propertiesFile.toFile());
					}

					Path yamlFile = modulePropertiesDir.resolve("application-" + profile + ".yml");
					if (Files.exists(yamlFile)) {
						cleanupPropertiesFile(yamlFile.toFile());
					}
				}
			} else {
				log.warn("Properties directory not found: " + modulePropertiesDir.toString());
			}
		} catch (IOException e) {
			log.warn("Exceptiokn occured while cleaning up properties/yaml files in target.");
		}
	}

	/**
	 * remove all annotations from property and yaml files before packaging
	 * 
	 * @param inputFile
	 * @return
	 * @throws IOException
	 */
	private boolean cleanupPropertiesFile(File inputFile) throws IOException {

		File tempFile = new File(inputFile.getName() + ".tmp");

		BufferedReader reader = new BufferedReader(new FileReader(inputFile));
		BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));
		String currentLine;
		while ((currentLine = reader.readLine()) != null) {

			String trimmedLine = currentLine.trim();
			if (trimmedLine.startsWith("#") && trimmedLine.contains(DOCKER_INCLUDE_COMMENT))
				continue;
			writer.write(currentLine + System.getProperty("line.separator"));
		}

		writer.close();
		reader.close();
		return tempFile.renameTo(inputFile);
	}

	/**
	 * For multi-module projects process the modules.
	 * 
	 * @param moduleHelper - ModulesHelper instance providing helper methods to
	 *                     handle the multi-module project's modules.
	 * @param services     - List of services shared with calling method.
	 * @param modules      List of File instances of the module directories. These
	 *                     are those containing a file with the name pom.xml
	 * @return the common environment. This is a map of properties that are common
	 *         to at least two of (runnable) modules involved.
	 * @throws MojoExecutionException
	 * @throws IOException
	 */
	private Map<String, String> processModules(ModuleHelper moduleHelper, final List<File> modules,
			final List<DockerService> services, String activeProfile) throws MojoExecutionException, IOException {
		Map<String, String> commonEnvironment;
		log.info("Found " + modules.size() + " module(s).");

		// Iterate over each module
		for (File module : modules) {
			// is it in the <skip> list?
			if (skipModules.contains(module.getName())) {
				log.info("Skipping module " + module.getName());
				// is it runnable?
			} else if (moduleHelper.isRunnableModule(module)) {
				DockerService dockerService = generateService(module);
				services.add(dockerService);
				ComposeFileGenerator composeFileGenerator = ComposeFileGenerator.builder().outputDir(outputDir)
						.moduleName(module.getName()).services(services).activeProfile(activeProfile).build();
				composeFileGenerator.generateModuleDockerCompose();
			} else {
				log.info("Skipping non-runnable module: " + module.getName());
			}
		}
		commonEnvironment = compileCommonProperties(services);
		return commonEnvironment;
	}

	/**
	 * compiles a map of common properties for the list of given services and
	 * returns this. All common properties are removed from the service instances,
	 * so that no duplications occur. A property is valid a as a common property if
	 * it occurs in at least 2 modules and matches with the key as well as the value
	 * in all modules the key occurs.
	 * 
	 * @param services - the list of services for the multi-module project
	 * @return A map of common properties fulfilling the above conditions over all
	 *         modules.
	 */
	private Map<String, String> compileCommonProperties(List<DockerService> services) {

		final Map<String, String> commonEnv = new HashMap<>();
		if (services == null || services.size() < 2) {
			return commonEnv;
		}

		DockerService refererenceService = null;

		for (int serviceIndex = 0; serviceIndex < services.size() - 1; serviceIndex++) {
			refererenceService = services.get(serviceIndex);
			Set<String> refServiceKeys = refererenceService.getDockerEnvVars().keySet();
			for (String refServiceKey : refServiceKeys) {
				boolean allEqualOrMissing = true;
				int numOccurences = 0;
				String referenceValue = refererenceService.getDockerEnvVars().get(refServiceKey);
				for (int compareServiceIndex = serviceIndex + 1; compareServiceIndex < services
						.size(); compareServiceIndex++) {
					DockerService compareService = services.get(compareServiceIndex);
					Map<String, String> compareMap = compareService.getDockerEnvVars();

					if (compareMap.containsKey(refServiceKey)) {
						if (compareMap.get(refServiceKey).equals(referenceValue)) {
							numOccurences++;
						} else {
							allEqualOrMissing = false;
						}
					}
				}
				if (allEqualOrMissing && numOccurences > 0) {
					commonEnv.put(refServiceKey, referenceValue);
				}
			}
		}
		// remove common variables from individual services
		for (int removeIndex = 0; removeIndex < services.size(); removeIndex++) {
			for (String key : commonEnv.keySet()) {
				if (services.get(removeIndex).getDockerEnvVars().containsKey(key)) {
					services.get(removeIndex).getDockerEnvVars().remove(key);
				}
			}
		}
		return commonEnv;
	}

	/**
	 * generate a populated <code>DockerService</code> instance for the given module
	 * directory in a multi-module project
	 * 
	 * @param moduleDirectory - the directory of the module containing a file called
	 *                        pom.xml
	 * @return The <code>DockerService</code> instance populated with data from the
	 *         module's annotated properties
	 * @throws IOException
	 */
	private DockerService generateService(File moduleDirectory) throws IOException {

		String serviceName = moduleDirectory.getName(); // Use the module's directory name as the service name
		log.info("Processing runnable module: " + serviceName);

		Map<String, String> dockerEnvVars = new HashMap<>();
		Map<String, String> jdbcConfigs = new HashMap<>();
		List<String> ports = new ArrayList<>();

		// Iterate through all specified properties directories
		for (String propertiesDirPath : propertiesDirs) {
			Path modulePropertiesDir = moduleDirectory.toPath().resolve(propertiesDirPath);

			if (Files.exists(modulePropertiesDir)) {
				log.info("Processing properties directory: " + modulePropertiesDir.toString());

				// Always include base application.properties and application.yml
				Path applicationProperties = modulePropertiesDir.resolve("application.properties");
				if (Files.exists(applicationProperties)) {
					log.info("Reading base properties from: " + applicationProperties.toString());
					processProperties(applicationProperties, dockerEnvVars, jdbcConfigs, ports);
				}

				Path applicationYaml = modulePropertiesDir.resolve("application.yml");
				if (Files.exists(applicationYaml)) {
					log.info("Reading base YAML properties from: " + applicationYaml.toString());
					processYaml(applicationYaml, dockerEnvVars, jdbcConfigs, ports);
				}

				// Process profile-specific files
				for (String profile : profiles) {
					Path propertiesFile = modulePropertiesDir.resolve("application-" + profile + ".properties");
					if (Files.exists(propertiesFile)) {
						log.info("Reading properties from: " + propertiesFile.toString());
						processProperties(propertiesFile, dockerEnvVars, jdbcConfigs, ports);
					}

					Path yamlFile = modulePropertiesDir.resolve("application-" + profile + ".yml");
					if (Files.exists(yamlFile)) {
						log.info("Reading YAML properties from: " + yamlFile.toString());
						processYaml(yamlFile, dockerEnvVars, jdbcConfigs, ports);
					}
				}
			} else {
				log.warn("Properties directory not found: " + modulePropertiesDir.toString());
			}
		}
		// add profiles setting
		if (!dockerEnvVars.containsKey("spring.profiles.active")) {
			dockerEnvVars.put("spring.profiles.active", String.join(",", profiles));
		}
		// set default server.port if missing
		if (!dockerEnvVars.containsKey(SERVER_PORT_PROPERTY) && !dockerEnvVars.containsKey("SERVER_PORT")) {
			dockerEnvVars.put("server.port", "8080");
			ports.add("8080");
		}

		DockerService dockerService = DockerService.builder().name(moduleDirectory.getName()).jdbcConfigs(jdbcConfigs)
				.ports(ports).dockerEnvVars(formatEnvironmentVariables(dockerEnvVars)).imagePrefix(imagePrefix)
				.version(project.getVersion()).build();
		return dockerService;
	}

	/**
	 * @param propertiesFile
	 * @param dockerEnvVars
	 * @param jdbcConfigs
	 * @param ports
	 * @throws IOException
	 */
	private void processProperties(Path propertiesFile, Map<String, String> dockerEnvVars,
			Map<String, String> jdbcConfigs, List<String> ports) throws IOException {
		List<String> lines = Files.readAllLines(propertiesFile);

		boolean includeNext = false; // Flag to indicate the next property should be included
		for (String line : lines) {
			line = line.trim();

			if (line.isEmpty() || line.startsWith("#")) {
				// Check if it's a DockerInclude comment
				if (isLineComment(line) && line.contains(DOCKER_INCLUDE_COMMENT)) {
					includeNext = true;
				}
				continue; // Skip comments and empty lines
			}

			if (includeNext) {
				String[] keyValue = line.split("=", 2);
				if (keyValue.length == 2) {
					String key = keyValue[0].trim();
					String value = keyValue[1].trim();
					dockerEnvVars.put(key, value);
				}
				includeNext = false; // Reset the flag after processing
			}

			// Always include server.port in ports
			if (line.startsWith(SERVER_PORT_PROPERTY + "=")) {
				String port = line.split("=", 2)[1].trim();
				ports.add(port);
			}

			// Capture JDBC configurations based on configurable prefix
			if (line.startsWith(this.jdbcPrefix)) {
				String[] keyValue = line.split("=", 2);
				if (keyValue.length == 2) {
					jdbcConfigs.put(keyValue[0].trim(), keyValue[1].trim());
				}
			}
		}
	}

	/**
	 * @param yamlFile
	 * @param dockerEnvVars
	 * @param jdbcConfigs
	 * @param ports
	 * @throws IOException
	 */
	private void processYaml(Path yamlFile, Map<String, String> dockerEnvVars, Map<String, String> jdbcConfigs,
			List<String> ports) throws IOException {
		List<String> lines = Files.readAllLines(yamlFile);
		Set<String> includeKeys = new HashSet<>();

		// Preprocess the file to identify keys associated with # DockerInclude
		String lastIncludeKey = null;
		for (String line : lines) {
			line = line.trim();

			if (line.startsWith("#") && line.contains(DOCKER_INCLUDE_COMMENT)) {
				lastIncludeKey = null; // Reset for new include marker
			} else if (lastIncludeKey == null && !line.isEmpty() && !line.startsWith("#")) {
				int colonIndex = line.indexOf(':');
				if (colonIndex > 0) {
					lastIncludeKey = line.substring(0, colonIndex).trim();
					includeKeys.add(lastIncludeKey);
				}
			}
		}

		// Parse the YAML file and process keys recursively
		Yaml yaml = new Yaml();
		try (InputStream input = Files.newInputStream(yamlFile)) {
			Map<String, Object> yamlMap = yaml.load(input);
			if (yamlMap != null) {
				traverseYaml("", yamlMap, dockerEnvVars, jdbcConfigs, ports, includeKeys);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void traverseYaml(String parentKey, Map<String, Object> yamlMap, Map<String, String> dockerEnvVars,
			Map<String, String> jdbcConfigs, List<String> ports, Set<String> includeKeys) {
		for (Map.Entry<String, Object> entry : yamlMap.entrySet()) {
			String currentKey = parentKey.isEmpty() ? entry.getKey() : parentKey + "_" + entry.getKey();
			Object value = entry.getValue();

			if (value instanceof Map) {
				// Recursively process nested maps
				traverseYaml(currentKey, (Map<String, Object>) value, dockerEnvVars, jdbcConfigs, ports, includeKeys);
			} else if (value != null) {
				String originalKey = entry.getKey(); // Preserve the original YAML key
				if (!includeKeys.contains(originalKey)) {
					continue; // Skip keys not marked with # DockerInclude
				}

				// Format the key for Docker environment variables
				String formattedKey = currentKey.toUpperCase().replace(".", "_").replace("-", "_");
				String stringValue = value.toString().trim();

				// Check for JDBC configurations
				if (formattedKey.startsWith(jdbcPrefix.toUpperCase().replace(".", "_").replace("-", "_"))) {
					jdbcConfigs.put(formattedKey, stringValue);
				}

				// Always include server.port in ports
				if (formattedKey.equals(SERVER_PORT_PROPERTY.toUpperCase().replace(".", "_").replace("-", "_"))) {
					ports.add(stringValue);
				}

				// Add DockerInclude properties to the environment variables
				dockerEnvVars.put(formattedKey, stringValue);
			}
		}
	}

	/**
	 * generate a docker compose file for the given database.
	 * 
	 * @param jdbcConfigs
	 * @throws IOException
	 */
	private void generateDatabaseCompose(Map<String, String> jdbcConfigs) throws IOException {
		if (jdbcConfigs.isEmpty()) {
			return;
		}

		Path databaseComposeFile = Paths.get(outputDir, "docker-compose-db.yml");
		try (BufferedWriter writer = Files.newBufferedWriter(databaseComposeFile)) {
			writer.write("version: '3.8'\n");
			writer.write("services:\n");
			writer.write("  database:\n");
			writer.write("    image: mysql:8.0\n");
			writer.write("    environment:\n");

			for (Map.Entry<String, String> entry : jdbcConfigs.entrySet()) {
				String envVar = entry.getKey().replace(jdbcPrefix, "").toUpperCase();
				writer.write("      - " + envVar + "=" + entry.getValue() + "\n");
			}

			writer.write("    ports:\n");
			writer.write("      - \"3306:3306\"\n");
		}
		log.info("Generated Database Docker Compose file: " + databaseComposeFile.toString());
	}

	/**
	 * format environment variable names into a docker conform format. This converts
	 * the keys to upper case and '.' as well as '-' to '_'. The values in the given
	 * map remain unchanged.
	 * 
	 * @param envVars - a map of environment variables
	 * @return the formatted map.
	 */
	private Map<String, String> formatEnvironmentVariables(Map<String, String> envVars) {
		Map<String, String> formatted = new HashMap<>();
		for (Map.Entry<String, String> entry : envVars.entrySet()) {
			String key = formatPropertyKey(entry.getKey());
			formatted.put(key, entry.getValue());
		}
		return formatted;
	}

	/**
	 * format environment variable names into a docker conform format. This converts
	 * the keys to upper case and '.' as well as '-' to '_'. The values in the given
	 * map remain unchanged.
	 * 
	 * @param propertyKey - the key to format
	 * @return the formatted key.
	 */
	private String formatPropertyKey(String propertyKey) {
		return propertyKey.toUpperCase().replace('.', '_').replace('-', '_').replace('[', '_').replace(']', '_')
				.replaceAll("__", "_");
	}

	/**
	 * checks whether or not the given line is a comment.
	 * 
	 * @param line - the input line string
	 * @return whether or not the given line starts with <i>#</i>
	 */
	private boolean isLineComment(String line) {
		return line.stripIndent().startsWith("#");
	}

}
