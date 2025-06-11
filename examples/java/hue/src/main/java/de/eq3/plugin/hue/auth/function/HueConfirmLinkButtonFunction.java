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

package de.eq3.plugin.hue.auth.function;

import java.util.function.Function;

import de.eq3.plugin.hue.auth.model.HueConfirmLinkButtonRequest;
import de.eq3.plugin.hue.auth.model.HueConfirmLinkButtonResponse;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

public class HueConfirmLinkButtonFunction implements Function<String, Future<String>> {
	private final WebClient webClient;

	public HueConfirmLinkButtonFunction(WebClient webClient) {
		this.webClient = webClient;
	}

	@Override
	public Future<String> apply(String hostname) {
		return Future.future(promise -> {
			HueConfirmLinkButtonRequest confirmLinkButtonRequest = new HueConfirmLinkButtonRequest("homematic_ip");

			this.webClient.post(hostname, "/api")
					.sendJsonObject(JsonObject.mapFrom(confirmLinkButtonRequest), confirmResponse -> {

						if (confirmResponse.result() != null) {
							if (confirmResponse.result().statusCode() == HttpResponseStatus.OK.code()) {
								JsonArray jsonResponse = confirmResponse.result().bodyAsJsonArray();

								if (jsonResponse.size() >= 1) {
									JsonObject jsonObject = jsonResponse.getJsonObject(0);
									HueConfirmLinkButtonResponse confirmLinkButtonResponse = jsonObject
											.mapTo(HueConfirmLinkButtonResponse.class);

									HueConfirmLinkButtonResponse.NestedResponse successField = confirmLinkButtonResponse
											.getSuccess();
									if (successField != null && successField.getUsername() != null) {
										promise.complete(successField.getUsername());
									} else {
										promise.fail(confirmResponse.result().bodyAsString());
									}
								} else {
									promise.fail(confirmResponse.result().bodyAsString());
								}
							} else {
								promise.fail(confirmResponse.result().bodyAsString());
							}
						} else {
							promise.fail("No response data received");
						}
					});
		});
	}
}
