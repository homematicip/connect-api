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

import de.eq3.plugin.domain.config.ConfigUpdateRequest;
import de.eq3.plugin.domain.config.ConfigUpdateResponse;
import de.eq3.plugin.domain.config.ConfigUpdateResponseStatus;
import de.eq3.plugin.domain.discover.DiscoverRequest;
import de.eq3.plugin.domain.plugin.PluginReadinessStatus;
import de.eq3.plugin.serialization.PluginMessage;
import de.eq3.plugin.serialization.PluginMessageType;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.plugin.PluginStarter;
import org.example.plugin.util.PersistenceHelper;
import org.example.plugin.ws.PluginWebsocketClient;

import java.util.Map;
import java.util.UUID;

public class ConfigUpdateRequestHandler extends AbstractVerticle implements Handler<Message<JsonObject>> {
    private final Logger logger = LogManager.getLogger(this.getClass());

    @Override
    public void start() {
        vertx.eventBus().consumer(ConfigUpdateRequest.class.getName(), this);

        logger.info("SYSTEM: {} Verticle or Worker started", this.getClass().getSimpleName());
    }

    @Override
    public void handle(Message<JsonObject> event) {
        if (event == null || event.body() == null) {
            return;
        }
        PluginMessage<ConfigUpdateRequest> configUpdateRequest = event.body().mapTo(PluginMessage.class);
        Map<String, Object> properties = configUpdateRequest.getBody().getProperties();

        String bridgeAddress = null;
        try {
            bridgeAddress = (String) properties.get("bridge_address");
        } catch (Exception e) {
            logger.error("Could not read incoming property", e);
        }
        if (bridgeAddress == null) {
            sendFeedback(configUpdateRequest, ConfigUpdateResponseStatus.FAILED,
                    "Localized Unsupported value supplied");
            return;
        }
        PersistenceHelper.getInstance().saveAuthData("myBridgeId", "key",bridgeAddress);
        sendFeedback(configUpdateRequest, ConfigUpdateResponseStatus.APPLIED, "Bridge Address saved");

        // Automatically start discovery after successful linking
        PluginMessage<DiscoverRequest> discoveryRequest = new PluginMessage<>(
                UUID.randomUUID().toString(), PluginStarter.PLUGIN_ID,
                PluginMessageType.DISCOVER_REQUEST, null);
        vertx.eventBus().publish(DiscoverRequest.class.getName(),
                JsonObject.mapFrom(discoveryRequest));
    }

    private void sendFeedback(PluginMessage<ConfigUpdateRequest> configUpdateRequest, ConfigUpdateResponseStatus status,
                              String message, boolean sendStateUpdate) {
        PluginMessage<ConfigUpdateResponse> updateResponse = new PluginMessage<>(configUpdateRequest.getId(),
                PluginStarter.PLUGIN_ID, PluginMessageType.CONFIG_UPDATE_RESPONSE,
                new ConfigUpdateResponse(status, message));
        vertx.eventBus().send(PluginWebsocketClient.getWsHandlerId(), JsonObject.mapFrom(updateResponse).encode());

        if (sendStateUpdate) {
            PluginWebsocketClient.sendPluginReadinessStatus(
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
