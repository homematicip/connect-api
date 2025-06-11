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

package de.eq3.plugin.hue.discovery.mapping;

import java.util.HashSet;
import java.util.Set;

import com.google.common.base.Converter;
import de.eq3.plugin.domain.features.IFeature;
import de.eq3.plugin.domain.features.Maintenance;
import de.eq3.plugin.hue.model.HueDeviceData;
import de.eq3.plugin.hue.model.connectivity.DeviceConnectivity;
import de.eq3.plugin.hue.model.device.Device;
import de.eq3.plugin.hue.model.light.Light;
import de.eq3.plugin.serialization.DeviceType;
import de.eq3.plugin.serialization.Feature;

public final class HueDeviceConverter extends Converter<HueDeviceData, de.eq3.plugin.domain.device.Device> {
	public static final String CONNECTED = "connected";
	private static final HueDeviceConverter instance = new HueDeviceConverter();

	private HueDeviceConverter() {
	}

	public static HueDeviceConverter getInstance() {
		return instance;
	}

	@Override
	protected de.eq3.plugin.domain.device.Device doForward(HueDeviceData hueDeviceData) {
		Device device = hueDeviceData.getDevice();

		Set<IFeature> features = parseFeatures(hueDeviceData);
		DeviceType deviceType = DeviceType.LIGHT;

		if (device.getProductData().getProductArchetype().equals("plug")) {
			deviceType = DeviceType.SWITCH;
			if(features.stream().anyMatch(feature -> feature.getType() == Feature.DIMMING)){
				deviceType = DeviceType.LIGHT;
			}
		}
		return new de.eq3.plugin.domain.device.Device(device.getId(), device.getProductData().getModelId(),
				device.getMetadata().getName(), device.getProductData().getSoftwareVersion(), deviceType,
				features);
	}

	private Set<IFeature> parseFeatures(HueDeviceData hueDeviceData) {
		Set<IFeature> features = new HashSet<>();

		DeviceConnectivity deviceConnectivity = hueDeviceData.getDeviceConnectivity();
		Maintenance maintenance = new Maintenance();
		maintenance.setUnreach(!CONNECTED.equals(deviceConnectivity.getStatus()));

		features.add(maintenance);

		Light light = hueDeviceData.getLight();
		features.addAll(FeatureConverter.getInstance().getSupportedFeatures(light));

		return features;
	}

	@Override
	protected HueDeviceData doBackward(de.eq3.plugin.domain.device.Device device) {
		// full backward conversion not needed
		return null;
	}
}
