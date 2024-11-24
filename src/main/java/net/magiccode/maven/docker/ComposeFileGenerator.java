/**
 * Helper class for generating docker-compose.yml files.
 * 
 * @author Volker Karlmeier
 *  
 */
package net.magiccode.maven.docker;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import lombok.Builder;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Builder
public class ComposeFileGenerator {

	/**
	 * List of DockerServcie instances to be included in this docker compose file.
	 * In multi-module projects each runnable project creates one service entry.,
	 * A module is identified as runnable if it contains a class with a main method 
	 * or one with a @SpringBootApplication annotation. 
	 */
	private List<DockerService> services;
	
	/** 
	 * The output directory for the generated docker compose file
	 */
	private String outputDir; 
	
	/**
	 * The name of the module the file is being created for. In multi-module projects this
	 * is the name of parent project. ${project.name}
	 */
	private String moduleName;
	
	/**
	 * A map containing properties which are common to at least 2 runnable modules. For being a
	 * common property, it needs to be 
	 * - either missing or equal in key AND value in all modules/services
	 * - occur in more than one module/service
	 */
	private Map<String,String> commonEnvironment; 
	
	/**
	 * the active maven profile. This can have different profile out output settings or whatever,
	 * so if this is neither null nor empty, we add a suffix to the generated filename.
	 */
	private String activeProfile;
	
	/**
	 * Creates a docker-compose file 
	 * @throws IOException
	 */
	public void generateDockerCompose() throws IOException {
		
		Path dockerComposeFile = StringUtils.isBlank(activeProfile)  
						? Paths.get(outputDir, "docker-compose.yml")
						: Paths.get(outputDir, "docker-compose-"+activeProfile+".yml");
		
	 	try (BufferedWriter writer = Files.newBufferedWriter(dockerComposeFile)) {
	 		
	 		writer.write(generateCommentSection(activeProfile, moduleName)); 		
	 		writer.write("name: " + moduleName+ "\n");
	 		boolean containsCommonEnvironment = (commonEnvironment!=null && ! commonEnvironment.isEmpty());
	 		String commonEnvironmentName = null, commonName=null;
	 		if (containsCommonEnvironment) {
	 			commonName = moduleName+"-common";
	 			commonEnvironmentName = moduleName+"-env";
	 			StringBuffer commonBuffer = new StringBuffer();
	 			commonBuffer.append("x-").append(moduleName).append("-common").append(":\n");
	 			commonBuffer.append(StringUtils.repeat(" ", 4))
	 						.append("&").append(moduleName).append("-common\n");
	 			
	 			commonBuffer.append(StringUtils.repeat(" ", 4)).append("environment:\n");

	 			commonBuffer.append(StringUtils.repeat(" ", 6)).append("&")
	 														   .append(commonEnvironmentName)
	 														   .append("\n");;
	 														   	 			
	 			commonEnvironment.entrySet()	    			
					    		 .stream()
						 		 .sorted(Map.Entry.comparingByKey())
					    		 .forEach(entry -> commonBuffer.append(StringUtils.repeat(" ", 6))
					    							  		   .append(entry.getKey())
					    									   .append(": ")
					    									   .append(entry.getValue().contains(" ")
					    											   ? "'"+entry.getValue()+"'"					    											   
					    											   :entry.getValue())
					    									   .append("\n"));
	 			
	 			writer.write(commonBuffer.toString());
	 		}
	 		
			writer.write("services:\n");
			for (DockerService service : services) {
				 writer.write(service.generateServiceEntry(commonName, commonEnvironmentName));
			}
		}
		log.info("Generated Docker Compose file: " + dockerComposeFile.toString());
	}
	
	/**
	 * generates a docker compose file for a single module
	 * 
	 * @param serviceName
	 * @param outputDir
	 * @param envVars
	 * @param ports
	 * @throws IOException
	 */
	public void generateModuleDockerCompose() throws IOException {

		Path moduleComposeFile = StringUtils.isBlank(activeProfile)  
				? Paths.get(outputDir, "docker-compose-" + moduleName + ".yml")
				: Paths.get(outputDir, "docker-compose-" + moduleName + "-" + activeProfile + ".yml");
		
	    try (BufferedWriter writer = Files.newBufferedWriter(moduleComposeFile)) {
	    	writer.write("name: " + moduleName+ "\n");
	    	writer.write(generateCommentSection(activeProfile, moduleName));
	        writer.write("services:\n");
	        writer.write(services.stream().filter(service->service.getName().equals(moduleName))
	        		                      .findFirst()
	        		                      .get()
	        		                      .generateServiceEntry());
	    }
	    log.info("Generated module-specific Docker Compose file: " + moduleComposeFile.toString());
	}
	
	
	/**
	 * generate comment section for docker compose file
	 * @param activeProfile - the profile the file is generated for
	 * @param moduleName - the name of the module or system(name of topmost module in multi-module projects)
	 * @return A string containing the comment section 
	 */
	private String generateCommentSection(String activeProfile, String moduleName) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy @ HH:mm:ss");
		StringBuffer comment = new StringBuffer();
		 comment.append(StringUtils.repeat("#", 60)).append("\n")
									.append("# ").append("\n")
							 		.append("# ").append("docker-compose file for "+moduleName)
							 					 .append(StringUtils.isNotBlank(activeProfile)
							 							 ? ", generated for profile "+activeProfile
							 							 : "")
							 					 .append("\n")
							 		.append("# ").append("\n")
							 		.append("# ").append("generated on ")
							 					 .append(formatter.format(LocalDateTime.now()))
							 					 .append(" using spring-dockerator-plugin.").append("\n")
							 		.append("# ").append("\n")
									.append(StringUtils.repeat("#", 60)).append("\n").append("\n");
		return comment.toString();
	}
	
}
