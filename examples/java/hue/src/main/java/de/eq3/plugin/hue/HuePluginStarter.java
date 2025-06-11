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

package de.eq3.plugin.hue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.domain.plugin.PluginReadinessStatus;
import de.eq3.plugin.hue.auth.HueLookupRequestHandler;
import de.eq3.plugin.hue.auth.HuePairingRequestHandler;
import de.eq3.plugin.hue.auth.model.HueBridge;
import de.eq3.plugin.hue.configuration.HueConfigTemplateRequestHandler;
import de.eq3.plugin.hue.configuration.HueConfigUpdateRequestHandler;
import de.eq3.plugin.hue.control.HueControlRequestHandler;
import de.eq3.plugin.hue.control.HueLightStateRequestHandler;
import de.eq3.plugin.hue.discovery.HueDiscoverAndStatusRequestHandler;
import de.eq3.plugin.hue.discovery.HueStateEventHandler;
import de.eq3.plugin.hue.inclusion.HueDeviceExclusionHandler;
import de.eq3.plugin.hue.inclusion.HueDeviceInclusionHandler;
import de.eq3.plugin.hue.plugin.HuePluginStateRequestHandler;
import de.eq3.plugin.hue.util.HuePersistenceHelper;
import de.eq3.plugin.hue.ws.HuePluginWebsocketClient;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class HuePluginStarter {
	private static final Logger logger = LogManager.getLogger(HuePluginStarter.class);

	public static final String PLUGIN_ID = "de.homematicip.example.plugin.hue";
	public static final String PLUGIN_NAME = "Philips Hue Example";

	public static void main(String[] args) throws IOException {

		try (InputStream fis = HuePluginStarter.class.getClassLoader().getResourceAsStream("hue-plugin.properties")) {
			Properties properties = new Properties();
			properties.load(fis);
			properties.forEach((key, value) -> System.getProperties().putIfAbsent(key, value));
		}
		// disable Vertx internal DNS resolver for usage with local host names
		System.setProperty("vertx.disableDnsResolver", "true");
		Vertx vertx = Vertx.vertx();
		HuePersistenceHelper.getInstance().init(vertx);

		Future<String> wsClient = vertx.deployVerticle(HuePluginWebsocketClient.class.getName());

		wsClient.compose(
				deploymentId -> Future.join(List.of(vertx.deployVerticle(HueLookupRequestHandler.class.getName()),
						vertx.deployVerticle(HueDiscoverAndStatusRequestHandler.class.getName()),
						vertx.deployVerticle(HueDeviceInclusionHandler.class.getName()),
						vertx.deployVerticle(HueDeviceExclusionHandler.class.getName()),
						vertx.deployVerticle(HueControlRequestHandler.class.getName()),
						vertx.deployVerticle(HueStateEventHandler.class.getName()),
						vertx.deployVerticle(HuePluginStateRequestHandler.class.getName()),
						vertx.deployVerticle(HueConfigUpdateRequestHandler.class.getName()),
						vertx.deployVerticle(HueConfigTemplateRequestHandler.class.getName()),
						vertx.deployVerticle(HuePairingRequestHandler.class.getName()),
						vertx.deployVerticle(HueLightStateRequestHandler.class.getName()))))

				.onSuccess(future -> {
					logger.info("SYSTEM: All Verticles started successfully");
					Optional<HueBridge> optionalHueBridge = HuePersistenceHelper.getInstance().getHueBridge();
					PluginReadinessStatus status = PluginReadinessStatus.CONFIG_REQUIRED;
					if (optionalHueBridge.isPresent()) {
						status = PluginReadinessStatus.READY;
					}
					HuePluginWebsocketClient.sendPluginReadinessStatus(status, vertx);
				})
				.onFailure(throwable -> {
					logger.error("SYSTEM: Error starting Verticles", throwable);

					HuePluginWebsocketClient.sendPluginReadinessStatus(PluginReadinessStatus.ERROR, vertx);
				});

		logger.info("SYSTEM: Starting Philips Hue plugin...");

	}
}
