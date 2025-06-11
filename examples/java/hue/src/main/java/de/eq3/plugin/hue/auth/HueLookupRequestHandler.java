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

package de.eq3.plugin.hue.auth;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.hue.auth.model.HueBridge;
import de.eq3.plugin.hue.auth.model.HueLookupRequest;
import de.eq3.plugin.hue.auth.model.HueLookupResponse;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import lombok.Getter;

/**
 * Handles requests for discovering Philips Hue bridges and resolving their IP
 * addresses via mDNS.
 * <p>
 * This Vert.x verticle listens for lookup requests on the event bus and uses
 * JmDNS to find Hue bridges
 * in the local network. It provides utility methods to match local addresses to
 * discovered bridges and
 * to resolve .local hostnames to IP addresses, with caching support.
 * </p>
 */
public class HueLookupRequestHandler extends AbstractVerticle implements Handler<Message<JsonObject>> {

	private static final Logger logger = LogManager.getLogger(HueLookupRequestHandler.class);
	private static final Pattern BRIDGE_ID_PATTERN = Pattern.compile("^([a-z0-9]+)(?:-(\\d+))?\\.local");
	private static final Pattern LOCAL_ONLY_ADDRESS_PATTERN = Pattern.compile("(.*).local");
	public static final String GET_HUE_BRIDGE_ENDPOINT = "get.hue.bridge.endpoint";
	private static final int RELEVANT_BRIDGE_ID_PART = 6;

	/**
	 * Starts the verticle and sets up event bus consumers for bridge lookup and IP
	 * resolution.
	 */
	@Override
	public void start() {
		vertx.eventBus().consumer(HueLookupRequest.ENDPOINT, this);
		logger.info("SYSTEM: {} Verticle or Worker started", this.getClass().getSimpleName());
		vertx.eventBus().consumer(GET_HUE_BRIDGE_ENDPOINT, (Handler<Message<String>>) event -> {
			String bridgeLocal = event.body();
			if (bridgeLocal == null) {
				event.fail(-1, "No bridge given");
				return;
			}
			handleGetIpRequestForLocalAddress(bridgeLocal).map(bridge -> {
				if (bridge.getIpv4() != null) {
					return bridge.getIpv4().getHostAddress();
				} else {
					return bridgeLocal;
				}
			}).onSuccess(event::reply).onFailure(err -> {
				logger.error("Could not get ip", err);
				event.fail(-2, "Could not get ip");
			});
		});
	}

	/**
	 * Handles a request to get the IP address for a local bridge address.
	 *
	 * @param localAddress The local address of the bridge (e.g.
	 *                     "hue-123456.local").
	 * @return a Future containing the resolved HueBridge
	 */
	public Future<HueBridge> handleGetIpRequestForLocalAddress(String localAddress) {
		Future<Set<HueBridge>> bridgeLookup = vertx.executeBlocking(promise -> {
			MDNSListener mdnsListener = new MDNSListener();
			getBridges(promise, mdnsListener);
		});
		return bridgeLookup.compose(bridges -> getBridgeForLocalAddress(localAddress, bridges));
	}

	/**
	 * Resolves the IP address for a Hue bridge, using cache if available.
	 *
	 * @param bridge the HueBridge object
	 * @param host   the hostname (may be .local)
	 * @param vertx  the Vert.x instance
	 * @return a Future containing the resolved IP address as String
	 */
	public static Future<String> getHueBridgeIp(HueBridge bridge, String host, Vertx vertx) {
		if (!host.endsWith(".local")) {
			return Future.succeededFuture(host);
		}
		if (bridge.getLastSuccessfullAddress() != null) {
			logger.debug("Using cached url");
			return Future.succeededFuture(bridge.getLastSuccessfullAddress());
		}
		logger.debug("Getting fresh url");
		return vertx.eventBus()
				.request(HueLookupRequestHandler.GET_HUE_BRIDGE_ENDPOINT, host)
				.map(t -> (String) t.body())
				.onFailure(err -> logger.error("Error getting Bridge IP {}", err.getMessage()));
	}

	/**
	 * Matches a local address to a Hue bridge from a set of discovered bridges.
	 *
	 * @param localAddress the local address to match
	 * @param bridges      the set of available bridges
	 * @return a Future containing the matched HueBridge
	 */
	public Future<HueBridge> getBridgeForLocalAddress(String localAddress, Set<HueBridge> bridges) {
		Matcher localDefaultMatcher = BRIDGE_ID_PATTERN.matcher(localAddress);
		if (localDefaultMatcher.find()) {
			String id = localDefaultMatcher.group(1);
			if (id.length() < RELEVANT_BRIDGE_ID_PART) {
				logger.debug("Bridge id too short");
				return Future.failedFuture("Bridge id too short");
			}
			id = id.substring(id.length() - RELEVANT_BRIDGE_ID_PART);
			for (HueBridge bridge : bridges) {
				logger.trace("Found bridge {} {}", bridge, id);
				if (bridge.getBridgeId() != null && bridge.getBridgeId().contains(id)) {
					logger.debug("Matched bridge {}", bridge);
					return Future.succeededFuture(bridge);
				}
			}
		}
		Matcher localCustomMatcher = LOCAL_ONLY_ADDRESS_PATTERN.matcher(localAddress);
		if (localCustomMatcher.find()) {
			for (HueBridge bridge : bridges) {
				if (bridge.getLocalAddress().equalsIgnoreCase(localAddress)) {
					logger.debug("Matched bridge {}", bridge);
					return Future.succeededFuture(bridge);
				}
			}
		}
		return Future.failedFuture("No bridge found in network");
	}

	/**
	 * Handles incoming messages for bridge lookup requests.
	 *
	 * @param message The incoming message
	 */
	@Override
	public void handle(Message<JsonObject> message) {
		Future<Set<HueBridge>> bridgeLookup = vertx.executeBlocking(promise -> {
			MDNSListener mdnsListener = new MDNSListener();
			getBridges(promise, mdnsListener);
		});
		bridgeLookup.onSuccess(hueBridges -> {
			HueLookupResponse lookupResponse = new HueLookupResponse(hueBridges);
			message.reply(JsonObject.mapFrom(lookupResponse));
		});
		bridgeLookup.onFailure(throwable -> {
			HueLookupResponse lookupResponse = new HueLookupResponse(Collections.emptySet());
			message.reply(JsonObject.mapFrom(lookupResponse));
		});
	}

	/**
	 * Discovers Hue bridges on the local network using mDNS.
	 *
	 * @param promise      the promise to complete with the discovered bridges
	 * @param mdnsListener the mDNS listener for discovering bridges
	 */
	private void getBridges(Promise<Set<HueBridge>> promise, MDNSListener mdnsListener) {
		InetAddress address;
		try {
			String networkInterface = System.getProperty("plugin.hue.networkInterface");
			address = networkInterface == null ? InetAddress.getLocalHost() : InetAddress.getByName(networkInterface);
		} catch (UnknownHostException e) {
			logger.error("Could not get local network interface {}", e.getMessage());
			promise.fail(e);
			return;
		}
		try (JmDNS jmdns = JmDNS.create(address)) {
			jmdns.addServiceListener("_hue._tcp.local.", mdnsListener);
			Thread.sleep(2000L);
			mdnsListener.getHueBridges().forEach((bridgeId, hueBridge) -> logger.info(hueBridge));
			promise.complete(new HashSet<>(mdnsListener.getHueBridges().values()));
		} catch (InterruptedException | IOException e) {
			promise.fail(e);
			logger.error("Error discovering Philips Hue Bridge(s) via mDNS", e);
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Listener for mDNS service events related to Hue bridges.
	 */
	private class MDNSListener implements ServiceListener {
		@Getter
		private final Map<String, HueBridge> hueBridges = new HashMap<>();

		@Override
		public void serviceAdded(ServiceEvent event) {
			// Not used
		}

		@Override
		public void serviceRemoved(ServiceEvent event) {
			// Not used
		}

		@Override
		public void serviceResolved(ServiceEvent event) {
			HueBridge hueBridge = new HueBridge(event.getInfo());
			hueBridges.put(hueBridge.getBridgeId(), hueBridge);
		}
	}
}
