# spring-dockerator-plugin

The purpose of this plugin is to generate docker-compose files during a builld with maven.
The generated docker-compose<...>.yml file is generated based on the (spring) profiles defined in the plugin configuration.
To expose properties as environment variables to the docker-compose file, these can be annotated by a preceding comment[^comment] 
containing the tag _DockerInclude_ 

The default plugin life-cycle phase is _prepare-package_. This makes sure all code has been compiled and tested but not yet 
packaged, so that a cleanup procedure can remove all annotations from the properties files before they end up in the created
archive. Be ware that *only packaged properties/yml files are being cleaned*. This does not happen for the _config_ directory
for instance, since this is not copied into the _target_ directory and thus included in the archive (by default).

It might be important to mention that this plugin only create the docker-compose file from information taken from the project,
it does NOT create a Dockerfile or even a docker image. This remains the responsibility of the developer.


[^comment]:
	As a comment counts a line of which the first non-space charachter is '#'.
  

## Requirements

This maven-plugin requires Java Version 17 or newer, Maven 3.3 or newer.

## Goals

By the time being, this plugin offers just one goal, which is ```spring-dockerator:generate-docker-compose```.

## Usage

To add this plugin to your project, just follow the usual pattern for maven plugins.

```
    <project>
      ...
      <build>
	      <plugins>
	        <plugin>
			  <groupId>net.magiccode.maven</groupId>
			  <artifactId>spring-dockerator-plugin</artifactId>
			  <version>0.0.2</version>
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
```
    <project>
      ...
      <build>
		<plugin>
			<groupId>net.magiccode.maven</groupId>
			<artifactId>spring-dockerator-plugin</artifactId>
			<version>0.0.2</version>
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

## Configuration

Without any configuration, the plugin will not do very much. 

| Option         | Desription                       | example                                                |
| ------         | -----------------                | ------------------------                               |
| outputDir      | The directory the docker-compose files will be created in. Defaults to __${project.basedir}__, but should be changed to not overwrite your files if you already have this directory.| <outputDir>${project.basedir}/target/docker</outputDir>|
| imagePrefix    | A prefix to be added in front of the generated image name.| \<imagePrefix\>nexus.magiccode.net:8891/plugins/\</imagePrefix\>|
| profiles       | List of profile names which the profiles (ie. properties/yaml files) parsed for generation |\<profiles\><br/>&nbsp;&nbsp;\<profile\>demo\</profile\><br/>&nbsp;&nbsp;<profile\>postgres\</profile\><br/>\</profiles\>|
| propertiesDirs | List of directories to scan for the properies/yml files.|\<propertiesDirs\><br/>&nbsp;&nbsp;\<propertiesDir\>src/main/resources\</propertiesDir\><br/>&nbsp;&nbsp;\<propertiesDir\>config\</propertiesDir\><br/>\</propertiesDirs\> |
| skipModules    | The code only recognises runnable modules in multi-module projects[^runnable]. To explicitely exclude modules, list them here.|\<skipModules\><br/>&nbsp;&nbsp;\<skipModule\>demo-core\</skipModule\><br/>&nbsp;&nbsp;\<skipModule\>demo-common\</skipModule\><br/>\</skipModules\>|


### Example
```	
    <project>
      ...
      <build>
		<plugin>
			<groupId>net.magiccode.maven</groupId>
			<artifactId>spring-dockerator-plugin</artifactId>
			<version>0.0.2</version>
			<executions>
				<execution>
					<!-- <phase>package</phase> --><!-- needed no longer -->
					<goals>
						<goal>generate-docker-compose</goal>
					</goals>
				</execution>
			</executions>
			<configuration>
				<skipModules>
					<skipModule>demo-core</skipModule>
					<skipModule>demo-common</skipModule>
				</skipModules>
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
	A module is considered as runnable when it either has a Java class with a _main_ method or 
	one with a _@SpringBootApplication_ annotation. In some cases this can be the case for modules
	that are not considered to have a docker-compose file generated, for instance for debugging
	purposes. These modules (the name of their sub-directories inside the project) can be listed 
	here for an explicit exclusion.
	 
## Single module projects

The usage in a single-module project is quite straight forward. The plugin parses the properties and yml files for the given profiles (as well as the default application.properties/application.yml) for lines preceeded by a comment containing teh tag _DockerInclude_.
These will be formatted and added to the _environment_ section for the docker service. 

The name of the service will be the name of the project. The name of the image is formed like this

   ```<imagePrefix>${project.name}:${project.version}```
    
where the imagePrefix is taken from the plugin configuration and ${project.name} as well as ${project.version} from the pom.xml file of the project. 
 
An example for a single-module docker-compose file could look like this:
```
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
	    ports:
	      - "9080:9080"

```

## Multi module projects

In multi-module projects, the plugin will iterate over all module sub-directories and collect the necessary information
from the properties/yml files for the profiles specified using the directories from the plugin configuration.

For the time being this means, for the docker-compose file to be created all runnable[^runnable] sub-modules need to use
the same profiles. Differing profile configurations can be configured in the pom.xml module configuration of the sub-module.
This will also create a docker-compose file within this submodule.

Just like in the single-module case, the name of the image, in this case for the sub-module, is derived from the name of the 
sub-module, i.e. the name of the sub-directory of the parent project. Also, the project-version is taken from the pom.xml of 
the parent project!

   ```<imagePrefix><module-name>:${project.version}```

*Remark:* It is recommended to put the _outputDir_ into the target directory, so that possibly existing files in your source
		  branch will not be overwritten.
		  

The plugin seeks to collect all common properties[^common] and list them in a common anchor in the docker-compose file.

This anchor is then used in all of the service entries to make these common variables available to them. This implies a 
slightly different format for the environment variables than in the single-module project. 

 
```
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
	Common properties are those, which
	- have the same key AND value in ALL sub-modules they appear in	
	- appear in properties files of at least two sub-modules
	

## Build-profiles

If a build profile is used (mvn -P \<profile\>) and this profile has the spring-dockerator-plugin configured,
the docker compose files generated will have the profile name added as in the pattern docker-compose-<profile>.yml

This allows to use different configurations and profiles for certain environments like user acceptance testing etc.

This means, building with a profile _uat_ would lead to _docker-compose-uat.yml_.




