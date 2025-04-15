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

package org.example.plugin.ws;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.plugin.PluginStarter;
import org.example.plugin.util.PersistenceHelper;

import de.eq3.plugin.Headers;
import de.eq3.plugin.domain.plugin.PluginReadinessStatus;
import de.eq3.plugin.domain.plugin.PluginStateResponse;
import de.eq3.plugin.serialization.PluginMessage;
import de.eq3.plugin.serialization.PluginMessageType;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.json.JsonObject;
import lombok.Getter;

public class PluginWebsocketClient extends AbstractVerticle {
	public static final long RECONNECT_DELAY = 20000L;
	private static final Logger logger = LogManager.getLogger(PluginWebsocketClient.class);
	@Getter
	private static String wsHandlerId;

	@Override
	public void start(Promise<Void> startPromise) {
		connect().onSuccess(startHandler -> {
			startPromise.complete();
			logger.info("SYSTEM: {} Verticle or Worker started", this.getClass().getSimpleName());
		});

		logger.info("SYSTEM: {} Verticle or Worker starting. Waiting for WS connection",
				this.getClass().getSimpleName());
	}

	private Future<Void> connect() {
		Promise<Void> success = Promise.promise();
		HttpClientOptions clientOptions = new HttpClientOptions().setTrustAll(true)
				.setVerifyHost(false)
				.setMaxWebSocketFrameSize(1_024_000)
				.setMaxWebSocketMessageSize(1_024_000);

		WebSocketConnectOptions connectOptions = new WebSocketConnectOptions().setRegisterWriteHandlers(true)
				.setHost(System.getProperty("websocket.host", "host.containers.internal"))
				.setPort(Integer.valueOf(System.getProperty("websocket.port", "9001")))
				.setSsl(true)
				.putHeader(Headers.PLUGIN_ID.toString(), PluginStarter.PLUGIN_ID)
				.putHeader("AUTHTOKEN", PersistenceHelper.getInstance().readToken());

		Future<WebSocket> wsConnection = this.vertx.createHttpClient(clientOptions).webSocket(connectOptions);

		wsConnection.onSuccess(webSocket -> {

			webSocket.closeHandler(aVoid -> {
				logger.info("Closed WebSocket - wsHandlerId: {}", webSocket.textHandlerID());

				setWsHandlerId(null);
				vertx.setTimer(RECONNECT_DELAY, timerId -> connect());
			});

			webSocket.exceptionHandler(throwable -> {
				logger.error("WebSocket exception - message: {}, wsHandlerId: {}", throwable.getMessage(),
						webSocket.textHandlerID());

				webSocket.close();
			});

			webSocket.handler(buffer -> {
				PluginMessage<?> message = buffer.toJsonObject().mapTo(PluginMessage.class);
				logger.debug("Received WS message {}", message);
				vertx.eventBus().send(message.getType().getMappingClazz().getName(), JsonObject.mapFrom(message));
			});

			setWsHandlerId(webSocket.textHandlerID());
			success.complete();
		});

		wsConnection.onFailure(throwable -> {
			setWsHandlerId(null);
			logger.error("Error opening websocket connection", throwable.fillInStackTrace());
			vertx.setTimer(RECONNECT_DELAY, timerId -> connect().onSuccess(handler -> success.complete()));
		});
		return success.future();
	}

	public static void setWsHandlerId(String wsHandlerId) {
		PluginWebsocketClient.wsHandlerId = wsHandlerId;
	}

	public static void sendPluginReadinessStatus(PluginReadinessStatus status, Vertx vertx) {
		Map<String, String> pluginNameMap = new HashMap<>();
		pluginNameMap.put("en", PluginStarter.PLUGIN_NAME);
		PluginStateResponse pluginState = new PluginStateResponse(pluginNameMap, status);

		PluginMessage<PluginStateResponse> pluginMessage = new PluginMessage<>(UUID.randomUUID().toString(),
				PluginStarter.PLUGIN_ID, PluginMessageType.PLUGIN_STATE_RESPONSE, pluginState);

		if (getWsHandlerId() != null) {
			vertx.eventBus().send(getWsHandlerId(), JsonObject.mapFrom(pluginMessage).encode());
		} else {
			logger.error("SYSTEM: Missing websocket connection to HCU1 system, cannot report plugin readiness state");
		}
	}
}
