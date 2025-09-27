# Enhanced INFO Level Logging

## Overview
The Spring Dockerator Plugin now includes comprehensive INFO level logging to provide better visibility into the plugin execution process and make troubleshooting easier.

## Enhanced Logging Features

### 🚀 Startup Banner
```
========================================
Starting Spring Dockerator Plugin v0.0.6-SNAPSHOT
========================================
Project: my-app (1.0.0-SNAPSHOT)
Base directory: /path/to/project
Output directory: /path/to/project/target/docker
Image prefix: nexus.company.com:8891/rcl/
Create .env file: true
```

### 📁 Project Detection
```
Single module project detected
// OR //
Multi-module project detected with 3 module(s)
```

### ⚙️ Profile Information
```
Active profiles: postgres, docker, ssl
// OR //
No active profiles found, using default configuration
Processing profile: 'docker'
```

### 📦 Volume Processing
```
Processing 2 configured volume(s) for module: my-app
✓ Added volume mapping: ../ssl -> /opt/ssl
✓ Added volume mapping: ./data -> /var/data
// OR //
No volume configuration found for module: my-app
```

### 🛠️ Service Configuration
```
Processing runnable module: my-app
Using default port 8080 for service: my-app
Service 'my-app' configured with 5 environment variable(s), 1 port(s), 2 volume(s)
```

### 🔗 Common Elements (Multi-module)
```
Found 3 common environment variable(s)
Found 1 common volume(s) across modules
Found common volume: ../ssl -> /opt/ssl (appears in 2 service(s))
```

### 📄 File Generation
```
Generating docker-compose.yml for 2 service(s)
Successfully generated docker-compose.yml
Generating database docker-compose file with 3 JDBC configuration(s)
Generating .env file with environment variables
Successfully generated environment file: /path/to/.env
```

### 🧹 Cleanup
```
Cleaning up target directory files
```

### ✅ Completion
```
========================================
Spring Dockerator Plugin execution completed successfully
========================================
```

## Benefits

- **Clear visibility** into plugin execution flow
- **Easy troubleshooting** with detailed progress information
- **Configuration validation** feedback
- **Volume processing transparency** - especially helpful for diagnosing volume configuration issues
- **Service generation summary** - shows how many services, ports, volumes, and environment variables were configured
- **File creation confirmations** - confirms successful generation of docker-compose.yml and .env files
- **Professional logging format** with consistent styling

## Debug Level Logging

For even more detailed output, use `-X` or `--debug`:

```bash
mvn net.magiccode.maven:spring-dockerator-plugin:generate-docker-compose -X
```

Debug level shows additional details:
- Individual file processing: `Reading properties from: application-docker.properties`
- Detailed volume information: `Volume 0: external='../ssl', internal='/opt/ssl'`
- Directory processing: `Processing properties directory: /path/to/resources`

## Error Handling

Enhanced error messages provide better context:
```
Error during plugin execution: Could not read properties file
✗ Skipping incomplete volume configuration: external='null', internal='/opt/ssl'
Properties directory not found: /path/to/src/main/resources
```

This logging enhancement makes the plugin much more user-friendly and easier to debug, especially for volume configuration issues that were previously hard to diagnose.