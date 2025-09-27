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
import net.magiccode.maven.docker.VolumeMapping;
import net.magiccode.maven.util.EnvironmentHelper;
import net.magiccode.maven.util.ModuleHelper;

/**
 * Mojo implementation for generating docker compose files during build.
 * This plugin processes Spring Boot applications and generates docker-compose.yml files
 * with environment variables, volume mappings, and port configurations.
 * 
 * @author CodeWeazle
 * @since 0.0.6
 */
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

	@Parameter(property = "volumes")
	private List<Volume> volumes;

	@Parameter(defaultValue = "true", property = "createEnv")
	private Boolean createEnv;

	@Parameter(defaultValue = "${project.basedir}/docker", property = "outputDir")
	private String outputDir;

	@Parameter(defaultValue = "${project.basedir}", property = "basedir")
	private File basedir;

	private static final String DOCKER_INCLUDE_COMMENT = "DockerInclude";
	private static final String SERVER_PORT_PROPERTY = "server.port";

	/**
	 * Executes the Docker Compose plugin to generate docker-compose.yml files.
	 * Processes both single-module and multi-module Maven projects, extracting
	 * configuration from Spring Boot properties files and generating appropriate
	 * Docker Compose configurations.
	 * 
	 * @throws MojoExecutionException if an error occurs during plugin execution
	 * @throws MojoFailureException if the plugin fails to complete successfully
	 */
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		log.info("========================================");
		log.info("Starting Spring Dockerator Plugin v{}", project.getVersion());
		log.info("========================================");
		log.info("Project: {} ({})", project.getName(), project.getVersion());
		log.info("Base directory: {}", basedir.getAbsolutePath());
		log.info("Output directory: {}", outputDir);
		log.info("Image prefix: {}", imagePrefix);
		log.info("Create .env file: {}", createEnv);

		MavenProject mvnProject = (MavenProject) this.getPluginContext().get("project");
		List<?> activeProfiles = mvnProject.getActiveProfiles();

		List<String> profiles = new ArrayList<>();
		activeProfiles.stream().filter(profile -> (!((Profile) profile).getId().equals("maven-central")))
				.forEach(profile -> profiles.add(((Profile) profile).getId()));
		// In case no profile is specified, add an empty one as default
		if (profiles.isEmpty()) {
			profiles.add("");
			log.info("No active profiles found, using default configuration");
		} else {
			log.info("Active profiles: {}", String.join(", ", profiles));
		}

		profiles.stream().forEach(profile -> {
			log.info("Processing profile: '{}'", profile.isEmpty() ? "default" : profile);
			List<DockerService> services = new ArrayList<>();
			Map<String, String> commonEnvironment = new HashMap<>();

			ModuleHelper moduleHelper = ModuleHelper.builder().basedir(basedir).build();

			try {
				// if this is a multi-module project, we need to check for the modules
				List<File> modules = moduleHelper.getModules();

				Files.createDirectories(Paths.get(outputDir));

				// a map for jdbc configurations.
				Map<String, String> globalJdbcConfigs = new HashMap<>();
				List<VolumeMapping> commonVolumes = new ArrayList<>();
				
				// In multi-module projects, process the modules
				if (!modules.isEmpty()) {
					log.info("Multi-module project detected with {} module(s)", modules.size());
					commonEnvironment = processModules(moduleHelper, modules, services, profile);
					commonVolumes = compileCommonVolumes(services);
					if (!commonVolumes.isEmpty()) {
						log.info("Found {} common volume(s) across modules", commonVolumes.size());
					}
				} else {
					log.info("Single module project detected");
					DockerService dockerService = generateService(basedir);
					services.add(dockerService);
				}
				// Generate docker-compose.yml for all services
				if (!services.isEmpty()) {
					log.info("Generating docker-compose.yml for {} service(s)", services.size());
					if (!commonEnvironment.isEmpty()) {
						log.info("Found {} common environment variable(s)", commonEnvironment.size());
					}
					ComposeFileGenerator composeFileGenerator = ComposeFileGenerator.builder().services(services)
							.outputDir(outputDir).commonEnvironment(commonEnvironment).commonVolumes(commonVolumes)
							.moduleName(project.getName()).activeProfile(profile).createEnvironmentFile(createEnv).build();
					composeFileGenerator.generateDockerCompose();
					log.info("Successfully generated docker-compose.yml");
				} else {
					log.warn("No runnable modules found; docker-compose.yml will not be generated.");
				}

				// Generate docker-compose-db.yml for database containers
				if (!globalJdbcConfigs.isEmpty()) {
					log.info("Generating database docker-compose file with {} JDBC configuration(s)", globalJdbcConfigs.size());
					generateDatabaseCompose(globalJdbcConfigs);
				}
				// create .env file if required
				if (createEnv) {
					log.info("Generating .env file with environment variables");
					createEnvironmentFile(commonEnvironment, services);
				}

			} catch (IOException | MojoExecutionException e) {
				log.error("Error during plugin execution: {}", e.getMessage());
				throw new RuntimeException(new MojoExecutionException("Error processing property files", e));
			}
		});
		
		log.info("Cleaning up target directory files");
		cleanupTarget();
		log.info("========================================");
		log.info("Spring Dockerator Plugin execution completed successfully");
		log.info("========================================");
	}

	/**
	 * Removes all DockerInclude annotations from properties/yml files in the target directory
	 * before packaging to ensure clean deployment artifacts.
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
	 * Removes all DockerInclude annotations from property and yaml files before packaging
	 * to ensure clean artifacts without build-time annotations.
	 * 
	 * @param inputFile the property or YAML file to clean up
	 * @return true if the cleanup was successful, false otherwise
	 * @throws IOException if an I/O error occurs during file processing
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
	 * Processes modules in a multi-module Maven project.
	 * Iterates through all modules, identifies runnable modules (those containing Spring Boot applications),
	 * and generates Docker services for each. Also generates individual module docker-compose files.
	 * 
	 * @param moduleHelper helper instance providing methods to handle multi-module project operations
	 * @param modules list of module directories containing pom.xml files
	 * @param services shared list of services to be populated with Docker service configurations
	 * @param activeProfile the currently active Maven profile
	 * @return map of common environment variables shared between at least two modules
	 * @throws MojoExecutionException if an error occurs during module processing
	 * @throws IOException if an I/O error occurs during file operations
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
						.moduleName(module.getName()).services(services).activeProfile(activeProfile)
						.createEnvironmentFile(createEnv).build();
				composeFileGenerator.generateModuleDockerCompose();
			} else {
				log.info("Skipping non-runnable module: " + module.getName());
			}
		}
		commonEnvironment = compileCommonProperties(services);
		return commonEnvironment;
	}

	/**
	 * Compiles a map of common properties for the list of given services and
	 * returns it. All common properties are removed from the service instances
	 * to avoid duplications. A property is considered common if it occurs in at 
	 * least 2 modules and matches both the key and value in all modules where the key occurs.
	 * 
	 * @param services the list of services for the multi-module project
	 * @return a map of common properties fulfilling the above conditions across all modules
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
	 * Compiles a list of common volumes for the given services and returns it.
	 * All common volumes are removed from the service instances to avoid duplications.
	 * A volume is considered common if it occurs in at least 2 modules and matches
	 * both the external and internal paths in all modules where the volume occurs.
	 * 
	 * According to Docker Compose requirements: if a service defines specific volumes,
	 * all volumes (common + specific) are listed directly in that service to comply
	 * with Docker Compose and YAML rules.
	 * 
	 * @param services the list of services for the multi-module project
	 * @return a list of common volumes fulfilling the above conditions across all modules
	 */
	private List<VolumeMapping> compileCommonVolumes(List<DockerService> services) {
		final List<VolumeMapping> commonVolumes = new ArrayList<>();
		if (services == null || services.size() < 2) {
			return commonVolumes;
		}

		// Count occurrences of each volume mapping
		Map<VolumeMapping, Integer> volumeCounts = new HashMap<>();
		for (DockerService service : services) {
			for (VolumeMapping volume : service.getSpecificVolumes()) {
				volumeCounts.put(volume, volumeCounts.getOrDefault(volume, 0) + 1);
			}
		}

		// Find volumes that occur in at least 2 services
		for (Map.Entry<VolumeMapping, Integer> entry : volumeCounts.entrySet()) {
			if (entry.getValue() >= 2) {
				commonVolumes.add(entry.getKey());
				log.info("Found common volume: {} -> {} (appears in {} service(s))", 
						entry.getKey().getExternal(), entry.getKey().getInternal(), entry.getValue());
			}
		}

		// According to requirements: "If the submodule/service itself defines volumes, 
		// just add the list of additional volumes and do not use the common-volumes 
		// to comply with the rules given by docker compose and yaml."
		// This means services with specific volumes should list ALL volumes directly.
		for (DockerService service : services) {
			boolean hasSpecificVolumes = !service.getSpecificVolumes().isEmpty();
			if (hasSpecificVolumes) {
				// Services with specific volumes should list ALL volumes (common + specific) directly
				// Create a new list with common volumes first, then specific volumes
				List<VolumeMapping> allVolumes = new ArrayList<>(commonVolumes);
				for (VolumeMapping specificVol : new ArrayList<>(service.getSpecificVolumes())) {
					if (!commonVolumes.contains(specificVol)) {
						allVolumes.add(specificVol);
					}
				}
				service.getSpecificVolumes().clear();
				service.getSpecificVolumes().addAll(allVolumes);
			} else {
				// Remove common volumes from services that have no specific volumes
				service.getSpecificVolumes().removeAll(commonVolumes);
			}
		}

		return commonVolumes;
	}

	/**
	 * Generates a populated DockerService instance for the given module directory
	 * in a multi-module project or for a single-module project.
	 * Processes Spring Boot properties files to extract environment variables,
	 * port configurations, and volume mappings.
	 * 
	 * @param moduleDirectory the directory of the module containing a pom.xml file
	 * @return the DockerService instance populated with data from the module's annotated properties
	 * @throws IOException if an I/O error occurs during file processing
	 */
	private DockerService generateService(File moduleDirectory) throws IOException {

		String serviceName = moduleDirectory.getName(); // Use the module's directory name as the service name
		log.info("Processing runnable module: " + serviceName);

		Map<String, String> dockerEnvVars = new HashMap<>();
		Map<String, String> jdbcConfigs = new HashMap<>();
		List<String> ports = new ArrayList<>();
		List<VolumeMapping> volumeMappings = new ArrayList<>();

		// Process configured volumes
		if (volumes != null && !volumes.isEmpty()) {
			log.info("Processing {} configured volume(s) for module: {}", volumes.size(), serviceName);
			for (int i = 0; i < volumes.size(); i++) {
				Volume volume = volumes.get(i);
				if (log.isDebugEnabled()) {
					log.debug("Volume {}: external='{}', internal='{}'", i, volume.getExternal(), volume.getInternal());
				}
				if (volume.getExternal() != null && volume.getInternal() != null) {
					VolumeMapping mapping = VolumeMapping.builder()
							.external(volume.getExternal())
							.internal(volume.getInternal())
							.build();
					volumeMappings.add(mapping);
					log.info("✓ Added volume mapping: {} -> {}", volume.getExternal(), volume.getInternal());
				} else {
					log.warn("✗ Skipping incomplete volume configuration: external='{}', internal='{}'", 
							volume.getExternal(), volume.getInternal());
				}
			}
		} else {
			if (volumes == null) {
				log.info("No volume configuration found for module: {}", serviceName);
			} else {
				log.info("Volume configuration is empty for module: {}", serviceName);
			}
		}

		// Iterate through all specified properties directories
		for (String propertiesDirPath : propertiesDirs) {
			Path modulePropertiesDir = moduleDirectory.toPath().resolve(propertiesDirPath);

			if (Files.exists(modulePropertiesDir)) {
				if (log.isDebugEnabled()) {
					log.debug("Processing properties directory: {}", modulePropertiesDir.toString());
				}

				// Always include base application.properties and application.yml
				Path applicationProperties = modulePropertiesDir.resolve("application.properties");
				if (Files.exists(applicationProperties)) {
					if (log.isDebugEnabled()) {
						log.debug("Reading base properties from: {}", applicationProperties.toString());
					}
					processProperties(applicationProperties, dockerEnvVars, jdbcConfigs, ports);
				}

				Path applicationYaml = modulePropertiesDir.resolve("application.yml");
				if (Files.exists(applicationYaml)) {
					if (log.isDebugEnabled()) {
						log.debug("Reading base YAML properties from: {}", applicationYaml.toString());
					}
					processYaml(applicationYaml, dockerEnvVars, jdbcConfigs, ports);
				}

				// Process profile-specific files
				for (String profile : profiles) {
					Path propertiesFile = modulePropertiesDir.resolve("application-" + profile + ".properties");
					if (Files.exists(propertiesFile)) {
						if (log.isDebugEnabled()) {
							log.debug("Reading properties from: {}", propertiesFile.toString());
						}
						processProperties(propertiesFile, dockerEnvVars, jdbcConfigs, ports);
					}

					Path yamlFile = modulePropertiesDir.resolve("application-" + profile + ".yml");
					if (Files.exists(yamlFile)) {
						if (log.isDebugEnabled()) {
							log.debug("Reading YAML properties from: {}", yamlFile.toString());
						}
						processYaml(yamlFile, dockerEnvVars, jdbcConfigs, ports);
					}
				}
			} else {
				log.warn("Properties directory not found: {}", modulePropertiesDir.toString());
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
			log.info("Using default port 8080 for service: {}", serviceName);
		}

		log.info("Service '{}' configured with {} environment variable(s), {} port(s), {} volume(s)", 
				serviceName, dockerEnvVars.size(), ports.size(), volumeMappings.size());

		DockerService dockerService = DockerService.builder()
												   .name(moduleDirectory.getName())
												   .jdbcConfigs(jdbcConfigs)
												   .ports(ports)
												   .dockerEnvVars(formatEnvironmentVariables(dockerEnvVars))
												   .specificVolumes(volumeMappings)
												   .imagePrefix(imagePrefix)
												   .version(project.getVersion())
												   .createEnvironmentFile(createEnv)
												   .build();
		return dockerService;
	}

	/**
	 * Processes a Spring Boot properties file to extract Docker-relevant configurations.
	 * Looks for properties marked with DockerInclude comments and processes server.port
	 * and JDBC configurations based on the configured prefix.
	 * 
	 * @param propertiesFile the properties file to process
	 * @param dockerEnvVars map to store Docker environment variables
	 * @param jdbcConfigs map to store JDBC configuration properties
	 * @param ports list to store port configurations
	 * @throws IOException if an I/O error occurs during file reading
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
	 * Processes a Spring Boot YAML file to extract Docker-relevant configurations.
	 * Looks for properties marked with DockerInclude comments and processes server.port
	 * and JDBC configurations based on the configured prefix.
	 * 
	 * @param yamlFile the YAML file to process
	 * @param dockerEnvVars map to store Docker environment variables
	 * @param jdbcConfigs map to store JDBC configuration properties
	 * @param ports list to store port configurations
	 * @throws IOException if an I/O error occurs during file reading
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

	/**
	 * Recursively traverses a YAML structure to extract configuration properties.
	 * Processes nested maps and converts YAML keys to Docker environment variable format.
	 * Only includes properties that are marked with DockerInclude comments.
	 * 
	 * @param parentKey the parent key path for nested properties
	 * @param yamlMap the YAML map structure to traverse
	 * @param dockerEnvVars map to store Docker environment variables
	 * @param jdbcConfigs map to store JDBC configuration properties
	 * @param ports list to store port configurations
	 * @param includeKeys set of keys that should be included (marked with DockerInclude)
	 */
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
	 * Generates a docker-compose file for the given database configuration.
	 * Creates a MySQL 8.0 database service with the provided JDBC configurations.
	 * 
	 * @param jdbcConfigs map containing JDBC configuration properties
	 * @throws IOException if an I/O error occurs during file writing
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
	 * Formats environment variable names into Docker-compliant format.
	 * Converts keys to uppercase and replaces '.', '-', '[', ']' with '_'.
	 * The values in the given map remain unchanged.
	 * 
	 * @param envVars map of environment variables to format
	 * @return the formatted map with Docker-compliant variable names
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
	 * Formats a single environment variable name into Docker-compliant format.
	 * Converts the key to uppercase and replaces '.', '-', '[', ']' with '_'.
	 * Also removes consecutive underscores.
	 * 
	 * @param propertyKey the property key to format
	 * @return the formatted key in Docker-compliant format
	 */
	private String formatPropertyKey(String propertyKey) {
		return propertyKey.toUpperCase().replace('.', '_').replace('-', '_').replace('[', '_').replace(']', '_')
				.replaceAll("__", "_");
	}

	/**
	 * Checks whether the given line is a comment line.
	 * A line is considered a comment if it starts with '#' after stripping indentation.
	 * 
	 * @param line the input line string to check
	 * @return true if the line starts with '#', false otherwise
	 */
	private boolean isLineComment(String line) {
		return line.stripIndent().startsWith("#");
	}

	/**
	 * Creates a .env file in the output directory containing all environment
	 * variables used in the docker-compose file. The file includes both common
	 * environment variables and service-specific variables with proper naming prefixes.
	 * 
	 * @param commonEnvironment map of common environment variables shared across services
	 * @param services list of services to include environment variables from
	 * @return true if the file was created successfully, false otherwise
	 */
	private boolean createEnvironmentFile(final Map<String, String> commonEnvironment,
										  final List<DockerService> services) {
		Path environmentFile = Paths.get(outputDir, ".env");

		try (BufferedWriter writer = Files.newBufferedWriter(environmentFile)) {
			StringBuffer commonBuffer = new StringBuffer();
			commonEnvironment.entrySet().stream().sorted(Map.Entry.comparingByKey())
					.forEach(entry -> commonBuffer.append(entry.getKey())
							.append("=")
							.append(EnvironmentHelper.generateValueEntry(false, entry.getKey(), entry.getValue()))
							.append("\n"));

			writer.write(commonBuffer.toString());

			for (DockerService service : services) {
				StringBuffer serviceBuffer = new StringBuffer();
				service.getDockerEnvVars().entrySet().stream().sorted(Map.Entry.comparingByKey())
						.forEach(entry -> serviceBuffer.append(EnvironmentHelper.generateNameEntry(true, entry.getKey(), service.getName()))
								.append("=")
								.append(EnvironmentHelper.generateValueEntry(false, entry.getKey(), entry.getValue()))
								.append("\n"));
				writer.write(serviceBuffer.toString());
			}
		} catch (IOException e) {
			log.error("Error writing .env file: {}", e.getMessage());
			return false;
		}
		log.info("Successfully generated environment file: {}", environmentFile.toString());
		return true;
	}

}
