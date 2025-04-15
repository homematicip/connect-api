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

package org.example.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.plugin.handler.ConfigTemplateRequestHandler;
import org.example.plugin.handler.ConfigUpdateRequestHandler;
import org.example.plugin.handler.ControlRequestHandler;
import org.example.plugin.handler.DeviceExclusionHandler;
import org.example.plugin.handler.DeviceInclusionHandler;
import org.example.plugin.handler.DiscoverRequestHandler;
import org.example.plugin.handler.PluginStateRequestHandler;
import org.example.plugin.handler.StatusRequestHandler;
import org.example.plugin.handler.UserMessageResponseHandler;
import org.example.plugin.util.PersistenceHelper;
import org.example.plugin.ws.PluginWebsocketClient;

import de.eq3.plugin.domain.plugin.PluginReadinessStatus;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class PluginStarter {
	private static final Logger logger = LogManager.getLogger(PluginStarter.class);

	public static final String PLUGIN_ID = "org.example.plugin.java";
	public static final String PLUGIN_NAME = "Plugin Example Java";

	public static void main(String[] args) throws IOException {

		try (InputStream fis = PluginStarter.class.getClassLoader().getResourceAsStream("plugin.properties")) {
			if (fis != null) {
				Properties properties = new Properties();
				properties.load(fis);
				properties.forEach((key, value) -> System.getProperties().putIfAbsent(key, value));
			}
		}

		Vertx vertx = Vertx.vertx();
		PersistenceHelper.getInstance().init(vertx);

		Future<String> wsClient = vertx.deployVerticle(PluginWebsocketClient.class.getName());

		wsClient.compose(
				deploymentId -> Future.join(List.of(vertx.deployVerticle(PluginStateRequestHandler.class.getName()),
						vertx.deployVerticle(ConfigTemplateRequestHandler.class.getName()),
						vertx.deployVerticle(ConfigUpdateRequestHandler.class.getName()),
						vertx.deployVerticle(DeviceInclusionHandler.class.getName()),
						vertx.deployVerticle(DeviceExclusionHandler.class.getName()),
						vertx.deployVerticle(ControlRequestHandler.class.getName()),
						vertx.deployVerticle(StatusRequestHandler.class.getName()),
						vertx.deployVerticle(UserMessageResponseHandler.class.getName()),
						vertx.deployVerticle(DiscoverRequestHandler.class.getName()))))

				.onSuccess(future -> {
					logger.info("SYSTEM: All Verticles started successfully");
					boolean readinessStatus;
					// check if the plugin is ready
					// if (myPlugin is ready)
					readinessStatus = true;

					PluginReadinessStatus status = PluginReadinessStatus.CONFIG_REQUIRED;
					if (readinessStatus) {
						status = PluginReadinessStatus.READY;
					}
					PluginWebsocketClient.sendPluginReadinessStatus(status, vertx);

				})
				.onFailure(throwable -> {
					logger.error("SYSTEM: Error starting Verticles", throwable);

					PluginWebsocketClient.sendPluginReadinessStatus(PluginReadinessStatus.ERROR, vertx);
				});

		logger.info("SYSTEM: Starting {} plugin...", PLUGIN_NAME);

	}
}
