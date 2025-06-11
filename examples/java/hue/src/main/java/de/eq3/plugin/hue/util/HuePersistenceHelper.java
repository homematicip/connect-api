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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.hue.auth.model.HueBridge;
import de.eq3.plugin.hue.auth.model.HuePersistence;
import de.eq3.plugin.hue.model.device.Device;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

/**
 * Utility class for managing persistence of Philips Hue bridge authentication
 * and device data.
 * <p>
 * Handles reading, writing, and updating authentication and device information
 * to the local file system, including migration and backup of persisted data.
 * Uses Vert.x for file operations and JSON serialization.
 * </p>
 * <p>
 * This class is a singleton and not intended to be instantiated directly.
 * </p>
 */
public final class HuePersistenceHelper {
	private static final Logger logger = LogManager.getLogger(HuePersistenceHelper.class);
	private static final String OLD_FILE_ENDING = ".OLD";
	private static final String NEW_FILE_ENDING = ".NEW";
	private static final String FILE_NAME = "/hue.auth";
	private static final String TOKEN_PROPERTY = "websocket.token";

	private static final HuePersistenceHelper HUE_PERSISTENCE_HELPER = new HuePersistenceHelper();

	private Vertx vertx;
	private HuePersistence persistence;

	private HuePersistenceHelper() {
	}

	/**
	 * Returns the singleton instance of the HuePersistenceHelper.
	 *
	 * @return the singleton instance
	 */
	public static HuePersistenceHelper getInstance() {
		return HUE_PERSISTENCE_HELPER;
	}

	/**
	 * Initializes the persistence helper with the given Vert.x instance and loads
	 * persisted data.
	 *
	 * @param vertx the Vert.x instance to use for file operations
	 */
	public void init(Vertx vertx) {
		final String storagePath = System.getProperty("plugin.hue.authFolder", "/data");
		this.vertx = vertx;

		if (!vertx.fileSystem().existsBlocking(storagePath)) {
			vertx.fileSystem().mkdirsBlocking(storagePath);
		}
		load();
	}

	/**
	 * Reads the websocket token from system properties or from the /TOKEN file.
	 *
	 * @return the websocket token as a String
	 */
	public String readToken() {
		String propertyToken = System.getProperty(TOKEN_PROPERTY, null);
		if (propertyToken != null) {
			return propertyToken;
		}
		return this.vertx.fileSystem().readFileBlocking("/TOKEN").toString().trim();
	}

	/**
	 * Returns the persisted HueBridge data, if available.
	 *
	 * @return an Optional containing the HueBridge, or empty if not set
	 */
	public Optional<HueBridge> getHueBridge() {
		return Optional.ofNullable(this.persistence.getBridgeData());
	}

	/**
	 * Deletes the persisted HueBridge data.
	 */
	public void deleteHueBridge() {
		this.persistence.setBridgeData(null);
		persist();
	}

	/**
	 * Saves authentication data for the Hue bridge.
	 *
	 * @param bridgeId       the bridge ID
	 * @param applicationKey the application key
	 * @param localAddress   the local IP address
	 */
	public void saveAuthData(String bridgeId, String applicationKey, String localAddress) {
		HueBridge bridge = this.persistence.getBridgeData();
		if (bridge == null) {
			bridge = new HueBridge();
		}

		bridge.setBridgeId(bridgeId);
		bridge.setLocalAddress(localAddress);
		bridge.setApplicationKey(applicationKey);

		this.persistence.setBridgeData(bridge);
		this.persist();
		logger.debug("Saved hue auth data");
	}

	/**
	 * Saves the set of devices associated with the Hue bridge.
	 *
	 * @param devices the set of devices to persist
	 */
	public void saveDeviceData(Set<Device> devices) {
		HueBridge bridge = this.persistence.getBridgeData();

		if (bridge == null) {
			return;
		}
		bridge.setDevices(devices.stream().collect(Collectors.toMap(Device::getId, device -> device)));

		this.persist();
		logger.debug("Bridge {}: Saved device data", bridge.getBridgeId());
	}

	/**
	 * Saves the set of included device IDs for the Hue bridge.
	 *
	 * @param deviceIds the set of included device IDs
	 */
	public void saveIncludedDevices(Set<String> deviceIds) {
		HueBridge bridge = this.persistence.getBridgeData();

		if (bridge == null) {
			return;
		}
		bridge.setIncludedDevices(deviceIds);

		this.persist();
		logger.debug("Bridge {}: Saved included devices", bridge.getBridgeId());
	}

	/**
	 * Persists the current state to disk, creating backups of previous versions.
	 */
	private void persist() {
		final String storagePath = System.getProperty("plugin.hue.authFolder", "/data") + FILE_NAME;
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
			logger.error("Could not save hue auth data {}", e.getMessage());
		}
	}

	/**
	 * Loads persisted data from disk, handling migration and error cases.
	 */
	private void load() {
		Buffer fileBuffer;

		try {
			final String storagePath = System.getProperty("plugin.hue.authFolder", "/data") + FILE_NAME;
			logger.debug("Loading data from {}", storagePath);
			if (this.vertx.fileSystem().existsBlocking(storagePath)) {
				fileBuffer = this.vertx.fileSystem().readFileBlocking(storagePath);
			} else {
				fileBuffer = this.vertx.fileSystem().readFileBlocking(storagePath + OLD_FILE_ENDING);
			}

			this.persistence = fileBuffer.toJsonObject().mapTo(HuePersistence.class);
		} catch (FileSystemException e) {
			logger.info("Could not read auth data, creating new map");
			this.persistence = new HuePersistence();
		} catch (IllegalArgumentException | DecodeException e) {
			logger.info("Could not parse auth data, creating new map");
			this.persistence = new HuePersistence();
		}
		logger.info("Hue auth data successfully read from file");
	}

}
