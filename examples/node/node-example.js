const WebSocket = require("ws");
const { v4: uuidv4 } = require("uuid");
const fs = require("fs").promises;

async function start(pluginId, host, authtokenFile) {
	const authtoken = await fs.readFile(authtokenFile, "utf8");
	
	const webSocket = new WebSocket("wss://" + host + ":9001", {
		rejectUnauthorized: false,
		headers: {
			"authtoken": authtoken,
			"plugin-id": pluginId
		}
	});

	function sendPluginReady(messageId) {
		const message = {
			id: messageId,
			pluginId: pluginId,
			type: "PLUGIN_STATE_RESPONSE",
			body: {
				pluginReadinessStatus: "READY"
			}
		};
		webSocket.send(JSON.stringify(message));
		console.log("Sent message:", JSON.stringify(message, null, 2));
	}

	webSocket.on("open", () => {
		console.log("Connected to WebSocket");

		// send PLUGIN_STATE_RESPONSE upon startup
		sendPluginReady(uuidv4());
	});

	webSocket.on("message", (data) => {
		const message = JSON.parse(data);
		console.log("Received message:", JSON.stringify(message, null, 2));

		// send PLUGIN_STATE_RESPONSE on receiving a PLUGIN_STATE_REQUEST
		if (message.type === "PLUGIN_STATE_REQUEST") {
			sendPluginReady(message.id);
		}
	});

	webSocket.on('error', (err) => {
		console.error('WebSocket error:', err.code, err.message || err);
	});
}

// parse command line parameters
const args = process.argv.slice(2);
const pluginId = args[0];
const host = args[1];
const authtokenFile = args[2];

// start the plugin
start(pluginId, host, authtokenFile);
