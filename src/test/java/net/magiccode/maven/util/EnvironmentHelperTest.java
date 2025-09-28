package net.magiccode.maven.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EnvironmentHelper}
 */
public class EnvironmentHelperTest {

    @Test
    @DisplayName("generateValueEntry: without env file and no spaces returns raw value")
    void generateValueEntryNoEnvFileNoSpaces() {
        String result = EnvironmentHelper.generateValueEntry(false, "KEY", "value");
        assertThat(result).isEqualTo("value");
    }

    @Test 
    @DisplayName("generateValueEntry: without env file and with spaces returns quoted value")
    void generateValueEntryNoEnvFileWithSpaces() {
        String result = EnvironmentHelper.generateValueEntry(false, "KEY", "some value");
        assertThat(result).isEqualTo("'some value'");
    }

    @Test
    @DisplayName("generateValueEntry: with env file and no service name returns placeholder")
    void generateValueEntryWithEnvFileNoServiceName() {
        String result = EnvironmentHelper.generateValueEntry(true, "DATABASE_URL", "jdbc:mysql://localhost");
        assertThat(result).isEqualTo("${DATABASE_URL}");
    }

    @Test
    @DisplayName("generateValueEntry: with env file and service name returns prefixed placeholder")
    void generateValueEntryWithEnvFileWithServiceName() {
        String result = EnvironmentHelper.generateValueEntry(true, "DATABASE_URL", "jdbc:mysql://localhost", "user-service");
        assertThat(result).isEqualTo("${USER-SERVICE_DATABASE_URL}");
    }

    @Test
    @DisplayName("generateValueEntry: with env file and null service name returns unprefixed placeholder")
    void generateValueEntryWithEnvFileNullServiceName() {
        String result = EnvironmentHelper.generateValueEntry(true, "KEY", "value", null);
        assertThat(result).isEqualTo("${KEY}");
    }

    @Test
    @DisplayName("generateValueEntry: with env file and empty service name returns unprefixed placeholder")  
    void generateValueEntryWithEnvFileEmptyServiceName() {
        String result = EnvironmentHelper.generateValueEntry(true, "KEY", "value", "");
        assertThat(result).isEqualTo("${KEY}");
    }

    @Test
    @DisplayName("generateValueEntry: with env file and service name with spaces/hyphens converted to uppercase")
    void generateValueEntryWithEnvFileServiceNameFormatted() {
        String result = EnvironmentHelper.generateValueEntry(true, "PORT", "8080", "my-service");
        assertThat(result).isEqualTo("${MY-SERVICE_PORT}");
    }

    @Test
    @DisplayName("generateNameEntry: without env file returns raw name")
    void generateNameEntryNoEnvFile() {
        String result = EnvironmentHelper.generateNameEntry(false, "DATABASE_URL", "service");
        assertThat(result).isEqualTo("DATABASE_URL");
    }

    @Test
    @DisplayName("generateNameEntry: with env file and service name returns prefixed placeholder")
    void generateNameEntryWithEnvFileWithServiceName() {
        String result = EnvironmentHelper.generateNameEntry(true, "DATABASE_URL", "user-service");
        assertThat(result).isEqualTo("${USER-SERVICE_DATABASE_URL}");
    }

    @Test
    @DisplayName("generateNameEntry: with env file and null service name returns unprefixed placeholder")
    void generateNameEntryWithEnvFileNullServiceName() {
        String result = EnvironmentHelper.generateNameEntry(true, "DATABASE_URL", null);
        assertThat(result).isEqualTo("${DATABASE_URL}");
    }

    @Test
    @DisplayName("generateNameEntry: with env file and empty service name returns unprefixed placeholder")
    void generateNameEntryWithEnvFileEmptyServiceName() {
        String result = EnvironmentHelper.generateNameEntry(true, "DATABASE_URL", "");
        assertThat(result).isEqualTo("${DATABASE_URL}");
    }
}