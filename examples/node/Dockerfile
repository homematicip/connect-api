# Use an ARM64-compatible base image
FROM --platform=linux/arm64 node:18-alpine

# Set the working directory inside the container
WORKDIR /app

# Copy package.json and package-lock.json
COPY package*.json .

# Install the required npm packages
RUN npm install

# Copy the executable
COPY node-example.js .

# Set the entrypoint to run the executable
ENTRYPOINT ["node", "node-example.js", "de.doe.jane.plugin.example.node", "host.containers.internal", "/TOKEN"]

# Set the plugin metadata label
LABEL de.eq3.hmip.plugin.metadata=\
'{\
	"pluginId": "de.doe.jane.plugin.example.node",\
	"issuer": "Jane Doe",\
	"version": "1.0.0",\
	"hcuMinVersion": "1.3.7",\
	"scope": "LOCAL",\
	"friendlyName": {\
		"en": "Node.js Example",\
		"de": "Node.js-Beispiel"\
	},\
    "description": {\
        "en": "This plugin was built with Node.js.",\
        "de": "Dieses Plugin wurde mit Node.js erstellt."\
    },\
	"logsEnabled": true\
}'