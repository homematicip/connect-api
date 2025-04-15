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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.plugin.PluginStarter;
import org.example.plugin.model.Bridge;
import org.example.plugin.util.PersistenceHelper;
import org.example.plugin.ws.PluginWebsocketClient;

import de.eq3.plugin.domain.config.ConfigTemplateRequest;
import de.eq3.plugin.domain.config.ConfigTemplateResponse;
import de.eq3.plugin.domain.config.GroupTemplate;
import de.eq3.plugin.domain.config.PropertyTemplate;
import de.eq3.plugin.domain.config.PropertyType;
import de.eq3.plugin.serialization.PluginMessage;
import de.eq3.plugin.serialization.PluginMessageType;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

public class ConfigTemplateRequestHandler extends AbstractVerticle implements Handler<Message<JsonObject>> {
	private final Logger logger = LogManager.getLogger(this.getClass());

	@Override
	public void start() {
		vertx.eventBus().consumer(ConfigTemplateRequest.class.getName(), this);

		logger.info("SYSTEM: {} Verticle or Worker started", this.getClass().getSimpleName());
	}

	@SuppressWarnings("unchecked")
	@Override
	public void handle(Message<JsonObject> event) {
		if (event == null || event.body() == null) {
			return;
		}
		Map<String, PropertyTemplate> properties = new HashMap<>();
		PluginMessage<ConfigTemplateRequest> configTemplateRequest = event.body().mapTo(PluginMessage.class);

		PropertyTemplate bridgeAddress = new PropertyTemplate("External Bridge address",
				"Address of the bridge. Make sure the ip is statically defined in the bridge or router", true,
				PropertyType.TYPEAHEAD);
		bridgeAddress.setDefaultValue("");

		Optional<Bridge> bridge = PersistenceHelper.getInstance().getBridge();
		bridge.ifPresent(myBridge -> bridgeAddress.setCurrentValue(myBridge.getAddress()));

		properties.put("bridge_address", bridgeAddress);

		GroupTemplate template = new GroupTemplate("Configuration");
		template.setOrder(0);

		Map<String, GroupTemplate> groups = Map.of("config", template);

		PluginMessage<ConfigTemplateResponse> response = new PluginMessage<>(configTemplateRequest.getId(),
				PluginStarter.PLUGIN_ID, PluginMessageType.CONFIG_TEMPLATE_RESPONSE,
				new ConfigTemplateResponse(properties, groups));

		vertx.eventBus().send(PluginWebsocketClient.getWsHandlerId(), JsonObject.mapFrom(response).encode());

	}
}
