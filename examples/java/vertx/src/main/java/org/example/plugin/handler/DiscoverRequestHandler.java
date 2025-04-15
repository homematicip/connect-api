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
import de.eq3.plugin.domain.discover.DiscoverRequest;
import de.eq3.plugin.domain.discover.DiscoverResponse;
import de.eq3.plugin.domain.features.IFeature;
import de.eq3.plugin.domain.features.SwitchState;
import de.eq3.plugin.domain.user.message.BehaviorType;
import de.eq3.plugin.domain.user.message.CreateUserMessageRequest;
import de.eq3.plugin.domain.user.message.DeleteUserMessageRequest;
import de.eq3.plugin.domain.user.message.MessageCategory;
import de.eq3.plugin.serialization.DeviceType;
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

import java.util.*;

public class DiscoverRequestHandler extends AbstractVerticle
        implements Handler<Message<JsonObject>> {
    public static final String EN = "en";
    private final Logger logger = LogManager.getLogger(this.getClass());

    @Override
    public void start() {
        vertx.eventBus().consumer(DiscoverRequest.class.getName(), this);

        logger.info("SYSTEM: {} Verticle or Worker started", this.getClass().getSimpleName());
    }

    public void handle(Message<JsonObject> event) {
        if (event == null || event.body() == null) {
            return;
        }
        PluginMessage<?> request = event.body().mapTo(PluginMessage.class);
        Optional<Bridge> optionalBridge = PersistenceHelper.getInstance().getBridge();

        if (optionalBridge.isEmpty()) {
            sendErrorResponse("No configured Bridge available (3)", request);
            return;
        }
        Bridge bridge = optionalBridge.get();
        // Get devices from bridge or api
        // Here: Mock some Devices
        Set<Device> devices = new HashSet<>();
        Set<IFeature> features = new HashSet<>();
        features.add(new SwitchState(true));
        devices.add(new Device("mockedDevice1", "model0815", "myFriendlyName1",
                "firmwareVersion", DeviceType.SWITCH, features));
        devices.add(new Device("mockedDevice2", "model0815", "myFriendlyName2",
                "firmwareVersion", DeviceType.SWITCH, features));
        PersistenceHelper.getInstance().saveDeviceData(devices);
        // Send a messages to the user
        if (devices.isEmpty()) {
            sendInfoMessage("Plugin Info", "No device found in the plugin!");
        } else {
            deleteInfoMessage();
        }
        sendSuccessResponse(bridge, devices, request);
    }

    private void sendInfoMessage(String title, String message) {
        final String languageCode = EN;
        CreateUserMessageRequest createUserMessageRequest = new CreateUserMessageRequest();
        createUserMessageRequest.setBehaviorType(BehaviorType.DISMISSIBLE);
        createUserMessageRequest.setTimestamp(System.currentTimeMillis());
        createUserMessageRequest.setUserMessageId(PluginStarter.PLUGIN_ID + "_no_devices");
        createUserMessageRequest.setMessageCategory(MessageCategory.INFO);
        Map<String, String> messageMap = new HashMap<>();
        Map<String, String> titleMap = new HashMap<>();
        messageMap.put(languageCode, message);
        titleMap.put(languageCode, title);
        // Only necessary, if you have a different language
        // The code "en" has to be filled always
        messageMap.putIfAbsent(EN, message);
        titleMap.putIfAbsent(EN, title);
        createUserMessageRequest.setMessage(messageMap);
        createUserMessageRequest.setTitle(titleMap);

        PluginMessage<CreateUserMessageRequest> pluginMessage = new PluginMessage<>(UUID.randomUUID().toString(),
                PluginStarter.PLUGIN_ID, PluginMessageType.CREATE_USER_MESSAGE_REQUEST, createUserMessageRequest);

        vertx.eventBus().send(PluginWebsocketClient.getWsHandlerId(), JsonObject.mapFrom(pluginMessage).encode());
    }

    private void deleteInfoMessage() {
        DeleteUserMessageRequest request = new DeleteUserMessageRequest(PluginStarter.PLUGIN_ID + "_no_devices");
        PluginMessage<DeleteUserMessageRequest> message = new PluginMessage<>(UUID.randomUUID().toString(),
                PluginStarter.PLUGIN_ID, PluginMessageType.DELETE_USER_MESSAGE_REQUEST, request);

        vertx.eventBus().send(PluginWebsocketClient.getWsHandlerId(), JsonObject.mapFrom(message).encode());
    }

    private void sendSuccessResponse(Bridge bridge, Set<Device> devices, PluginMessage<?> request) {

        logger.info("Bridge {}: Successfully discovered and converted {} device(s)",
                bridge.getBridgeId(), devices.size());

        PluginMessage<DiscoverResponse> message = new PluginMessage<>(request.getId(), PluginStarter.PLUGIN_ID,
                PluginMessageType.DISCOVER_RESPONSE, new DiscoverResponse(true, devices, null));

        vertx.eventBus().send(PluginWebsocketClient.getWsHandlerId(), JsonObject.mapFrom(message).encode());
    }

    private void sendErrorResponse(String errorMessage, PluginMessage<?> request) {
        Error error = new Error("DISCOVER_REQUEST_FAILED", errorMessage);

        PluginMessage<DiscoverResponse> message = new PluginMessage<>(request.getId(), PluginStarter.PLUGIN_ID,
                PluginMessageType.DISCOVER_RESPONSE, new DiscoverResponse(false, null, error));

        vertx.eventBus().send(PluginWebsocketClient.getWsHandlerId(), JsonObject.mapFrom(message).encode());
    }
}
