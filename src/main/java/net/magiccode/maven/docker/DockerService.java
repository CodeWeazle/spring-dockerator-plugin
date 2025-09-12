/**
 * Helper class for docker compose file service entries.
 * 
 * @author Volker Karlmeier
 * 
 */
package net.magiccode.maven.docker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import lombok.Builder;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import net.magiccode.maven.util.EnvironmentHelper;

/**
 * Container for the services created for each module. 
 */
@Data
@Builder
@Log4j2
public class DockerService {

	private String name;
	private String version;
	private String imagePrefix;
	
	@Builder.Default
	private Map<String, String> dockerEnvVars = new HashMap<>();
	
	@Builder.Default
	private Map<String, String> jdbcConfigs = new HashMap<>();

	@Builder.Default
	private Map<String, String> volumes = new HashMap<>();

	@Builder.Default
	private List<String> ports = new ArrayList<>();
	
	@Builder.Default
	private boolean createEnvironmentFile = false;
	/**
	 * wrapper method calling <code>generateServiceEntry(String commonName, String commonEnvironmentName)</code> with
	 * null values to indicate no common environment is provided and a service entry for a single module project is 
	 * being created.
	 * @return a String containing the yaml formatted service entry for the project.
	 */
	public String generateServiceEntry() {
		return generateServiceEntry(null,null);
	}
	
	/**
	 * creates a string in yaml format for the docker-compose file to be generated. If commonName and commonEnvironmentName 
	 * are specified (not empty nor null) a common property entry is generated, otherwise a single module entry.
	 * (Single modules entries start with '- ' and use '=' for a delimiter, common property entries do not start with '- ' 
	 * and delimit entries with ':'
	 *  
	 * @param commonName - a common name for all common settings in the docker compose file.
	 * @param commonEnvironmentName - a common environment name used for the anchor of the environment settings
	 * @return a string containing the entire service definition for the docker compose file.
	 */
	public String generateServiceEntry(String commonName, String commonEnvironmentName) {
	    StringBuilder serviceEntry = new StringBuilder();
	    serviceEntry.append(StringUtils.repeat(" ", 2))
	    			.append(name).append(":\n");
	    if (StringUtils.isNotBlank(commonName)) {
	    	serviceEntry.append(StringUtils.repeat(" ", 4))
	    				.append("<<: *")
	    				.append(commonName)
	    				.append("\n");
	    }
	    serviceEntry.append(StringUtils.repeat(" ", 4))
	    			.append("image: ")
	    			.append(imagePrefix)
	    			.append(name).append(":")
	    			.append(version)
	    			.append("\n");
	    serviceEntry.append(StringUtils.repeat(" ", 4))
	    			.append("environment:\n");
	    
	    if (StringUtils.isNotBlank(commonEnvironmentName)) {
	    	serviceEntry.append(StringUtils.repeat(" ", 6))
	    				.append("<<: *")
	    				.append(commonEnvironmentName)
	    				.append("\n");
		    dockerEnvVars
			.entrySet()	    			
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(entry -> serviceEntry.append(StringUtils.repeat(" ", 6))
										  .append(entry.getKey())
										  .append(": ")
										  .append(EnvironmentHelper.generateValueEntry(createEnvironmentFile, entry.getKey(), entry.getValue(), this.getName()))
										  .append("\n"));
	    } else {
		    dockerEnvVars
			.entrySet()	    			
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(entry -> serviceEntry.append(StringUtils.repeat(" ", 6))
										  .append("- ")
										  .append(entry.getKey())
										  .append("=")
										  .append(EnvironmentHelper.generateValueEntry(createEnvironmentFile, entry.getKey(), entry.getValue(), this.getName()))
										  .append("\n"));

	    }
	    
	    if (!ports.isEmpty()) {
	        serviceEntry.append(StringUtils.repeat(" ", 4))
	        			.append("ports:\n");
	        ports.stream()
	        	 .sorted()
	        	 .forEach(port -> serviceEntry.append(StringUtils.repeat(" ", 6))
	        								  .append("- \"")
	        								  .append(port).append(":").append(port)
	        								  .append("\"\n"));
	    }
	    log.info("Generated service entry for " + name);
	    return serviceEntry.toString();
	}
}
