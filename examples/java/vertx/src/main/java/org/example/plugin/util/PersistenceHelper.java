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

package org.example.plugin.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.plugin.model.Bridge;
import org.example.plugin.model.Persistence;

import de.eq3.plugin.domain.device.Device;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

public final class PersistenceHelper {
	private static final Logger logger = LogManager.getLogger(PersistenceHelper.class);
	private static final String OLD_FILE_ENDING = ".OLD";
	private static final String NEW_FILE_ENDING = ".NEW";
	private static final String FILE_NAME = "/plugin.auth";
	private static final String PLUGIN_TEMPLATE_AUTH_FOLDER = "persistence.folder";
	private static final String TOKEN_PROPERTY = "websocket.token";
	private static final String DEFAULT_PATH = "/data";
	private static final PersistenceHelper PERSISTENCE_HELPER = new PersistenceHelper();
	private Vertx vertx;
	private Persistence persistence;

	private PersistenceHelper() {
	}

	public static PersistenceHelper getInstance() {
		return PERSISTENCE_HELPER;
	}

	public void init(Vertx vertx) {
		final String storagePath = getStoragePath();
		this.vertx = vertx;

		if (!vertx.fileSystem().existsBlocking(storagePath)) {
			vertx.fileSystem().mkdirsBlocking(storagePath);
		}
		load();
	}

	public String readToken() {
		String propertyToken = System.getProperty(TOKEN_PROPERTY, null);
		if (propertyToken != null) {
			return propertyToken;
		}
		return this.vertx.fileSystem().readFileBlocking("/TOKEN").toString().trim();
	}

	public Optional<Bridge> getBridge() {
		return Optional.ofNullable(this.persistence.getBridgeData());
	}

	public void deleteBridge() {
		this.persistence.setBridgeData(null);
		persist();
	}

	public void saveAuthData(String bridgeId, String applicationKey, String address) {
		Bridge bridge = this.persistence.getBridgeData();
		if (bridge == null) {
			bridge = new Bridge();
		}

		bridge.setBridgeId(bridgeId);
		bridge.setAddress(address);
		bridge.setApplicationKey(applicationKey);

		this.persistence.setBridgeData(bridge);
		this.persist();
		logger.debug("Saved auth data");
	}

	public void saveDeviceData(Set<Device> devices) {
		Bridge bridge = this.persistence.getBridgeData();

		if (bridge == null) {
			return;
		}
		bridge.setDevices(devices.stream().collect(Collectors.toMap(Device::getDeviceId, device -> device)));

		this.persist();
		logger.debug("Bridge {}: Saved device data", bridge.getBridgeId());
	}

	public void saveIncludedDevices(Set<String> deviceIds) {
		Bridge bridge = this.persistence.getBridgeData();

		if (bridge == null) {
			return;
		}
		bridge.setIncludedDevices(deviceIds);

		this.persist();
		logger.debug("Bridge {}: Saved included devices", bridge.getBridgeId());
	}

	private void persist() {
		final String storagePath = getStoragePath() + FILE_NAME;
		JsonObject updateMetaDataJson = JsonObject.mapFrom(this.persistence);
		Buffer metaDataBuffer = updateMetaDataJson.toBuffer();

		try {
			// Create new file
			this.vertx.fileSystem().writeFileBlocking(storagePath + NEW_FILE_ENDING, metaDataBuffer);

			// if current version exists, make it old
			if (this.vertx.fileSystem().existsBlocking(storagePath)) {
				Files.move(Paths.get(storagePath), Paths.get(storagePath + OLD_FILE_ENDING),
						StandardCopyOption.ATOMIC_MOVE);
			}

			// make new file to current file
			Files.move(Paths.get(storagePath + NEW_FILE_ENDING), Paths.get(storagePath),
					StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			logger.error("Could not save auth data {}", e.getMessage());
		}
	}

	private void load() {
		Buffer fileBuffer;
		try {
			final String storagePath = getStoragePath() + FILE_NAME;
			logger.debug("Loading data from {}", storagePath);
			if (this.vertx.fileSystem().existsBlocking(storagePath)) {
				fileBuffer = this.vertx.fileSystem().readFileBlocking(storagePath);
			} else {
				fileBuffer = this.vertx.fileSystem().readFileBlocking(storagePath + OLD_FILE_ENDING);
			}

			this.persistence = fileBuffer.toJsonObject().mapTo(Persistence.class);
		} catch (FileSystemException e) {
			logger.info("Could not read auth data, creating new map");
			this.persistence = new Persistence();
		} catch (IllegalArgumentException | DecodeException e) {
			logger.info("Could not parse auth data, creating new map");
			this.persistence = new Persistence();
		}
		logger.info("Template auth data successfully read from file");
	}

	private static String getStoragePath() {
		return System.getProperty(PLUGIN_TEMPLATE_AUTH_FOLDER, DEFAULT_PATH);
	}

}
