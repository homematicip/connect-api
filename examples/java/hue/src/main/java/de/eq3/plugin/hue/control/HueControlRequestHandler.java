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

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.domain.control.ControlRequest;
import de.eq3.plugin.domain.control.ControlResponse;
import de.eq3.plugin.domain.error.Error;
import de.eq3.plugin.domain.features.OnTime;
import de.eq3.plugin.domain.features.SwitchState;
import de.eq3.plugin.hue.HueHttpClientConfiguration;
import de.eq3.plugin.hue.HuePluginStarter;
import de.eq3.plugin.hue.auth.model.HueBridge;
import de.eq3.plugin.hue.auth.model.HueScheduledTestTimer;
import de.eq3.plugin.hue.control.function.HueControlLightServiceFunction;
import de.eq3.plugin.hue.discovery.mapping.FeatureConverter;
import de.eq3.plugin.hue.model.device.Device;
import de.eq3.plugin.hue.model.device.Service;
import de.eq3.plugin.hue.model.light.Light;
import de.eq3.plugin.hue.util.HuePersistenceHelper;
import de.eq3.plugin.hue.ws.HuePluginWebsocketClient;
import de.eq3.plugin.serialization.Feature;
import de.eq3.plugin.serialization.PluginMessage;
import de.eq3.plugin.serialization.PluginMessageType;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class HueControlRequestHandler extends AbstractVerticle
		implements Handler<Message<JsonObject>>, HueHttpClientConfiguration {

	public static final String CONTROL_REQUEST_FAILED = "CONTROL_REQUEST_FAILED";

	private final Logger logger = LogManager.getLogger(this.getClass());

	private WebClient webClient;

	private Queue<PluginMessage<ControlRequest>> controlRequests = new ArrayDeque<PluginMessage<ControlRequest>>();

	private long delay = 150;

	@Override
	public void start() {
		vertx.eventBus().consumer(ControlRequest.class.getName(), this);
		delay = Integer.parseInt(System.getProperty("control.request.delay", "150"));

		WebClientOptions clientOptions = new WebClientOptions(getHttpClientOptions().setIdleTimeout(5));
		this.webClient = WebClient.create(this.vertx, clientOptions);

		logger.info("SYSTEM: {} Verticle or Worker started", this.getClass().getSimpleName());
	}

	@Override
	public void handle(Message<JsonObject> message) {
		if (message == null || message.body() == null) {
			return;
		}
		PluginMessage<ControlRequest> request = message.body().mapTo(PluginMessage.class);

		boolean isEmpty = controlRequests.isEmpty();
		controlRequests.add(request);
		if (isEmpty) {
			executeNextControlRequest();
		}
	}

	private void checkNextControlRequest() {
		logger.info("Executing next request in {} if present", delay);
		vertx.setTimer(delay, event -> {
			controlRequests.poll();
			if (!controlRequests.isEmpty()) {
				logger.info("Still control requests in group, executing next request");
				executeNextControlRequest();
			}
		});
	}

	private void executeNextControlRequest() {
		PluginMessage<ControlRequest> request = controlRequests.peek();
		if (request == null) {
			return;
		}
		Optional<HueBridge> optionalHueBridge = HuePersistenceHelper.getInstance().getHueBridge();

		if (optionalHueBridge.isEmpty()) {
			sendControlRequestResponse(request, false,
					new Error(CONTROL_REQUEST_FAILED, "No configured Hue Bridge available"));
			checkNextControlRequest();
			return;
		}
		HueBridge hueBridge = optionalHueBridge.get();

		this.logger.debug("Bridge {}: Got request to control Philips Hue light service", hueBridge.getBridgeId());
		this.logger.trace("Bridge {}: Incoming control request data: {}", hueBridge.getBridgeId(), request);

		Device device = hueBridge.getDevices().get(request.getBody().getDeviceId());

		if (device == null) {
			sendControlRequestResponse(request, false, new Error(CONTROL_REQUEST_FAILED, "Device not found"));
			checkNextControlRequest();
			return;
		}
		Optional<Service> lightService = device.getServices()
				.stream()
				.filter(service -> "light".equalsIgnoreCase(service.getRtype()))
				.findFirst();

		if (lightService.isEmpty()) {
			sendControlRequestResponse(request, false, new Error(CONTROL_REQUEST_FAILED, "Light service not found"));
			checkNextControlRequest();
			return;
		}
		String serviceId = lightService.get().getRid();

		Optional<OnTime> onTime = request.getBody()
				.getFeatures()
				.stream()
				.filter(feature -> feature.getType() == Feature.ON_TIME)
				.map(OnTime.class::cast)
				.filter(ontime -> ontime.getOnTime() != null)
				.filter(ontime -> ontime.getOnTime() < 8000000)
				.filter(ontime -> ontime.getOnTime().longValue() > 0)
				.findFirst();
		// cleanup old onTime requests currently scheduled
		HueScheduledTestTimer oldTimerId = hueBridge.getOnTimeTaskQueue().get(request.getBody().getDeviceId());
		if (oldTimerId != null) {
			logger.info("Replacing old onTime timer as it got another request{}", request.getBody().getDeviceId());
			hueBridge.getOnTimeTaskQueue().remove(request.getBody().getDeviceId());
			vertx.cancelTimer(oldTimerId.getTimerId());
		}

		// start new onTime timer
		if (onTime.isPresent()) {
			@SuppressWarnings("unchecked")
			PluginMessage<ControlRequest> onTimeOffRequest = JsonObject.mapFrom(request).mapTo(PluginMessage.class);

			onTimeOffRequest.getBody().setFeatures(Set.of(new SwitchState(false)));
			onTimeOffRequest.setId(onTimeOffRequest.getId() + "_OnTimeOff");
			long newTimerId = vertx.setTimer(onTime.get().getOnTime().longValue() * 1000, timer -> {
				logger.debug("Executing onTime off request");
				hueBridge.getOnTimeTaskQueue().remove(request.getBody().getDeviceId());
				boolean isEmpty = controlRequests.isEmpty();
				controlRequests.add(onTimeOffRequest);
				if (isEmpty) {
					executeNextControlRequest();
				}
			});
			logger.info("Queueing task for light {}", request.getBody().getDeviceId());
			hueBridge.getOnTimeTaskQueue()
					.put(request.getBody().getDeviceId(),
							new HueScheduledTestTimer(newTimerId, System.currentTimeMillis()));
		}
		Light light = FeatureConverter.getInstance().doForward(request.getBody().getFeatures());
		logger.info("ligth {} request {}", light, request);
		Future.succeededFuture(hueBridge)
				.compose(new HueControlLightServiceFunction(this.webClient, serviceId, light, vertx))
				.onComplete(asyncResult -> {

					if (asyncResult.succeeded()) {
						logger.trace("Bridge {} - Successfully called Philips Hue light service",
								hueBridge.getBridgeId());

						sendControlRequestResponse(request, true, null);
					} else {
						Error error = new Error(CONTROL_REQUEST_FAILED, asyncResult.cause().toString());
						logger.info(
								"Bridge {} - Error calling Philips Hue light service, light {}, request {} cause {}",
								hueBridge.getBridgeId(), light, request, asyncResult.cause());

						sendControlRequestResponse(request, false, error);
					}
				});
		checkNextControlRequest();
	}

	private void sendControlRequestResponse(PluginMessage<ControlRequest> request, boolean success, Error error) {
		ControlResponse controlResponse = new ControlResponse(request.getBody().getDeviceId(), success, error);

		PluginMessage<ControlResponse> response = new PluginMessage<>(request.getId(), HuePluginStarter.PLUGIN_ID,
				PluginMessageType.CONTROL_RESPONSE, controlResponse);

		vertx.eventBus().send(HuePluginWebsocketClient.getWsHandlerId(), JsonObject.mapFrom(response).encode());
	}
}
