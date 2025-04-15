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

package org.example.plugin.function;

import de.eq3.plugin.domain.device.Device;
import de.eq3.plugin.domain.features.IFeature;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.plugin.model.Bridge;

import java.util.Set;
import java.util.function.Function;

public class ControlServiceFunction implements Function<Bridge, Future<Void>> {

    private final Logger logger = LogManager.getLogger(this.getClass());
    private final WebClient webClient;
    private final Device device;
    private final Set<IFeature> features;

    public ControlServiceFunction(WebClient webClient, Device device, Set<IFeature> features) {
        this.webClient = webClient;
        this.device = device;
        this.features = features;
    }

    @Override
    public Future<Void> apply(Bridge bridge) {
        return Future.future(promise -> {

            /*
            String host = bridge.getLocalAddress();
            String endpoint = "/api/to/call/" + this.lightId;
            */
            String host = "www.eq-3.de";
            String endpoint = "/";

            JsonObject body = JsonObject.mapFrom(device);
            logger.trace(body.toString());
            logger.trace(features);

            this.webClient.get(host, endpoint).putHeader("some-header-key", bridge.getApplicationKey())
                    .sendJsonObject(body, controlResponse -> {
                        logger.info("External URL called for {}", this.device.getDeviceId());

                        if (controlResponse.succeeded()) {
                            if (controlResponse.result().statusCode() == HttpResponseStatus.OK.code()) {
                                promise.complete();
                            } else {
                                promise.fail(
                                        "Unexpected response: Status=" + controlResponse.result().statusCode() + " - Body=" + controlResponse.result().bodyAsString());
                            }
                        } else {
                            promise.fail(controlResponse.cause());
                        }
                    });
        });
    }
}
