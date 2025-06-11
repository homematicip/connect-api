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

import de.eq3.plugin.hue.security.HueBridgeTLS;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;

public interface HueHttpClientConfiguration {

	int PING_AND_RECONNECT_INTERVAL_SECONDS = 20;

	default HttpClientOptions getHttpClientOptions() {
		HttpClientOptions options = new HttpClientOptions();
		options.setProtocolVersion(HttpVersion.HTTP_2);
		options.setUseAlpn(true);
		options.setSsl(true);
		options.setVerifyHost(false);
		options.setDefaultPort(443);
		options.setTrustAll(true);
		//options.setTrustStoreOptions(HueBridgeTLS.getJksOptions());

		options.setConnectTimeout(5000);
		options.setReadIdleTimeout(PING_AND_RECONNECT_INTERVAL_SECONDS + 10);

		return options;
	}
}
