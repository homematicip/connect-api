/**
 * Copyright 2014-2025 eQ-3 AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.example.plugin.handler;

import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.plugin.PluginStarter;
import org.example.plugin.function.ControlServiceFunction;
import org.example.plugin.model.Bridge;
import org.example.plugin.util.PersistenceHelper;
import org.example.plugin.ws.PluginWebsocketClient;

import de.eq3.plugin.domain.error.Error;
import de.eq3.plugin.domain.control.ControlRequest;
import de.eq3.plugin.domain.control.ControlResponse;
import de.eq3.plugin.domain.device.Device;
import de.eq3.plugin.domain.features.IFeature;
import de.eq3.plugin.serialization.PluginMessage;
import de.eq3.plugin.serialization.PluginMessageType;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class ControlRequestHandler extends AbstractVerticle implements Handler<Message<JsonObject>> {

	public static final String CONTROL_REQUEST_FAILED = "CONTROL_REQUEST_FAILED";

	private final Logger logger = LogManager.getLogger(this.getClass());

	private WebClient webClient;

	@Override
	public void start() {
		vertx.eventBus().consumer(ControlRequest.class.getName(), this);
		WebClientOptions clientOptions = new WebClientOptions();
		this.webClient = WebClient.create(this.vertx, clientOptions);
		logger.info("SYSTEM: {} Verticle or Worker started", this.getClass().getSimpleName());
	}

	@Override
	public void handle(Message<JsonObject> message) {
		if (message == null || message.body() == null) {
			return;
		}
		PluginMessage<ControlRequest> request = message.body().mapTo(PluginMessage.class);

		Optional<Bridge> optionalBridge = PersistenceHelper.getInstance().getBridge();

		if (optionalBridge.isEmpty()) {
			sendControlRequestResponse(request, false,
					new Error(CONTROL_REQUEST_FAILED, "No configured Bridge available (1)"));
			return;
		}
		Bridge bridge = optionalBridge.get();

		this.logger.debug("Bridge {}: Got request to control mock devices service", bridge.getBridgeId());
		this.logger.trace("Bridge {}: Incoming control request data: {}", bridge.getBridgeId(), request);

		// Get control information from request
		Device device = bridge.getDevices().get(request.getBody().getDeviceId());
		Set<IFeature> features = request.getBody().getFeatures();

		if (device == null) {
			sendControlRequestResponse(request, false, new Error(CONTROL_REQUEST_FAILED, "Device not found"));
			return;
		}

		// Call a external API
		Future.succeededFuture(bridge)
				.compose(new ControlServiceFunction(this.webClient, device, features))
				.onComplete(asyncResult -> {

					if (asyncResult.succeeded()) {
						logger.info("Bridge {} - Successfully called api request", bridge.getBridgeId());

						sendControlRequestResponse(request, true, null);
					} else {
						Error error = new Error(CONTROL_REQUEST_FAILED, asyncResult.cause().toString());
						logger.info("Bridge {} - Error calling api request, cause {}", bridge.getBridgeId(),
								asyncResult.cause());

						sendControlRequestResponse(request, false, error);
					}
				});
	}

	private void sendControlRequestResponse(PluginMessage<ControlRequest> request, boolean success, Error error) {
		ControlResponse controlResponse = new ControlResponse(request.getBody().getDeviceId(), success, error);

		PluginMessage<ControlResponse> response = new PluginMessage<>(request.getId(), PluginStarter.PLUGIN_ID,
				PluginMessageType.CONTROL_RESPONSE, controlResponse);

		vertx.eventBus().send(PluginWebsocketClient.getWsHandlerId(), JsonObject.mapFrom(response).encode());
	}
}
