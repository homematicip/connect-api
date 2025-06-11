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

package de.eq3.plugin.hue.control;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.domain.features.IFeature;
import de.eq3.plugin.domain.status.StatusEvent;
import de.eq3.plugin.hue.HueHttpClientConfiguration;
import de.eq3.plugin.hue.HuePluginStarter;
import de.eq3.plugin.hue.auth.HueLookupRequestHandler;
import de.eq3.plugin.hue.auth.model.HueBridge;
import de.eq3.plugin.hue.control.messages.HueLightStateRequest;
import de.eq3.plugin.hue.discovery.mapping.FeatureConverter;
import de.eq3.plugin.hue.model.light.Light;
import de.eq3.plugin.hue.util.HuePersistenceHelper;
import de.eq3.plugin.hue.ws.HuePluginWebsocketClient;
import de.eq3.plugin.serialization.PluginMessage;
import de.eq3.plugin.serialization.PluginMessageType;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class HueLightStateRequestHandler extends AbstractVerticle implements HueHttpClientConfiguration {
	private final Logger logger = LogManager.getLogger(this.getClass());
	private static final String ENDPOINT = "/clip/v2/resource/light/";
	private WebClient webClient;

	@Override
	public void start() {
		WebClientOptions clientOptions = new WebClientOptions(getHttpClientOptions().setIdleTimeout(5));
		this.webClient = WebClient.create(this.vertx, clientOptions);
		vertx.eventBus().consumer(HueLightStateRequest.ENDPOINT, this::handleLightStateRequest);
	}

	private void handleLightStateRequest(Message<JsonObject> event) {
		if (event == null || event.body() == null) {
			return;
		}
		HueLightStateRequest request = event.body().mapTo(HueLightStateRequest.class);

		Optional<HueBridge> optionalHueBridge = HuePersistenceHelper.getInstance().getHueBridge();
		if (optionalHueBridge.isEmpty()) {
			return;
		}
		HueBridge hueBridge = optionalHueBridge.get();
		String host = hueBridge.getLocalAddress();
		String route = ENDPOINT + request.getLightId();
		HueLookupRequestHandler.getHueBridgeIp(hueBridge, host, vertx).onSuccess(ip -> {
			this.webClient.get(host, route)
					.putHeader("hue-application-key", hueBridge.getApplicationKey())
					.send(result -> {
						if (result.succeeded()) {
							try {
								JsonArray data = result.result().bodyAsJsonObject().getJsonArray("data");
								data.forEach(resource -> {
									if (resource instanceof JsonObject) {
										Light light = ((JsonObject) resource).mapTo(Light.class);
										sendLightStatusEvent(light);
									}
								});
							} catch (DecodeException e) {
								logger.error("Failed to decode message {} {}", result.result().bodyAsString(),
										e.getMessage());
							}
						}
					});
		});

	}

	private void sendLightStatusEvent(Light light) {
		Set<IFeature> features = FeatureConverter.getInstance().doBackward(light);
		logger.debug("Sending current light status: {}", features);
		StatusEvent statusEvent = new StatusEvent(light.getOwner().getRid(), features);
		PluginMessage<StatusEvent> message = new PluginMessage<>(UUID.randomUUID().toString(),
				HuePluginStarter.PLUGIN_ID, PluginMessageType.STATUS_EVENT, statusEvent);

		vertx.eventBus().send(HuePluginWebsocketClient.getWsHandlerId(), JsonObject.mapFrom(message).encode());
	}
}
