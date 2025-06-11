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

package de.eq3.plugin.hue.auth;

import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.domain.discover.DiscoverRequest;
import de.eq3.plugin.domain.plugin.PluginReadinessStatus;
import de.eq3.plugin.hue.HueHttpClientConfiguration;
import de.eq3.plugin.hue.HuePluginStarter;
import de.eq3.plugin.hue.auth.function.HueConfirmLinkButtonFunction;
import de.eq3.plugin.hue.auth.model.HueBridge;
import de.eq3.plugin.hue.auth.model.HuePairingRequest;
import de.eq3.plugin.hue.auth.model.HuePairingStatus;
import de.eq3.plugin.hue.discovery.model.EventstreamStartRequest;
import de.eq3.plugin.hue.util.HuePersistenceHelper;
import de.eq3.plugin.hue.util.TranslationIdentifier;
import de.eq3.plugin.hue.util.Translations;
import de.eq3.plugin.hue.ws.HuePluginWebsocketClient;
import de.eq3.plugin.serialization.PluginMessage;
import de.eq3.plugin.serialization.PluginMessageType;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class HuePairingRequestHandler extends AbstractVerticle
		implements Handler<Message<JsonObject>>, HueHttpClientConfiguration {

	private final Logger logger = LogManager.getLogger(this.getClass());

	private WebClient webClient;

	private boolean pairingActive = false;

	@Override
	public void start() {
		vertx.eventBus().consumer(HuePairingRequest.ENDPOINT, this);

		WebClientOptions clientOptions = new WebClientOptions(getHttpClientOptions().setIdleTimeout(5));
		this.webClient = WebClient.create(this.vertx, clientOptions);

		logger.info("SYSTEM: {} Verticle or Worker started", this.getClass().getSimpleName());
	}

	@Override
	public void handle(Message<JsonObject> message) {
		if (message == null || message.body() == null) {
			return;
		}
		if (this.pairingActive) {
			logger.info("Pairing process already active, ignoring request");
			return;
		}
		HuePairingRequest huePairingRequest = message.body().mapTo(HuePairingRequest.class);
		this.pairingActive = true;
		logger.info("Start pairing with Hue Bridge {}", huePairingRequest.getLocalAddress());
		vertx.eventBus()
				.publish(HuePairingStatus.ENDPOINT,
						JsonObject.mapFrom(new HuePairingStatus(Translations.get(huePairingRequest.getLanguageCode(),
								TranslationIdentifier.CONFIG_CONFIRM_CONNECTION_HINT))));
		long timerId = vertx.setPeriodic(10000L,
				timer -> HueLookupRequestHandler
						.getHueBridgeIp(new HueBridge(), huePairingRequest.getLocalAddress(), vertx)
						.compose(new HueConfirmLinkButtonFunction(this.webClient))

						.onSuccess(hueApplicationKey -> {
							logger.info("Got hue-application-key: {}", hueApplicationKey);
							vertx.cancelTimer(timer);

							HuePersistenceHelper.getInstance()
									.saveAuthData(huePairingRequest.getLocalAddress(), hueApplicationKey,
											huePairingRequest.getLocalAddress());

							EventstreamStartRequest request = new EventstreamStartRequest(
									huePairingRequest.getLocalAddress());

							vertx.eventBus().send(EventstreamStartRequest.ENDPOINT, JsonObject.mapFrom(request));
							HuePluginWebsocketClient.sendPluginReadinessStatus(PluginReadinessStatus.READY, vertx);

							this.pairingActive = false;
							message.reply(JsonObject.mapFrom(huePairingRequest));
							// Automatically start discovery after successful linking
							PluginMessage<DiscoverRequest> discoveryRequest = new PluginMessage<>(
									UUID.randomUUID().toString(), HuePluginStarter.PLUGIN_ID,
									PluginMessageType.DISCOVER_REQUEST, null);
							vertx.eventBus()
									.publish(DiscoverRequest.class.getName(), JsonObject.mapFrom(discoveryRequest));

						})
						.onFailure(throwable -> {
							logger.info("Bridge Button not yet pressed, message={}", throwable.getMessage());
							try {
								JsonArray errorResult = new JsonArray(throwable.getMessage());
								JsonObject resultJson = errorResult.getJsonObject(0);
								String hueErrorDescription = resultJson.getJsonObject("error").getString("description");
								if (resultJson.getJsonObject("error").getInteger("type") == 101) {
									hueErrorDescription = Translations.get(huePairingRequest.getLanguageCode(),
											TranslationIdentifier.CONFIG_CONFIRM_CONNECTION_SECOND_HINT);
								}

								vertx.eventBus()
										.publish(HuePairingStatus.ENDPOINT,
												JsonObject.mapFrom(new HuePairingStatus(hueErrorDescription)));

							} catch (Exception e) {
								vertx.eventBus()
										.publish(HuePairingStatus.ENDPOINT,
												JsonObject.mapFrom(new HuePairingStatus(
														Translations.get(huePairingRequest.getLanguageCode(),
																TranslationIdentifier.CONFIG_BRIDGE_UNREACHABLE))));
							}

						}));

		vertx.setTimer(30000L, timeoutId -> {
			if (vertx.cancelTimer(timerId)) {
				this.pairingActive = false;
				logger.info("Bridge Button not pressed for 30 seconds, aborting");
				message.fail(4711, Translations.get(huePairingRequest.getLanguageCode(),
						TranslationIdentifier.CONFIG_CONFIRM_FAILURE));
			}
		});
	}
}
