# Philips Hue Example Plugin for Homematic IP Connect API

This directory contains a Java-based example plugin for the Homematic IP Connect API, demonstrating how to connect a Philips Hue Bridge to Homematic IP. Devices paired with the Hue Bridge will appear in the Homematic IP app after configuration.


## Prerequisites

- **Java 11+**
- **Apache Maven**
- **Docker or Podman** (for container build and deployment)
- **Philips Hue Bridge**
- **Homematic IP HCU** with developer mode enabled

## How to Use

1. **Configure the Plugin**
   - Edit `src/main/resources/hue-plugin.properties` if you need to set a specific network interface or websocket token.
   - The plugin will attempt to discover Hue bridges automatically.

2. **Prepare for Local Development**:
   - If you are preparing the plugin for local development, make the following changes (remove these for container builds):
     - Update the `hue-plugin.properties` file in the `resources` directory:
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
   ```powershell
   mvn clean install
   ```

4. **Run the Plugin Locally**:
   Execute the `HuePluginStarter` class:
   ```powershell
   mvn exec:java "-Dexec.mainClass=de.eq3.plugin.hue.HuePluginStarter"
   ```

5. **Build and Save the Docker Image**:
   
   > **Note:** The following command is designed for Unix-style shells (e.g., bash) due to the use of nested shell calls (`$(...)`).  
   > On Windows, you may need to adjust the command or use a Unix-like environment such as WSL (Windows Subsystem for Linux).

   ```
   mvn package docker:build docker:save -DcontainerMetadata="$(python3 PrepareMetadata.py 1.4.0)"
   ```
   - This creates a `.tar.gz` file in the `target` directory.

6. **Deploy the Plugin**
   - **Option 1: Upload via HCUweb**
     - Open the HCU web interface and navigate to the plugin page.
     - Upload the `.tar.gz` archive to install the plugin.
     - **Note**: Developer mode must be enabled on the HCU to install custom plugins.
   - **Option 2: Connect to Remote WebSocket Port**
     - If you are developing or testing, you can run the plugin locally or in a container and connect to the HCU's remote WebSocket port.
     - Ensure the WebSocket port is accessible and the correct token is set in `hue-plugin.properties`.
     - Example (run in container):
       ```powershell
       podman run -i -t eq3/hmip/plugin/hue-plugin:latest
       ```
     - The plugin will connect to the configured HCU WebSocket server

## Documentation

For detailed information about the Homematic IP Connect API, see the [full documentation](https://github.com/homematicip/connect-api) or the `connect-api-documentation-1.0.0.html` file in the repository.

## License

This example is licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt).

## Maintainer

Developed and maintained by **eQ-3 AG**.\
Homematic IP is a trademark of **eQ-3 AG**.