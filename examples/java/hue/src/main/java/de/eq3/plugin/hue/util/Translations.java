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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Translations {
    private static final Logger logger = LogManager.getLogger(Translations.class);
    private static Properties de = loadPropertiesFromPath("translations/intl_de.properties");
    private static Properties en = loadPropertiesFromPath("translations/intl_en.properties");

    private Translations() {
    }

    private static Properties loadPropertiesFromPath(String path) {
        Properties properties = new Properties();
        try (InputStream fis = Translations.class.getClassLoader().getResourceAsStream(path)) {
            if (fis != null) {
                properties.load(fis);
                properties.forEach((key, value) -> System.getProperties().putIfAbsent(key, value));
            }
        } catch (IOException e) {
            logger.error("Could not load {}", path);
        }
        return properties;
    }

    public static String get(String languageCode, String identifier) {
        if (languageCode == null)
            languageCode = "";

        switch (languageCode) {
            case ("de"):
                return formatToUtf8(de.getProperty(identifier, getFallback(identifier)));
            case ("en"):
                return formatToUtf8(en.getProperty(identifier, getFallback(identifier)));
            default:
                logger.warn("Unknown language code {}. Returning fallback language", languageCode);
                return getFallback(identifier);
        }
    }

    public static String getFallback(String identifier) {
        String fallbackString = en.getProperty(identifier);
        if (fallbackString == null) {
            logger.error("Undefined translation identifier {}", identifier);
            return "";
        }
        return formatToUtf8(fallbackString);
    }

    private static String formatToUtf8(String unformattedString) {
        byte[] byteString = unformattedString.getBytes(StandardCharsets.UTF_8);
        return new String(byteString, StandardCharsets.UTF_8);
    }
}
