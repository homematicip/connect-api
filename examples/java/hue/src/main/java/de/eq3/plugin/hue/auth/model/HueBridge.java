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

package de.eq3.plugin.hue.auth.model;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.jmdns.ServiceInfo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.eq3.plugin.hue.model.device.Device;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HueBridge {
	private String bridgeId;
	private String localAddress;
	private String applicationKey;
	@JsonIgnore
	private Inet4Address ipv4;
	@JsonIgnore
	private Inet6Address ipv6;
	private String lastSuccessfullAddress;

	private Map<String, Device> devices = new HashMap<>();
	@JsonIgnore
	private final Map<String, de.eq3.plugin.domain.device.Device> pluginDevices = new HashMap<>();
	private Set<String> includedDevices = new HashSet<>();
	@JsonIgnore
	private Map<String, HueScheduledTestTimer> onTimeTaskQueue = new HashMap<>();

	public HueBridge(ServiceInfo service) {
		this.bridgeId = service.getPropertyString("bridgeid");
		this.localAddress = service.getServer();
		if (service.getInet4Addresses() != null && service.getInet4Addresses().length != 0) {
			this.ipv4 = service.getInet4Addresses()[0];
		}
		if (service.getInet6Addresses() != null && service.getInet6Addresses().length != 0) {
			this.ipv6 = service.getInet6Addresses()[0];
		}

		if (this.localAddress != null && this.localAddress.endsWith(".")) {
			this.localAddress = this.localAddress.substring(0, this.localAddress.length() - 1);
		}
	}
}
