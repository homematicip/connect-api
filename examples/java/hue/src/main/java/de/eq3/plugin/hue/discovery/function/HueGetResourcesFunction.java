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

package de.eq3.plugin.hue.discovery.function;

import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.hue.auth.HueLookupRequestHandler;
import de.eq3.plugin.hue.auth.model.HueBridge;
import de.eq3.plugin.hue.model.HueResponse;
import de.eq3.plugin.hue.model.connectivity.DeviceConnectivity;
import de.eq3.plugin.hue.model.device.Device;
import de.eq3.plugin.hue.model.light.Light;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

/**
 * Function to retrieve resources from a Hue bridge.
 */
public class HueGetResourcesFunction implements Function<HueBridge, Future<HueResponse>> {

	private final WebClient webClient;
	private final Logger logger = LogManager.getLogger(this.getClass());
	private final Vertx vertx;

	public HueGetResourcesFunction(WebClient webClient, Vertx vertx) {
		this.webClient = webClient;
		this.vertx = vertx;
	}

	/**
	 * Applies the function to retrieve resources from the specified Hue bridge.
	 *
	 * @param  bridge The Hue bridge to retrieve resources from.
	 * @return        A future containing the Hue response.
	 */
	@Override
	public Future<HueResponse> apply(HueBridge bridge) {
		return Future.future(promise -> {

			String host = bridge.getLocalAddress(); // lookup bridge ip
			String endpoint = "/clip/v2/resource";
			Future<String> address = HueLookupRequestHandler.getHueBridgeIp(bridge, host, vertx);

			address.onSuccess(url -> {
				this.webClient.get(url, endpoint)
						.putHeader("hue-application-key", bridge.getApplicationKey())
						.send(getResponse -> {

							if (getResponse.succeeded()) {

								if (getResponse.result().statusCode() == HttpResponseStatus.OK.code()) {

									JsonObject jsonResponse = getResponse.result().bodyAsJsonObject();
									HueResponse hueResponse = parseResponse(jsonResponse);

									promise.complete(hueResponse);
								} else {
									promise.fail("Unexpected response: Status=" + getResponse.result().statusCode()
											+ " - Body=" + getResponse.result().bodyAsString());
								}
							} else {
								promise.fail("No response data received");
							}
						});
			});

		});
	}

	/**
	 * Parses the response from the Hue bridge into a HueResponse object.
	 *
	 * @param  response The JSON response from the Hue bridge.
	 * @return          The parsed HueResponse object.
	 */
	private HueResponse parseResponse(JsonObject response) {
		HueResponse hueResponse = new HueResponse();

		JsonArray errors = response.getJsonArray("errors");
		errors.forEach(error -> {
			if (error instanceof JsonObject) {
				hueResponse.getErrors().add(((JsonObject) error).mapTo(Error.class));
			}
		});

		JsonArray data = response.getJsonArray("data");
		data.forEach(resource -> {
			if (resource instanceof JsonObject) {
				String type = ((JsonObject) resource).getString("type");

				switch (type) {
				case "device":
					hueResponse.getDevices().add(((JsonObject) resource).mapTo(Device.class));
					break;
				case "light":
					hueResponse.getLights().add(((JsonObject) resource).mapTo(Light.class));
					break;
				case "zigbee_connectivity":
					hueResponse.getDeviceConnectivities().add(((JsonObject) resource).mapTo(DeviceConnectivity.class));
					break;
				default:
					break;
				}
			}
		});
		return hueResponse;
	}
}
