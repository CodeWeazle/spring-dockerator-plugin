/**
 * Helper class for module handling
 * 
 * @author Volker Karlmeier
 * 
 */
package net.magiccode.maven.util;

import org.apache.commons.lang3.StringUtils;

/**
 * Helper class for module handling
 */
public class EnvironmentHelper {

	/**
	 * generates a single entry for the .env file or the docker-compose file
	 * depending on useEnvFile flag
	 * 
	 * @param useEnvFile
	 * @param name
	 * @param value
	 * @param serviceName the name of the service for which the entry is generated.
	 *                    Used as a prefix if useEnvFile is true.
	 * @return the generated entry
	 */
	public static String generateValueEntry(boolean useEnvFile, String name, String value, String serviceName) {
		if (useEnvFile) {
			String prefix = StringUtils.isNotBlank(serviceName) ? serviceName.toUpperCase() + "_" : "";
			return "${" + prefix + name + "}";
		} else {
			return value.contains(" ") ? "'" + value + "'" : value;
		}
	}

	/**
	 * Wrapper method calling
	 * <code>generateValueEntry(boolean useEnvFile, String name, String value, String serviceName)</code>
	 * with a null serviceName to indicate no prefix is needed.
	 * 
	 * @param useEnvFile
	 * @param name
	 * @param value
	 * @return the generated entry
	 */
	public static String generateValueEntry(boolean useEnvFile, String name, String value) {
		return generateValueEntry(useEnvFile, name, value, null);
	}

	/**
	 * generates a single entry for the .env file or the docker-compose file
	 * depending on useEnvFile flag
	 * 
	 * @param useEnvFile
	 * @param name
	 * @param serviceName the name of the service for which the entry is generated.
	 *                    Used as a prefix if useEnvFile is true.
	 * @return the generated entry
	 */
	public static String generateNameEntry(boolean useEnvFile, 
										   String name, 
										   String serviceName) {
		if (useEnvFile) {
			String prefix = StringUtils.isNotBlank(serviceName) ? serviceName.toUpperCase()+"_" : "";
			return "${"+prefix+name+"}";
		} else {
			return name;
		}
	}

}
