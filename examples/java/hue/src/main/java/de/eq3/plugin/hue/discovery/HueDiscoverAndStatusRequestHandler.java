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

package de.eq3.plugin.hue.discovery;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.domain.device.Device;
import de.eq3.plugin.domain.discover.DiscoverRequest;
import de.eq3.plugin.domain.discover.DiscoverResponse;
import de.eq3.plugin.domain.error.Error;
import de.eq3.plugin.domain.features.OnTime;
import de.eq3.plugin.domain.status.StatusRequest;
import de.eq3.plugin.domain.status.StatusResponse;
import de.eq3.plugin.hue.HueHttpClientConfiguration;
import de.eq3.plugin.hue.HuePluginStarter;
import de.eq3.plugin.hue.auth.model.HueBridge;
import de.eq3.plugin.hue.discovery.function.HueConvertDevicesFunction;
import de.eq3.plugin.hue.discovery.function.HueGetResourcesFunction;
import de.eq3.plugin.hue.model.HueResponse;
import de.eq3.plugin.hue.util.HuePersistenceHelper;
import de.eq3.plugin.hue.ws.HuePluginWebsocketClient;
import de.eq3.plugin.serialization.PluginMessage;
import de.eq3.plugin.serialization.PluginMessageType;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class HueDiscoverAndStatusRequestHandler extends AbstractVerticle
		implements HueHttpClientConfiguration, Handler<Message<JsonObject>> {
	private final Logger logger = LogManager.getLogger(this.getClass());

	private WebClient webClient;

	@Override
	public void start() {
		vertx.eventBus().consumer(DiscoverRequest.class.getName(), this);
		vertx.eventBus().consumer(StatusRequest.class.getName(), this);

		WebClientOptions clientOptions = new WebClientOptions(getHttpClientOptions().setIdleTimeout(5));
		this.webClient = WebClient.create(this.vertx, clientOptions);

		logger.info("SYSTEM: {} Verticle or Worker started", this.getClass().getSimpleName());
	}

	@Override
	public void handle(Message<JsonObject> event) {
		if (event == null || event.body() == null) {
			return;
		}
		PluginMessage<?> request = event.body().mapTo(PluginMessage.class);
		Optional<HueBridge> optionalHueBridge = HuePersistenceHelper.getInstance().getHueBridge();

		if (optionalHueBridge.isEmpty()) {
			sendErrorResponse("No configured Hue Bridge available", request);
			return;
		}
		HueBridge hueBridge = optionalHueBridge.get();

		Future.succeededFuture(hueBridge)
				.compose(new HueGetResourcesFunction(this.webClient, vertx))
				.onComplete(hueResponse -> {
					if (hueResponse.succeeded()) {
						HueResponse result = hueResponse.result();
						HuePersistenceHelper.getInstance().saveDeviceData(result.getDevices());

						Future.succeededFuture(result)
								.compose(new HueConvertDevicesFunction())
								.onComplete(convertDevices -> {
									if (convertDevices.succeeded()) {
										Set<Device> devices = convertDevices.result();
										hueBridge.getPluginDevices().clear();
										Map<String, Device> deviceMap = devices.stream()
												.collect(Collectors.toMap(Device::getDeviceId, device -> device));
										hueBridge.getPluginDevices().putAll(deviceMap);
										// onTime is supported by all devices
										devices.forEach(device -> device.getFeatures().add(new OnTime()));
										sendSuccessResponse(hueBridge, devices, request);
									} else {
										String errorMessage = "Bridge %s: Error converting Philips Hue Devices";

										logger.error(String.format(errorMessage, hueBridge.getBridgeId()),
												convertDevices.cause());

										sendErrorResponse(convertDevices.cause().toString(), request);
									}
								});
					} else {
						logger.info("Bridge {}: Error calling Philips Hue resource, cause {}", hueBridge.getBridgeId(),
								hueResponse.cause());

						sendErrorResponse(hueResponse.cause().toString(), request);
					}
				});
	}

	private void sendSuccessResponse(HueBridge bridge, Set<Device> devices, PluginMessage<?> request) {

		if (request.getType() == PluginMessageType.DISCOVER_REQUEST) {
			logger.info("Bridge {}: Successfully discovered and converted {} Philips Hue device(s)",
					bridge.getBridgeId(), devices.size());

			PluginMessage<DiscoverResponse> message = new PluginMessage<>(request.getId(), HuePluginStarter.PLUGIN_ID,
					PluginMessageType.DISCOVER_RESPONSE, new DiscoverResponse(true, devices, null));

			vertx.eventBus().send(HuePluginWebsocketClient.getWsHandlerId(), JsonObject.mapFrom(message).encode());
		} else {
			StatusRequest statusRequest = (StatusRequest) request.getBody();
			if (statusRequest != null) {
				Set<String> deviceIds = statusRequest.getDeviceIds();
				if (deviceIds != null && !deviceIds.isEmpty()) {

					devices = devices.stream()
							.filter(device -> deviceIds.contains(device.getDeviceId()))
							.collect(Collectors.toSet());
				}
			}
			devices = devices.stream()
					.filter(device -> bridge.getIncludedDevices().contains(device.getDeviceId()))
					.collect(Collectors.toSet());

			logger.trace("Bridge {}: Successfully requested status and converted {} Philips Hue device(s)",
					bridge.getBridgeId(), devices.size());

			PluginMessage<StatusResponse> message = new PluginMessage<>(request.getId(), HuePluginStarter.PLUGIN_ID,
					PluginMessageType.STATUS_RESPONSE, new StatusResponse(true, devices, null));

			vertx.eventBus().send(HuePluginWebsocketClient.getWsHandlerId(), JsonObject.mapFrom(message).encode());

		}
	}

	private void sendErrorResponse(String errorMessage, PluginMessage<?> request) {
		if (request.getType() == PluginMessageType.DISCOVER_REQUEST) {
			Error error = new Error("DISCOVER_REQUEST_FAILED", errorMessage);

			PluginMessage<DiscoverResponse> message = new PluginMessage<>(request.getId(), HuePluginStarter.PLUGIN_ID,
					PluginMessageType.DISCOVER_RESPONSE, new DiscoverResponse(false, null, error));

			vertx.eventBus().send(HuePluginWebsocketClient.getWsHandlerId(), JsonObject.mapFrom(message).encode());

		} else {
			Error error = new Error("STATUS_REQUEST_FAILED", errorMessage);

			PluginMessage<StatusResponse> message = new PluginMessage<>(request.getId(), HuePluginStarter.PLUGIN_ID,
					PluginMessageType.STATUS_RESPONSE, new StatusResponse(false, null, error));

			vertx.eventBus().send(HuePluginWebsocketClient.getWsHandlerId(), JsonObject.mapFrom(message).encode());
		}
	}
}
