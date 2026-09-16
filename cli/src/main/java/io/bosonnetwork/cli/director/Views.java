/*
 * Copyright (c) 2023 -      bosonnetwork.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.bosonnetwork.cli.director;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.bosonnetwork.cli.common.Output;
import io.bosonnetwork.cli.common.Formats;
import io.bosonnetwork.director.client.Device;
import io.bosonnetwork.director.client.NodeStatus;
import io.bosonnetwork.director.client.Profile;

/**
 * How both tools show what they share: profiles, devices and the node status. The JSON forms are
 * built field by field, so that the output scripts rely on is stated here rather than inferred from
 * the client's model classes.
 */
public final class Views {
	/** The table headers of a device list. */
	public static final List<String> DEVICE_HEADERS = List.of("DEVICE ID", "NAME", "APP", "LAST SEEN", "LAST ADDRESS");

	private Views() {
	}

	/**
	 * Returns the details of a profile.
	 *
	 * @param profile the profile
	 * @return the details, keyed by label
	 */
	public static Map<String, String> profile(Profile profile) {
		Map<String, String> rows = new LinkedHashMap<>();
		rows.put("User id", profile.getId().toBase58String());
		rows.put("Name", Formats.text(profile.getName()));
		rows.put("Email", Formats.text(profile.getEmail()));
		rows.put("Bio", Formats.text(profile.getBio()));
		rows.put("Avatar", Formats.text(profile.getAvatar()));
		rows.put("Plan", Formats.text(profile.getPlanName()));
		rows.put("Administrator", Formats.yesNo(profile.isAdmin()));
		rows.put("Passphrase", profile.isPassphraseProtected() ? "set" : "not set");
		rows.put("Created", Formats.time(profile.getCreatedAt()));
		rows.put("Updated", Formats.time(profile.getUpdatedAt()));
		return rows;
	}

	/**
	 * Returns the JSON of a profile.
	 *
	 * @param profile the profile
	 * @return the JSON object
	 */
	public static Map<String, Object> profileJson(Profile profile) {
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("id", profile.getId());
		json.put("name", profile.getName().orElse(null));
		json.put("email", profile.getEmail().orElse(null));
		json.put("bio", profile.getBio().orElse(null));
		json.put("avatar", profile.getAvatar().orElse(null));
		json.put("planName", profile.getPlanName());
		json.put("admin", profile.isAdmin());
		json.put("passphraseProtected", profile.isPassphraseProtected());
		json.put("createdAt", profile.getCreatedAt());
		json.put("updatedAt", profile.getUpdatedAt());
		return json;
	}

	/**
	 * Returns the table row of a device.
	 *
	 * @param device the device
	 * @return the row, matching {@link #DEVICE_HEADERS}
	 */
	public static List<String> deviceRow(Device device) {
		return List.of(device.getId().toBase58String(),
				Formats.text(device.getName()),
				Formats.text(device.getApp()),
				Formats.time(device.getLastSeen()),
				Formats.text(device.getLastAddress()));
	}

	/**
	 * Returns the details of a device.
	 *
	 * @param device the device
	 * @return the details, keyed by label
	 */
	public static Map<String, String> device(Device device) {
		Map<String, String> rows = new LinkedHashMap<>();
		rows.put("Device id", device.getId().toBase58String());
		rows.put("User id", device.getUserId().toBase58String());
		rows.put("Name", Formats.text(device.getName()));
		rows.put("App", Formats.text(device.getApp()));
		rows.put("Registered", Formats.time(device.getCreatedAt()));
		rows.put("Updated", Formats.time(device.getUpdatedAt()));
		rows.put("Last seen", Formats.time(device.getLastSeen()));
		rows.put("Last address", Formats.text(device.getLastAddress()));
		return rows;
	}

	/**
	 * Returns the JSON of a device.
	 *
	 * @param device the device
	 * @return the JSON object
	 */
	public static Map<String, Object> deviceJson(Device device) {
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("id", device.getId());
		json.put("userId", device.getUserId());
		json.put("name", device.getName());
		json.put("app", device.getApp());
		json.put("createdAt", device.getCreatedAt());
		json.put("updatedAt", device.getUpdatedAt());
		json.put("lastSeen", device.getLastSeen());
		json.put("lastAddress", device.getLastAddress().orElse(null));
		return json;
	}

	/**
	 * Writes the status of a node: its details, then its services.
	 *
	 * @param output the output
	 * @param status the status
	 */
	public static void nodeStatus(Output output, NodeStatus status) {
		Map<String, String> rows = new LinkedHashMap<>();
		rows.put("Node id", status.getNodeId().toBase58String());
		rows.put("Name", Formats.text(status.getName()));
		String software = status.getSoftware().orElse("") + " " + status.getVersion().orElse("");
		rows.put("Software", Formats.text(software.strip()));
		rows.put("Website", Formats.text(status.getWebsite()));
		rows.put("Contact", Formats.text(status.getContact()));
		rows.put("Running", status.isRunning() ?
				(status.getStartedAt() > 0 ? "yes, since " + Formats.time(status.getStartedAt()) : "yes") : "no");
		output.details(rows);

		output.blank();
		if (status.getServices().isEmpty()) {
			output.message("The node offers no services.");
			return;
		}

		output.message("Services:");
		output.table(List.of("SERVICE ID", "NAME", "PEER ID", "ENDPOINT"), status.getServices().stream()
				.map(service -> List.of(service.getServiceId(),
						Formats.text(service.getServiceName()),
						service.getPeerId().toBase58String(),
						Formats.text(service.getEndpoint())))
				.toList());
	}

	/**
	 * Returns the JSON of a node status.
	 *
	 * @param status the status
	 * @return the JSON object
	 */
	public static Map<String, Object> nodeStatusJson(NodeStatus status) {
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("nodeId", status.getNodeId());
		json.put("name", status.getName().orElse(null));
		json.put("software", status.getSoftware().orElse(null));
		json.put("version", status.getVersion().orElse(null));
		json.put("logo", status.getLogo().orElse(null));
		json.put("website", status.getWebsite().orElse(null));
		json.put("contact", status.getContact().orElse(null));
		json.put("running", status.isRunning());
		json.put("startedAt", status.getStartedAt());
		json.put("services", status.getServices().stream().map(service -> {
			Map<String, Object> s = new LinkedHashMap<>();
			s.put("serviceId", service.getServiceId());
			s.put("serviceName", service.getServiceName().orElse(null));
			s.put("peerId", service.getPeerId());
			s.put("endpoint", service.getEndpoint().orElse(null));
			return s;
		}).toList());
		return json;
	}
}
