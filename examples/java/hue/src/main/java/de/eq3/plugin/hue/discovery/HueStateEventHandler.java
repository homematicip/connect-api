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

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.eq3.plugin.domain.device.Device;
import de.eq3.plugin.domain.features.IFeature;
import de.eq3.plugin.domain.features.Maintenance;
import de.eq3.plugin.domain.plugin.PluginReadinessStatus;
import de.eq3.plugin.domain.status.StatusEvent;
import de.eq3.plugin.hue.HueHttpClientConfiguration;
import de.eq3.plugin.hue.HuePluginStarter;
import de.eq3.plugin.hue.auth.HueLookupRequestHandler;
import de.eq3.plugin.hue.auth.model.HueBridge;
import de.eq3.plugin.hue.auth.model.HueScheduledTestTimer;
import de.eq3.plugin.hue.discovery.mapping.FeatureConverter;
import de.eq3.plugin.hue.discovery.mapping.HueDeviceConverter;
import de.eq3.plugin.hue.discovery.model.EventstreamStartRequest;
import de.eq3.plugin.hue.discovery.model.EventstreamStopRequest;
import de.eq3.plugin.hue.model.light.Light;
import de.eq3.plugin.hue.model.sse.Event;
import de.eq3.plugin.hue.util.HuePersistenceHelper;
import de.eq3.plugin.hue.ws.HuePluginWebsocketClient;
import de.eq3.plugin.serialization.PluginMessage;
import de.eq3.plugin.serialization.PluginMessageType;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;

public class HueStateEventHandler extends AbstractVerticle implements HueHttpClientConfiguration {
	private final Logger logger = LogManager.getLogger(this.getClass());
	private HttpClient client;
	/**
	 * All Hue events end on two line breaks (ASCII 10). Used to detect non complete frames
	 */
	private static final byte[] HUE_MESSAGE_ENDING = { 10, 10 };
	private static final long HUE_ONTIME_EVENT_IGNORE_TIME = 2000;
	private static final Map<String, HttpConnection> bridgeConnections = new HashMap<>();

	private final SecureRandom secureRandom = new SecureRandom();
	private Buffer messageBuffer = Buffer.buffer();

	@Override
	public void start() {
		this.client = vertx.createHttpClient(getHttpClientOptions());

		vertx.eventBus().consumer(EventstreamStartRequest.ENDPOINT, this::handleStartEventListener);
		vertx.eventBus().consumer(EventstreamStopRequest.ENDPOINT, this::handleStopEventListener);

		Optional<HueBridge> optionalHueBridge = HuePersistenceHelper.getInstance().getHueBridge();
		optionalHueBridge.ifPresent(this::startEventStream);

		logger.info("SYSTEM: {} Verticle or Worker started", this.getClass().getSimpleName());
	}

	public static boolean isEventstreamConnected() {
		return !bridgeConnections.isEmpty();
	}

	private void handleStartEventListener(Message<JsonObject> message) {
		if (message == null || message.body() == null) {
			return;
		}
		Optional<HueBridge> optionalHueBridge = HuePersistenceHelper.getInstance().getHueBridge();
		optionalHueBridge.ifPresent(this::startEventStream);
	}

	private void handleStopEventListener(Message<JsonObject> message) {
		if (message == null || message.body() == null) {
			return;
		}
		EventstreamStopRequest stopRequest = message.body().mapTo(EventstreamStopRequest.class);
		stopEventStream(stopRequest.getBridgeId());
	}

	private void startEventStream(HueBridge hueBridge) {
		if (bridgeConnections.containsKey(hueBridge.getBridgeId())) {
			logger.info("Bridge {}: Already connected, ignore incoming event stream request", hueBridge.getBridgeId());
			return;
		}
		logger.info("Bridge {}: Starting server-sent events (SSE) stream", hueBridge.getBridgeId());

		String url = hueBridge.getLocalAddress();
		Future<String> urlFuture = HueLookupRequestHandler.getHueBridgeIp(hueBridge, url, vertx);
		urlFuture.onSuccess(requestUrl -> {

			logger.debug("Starting stream on {}", requestUrl);
			client.request(HttpMethod.GET, requestUrl, "/eventstream/clip/v2", asyncResult -> {
				if (asyncResult.succeeded()) {
					hueBridge.setLastSuccessfullAddress(requestUrl);
					HttpClientRequest request = asyncResult.result();
					request.putHeader("hue-application-key", hueBridge.getApplicationKey());
					request.putHeader(HttpHeaders.ACCEPT, "text/event-stream");
					request.putHeader(HttpHeaders.CONNECTION, "keep-alive");

					Future<HttpClientResponse> eventStream = request.send();

					eventStream.onSuccess(response -> {
						bridgeConnections.put(hueBridge.getBridgeId(), request.connection());
						HuePluginWebsocketClient.sendPluginReadinessStatus(PluginReadinessStatus.READY, vertx);
						response.handler(getReadStreamHandler());

						response.endHandler(event -> {
							logger.debug("Ending bridge connection");
							hueBridge.setLastSuccessfullAddress(null);
						});
						response.exceptionHandler(
								throwable -> logger.error("Bridge {}: Error reading response data [message={}]",
										hueBridge.getBridgeId(), throwable.getMessage()));
					});

					long pingTimerId = startPeriodicPing(request);
					request.connection().closeHandler(getConnectionCloseHandler(hueBridge, pingTimerId));

				} else {
					bridgeConnections.remove(hueBridge.getBridgeId());
					Optional<HueBridge> optionalHueBridge = HuePersistenceHelper.getInstance().getHueBridge();

					optionalHueBridge.ifPresent(bridge -> {
						hueBridge.setLastSuccessfullAddress(null);
						logger.error(
								"Bridge {}: Error during connection establishment [message={}], trying to reconnect in {} seconds",
								bridge.getBridgeId(), asyncResult.cause(), PING_AND_RECONNECT_INTERVAL_SECONDS);

						vertx.setTimer(PING_AND_RECONNECT_INTERVAL_SECONDS * 1000L,
								timerId -> startEventStream(bridge));
					});
				}
			});
		});
	}

	private Handler<Buffer> getReadStreamHandler() {
		return incomingBuffer -> {
			int bufferLength = incomingBuffer.length();
			if (bufferLength < 2) {
				return;
			}
			byte[] lastTwoChars = incomingBuffer.getBytes(bufferLength - 2, bufferLength);
			messageBuffer.appendBuffer(incomingBuffer);
			if (!Arrays.equals(lastTwoChars, HUE_MESSAGE_ENDING)) {
				logger.trace("Received non ended frame of message");
				return;
			} else {
				logger.trace("Received last frame of message");
			}

			String response = messageBuffer.toString();
			String[] result = response.split("\\R");
			messageBuffer = Buffer.buffer();
			for (String val : result) {
				if (!val.startsWith("data: ")) {
					logger.trace("Received non data body: {}", val);
					continue;
				}

				val = val.substring(val.indexOf("["));
				try {
					logger.trace("Received data body: {}", new JsonArray(val));
				} catch (DecodeException e) {
					logger.error("Could not parse incoming data to JSON {}", val);
				}

				List<Event> events = new ArrayList<>();
				try {
					events = Arrays.asList(DatabindCodec.mapper().readValue(val, Event[].class));
				} catch (JsonProcessingException e) {
					logger.error("SYSTEM: Error mapping JSON", e);
				}
				logger.debug("Received Hue Events for {} endpoints", events.size());
				logger.debug("Events: {}", events);
				events.forEach(this::handleEvent);
			}
		};
	}

	private long startPeriodicPing(HttpClientRequest request) {
		byte[] pingBytes = new byte[8];
		secureRandom.nextBytes(pingBytes);
		return vertx.setPeriodic(PING_AND_RECONNECT_INTERVAL_SECONDS,
				id -> request.connection().ping(Buffer.buffer(pingBytes)));
	}

	private Handler<Void> getConnectionCloseHandler(HueBridge hueBridge, long pingTimerId) {
		return aVoid -> {
			vertx.cancelTimer(pingTimerId);
			bridgeConnections.remove(hueBridge.getBridgeId());

			Optional<HueBridge> optionalHueBridge = HuePersistenceHelper.getInstance().getHueBridge();
			optionalHueBridge.ifPresent(bridge -> {
				logger.info("Bridge {}: Connection closed, trying to reconnect in {} seconds", hueBridge.getBridgeId(),
						PING_AND_RECONNECT_INTERVAL_SECONDS);

				vertx.setTimer(PING_AND_RECONNECT_INTERVAL_SECONDS * 1000L, timerId -> startEventStream(hueBridge));
			});
		};
	}

	private void stopEventStream(String bridgeId) {
		HttpConnection connection = bridgeConnections.remove(bridgeId);
		connection.close();

		logger.info("Bridge {}: Removed and closed connection", bridgeId);
	}

	private void handleEvent(Event event) {
		if (Event.EVENT_TYPE_UPDATE.equals(event.getType())) {
			event.getData().forEach(light -> {
				logger.debug("Handling event for light {} with type {}", light.getId(), light.getType());
				// Ignore state updates for non included devices
				if (light.getOwner() == null) {
					return;
				}
				Optional<HueBridge> optionalHueBridge = HuePersistenceHelper.getInstance().getHueBridge();

				if (optionalHueBridge.isEmpty()) {
					return;
				}
				HueBridge bridge = optionalHueBridge.get();

				if (!bridge.getIncludedDevices().contains(light.getOwner().getRid())) {
					logger.trace("Included devices do not contain owner rid of light {}", light.getId());
					return;
				}

				if (Event.DATA_TYPE_LIGHT.equals(light.getType())) {
					logger.debug("[LIGHT-{}] Trying to map changes", light.getId());
					Set<IFeature> changes = FeatureConverter.getInstance().mapChanges(vertx, light, bridge);
					logger.debug("Sending changes: {}", changes);

					// cleanup onTime timers if light gets controlled from another source
					if (bridge.getOnTimeTaskQueue().containsKey(light.getOwner().getRid())) {
						HueScheduledTestTimer timer = bridge.getOnTimeTaskQueue().get(light.getOwner().getRid());
						long elapsed = System.currentTimeMillis() - timer.getStartTime();
						logger.trace("Event for light with existing onTimer request, elapsed {}", elapsed);
						if (elapsed > HUE_ONTIME_EVENT_IGNORE_TIME) {
							logger.info("Replacing old onTime timer for device as it got changed otherwise {}",
									light.getOwner().getRid());
							bridge.getOnTimeTaskQueue().remove(light.getOwner().getRid());
							vertx.cancelTimer(timer.getTimerId());
						}
					}
					sendChanges(light, changes, bridge);

				} else if (Event.DATA_TYPE_CONNECTIVITY.equals(light.getType())) {
					String connectivityStatus = light.getStatus();

					boolean isUnreach = !HueDeviceConverter.CONNECTED.equals(connectivityStatus);

					Maintenance maintenance = new Maintenance();
					maintenance.setUnreach(isUnreach);
					Set<IFeature> changes = Collections.singleton(maintenance);

					sendChanges(light, changes, bridge);

				}
			});
		}
	}

	private void sendChanges(Light light, Set<IFeature> changes, HueBridge bridge) {
		StatusEvent statusEvent = new StatusEvent(light.getOwner().getRid(), changes);
		Device matchDevice = bridge.getPluginDevices().get(light.getOwner().getRid());
		AtomicBoolean dirty = new AtomicBoolean(false);
		if (matchDevice != null) {
			changes.stream().forEach(feature -> {
				IFeature matchFeature = matchDevice.getFeatures()
						.stream()
						.filter(f -> f.getType() == feature.getType())
						.findFirst()
						.orElse(null);
				if (matchFeature != null && !matchFeature.equals(feature)) {
					matchDevice.getFeatures().remove(matchFeature);
					matchDevice.getFeatures().add(feature);
					dirty.set(true);
				}

			});
		}
		logger.debug("Sending current light status: {} dirty:{}", changes, dirty.get());

		PluginMessage<StatusEvent> message = new PluginMessage<>(UUID.randomUUID().toString(),
				HuePluginStarter.PLUGIN_ID, PluginMessageType.STATUS_EVENT, statusEvent);

		vertx.eventBus().send(HuePluginWebsocketClient.getWsHandlerId(), JsonObject.mapFrom(message).encode());
	}
}
