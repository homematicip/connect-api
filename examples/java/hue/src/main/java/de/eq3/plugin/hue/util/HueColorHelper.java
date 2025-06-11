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

package de.eq3.plugin.hue.util;

import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;

import de.eq3.plugin.hue.model.light.Xy;

/**
 * Utility class for color conversions and calculations related to Philips Hue
 * color models.
 * <p>
 * Provides static methods to convert between color temperature (Kelvin/Mirek),
 * HSB/XY color spaces, and to apply gamma correction for accurate color
 * representation.
 * </p>
 * <p>
 * This class is not instantiable.
 * </p>
 */
public final class HueColorHelper {
	/**
	 * Private constructor to prevent instantiation.
	 */
	private HueColorHelper() {
		// Utility class
	}

	/**
	 * Converts a color temperature in Kelvin to Mirek, clamped to the given min and
	 * max.
	 *
	 * @param colorTemperatureKelvin the color temperature in Kelvin
	 * @param min                    the minimum Mirek value
	 * @param max                    the maximum Mirek value
	 * @return the Mirek value (reciprocal megakelvin)
	 */
	public static int getColorTemperatureMirek(int colorTemperatureKelvin, int min, int max) {
		int mirek = Math.round(1_000_000F / colorTemperatureKelvin);

		if (mirek < min) {
			mirek = min;
		}
		if (mirek > max) {
			mirek = max;
		}
		return mirek;
	}

	/**
	 * Converts a color temperature in Mirek to Kelvin.
	 *
	 * @param colorTemperatureMirek the color temperature in Mirek
	 * @return the color temperature in Kelvin
	 */
	public static int getColorTemperatureKelvin(int colorTemperatureMirek) {
		return Math.round(1_000_000F / colorTemperatureMirek);
	}

	/**
	 * Converts HSB (Hue, Saturation) values to CIE 1931 XY color space.
	 *
	 * @param hue        the hue value (0-360)
	 * @param saturation the saturation value (0.0-1.0)
	 * @return the XY color representation
	 */
	public static Xy getXYByHueSaturation(Integer hue, Double saturation) {
		// always convert color with full brightness
		Color color = Color.getHSBColor(hue.floatValue() / 360, saturation.floatValue(), 1);

		// 1. Get the RGB values from your color object and convert them to be between 0
		// and 1. So the RGB color (255, 0, 100) becomes (1.0, 0.0, 0.39)
		BigDecimal divisor = BigDecimal.valueOf(255);
		BigDecimal red = BigDecimal.valueOf(color.getRed()).divide(divisor, 4, RoundingMode.HALF_UP);
		BigDecimal green = BigDecimal.valueOf(color.getGreen()).divide(divisor, 4, RoundingMode.HALF_UP);
		BigDecimal blue = BigDecimal.valueOf(color.getBlue()).divide(divisor, 4, RoundingMode.HALF_UP);

		// 2. Apply a gamma correction to the RGB values, which makes the color more
		// vivid and more the like the color displayed on the screen of your device.
		red = applyGammaCorrection(red);
		green = applyGammaCorrection(green);
		blue = applyGammaCorrection(blue);

		BigDecimal hundred = BigDecimal.valueOf(100);
		red = red.multiply(hundred);
		green = green.multiply(hundred);
		blue = blue.multiply(hundred);

		// 3. Convert the RGB values to XYZ using the Wide RGB D65 conversion formula
		BigDecimal X = red.multiply(BigDecimal.valueOf(0.4124)).add(green.multiply(BigDecimal.valueOf(0.3576))).add(
				blue.multiply(BigDecimal.valueOf(0.1805)));

		BigDecimal Y = red.multiply(BigDecimal.valueOf(0.2126)).add(green.multiply(BigDecimal.valueOf(0.7152))).add(
				blue.multiply(BigDecimal.valueOf(0.0722)));

		BigDecimal Z = red.multiply(BigDecimal.valueOf(0.0193)).add(green.multiply(BigDecimal.valueOf(0.1192))).add(
				blue.multiply(BigDecimal.valueOf(0.9505)));

		// 4. Calculate the xy values from the XYZ values
		BigDecimal XYZ = X.add(Y).add(Z);
		BigDecimal x = X.divide(XYZ, 4, RoundingMode.HALF_UP);
		BigDecimal y = Y.divide(XYZ, 4, RoundingMode.HALF_UP);

		return new Xy(x.doubleValue(), y.doubleValue());
	}

	/**
	 * Converts CIE 1931 XY color space values to HSB (Hue, Saturation).
	 *
	 * @param xy the XY color representation
	 * @return an array with hue (0-359) and saturation (0.0-1.0)
	 */
	public static double[] getHueSaturationByXY(Xy xy) {
		// always convert color with full brightness
		BigDecimal Y = BigDecimal.valueOf(100);
		BigDecimal x = BigDecimal.valueOf(xy.getX());
		BigDecimal y = BigDecimal.valueOf(xy.getY());
		BigDecimal z = BigDecimal.ONE.subtract(x).subtract(y);

		BigDecimal X = Y.divide(y, 4, RoundingMode.HALF_UP).multiply(x);
		BigDecimal Z = Y.divide(y, 4, RoundingMode.HALF_UP).multiply(z);

		BigDecimal hundred = BigDecimal.valueOf(100);
		X = X.divide(hundred, 4, RoundingMode.HALF_UP);
		Y = Y.divide(hundred, 4, RoundingMode.HALF_UP);
		Z = Z.divide(hundred, 4, RoundingMode.HALF_UP);

		BigDecimal r = X.multiply(BigDecimal.valueOf(3.2406)).add(Y.multiply(BigDecimal.valueOf(-1.5372))).add(
				Z.multiply(BigDecimal.valueOf(-0.4986)));

		BigDecimal g = X.multiply(BigDecimal.valueOf(-0.9689)).add(Y.multiply(BigDecimal.valueOf(1.8758))).add(
				Z.multiply(BigDecimal.valueOf(0.0415)));

		BigDecimal b = X.multiply(BigDecimal.valueOf(0.0557)).add(Y.multiply(BigDecimal.valueOf(-0.2040))).add(
				Z.multiply(BigDecimal.valueOf(1.0570)));

		BigDecimal[] rgb = recalculateOutOfRGBScale(new BigDecimal[] { r, g, b });

		r = applyReverseGammaCorrection(rgb[0]);
		g = applyReverseGammaCorrection(rgb[1]);
		b = applyReverseGammaCorrection(rgb[2]);

		rgb = recalculateOutOfRGBScale(new BigDecimal[] { r, g, b });
		r = rgb[0];
		g = rgb[1];
		b = rgb[2];

		BigDecimal multiplicand = BigDecimal.valueOf(255);
		r = r.multiply(multiplicand);
		g = g.multiply(multiplicand);
		b = b.multiply(multiplicand);

		float[] hsb = Color.RGBtoHSB(Math.max(Math.round(r.floatValue()), 0), Math.max(Math.round(g.floatValue()), 0),
				Math.max(Math.round(b.floatValue()), 0), null);

		multiplicand = BigDecimal.valueOf(360);
		BigDecimal hue = BigDecimal.valueOf(hsb[0]).multiply(multiplicand);
		double saturation = Math.round(hsb[1] * 200) / 200.0;

		return new double[] { Math.min(Math.round(hue.doubleValue()), 359), saturation };
	}

	/**
	 * Applies gamma correction to a color value for accurate color representation.
	 *
	 * @param color the color value (0.0-1.0)
	 * @return the gamma-corrected color value
	 */
	private static BigDecimal applyGammaCorrection(BigDecimal color) {
		BigDecimal correctedColor;
		if (color.doubleValue() > 0.04045) {
			correctedColor = BigDecimal.valueOf(Math.pow((color.doubleValue() + 0.055) / (1.0 + 0.055), 2.4)).setScale(
					4, RoundingMode.HALF_UP);
		} else {
			correctedColor = BigDecimal.valueOf(color.doubleValue() / 12.92).setScale(4, RoundingMode.HALF_UP);
		}
		return correctedColor;
	}

	/**
	 * Applies reverse gamma correction to a color value.
	 *
	 * @param color the gamma-corrected color value
	 * @return the original color value
	 */
	private static BigDecimal applyReverseGammaCorrection(BigDecimal color) {
		BigDecimal correctedColor;
		if (color.doubleValue() <= 0.0031308) {
			correctedColor = BigDecimal.valueOf(12.92 * color.doubleValue()).setScale(4, RoundingMode.HALF_UP);
		} else {
			correctedColor = BigDecimal.valueOf(1.0 + 0.055).multiply(
					BigDecimal.valueOf(Math.pow(color.doubleValue(), (1.0 / 2.4)))).subtract(
							BigDecimal.valueOf(0.055))
					.setScale(4, RoundingMode.HALF_UP);
		}
		return correctedColor;
	}

	/**
	 * Recalculates RGB values if any component is out of the [0,1] range, scaling
	 * them appropriately.
	 *
	 * @param rgb array of RGB values
	 * @return scaled RGB values within [0,1]
	 */
	private static BigDecimal[] recalculateOutOfRGBScale(BigDecimal[] rgb) {
		BigDecimal r = rgb[0];
		BigDecimal g = rgb[1];
		BigDecimal b = rgb[2];

		if (r.doubleValue() > b.doubleValue() && r.doubleValue() > g.doubleValue() && r.doubleValue() > 1.0) {
			// red is too big
			g = g.divide(r, 4, RoundingMode.HALF_UP);
			b = b.divide(r, 4, RoundingMode.HALF_UP);
			r = BigDecimal.ONE;
		} else if (g.doubleValue() > b.doubleValue() && g.doubleValue() > r.doubleValue() && g.doubleValue() > 1.0) {
			// green is too big
			r = r.divide(g, 4, RoundingMode.HALF_UP);
			b = b.divide(g, 4, RoundingMode.HALF_UP);
			g = BigDecimal.ONE;
		} else if (b.doubleValue() > r.doubleValue() && b.doubleValue() > g.doubleValue() && b.doubleValue() > 1.0) {
			// blue is too big
			r = r.divide(b, 4, RoundingMode.HALF_UP);
			g = g.divide(b, 4, RoundingMode.HALF_UP);
			b = BigDecimal.ONE;
		}
		return new BigDecimal[] { r, g, b };
	}
}
