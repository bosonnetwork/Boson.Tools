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

package io.bosonnetwork.node.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.bosonnetwork.Id;
import io.bosonnetwork.cli.common.CliException;
import io.bosonnetwork.cli.common.Formats;
import io.bosonnetwork.cli.common.Output;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.kademlia.routing.KBucketEntry;

/**
 * The routing table a node saves to its data directory, and writes back when it starts again: one
 * file per address family, {@code dht4.cache} and {@code dht6.cache}, in CBOR.
 * <p>
 * Read on its own, with no node running, so that an operator can see what a node knew - or what it
 * will start from - without starting it.
 */
public final class RoutingCache {
	/** The file a node saves its IPv4 routing table to. */
	public static final String IPV4_FILE = "dht4.cache";
	/** The file a node saves its IPv6 routing table to. */
	public static final String IPV6_FILE = "dht6.cache";

	/**
	 * A saved routing table.
	 *
	 * @param family       the address family, {@code IPv4} or {@code IPv6}
	 * @param file         the file it was read from
	 * @param nodeId       the id of the node that saved it
	 * @param timestamp    when it was saved, in epoch milliseconds
	 * @param entries      the nodes in the table
	 * @param replacements the nodes waiting to replace them
	 */
	public record Table(String family, Path file, Id nodeId, long timestamp, List<Map<String, Object>> entries,
			List<Map<String, Object>> replacements) {
	}

	private RoutingCache() {
	}

	/**
	 * Reads a saved routing table.
	 *
	 * @param file   the file
	 * @param family the address family it holds
	 * @return the table, or {@code null} if there is no such file
	 * @throws CliException if the file cannot be read, or is not a saved routing table
	 */
	public static Table read(Path file, String family) {
		if (!Files.isRegularFile(file))
			return null;

		try {
			JsonNode root = Json.cborMapper().readTree(file.toFile());
			Id nodeId = Id.of(root.get("nodeId").binaryValue());
			long timestamp = root.get("timestamp").asLong();
			return new Table(family, file, nodeId, timestamp, entries(root, "entries"), entries(root, "replacements"));
		} catch (IOException | RuntimeException e) {
			throw CliException.failed("Cannot read the routing table " + file + ": it is not a saved routing table.",
					"A node writes dht4.cache and dht6.cache in its data directory; check the directory.");
		}
	}

	private static List<Map<String, Object>> entries(JsonNode root, String field) {
		List<Map<String, Object>> entries = new ArrayList<>();
		JsonNode nodes = root.get(field);
		if (nodes == null)
			return entries;

		for (JsonNode node : nodes)
			entries.add(Json.cborMapper().convertValue(node, Json.mapType()));
		return entries;
	}

	/**
	 * Writes a saved routing table for people: when it was saved, and the nodes it holds.
	 *
	 * @param output the output
	 * @param table  the table
	 */
	public static void print(Output output, Table table) {
		Map<String, String> rows = new LinkedHashMap<>();
		rows.put("Node id", table.nodeId().toBase58String());
		rows.put("Saved", Formats.time(table.timestamp()) + " (" +
				Duration.ofMillis(System.currentTimeMillis() - table.timestamp()).toString() + " ago)");
		rows.put("Entries", Integer.toString(table.entries().size()));
		rows.put("Replacements", Integer.toString(table.replacements().size()));
		output.details(rows);

		print(output, "Entries", table.entries());
		print(output, "Replacements", table.replacements());
	}

	private static void print(Output output, String heading, List<Map<String, Object>> entries) {
		if (entries.isEmpty())
			return;

		output.blank();
		output.message(heading + ":");
		for (Map<String, Object> entry : entries)
			output.message("  " + KBucketEntry.fromMap(entry));
	}

	/**
	 * Returns a saved routing table as JSON, with the entries as the node wrote them.
	 *
	 * @param table the table
	 * @return the JSON object
	 */
	public static Map<String, Object> json(Table table) {
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("family", table.family());
		json.put("file", table.file().toString());
		json.put("nodeId", table.nodeId());
		json.put("savedAt", table.timestamp());
		json.put("entries", table.entries());
		json.put("replacements", table.replacements());
		return json;
	}
}
