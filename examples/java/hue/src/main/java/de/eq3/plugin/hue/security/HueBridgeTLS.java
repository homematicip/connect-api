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

package de.eq3.plugin.hue.security;

import java.io.IOException;
import java.io.InputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.JksOptions;

public final class HueBridgeTLS {
	private static final Logger logger = LogManager.getLogger(HueBridgeTLS.class);

	private HueBridgeTLS() {
	}

	private static JksOptions jksOptions;

	public static synchronized JksOptions getJksOptions() {
		if (jksOptions == null) {
			try {
				jksOptions = createJksOptions();
			} catch (Exception e) {
				logger.error("Error initializing TLS options", e);
			}
		}
		return jksOptions;
	}

	private static JksOptions createJksOptions() throws IOException {

		ClassLoader loader = HueBridgeTLS.class.getClassLoader();

		try (InputStream certInputStream = loader.getResourceAsStream("keystore.jks")) {
			Buffer buff = Buffer.buffer(certInputStream.readAllBytes());
			jksOptions = new JksOptions().setValue(buff);
		}

		return jksOptions;
	}
}
