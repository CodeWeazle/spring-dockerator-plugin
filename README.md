# spring-dockerator-plugin

The purpose of this plugin is to generate docker-compose files during a builld with maven.
The generated docker-compose<...>.yml file is generated based on the (spring) profiles defined in the plugin configuration.

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
			  <version>0.0.2-SNAPSHOT</version>
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
			<version>0.0.2-SNAPSHOT</version>
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
| outputDir      | <p> The directory the docker-compose files will be created in. Defaults to __${project.basedir}__, but should be changed to not overwrite your files if you already have this directory.| <outputDir>${project.basedir}/target/docker</outputDir>|
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
			<version>0.0.2-SNAPSHOT</version>
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
	 
	
	