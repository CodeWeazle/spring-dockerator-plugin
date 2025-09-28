# Spring Dockerator Plugin

The purpose of this plugin is to generate Docker Compose files during a Maven build cycle.
The generated `docker-compose<...>.yml` file is based on the (Spring) profiles defined in the plugin configuration.
To expose properties as environment variables to the Docker Compose file, these can be annotated by a preceding comment[^comment] 
containing the tag _DockerInclude_.

The default plugin life-cycle phase is _prepare-package_. This ensures all code has been compiled and tested but not yet 
packaged, so that a cleanup procedure can remove all annotations from the properties files before they end up in the created
archive. Be aware that *only packaged properties/yml files are cleaned*. This does not happen for the _config_ directory,
for instance, since this is not copied into the _target_ directory and thus included in the archive (by default).

It is important to note that this plugin only creates the Docker Compose file from information taken from the project;
it does NOT create a Dockerfile or even a Docker image. This remains the responsibility of the developer.


[^comment]:
	As a comment counts a line whose first non-space character is '#'.
  

## Requirements

This Maven plugin requires Java version 17 or newer, Maven 3.3 or newer.

## Goals

At present, this plugin offers just one goal: ```spring-dockerator:generate-docker-compose```.

## Usage

### Maven

To add this plugin to your project, simply follow the usual pattern for Maven plugins.

```xml
    <project>
      ...
      <build>
	      <plugins>
	        <plugin>
			  <groupId>net.magiccode.maven</groupId>
			  <artifactId>spring-dockerator-plugin</artifactId>
			  <version>0.0.7</version>
	          <configuration>
	            <!-- put your configurations here -->
	          </configuration>
	        </plugin>
	      </plugins>
      </build>
      ...
    </project>
```

### Example
```xml
    <project>
      ...
      <build>
		<plugin>
			<groupId>net.magiccode.maven</groupId>
			<artifactId>spring-dockerator-plugin</artifactId>
			<version>0.0.7</version>
			<executions>
				<execution>
					<goals>
						<goal>generate-docker-compose</goal>
					</goals>
				</execution>
			</executions>
			<configuration>
				<propertiesDirs>
					<propertiesDir>src/main/resources</propertiesDir>
					<propertiesDir>config</propertiesDir>
				</propertiesDirs>
				<profiles>
					<profile>demo</profile>
					<profile>postgres</profile>
					<profile>sba</profile>
				</profiles>
				<outputDir>${project.basedir}/target/docker</outputDir>
			</configuration>
		</plugin>
      </build>
      ...
    </project>
```	



The version shown is related to the *development* branch. For stable releases, please consult the [Maven Central repository](https://central.sonatype.com/artifact/net.magiccode.maven/spring-dockerator-plugin/overview).

### Other Build Systems

For other build systems, please consult the current [Maven Central site](https://central.sonatype.com/artifact/net.magiccode.maven/spring-dockerator-plugin).


## Configuration

Without any configuration, the plugin will not do very much.

| Option         | Description                       | Example                                                |
| ------         | -----------                       | -------                                               |
| outputDir      | The directory where the Docker Compose files will be created. Defaults to __${project.basedir}__, but should be changed to avoid overwriting existing files if you already have this directory.| <outputDir>${project.basedir}/target/docker</outputDir>|
| imagePrefix    | A prefix to be added in front of the generated image name.| \<imagePrefix\>nexus.magiccode.net:8891/plugins/\</imagePrefix\>|
| profiles       | List of profile names for which the profiles (i.e., properties/yaml files) are parsed for generation. |\<profiles\><br/>&nbsp;&nbsp;\<profile\>demo\</profile\><br/>&nbsp;&nbsp;<profile\>postgres\</profile\><br/>\</profiles\>|
| propertiesDirs | List of directories to scan for the properties/yml files.|\<propertiesDirs\><br/>&nbsp;&nbsp;\<propertiesDir\>src/main/resources\</propertiesDir\><br/>&nbsp;&nbsp;\<propertiesDir\>config\</propertiesDir\><br/>\</propertiesDirs\> |
| skipModules    | The code only recognises runnable modules in multi-module projects[^runnable]. To explicitly exclude modules, list them here.|\<skipModules\><br/>&nbsp;&nbsp;\<skipModule\>demo-core\</skipModule\><br/>&nbsp;&nbsp;\<skipModule\>demo-common\</skipModule\><br/>\</skipModules\>|
| createEnv      | Create an environment file `.env` instead of adding variable values directly to the Docker Compose file.|\<createEnv\><br/>true<br/>\</createEnv\>|
| volumes        | Define volume mappings between host and container paths for Docker services. See [Volume Support](#volume-support) section for detailed information.|\<volumes\><br/>&nbsp;&nbsp;\<volume\><br/>&nbsp;&nbsp;&nbsp;&nbsp;\<external\>../ssl\</external\><br/>&nbsp;&nbsp;&nbsp;&nbsp;\<internal\>/opt/ssl\</internal\><br/>&nbsp;&nbsp;\</volume\><br/>\</volumes\>|


### Configuration Example
```xml	
    <project>
      ...
      <build>
		<plugin>
			<groupId>net.magiccode.maven</groupId>
			<artifactId>spring-dockerator-plugin</artifactId>
			<version>0.0.7</version>
			<executions>
				<execution>
					<goals>
						<goal>generate-docker-compose</goal>
					</goals>
				</execution>
			</executions>
			<configuration>
				<createEnv>true</createEnv>
				<skipModules>
					<skipModule>demo-core</skipModule>
					<skipModule>demo-common</skipModule>
				</skipModules>
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
				<propertiesDirs>
					<propertiesDir>src/main/resources</propertiesDir>
					<propertiesDir>config</propertiesDir>
				</propertiesDirs>
				<profiles>
					<profile>demo</profile>
					<profile>postgres</profile>
					<profile>sba</profile>
				</profiles>
				<outputDir>${project.basedir}/target/docker</outputDir>
				<imagePrefix>nexus.magiccode.net:8891/plugins/</imagePrefix>
			</configuration>
		</plugin>
      </build>
      ...
    </project>
```	
    
[^runnable]:
	A module is considered runnable when it either has a Java class with a _main_ method or 
	one with a _@SpringBootApplication_ annotation. In some cases this can be the case for modules
	that are not intended to have a Docker Compose file generated, for instance for debugging
	purposes. These modules (the name of their sub-directories inside the project) can be listed 
	here for explicit exclusion.

## Single Module Projects

The usage in a single-module project is quite straightforward. The plugin parses the properties and yml files for the given profiles (as well as the default application.properties/application.yml) for lines preceded by a comment containing the tag _DockerInclude_.
These will be formatted and added to the _environment_ section for the Docker service.

The name of the service will be the name of the project. The name of the image is formed like this:

   ```<imagePrefix>${project.name}:${project.version}```
    
where the imagePrefix is taken from the plugin configuration and ${project.name} as well as ${project.version} from the pom.xml file of the project.
 
An example of a single-module Docker Compose file could look like this:
```yaml
	name: demo-application
	services:
	  demo-application:
	    image: nexus.magiccode.net:8891/demo/demo-application:2.9.6-SNAPSHOT
	    environment:
	      - SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
	      - SPRING_MANAGEMENT_DATASOURCE_NAME=demo
	      - SPRING_MANAGEMENT_DATASOURCE_PASSWORD=demoapp
	      - SPRING_MANAGEMENT_DATASOURCE_PLATFORM=postgresql
	      - SPRING_MANAGEMENT_DATASOURCE_URL=jdbc:postgresql://localhost:5432/demoapp
	      - SPRING_MANAGEMENT_DATASOURCE_USERNAME=demoapp
	      - SERVER_ADDRESS=0.0.0.0
	      - SERVER_PORT=9080
	      - SPRING_CLOUD_CONSUL_CONFIG_ENABLED=false
	      - SPRING_PROFILES_ACTIVE=demo,postgres,sba
	      - UPLOAD_FILES_DIRECTORY=~/import
	    volumes:
	      - ../ssl:/opt/ssl
	      - ./data:/var/data
	    ports:
	      - "9080:9080"

```

## Multi-Module Projects

In multi-module projects, the plugin will iterate over all module sub-directories and collect the necessary information
from the properties/yml files for the profiles specified using the directories from the plugin configuration.

At present, this means that for the Docker Compose file to be created, all runnable[^runnable] sub-modules need to use
the same profiles. Differing profile configurations can be configured in the pom.xml module configuration of the sub-module.
This will also create a Docker Compose file within this submodule.

Just like in the single-module case, the name of the image, in this case for the sub-module, is derived from the name of the 
sub-module, i.e., the name of the sub-directory of the parent project. Also, the project version is taken from the pom.xml of 
the parent project!

   ```<imagePrefix><module-name>:${project.version}```

*Note:* It is recommended to put the _outputDir_ into the target directory, so that possibly existing files in your source
		  branch will not be overwritten.
		  
The plugin seeks to collect all common properties[^common] and list them in a common anchor in the Docker Compose file.

This anchor is then used in all of the service entries to make these common variables available to them. This implies a 
slightly different format for the environment variables than in the single-module project.

```yaml
name: demo-system
x-demo-system-common:
    &demo-system-common
    environment:
      &demo-system-env
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: org.postgresql.Driver
      SPRING_DATASOURCE_NAME: demo-application
      SPRING_DATASOURCE_PASSWORD: demoapp
      SPRING_DATASOURCE_PLATFORM: postgresql
      SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/demoapp
      SPRING_DATASOURCE_USERNAME: demoapp
      SPRING_PROFILES_ACTIVE: demo,postgres,sba
services:
  demo-backend:
    <<: *demo-system-common
    image: nexus.magiccode.net:8891/demo/demo-backend:2.9.6-SNAPSHOT
    environment:
      <<: *demo-system-env
      LOGGING_LEVEL_NET_MAGICCODE: DEBUG
      SERVER_PORT: 8080
    ports:
      - "8080:8080"
  demo-frontend:
    <<: *demo-system-common
    image: nexus.magiccode.net:8891/demo/demo-frontend:2.9.6-SNAPSHOT
    environment:
      <<: *demo-system-env
      SERVER_ADDRESS: 0.0.0.0
      SERVER_PORT: 9080
      SPRING_CLOUD_CONSUL_CONFIG_ENABLED: false
      UPLOAD_FILES_DIRECTORY: ~/import
    ports:
      - "9080:9080"
```

[^common]:
	Common properties are those which:
	- have the same key AND value in ALL sub-modules they appear in	
	- appear in properties files of at least two sub-modules

## Volume Support

The plugin supports volume mappings between host and container paths. Volumes can be configured in the plugin configuration and will be automatically included in the generated Docker Compose files.

### Volume Configuration

Volumes are configured using the `<volumes>` section in the plugin configuration. **Important**: Use nested elements, not XML attributes.

**✅ CORRECT Format:**
```xml
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
```

**❌ INCORRECT Format:**
```xml
<volumes>
  <volume external="../ssl" internal="/opt/ssl" />
  <volume external="./data" internal="/var/data" />
</volumes>
```

> **Note**: The attribute format does not work because Maven plugin parameter binding doesn't automatically map XML attributes to Java object fields. Always use the nested element format shown above.

### Configuration Per Module

The plugin supports both global and per-module volume configuration:

1. **Global Configuration** - Defined in the parent pom.xml, applies to all modules
2. **Module-Specific Configuration** - Defined in individual module pom.xml files
3. **Additive Behaviour** - Module volumes are added to parent volumes (if any)

This allows for flexible volume management where common volumes (like SSL certificates) can be defined globally, whilst specific modules can define their own additional volumes.

### Volume Optimisation in Multi-Module Projects

For multi-module projects, the plugin performs intelligent volume optimisation:

1. **Common Volume Detection** - Volumes that appear in multiple modules are identified
2. **YAML Anchor Generation** - Common volumes are extracted to a shared anchor section
3. **Service-Specific Handling** - Services with additional volumes list all their volumes directly
4. **Mixed Scenarios** - Services using only common volumes inherit via the anchor, whilst others define their complete volume set

### Single Module Projects

For single module projects, volumes are added directly to the service definition:

```yaml
name: demo-application
services:
  demo-application:
    image: nexus.magiccode.net:8891/demo/demo-application:2.9.6-SNAPSHOT
    environment:
      - SERVER_PORT=8080
    volumes:
      - ../ssl:/opt/ssl
      - ./data:/var/data
    ports:
      - "8080:8080"
```

### Multi-Module Projects with Volume Optimisation

Example multi-module Docker Compose with volume optimisation:

```yaml
name: demo-system
x-demo-system-common:
    &demo-system-common
    environment:
      &demo-system-env
      SPRING_PROFILES_ACTIVE: demo,postgres
    volumes:
      &demo-system-volumes
      - ../ssl:/opt/ssl
services:
  demo-backend:
    <<: *demo-system-common
    image: nexus.magiccode.net:8891/demo/demo-backend:2.9.6-SNAPSHOT
    environment:
      <<: *demo-system-env
      SERVER_PORT: 8080
    volumes:
      - ../ssl:/opt/ssl
      - ./backend-logs:/var/log
    ports:
      - "8080:8080"
  demo-frontend:
    <<: *demo-system-common
    image: nexus.magiccode.net:8891/demo/demo-frontend:2.9.6-SNAPSHOT
    environment:
      <<: *demo-system-env
      SERVER_PORT: 9080
    ports:
      - "9080:9080"
```

In this example:
- `../ssl:/opt/ssl` is a common volume shared by both services
- `demo-backend` has an additional specific volume `./backend-logs:/var/log`, so it lists all volumes directly
- `demo-frontend` only uses common volumes, so it inherits them via the merge anchor

### Volume Path Types

The plugin supports various volume path formats:

- **Relative paths**: `../ssl`, `./data`, `logs`
- **Absolute paths**: `/host/path`  
- **Named volumes**: `my-volume`

Both external (host) and internal (container) paths support these formats and can include spaces, hyphens, and underscores.

### Troubleshooting Volume Configuration

If volumes are not appearing in your generated Docker Compose files, check the following:

1. **XML Format** - Ensure you're using nested elements (`<external>` and `<internal>`) rather than XML attributes
2. **Plugin Execution** - Run with debug logging: `mvn clean compile -X` to see volume processing logs
3. **Path Validation** - Verify that both external and internal paths are specified and not null
4. **Plugin Version** - Ensure you're using a recent version that includes volume support

Look for log messages such as:
- `Processing X configured volume(s) for module: module-name`
- `✓ Added volume mapping: external -> internal`
- `✗ Skipping incomplete volume configuration` (indicates missing paths)
	

## Build Profiles

If a build profile is used (mvn -P \<profile\>) and this profile has the spring-dockerator-plugin configured,
the Docker Compose files generated will have the profile name added in the pattern docker-compose-<profile>.yml.

This allows different configurations and profiles to be used for certain environments like user acceptance testing etc.

This means building with a profile _uat_ would lead to _docker-compose-uat.yml_.




