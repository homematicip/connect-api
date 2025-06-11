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

package de.eq3.plugin.hue.control.function;

import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.hue.auth.HueLookupRequestHandler;
import de.eq3.plugin.hue.auth.model.HueBridge;
import de.eq3.plugin.hue.model.light.Light;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

public class HueControlLightServiceFunction implements Function<HueBridge, Future<Void>> {

	private static final Logger logger = LogManager.getLogger(HueControlLightServiceFunction.class);

	private final WebClient webClient;
	private final String lightId;
	private final Light light;
	private final Vertx vertx;

	public HueControlLightServiceFunction(WebClient webClient, String lightId, Light light, Vertx vertx) {
		this.webClient = webClient;
		this.lightId = lightId;
		this.light = light;
		this.vertx = vertx;
	}

	@Override
	public Future<Void> apply(HueBridge bridge) {
		return Future.future(promise -> {

			String host = bridge.getLocalAddress();
			Future<String> address;
			String endpoint = "/clip/v2/resource/light/" + this.lightId;
			JsonObject body = JsonObject.mapFrom(this.light);
			logger.trace(body.toString());
			// Just ip or other DNS name
			address = HueLookupRequestHandler.getHueBridgeIp(bridge, host, vertx);

			address.onSuccess(url -> {
				this.webClient.put(url, endpoint)
						.putHeader("hue-application-key", bridge.getApplicationKey())
						.sendJsonObject(body, controlResponse -> {
							if (controlResponse.succeeded()) {
								if (controlResponse.result().statusCode() == HttpResponseStatus.OK.code()) {
									promise.complete();
								} else {
									promise.fail("Unexpected response: Status=" + controlResponse.result().statusCode()
											+ " - Body=" + controlResponse.result().bodyAsString());
								}
							} else {
								bridge.setLastSuccessfullAddress(null);
								promise.fail(controlResponse.cause());
							}
						});
			});

		});
	}
}
