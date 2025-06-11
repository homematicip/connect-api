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

package de.eq3.plugin.hue.configuration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.domain.config.ConfigUpdateRequest;
import de.eq3.plugin.domain.config.ConfigUpdateResponse;
import de.eq3.plugin.domain.config.ConfigUpdateResponseStatus;
import de.eq3.plugin.domain.plugin.PluginReadinessStatus;
import de.eq3.plugin.hue.HuePluginStarter;
import de.eq3.plugin.hue.auth.model.HuePairingRequest;
import de.eq3.plugin.hue.auth.model.HuePairingStatus;
import de.eq3.plugin.hue.util.TranslationIdentifier;
import de.eq3.plugin.hue.util.Translations;
import de.eq3.plugin.hue.ws.HuePluginWebsocketClient;
import de.eq3.plugin.serialization.PluginMessage;
import de.eq3.plugin.serialization.PluginMessageType;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

public class HueConfigUpdateRequestHandler extends AbstractVerticle implements Handler<Message<JsonObject>> {
	private final Logger logger = LogManager.getLogger(this.getClass());

	@Override
	public void start() throws Exception {
		vertx.eventBus().consumer(ConfigUpdateRequest.class.getName(), this);

		logger.info("SYSTEM: {} Verticle or Worker started", this.getClass().getSimpleName());
	}

	@Override
	public void handle(Message<JsonObject> event) {
		if (event == null || event.body() == null) {
			return;
		}

		PluginMessage<ConfigUpdateRequest> configUpdateRequest = event.body().mapTo(PluginMessage.class);
		String languageCode = configUpdateRequest.getBody().getLanguageCode();
		String bridgeAddress = null;
		try {
			bridgeAddress = (String) configUpdateRequest.getBody().getProperties().get("bridge_address");
		} catch (Exception e) {
			logger.error("Could not read incoming property", e);
		}
		if (bridgeAddress == null) {
			sendFeedback(configUpdateRequest, ConfigUpdateResponseStatus.FAILED,
			Translations.get(languageCode, TranslationIdentifier.CONFIG_INVALID_VALUES));
			return;
		}

		HuePairingRequest paringRequest = new HuePairingRequest();
		paringRequest.setLocalAddress(bridgeAddress);
		paringRequest.setLanguageCode(languageCode);

		MessageConsumer<JsonObject> huePairStatusEvent = vertx.eventBus().consumer(HuePairingStatus.ENDPOINT,
				message -> {
					HuePairingStatus status = message.body().mapTo(HuePairingStatus.class);
					sendFeedback(configUpdateRequest, ConfigUpdateResponseStatus.PENDING, status.getMessage(), false);
				});

		Future<Message<JsonObject>> pairingRequestResult = vertx.eventBus().request(HuePairingRequest.ENDPOINT,
				JsonObject.mapFrom(paringRequest));

		pairingRequestResult.onSuccess(successHandler -> {
			sendFeedback(configUpdateRequest, ConfigUpdateResponseStatus.APPLIED, Translations.get(languageCode, TranslationIdentifier.CONFIG_SUCCESS));
			huePairStatusEvent.unregister();
		}).onFailure(failHandler -> {
			sendFeedback(configUpdateRequest, ConfigUpdateResponseStatus.FAILED, Translations.get(languageCode, TranslationIdentifier.CONFIG_FAILURE));
			huePairStatusEvent.unregister();
		});
	}

	private void sendFeedback(PluginMessage<ConfigUpdateRequest> configUpdateRequest, ConfigUpdateResponseStatus status,
			String message, boolean sendStateUpdate) {
		PluginMessage<ConfigUpdateResponse> updateResponse = new PluginMessage<>(configUpdateRequest.getId(),
				HuePluginStarter.PLUGIN_ID, PluginMessageType.CONFIG_UPDATE_RESPONSE,
				new ConfigUpdateResponse(status, message));
		vertx.eventBus().send(HuePluginWebsocketClient.getWsHandlerId(), JsonObject.mapFrom(updateResponse).encode());

		if (sendStateUpdate) {
			HuePluginWebsocketClient.sendPluginReadinessStatus(
					status == ConfigUpdateResponseStatus.APPLIED ? PluginReadinessStatus.READY
							: PluginReadinessStatus.CONFIG_REQUIRED,
					vertx);
		}
	}

	private void sendFeedback(PluginMessage<ConfigUpdateRequest> configUpdateRequest, ConfigUpdateResponseStatus status,
			String message) {
		sendFeedback(configUpdateRequest, status, message, true);
	}
}
