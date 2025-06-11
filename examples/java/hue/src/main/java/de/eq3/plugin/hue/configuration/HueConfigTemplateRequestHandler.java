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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.domain.config.ConfigTemplateRequest;
import de.eq3.plugin.domain.config.ConfigTemplateResponse;
import de.eq3.plugin.domain.config.PropertyTemplate;
import de.eq3.plugin.domain.config.PropertyType;
import de.eq3.plugin.hue.HuePluginStarter;
import de.eq3.plugin.hue.auth.model.HueBridge;
import de.eq3.plugin.hue.auth.model.HueLookupRequest;
import de.eq3.plugin.hue.auth.model.HueLookupResponse;
import de.eq3.plugin.hue.util.HuePersistenceHelper;
import de.eq3.plugin.hue.util.TranslationIdentifier;
import de.eq3.plugin.hue.util.Translations;
import de.eq3.plugin.hue.ws.HuePluginWebsocketClient;
import de.eq3.plugin.serialization.PluginMessage;
import de.eq3.plugin.serialization.PluginMessageType;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

public class HueConfigTemplateRequestHandler extends AbstractVerticle implements Handler<Message<JsonObject>> {
	private final Logger logger = LogManager.getLogger(this.getClass());

	@Override
	public void start() throws Exception {
		vertx.eventBus().consumer(ConfigTemplateRequest.class.getName(), this);

		logger.info("SYSTEM: {} Verticle or Worker started", this.getClass().getSimpleName());
	}

	@Override
	public void handle(Message<JsonObject> event) {
		if (event == null || event.body() == null) {
			return;
		}

		PluginMessage<ConfigTemplateRequest> configTemplateRequest = event.body().mapTo(PluginMessage.class);
		String languageCode = configTemplateRequest.getBody().getLanguageCode();

		Future<Message<JsonObject>> discoverRequest = vertx.eventBus().request(HueLookupRequest.ENDPOINT,
				new JsonObject());

		discoverRequest.onComplete(message -> {

			Map<String, PropertyTemplate> properties = new HashMap<>();
			List<String> discoveredBridges = new ArrayList<>();

			if (message.succeeded()) {
				HueLookupResponse hueLookupResponse = message.result().body().mapTo(HueLookupResponse.class);
				for (HueBridge bridge : hueLookupResponse.getHueBridges()) {
					discoveredBridges.add(bridge.getLocalAddress());
				}
			}
			PropertyTemplate bridgeAddress = new PropertyTemplate(Translations.get(languageCode, TranslationIdentifier.CONFIG_ADDRESS_TITLE),
					Translations.get(languageCode, TranslationIdentifier.CONFIG_ADDRESS_DESCRIPTION),
					true, PropertyType.TYPEAHEAD);
			bridgeAddress.setValues(discoveredBridges);
			bridgeAddress.setDefaultValue(discoveredBridges.isEmpty() ? "" : discoveredBridges.get(0));

			Optional<HueBridge> bridge = HuePersistenceHelper.getInstance().getHueBridge();
			bridge.ifPresent(hueBridge -> bridgeAddress.setCurrentValue(hueBridge.getLocalAddress()));

			properties.put("bridge_address", bridgeAddress);

			PluginMessage<ConfigTemplateResponse> response = new PluginMessage<>(configTemplateRequest.getId(),
					HuePluginStarter.PLUGIN_ID, PluginMessageType.CONFIG_TEMPLATE_RESPONSE,
					new ConfigTemplateResponse(properties, null));

			vertx.eventBus().send(HuePluginWebsocketClient.getWsHandlerId(), JsonObject.mapFrom(response).encode());
		});
	}

}
