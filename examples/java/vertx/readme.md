# ConnectAPI Java Example

This directory contains a Java-based example implementation for the Homematic IP Connect API. The example demonstrates how to create a plugin that interacts with the Homematic IP system via WebSocket, enabling functionalities such as device inclusion, control, monitoring, and configuration.

## Prerequisites

- **Java Development Kit (JDK)**: Version 11 or higher.
- **Apache Maven**: For building and managing dependencies.
- **Homematic IP Home Control Unit (HCU)**:
- Developer mode must be activated.
- The Connect API WebSocket must be exposed. This is only required if you intend to run the plugin on your PC.
- **Authorization Token**: Obtain an authorization token for your plugin by following the steps in the Homematic IP Connect API documentation.

## How to Use

1. **Configure the Plugin**:
   - Update the `PluginStarter.java` file with your plugin's unique identifier and name:
    ```java
        public static final String PLUGIN_ID = "your.plugin.id";
        public static final String PLUGIN_NAME = "Your Plugin Name";
    ```

2. **Prepare for local development**:
    - If you are preparing the plugin for local development, ensure the following changes are made. These changes should be removed if creating a container:
        - Update the `application.properties` file located in the `resources` directory:
            - Set the IP address of your Homematic IP Home Control Unit (HCU):
                ```properties
                websocket.host=192.168.x.x
                ```
                Replace `192.168.x.x` with the actual IP address of your HCU.
            - Save the authorization token in the `websocket.token` property:
                ```properties
                websocket.token=your-authorization-token
                ```
                Replace `your-authorization-token` with the token obtained for your plugin.

3. **Build the Project**:
   Use Maven to build the project:
   ```bash
   mvn clean install
   ```

4. **Run the Plugin**:
   Execute the `PluginStarter` class:
   ```bash
   mvn exec:java -Dexec.mainClass="org.example.plugin.PluginStarter"
   ```
## Build the Container

1. **Update the Dockerfile**:
   Ensure the `LABEL` in the Dockerfile is updated with the correct metadata for your plugin. For example:
   ```dockerfile
   LABEL de.eq3.hmip.plugin.metadata="{\"pluginId\":\"org.example.plugin.java\", \
     \"version\":\"1.0.0\", \
     \"hcuMinVersion\":\"1.4.7\", \
     \"friendlyName\":{\"de\":\"Java-Beispiel\",\"en\":\"Java Example\"}, \
     \"changelog\": \"New Features\",\
     \"issuer\":\"Developer\", \
     \"description\":{\"de\":\"Meine Beschreibung\",\"en\":\"My description\"}, \
     \"scope\":\"LOCAL\", \
     \"logsEnabled\": true, \
     \"image\":null \
     }"
   ```

2. **Build and save the Docker Image**:
   Use the Maven Docker plugin to build and save the image:
   ```bash
   mvn docker:build docker:save
   ```

3. **Install the Plugin on the HCU**:
   - Open the HCUweb interface and navigate to the plugin page.
   - Upload the `.tar.gz` archive to install the plugin.

   **Note**: Developer mode must be enabled on the HCU to install custom plugins.


## Features

- **Device Inclusion**: Discover and include third-party devices into the Homematic IP system.
- **Device Control**: Handle control requests and send commands to devices.
- **System Monitoring**: Receive and process system events to stay updated on the Homematic IP system state.
- **Configuration Management**: Provide configuration options for users via the Homematic IP smartphone app.
- **Logging**: Integrated logging using Log4j2 for debugging and monitoring.

## Documentation

For detailed information about the Homematic IP Connect API, refer to the full documentation available at https://github.com/homematicip/connect-api

## License

This example is licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt).

## Maintainer

Developed and maintained by **eQ-3 AG**.\
Homematic IP is a trademark of **eQ-3 AG**.