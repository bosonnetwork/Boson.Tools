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

package io.bosonnetwork.node.cli.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import io.bosonnetwork.NodeConfiguration;
import io.bosonnetwork.cli.common.CliCommand;
import io.bosonnetwork.cli.common.CliException;
import io.bosonnetwork.node.cli.NodeOptions;
import io.bosonnetwork.node.cli.RoutingCache;

/**
 * {@code boson-node cache}: the routing table a node saved, read without starting it.
 */
@Command(name = "cache", description = {"Show the routing table a node saved in its data directory.",
		"A node saves its routing table when it stops, and starts again from it. Reading it needs no running node."})
public class CacheCommand extends CliCommand {
	@Option(names = {"-d", "--data-dir"}, paramLabel = "<dir>",
			description = "The node's data directory. Without it, the one its configuration names.")
	Path dataDir;

	@Option(names = {"-c", "--config"}, paramLabel = "<file>",
			description = "The node's configuration file, which names the data directory.")
	Path configFile;

	@ArgGroup(exclusive = true)
	Family family;

	static class Family {
		@Option(names = {"-4", "--ipv4"}, required = true, description = "Only the IPv4 routing table.")
		boolean ipv4;

		@Option(names = {"-6", "--ipv6"}, required = true, description = "Only the IPv6 routing table.")
		boolean ipv6;
	}

	@Override
	protected void run() {
		Path directory = dataDirectory();

		List<RoutingCache.Table> tables = new ArrayList<>();
		if (family == null || family.ipv4)
			add(tables, directory.resolve(RoutingCache.IPV4_FILE), "IPv4");
		if (family == null || family.ipv6)
			add(tables, directory.resolve(RoutingCache.IPV6_FILE), "IPv6");

		if (tables.isEmpty())
			throw CliException.notFound("No saved routing table in " + directory + ".",
					"A node writes one when it stops. Check the data directory, or name another with --data-dir.");

		if (output().isJson()) {
			output().json(tables.stream().map(RoutingCache::json).toList());
			return;
		}

		for (int i = 0; i < tables.size(); i++) {
			RoutingCache.Table table = tables.get(i);
			if (i > 0)
				output().blank();
			output().message(table.family() + " routing table (" + table.file() + "):");
			RoutingCache.print(output(), table);
		}
	}

	private static void add(List<RoutingCache.Table> tables, Path file, String family) {
		RoutingCache.Table table = RoutingCache.read(file, family);
		if (table != null)
			tables.add(table);
	}

	// The directory given, the one the configuration names, or the node's default.
	private Path dataDirectory() {
		if (dataDir != null)
			return dataDir;

		Path file = configFile;
		if (file == null) {
			for (Path candidate : NodeOptions.defaultConfigFiles()) {
				if (Files.isRegularFile(candidate)) {
					file = candidate;
					break;
				}
			}
		} else if (!Files.isRegularFile(file)) {
			throw CliException.config("The configuration file " + file + " does not exist.", null);
		}

		if (file != null) {
			Map<String, Object> config = NodeOptions.load(file);
			Object configured = config.get("dataDir");
			if (configured != null && !configured.toString().isBlank())
				return expandHome(configured.toString());
		}

		return NodeConfiguration.Builder.defaultDataDir();
	}

	private static Path expandHome(String value) {
		if (value.startsWith("~/") || value.startsWith("~\\"))
			return Path.of(System.getProperty("user.home"), value.substring(2));
		return Path.of(value);
	}
}
