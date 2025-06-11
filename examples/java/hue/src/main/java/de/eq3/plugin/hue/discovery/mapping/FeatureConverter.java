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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.domain.device.Device;
import de.eq3.plugin.domain.features.IFeature;
import de.eq3.plugin.domain.features.SwitchState;
import de.eq3.plugin.hue.auth.model.HueBridge;
import de.eq3.plugin.hue.control.messages.HueLightStateRequest;
import de.eq3.plugin.hue.model.light.Color;
import de.eq3.plugin.hue.model.light.ColorTemperature;
import de.eq3.plugin.hue.model.light.Dimming;
import de.eq3.plugin.hue.model.light.Light;
import de.eq3.plugin.hue.model.light.On;
import de.eq3.plugin.hue.model.light.Xy;
import de.eq3.plugin.hue.util.HueColorHelper;
import de.eq3.plugin.serialization.Feature;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class FeatureConverter {
	private static FeatureConverter instance;
	private final Logger logger = LogManager.getLogger(this.getClass());

	private FeatureConverter() {
	}

	public static FeatureConverter getInstance() {
		if (instance == null) {
			instance = setInstance();
		}
		return instance;
	}

	private static synchronized FeatureConverter setInstance() {
		if (instance == null) {
			instance = new FeatureConverter();
		}
		return instance;
	}

	public Light doForward(Set<IFeature> features) {
		Light light = new Light();

		features.forEach(feature -> {
			logger.debug(feature);

			if (feature instanceof SwitchState) {
				boolean switchState = ((SwitchState) feature).getOn();

				On on = new On();
				on.setOn(switchState);
				light.setOn(on);
			}

			if (feature instanceof de.eq3.plugin.domain.features.Dimming
					&& deriveDimLevel((de.eq3.plugin.domain.features.Dimming) feature) != null) {
				Dimming dimming = deriveDimLevel((de.eq3.plugin.domain.features.Dimming) feature);
				light.setDimming(dimming);
				light.setOn(new On(dimming.getBrightness() > 0));
			}

			if (feature instanceof de.eq3.plugin.domain.features.ColorTemperature
					&& deriveColorTemperature((de.eq3.plugin.domain.features.ColorTemperature) feature) != null) {
				light.setColorTemperature(
						deriveColorTemperature((de.eq3.plugin.domain.features.ColorTemperature) feature));
			}

			if (feature instanceof de.eq3.plugin.domain.features.Color
					&& deriveColor((de.eq3.plugin.domain.features.Color) feature) != null) {
				light.setColor(deriveColor((de.eq3.plugin.domain.features.Color) feature));
			}
		});

		return light;
	}

	private Dimming deriveDimLevel(de.eq3.plugin.domain.features.Dimming feature) {
		Double dimLevel = feature.getDimLevel();
		if (dimLevel >= 0.0 && dimLevel <= 1.0) {
			Dimming dimming = new Dimming();
			dimming.setBrightness(dimLevel * 100.0);
			return dimming;
		}
		return null;
	}

	private ColorTemperature deriveColorTemperature(de.eq3.plugin.domain.features.ColorTemperature feature) {
		Integer colorTemperatureValue = feature.getColorTemperature();

		if (colorTemperatureValue != de.eq3.plugin.domain.features.ColorTemperature.IGNORE_VALUE
				&& colorTemperatureValue != de.eq3.plugin.domain.features.ColorTemperature.LAST_VALUE) {
			Integer mirek = HueColorHelper.getColorTemperatureMirek(colorTemperatureValue, 153, 500);
			ColorTemperature colorTemperature = new ColorTemperature();
			colorTemperature.setMirek(mirek);
			return colorTemperature;
		}
		return null;
	}

	private Color deriveColor(de.eq3.plugin.domain.features.Color feature) {
		if (feature.getHue() != de.eq3.plugin.domain.features.Color.HUE_IGNORE_VALUE
				&& feature.getHue() != de.eq3.plugin.domain.features.Color.HUE_LAST_VALUE
				&& feature.getSaturationLevel() != de.eq3.plugin.domain.features.Color.SATURATION_IGNORE_VALUE
				&& feature.getSaturationLevel() != de.eq3.plugin.domain.features.Color.SATURATION_LAST_VALUE) {
			Xy xy = HueColorHelper.getXYByHueSaturation(feature.getHue(), feature.getSaturationLevel());

			Color colorXY = new Color();
			colorXY.setXy(xy);
			return colorXY;
		}
		return null;
	}

	public Set<IFeature> getSupportedFeatures(Light light) {
		// switching must be supported, otherwise the light is not valid
		if (light.getOn() == null) {
			return Collections.emptySet();
		}
		Boolean isOn = light.getOn().getOn();

		Set<IFeature> features = new HashSet<>();
		features.add(new SwitchState(isOn));
		features.addAll(mapRelevantFeatures(light, true));

		return features;
	}

	public Set<IFeature> doBackward(Light light) {
		// switching must be supported, otherwise the light is not valid
		if (light.getOn() == null) {
			return Collections.emptySet();
		}
		Boolean isOn = light.getOn().getOn();

		Set<IFeature> features = new HashSet<>();
		features.add(new SwitchState(isOn));
		features.addAll(mapRelevantFeatures(light, false));

		return features;
	}

	public Set<IFeature> mapChanges(Vertx vertx, Light light, HueBridge bridge) {
		Set<IFeature> changes = new HashSet<>();
		if (light.getOn() != null) {
			changes.add(new SwitchState(light.getOn().getOn()));

			// When the light has been turned on, the current light status should be requested to set the correct dim
			// level
			// Otherwise the dim level should be set to zero
			if (Boolean.TRUE.equals(light.getOn().getOn())) {
				changes.add(new SwitchState(light.getOn().getOn()));
				Device matchDevice = bridge.getPluginDevices().get(light.getOwner().getRid());
				if (matchDevice != null
						&& matchDevice.getFeatures().stream().anyMatch(f -> f.getType() == Feature.DIMMING)) {
					changes.add(matchDevice.getFeatures()
							.stream()
							.filter((f -> f.getType() == Feature.DIMMING))
							.findFirst()
							.orElseThrow());
				} else {
					HueLightStateRequest request = new HueLightStateRequest(light.getId());
					vertx.eventBus().send(HueLightStateRequest.ENDPOINT, JsonObject.mapFrom(request));
				}

			} else {
				de.eq3.plugin.domain.features.Dimming dimming = new de.eq3.plugin.domain.features.Dimming();
				dimming.setDimLevel(0.0);
				changes.add(dimming);
			}
		}

		if (light.getDimming() != null) {
			de.eq3.plugin.domain.features.Dimming dimming = new de.eq3.plugin.domain.features.Dimming();
			dimming.setDimLevel(light.getDimming().getBrightness() / 100.0);
			changes.add(dimming);
		}

		if (light.getColorTemperature() != null && Boolean.TRUE.equals(light.getColorTemperature().getMirekValid())
				&& light.getColorTemperature().getMirek() > 0) {

			int kelvin = HueColorHelper.getColorTemperatureKelvin(light.getColorTemperature().getMirek());
			changes.add(new de.eq3.plugin.domain.features.ColorTemperature(kelvin, null, null));
		}

		if (light.getColor() != null && (light.getColorTemperature() == null
				|| !Boolean.TRUE.equals(light.getColorTemperature().getMirekValid()))) {

			Xy xy = light.getColor().getXy();

			if (xy != null && xy.getY() != null && xy.getY() > 0) {
				double[] hsvByXy = HueColorHelper.getHueSaturationByXY(xy);

				changes.add(new de.eq3.plugin.domain.features.Color((int) hsvByXy[0], hsvByXy[1]));
			}
		}
		return changes;
	}

	public Set<IFeature> mapRelevantFeatures(Light light, boolean isInitial) {
		Set<IFeature> features = new HashSet<>();

		if (light.getDimming() != null) {
			features.add(new de.eq3.plugin.domain.features.Dimming(light.getDimming().getBrightness() / 100.0));
		}

		if (light.getColorTemperature() != null && (isInitial || light.getColorTemperature().getMirek() != null)) {
			Integer kelvin = null;
			if (Boolean.TRUE.equals(light.getColorTemperature().getMirekValid())) {
				kelvin = HueColorHelper.getColorTemperatureKelvin(light.getColorTemperature().getMirek());
			}

			Integer minKelvin = HueColorHelper
					.getColorTemperatureKelvin(light.getColorTemperature().getMirekSchema().getMirekMinimum());

			Integer maxKelvin = HueColorHelper
					.getColorTemperatureKelvin(light.getColorTemperature().getMirekSchema().getMirekMaximum());

			features.add(new de.eq3.plugin.domain.features.ColorTemperature(kelvin, minKelvin, maxKelvin));
		}

		if (light.getColor() != null) {
			Xy xy = light.getColor().getXy();

			de.eq3.plugin.domain.features.Color color = new de.eq3.plugin.domain.features.Color();
			if (xy != null && xy.getY() != null && xy.getY() > 0) {
				double[] hsvByXy = HueColorHelper.getHueSaturationByXY(xy);
				color.setHue((int) hsvByXy[0]);
				color.setSaturationLevel(hsvByXy[1]);
			}
			features.add(color);
		}
		return features;
	}
}
