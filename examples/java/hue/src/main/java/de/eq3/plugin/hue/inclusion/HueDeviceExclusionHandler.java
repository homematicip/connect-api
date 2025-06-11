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

package de.eq3.plugin.hue.inclusion;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.domain.inclusion.ExclusionEvent;
import de.eq3.plugin.hue.auth.model.HueBridge;
import de.eq3.plugin.hue.util.HuePersistenceHelper;
import de.eq3.plugin.serialization.PluginMessage;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

public class HueDeviceExclusionHandler extends AbstractVerticle implements Handler<Message<JsonObject>> {

	private final Logger logger = LogManager.getLogger(this.getClass());

	@Override
	public void start() {
		vertx.eventBus().consumer(ExclusionEvent.class.getName(), this);

		logger.info("SYSTEM: {} Verticle or Worker started", this.getClass().getSimpleName());
	}

	@Override
	public void handle(Message<JsonObject> message) {
		if (message == null || message.body() == null) {
			return;
		}
		PluginMessage<ExclusionEvent> exclusionEvent = message.body().mapTo(PluginMessage.class);

		Optional<HueBridge> optionalHueBridge = HuePersistenceHelper.getInstance().getHueBridge();

		optionalHueBridge.ifPresentOrElse(hueBridge -> {
					hueBridge.getIncludedDevices().removeAll(exclusionEvent.getBody().getDeviceIds());
					HuePersistenceHelper.getInstance().saveIncludedDevices(hueBridge.getIncludedDevices());

					logger.info("Bridge {}: Excluded devices [deviceIds={}]", hueBridge.getBridgeId(),
							exclusionEvent.getBody().getDeviceIds());
				},
				() -> logger.error("SYSTEM: No configured Hue Bridge available")
		);
	}
}
