# Node.js Example for Homematic IP Connect API

This directory contains a Node.js example plugin for the Homematic IP Connect API. The example demonstrates how to establish a WebSocket connection with the Homematic IP Home Control Unit (HCU) and interact with it by sending and receiving messages.

## Features

- Sends a `PLUGIN_STATE_RESPONSE` upon startup and in response to `PLUGIN_STATE_REQUEST` messages from the HCU.
- Provides a basic structure for developing your own Node.js plugins for the Homematic IP ecosystem.

## Prerequisites

- **Node.js**: Ensure you have Node.js installed. You can download it from [nodejs.org](https://nodejs.org/).
- **Homematic IP HCU**: Access to a Homematic IP Home Control Unit with developer mode enabled.
- **Authorization Token**: Obtain the authorization token for your plugin from the HCU.

## How to Use

1. **Prepare the Authorization Token**:
   - Obtain the authorization token for your plugin identifier.
   - Save the token in a file named `authtoken.txt` in the working directory.

2. **Run the Plugin**:
   - Use the following command to run the plugin:
     ```bash
     node node-example.js <plugin-id> <hcu-address> <authtoken-file>
     ```
     Replace the placeholders with:
     - `<plugin-id>`: The unique identifier for your plugin (e.g., `de.doe.jane.plugin.example.node`).
     - `<hcu-address>`: The address of your Homematic IP HCU (e.g., `hcu1-XXXX.local`).
     - `<authtoken-file>`: The path to the file containing the authorization token (e.g., `authtoken.txt`).

   Example:
     ```bash
     node node-example.js de.doe.jane.plugin.example.node hcu1-5678.local authtoken.txt
     ```

## How to Build and Deploy

- **Build the Docker Image**:
  Use the provided Dockerfile to build a container image for the plugin:
  ```bash
  docker build -t de/doe/jane/plugin/example/node:1.0.0 .
  ```

- **Export the Docker Image**:
  Save the image to a `.tar.gz` archive:
  ```bash
  docker save de/doe/jane/plugin/example/node:1.0.0 | gzip > node-example-1.0.0.tar.gz
  ```

- **Install the Plugin on the HCU**:
  Open the HCU web interface and navigate to the plugin page. Upload the `.tar.gz` archive to install the plugin.

  **Note**: Developer mode must be enabled on the HCU to install custom plugins.

## Documentation

For detailed information about the Homematic IP Connect API, refer to the full documentation available in the root of the repository.

## License

This example is licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt).

## Maintainer
Developed and maintained by **eQ-3 AG**.\
Homematic IP is a trademark of **eQ-3 AG**.