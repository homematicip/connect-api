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

import de.eq3.plugin.domain.error.Error;
import de.eq3.plugin.domain.device.Device;
import de.eq3.plugin.domain.status.StatusRequest;
import de.eq3.plugin.domain.status.StatusResponse;
import de.eq3.plugin.serialization.PluginMessage;
import de.eq3.plugin.serialization.PluginMessageType;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.plugin.PluginStarter;
import org.example.plugin.model.Bridge;
import org.example.plugin.util.PersistenceHelper;
import org.example.plugin.ws.PluginWebsocketClient;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class StatusRequestHandler extends AbstractVerticle
        implements Handler<Message<JsonObject>> {
    private final Logger logger = LogManager.getLogger(this.getClass());

    @Override
    public void start() {
        vertx.eventBus().consumer(StatusRequest.class.getName(), this);

        logger.info("SYSTEM: {} Verticle or Worker started", this.getClass().getSimpleName());
    }

    public void handle(Message<JsonObject> event) {
        if (event == null || event.body() == null) {
            return;
        }
        PluginMessage<?> request = event.body().mapTo(PluginMessage.class);
        Optional<Bridge> optionalBridge = PersistenceHelper.getInstance().getBridge();

        if (optionalBridge.isEmpty()) {
            sendErrorResponse("No configured Bridge available (4)", request);
            return;
        }
        Bridge bridge = optionalBridge.get();

        Set<Device> deviceSet = (Set<Device>) bridge.getDevices().values();
        sendSuccessResponse(bridge, deviceSet, request);
    }

    private void sendSuccessResponse(Bridge bridge, Set<Device> devices, PluginMessage<?> request) {

        StatusRequest statusRequest = (StatusRequest) request.getBody();
        if (statusRequest != null) {
            Set<String> deviceIds = statusRequest.getDeviceIds();
            if (deviceIds != null && !deviceIds.isEmpty()) {

                devices = devices.stream().filter(device -> deviceIds.contains(device.getDeviceId()))
                        .collect(Collectors.toSet());
            }
        }
        devices = devices.stream().filter(device -> bridge.getIncludedDevices().contains(device.getDeviceId()))
                .collect(Collectors.toSet());

        logger.trace("Bridge {}: Successfully requested status and converted {} device(s)",
                bridge.getBridgeId(), devices.size());

        PluginMessage<StatusResponse> message = new PluginMessage<>(request.getId(), PluginStarter.PLUGIN_ID,
                PluginMessageType.STATUS_RESPONSE, new StatusResponse(true, devices, null));

        vertx.eventBus().send(PluginWebsocketClient.getWsHandlerId(), JsonObject.mapFrom(message).encode());
    }

    private void sendErrorResponse(String errorMessage, PluginMessage<?> request) {

        Error error = new Error("STATUS_REQUEST_FAILED", errorMessage);

        PluginMessage<StatusResponse> message = new PluginMessage<>(request.getId(), PluginStarter.PLUGIN_ID,
                PluginMessageType.STATUS_RESPONSE, new StatusResponse(false, null, error));

        vertx.eventBus().send(PluginWebsocketClient.getWsHandlerId(), JsonObject.mapFrom(message).encode());
    }
}
