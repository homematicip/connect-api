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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.hue.discovery.mapping.HueDeviceConverter;
import de.eq3.plugin.hue.model.HueDeviceData;
import de.eq3.plugin.hue.model.HueResponse;
import de.eq3.plugin.hue.model.connectivity.DeviceConnectivity;
import de.eq3.plugin.hue.model.device.Device;
import de.eq3.plugin.hue.model.light.Light;
import io.vertx.core.Future;

public class HueConvertDevicesFunction
		implements Function<HueResponse, Future<Set<de.eq3.plugin.domain.device.Device>>> {

	private final Logger logger = LogManager.getLogger(this.getClass());

	@Override
	public Future<Set<de.eq3.plugin.domain.device.Device>> apply(HueResponse hueResponse) {

		return Future.future(promise -> {
			Set<Error> errors = hueResponse.getErrors();
			String concatenatedErrors = errors.stream().map(Error::toString).collect(Collectors.joining(", "));

			if (!errors.isEmpty()) {
				logger.warn("Errors in Philips Hue response: {}", concatenatedErrors);
			}
			Set<Device> deviceData = hueResponse.getDevices();
			Set<Light> lightData = hueResponse.getLights();
			Set<DeviceConnectivity> hueConnectivityData = hueResponse.getDeviceConnectivities();

			Map<String, HueDeviceData> deviceMap = new HashMap<>();

			deviceData.forEach(device -> {
				HueDeviceData data = new HueDeviceData();
				data.setDevice(device);
				deviceMap.put(device.getId(), data);
			});

			lightData.forEach(light -> {
				String deviceId = light.getOwner().getRid();
				HueDeviceData data = deviceMap.get(deviceId);
				if (data != null) {
					data.setLight(light);
				} else {
					logger.warn("Light service with rid {} could not be mapped to any device", light.getId());
				}
			});

			hueConnectivityData.forEach(deviceConnectivity -> {
				String deviceId = deviceConnectivity.getOwner().getRid();
				HueDeviceData data = deviceMap.get(deviceId);

				if (data != null) {
					data.setDeviceConnectivity(deviceConnectivity);
				} else {
					logger.warn("Connectivity service with rid {} could not be mapped to any device",
							deviceConnectivity.getId());
				}
			});

			Set<de.eq3.plugin.domain.device.Device> devices = deviceMap.values().stream()
					.filter(hueDeviceData ->
							hueDeviceData.getDevice() != null && hueDeviceData.getLight() != null
									&& hueDeviceData.getDeviceConnectivity() != null)
					.map(hueDeviceData -> HueDeviceConverter.getInstance().convert(hueDeviceData))
					.collect(Collectors.toSet());

			promise.complete(devices);
		});

	}
}
