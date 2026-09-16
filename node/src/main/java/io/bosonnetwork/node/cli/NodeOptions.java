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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import picocli.CommandLine.Option;

import io.bosonnetwork.Id;
import io.bosonnetwork.NodeConfiguration;
import io.bosonnetwork.NodeInfo;
import io.bosonnetwork.cli.common.CliException;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.utils.FileUtils;

/**
 * The options that say which node a command works with: its configuration file, and the settings that
 * override it. Shared by the commands that start a node, so that {@code run} and {@code shell} take
 * the same options.
 */
public class NodeOptions {
	/** The name of a node configuration file. */
	public static final String CONFIG_FILE_NAME = "node.yaml";

	@Option(names = {"-c", "--config"}, paramLabel = "<file>",
			description = "The configuration file. Without it, the first of ./node.yaml, the user's "
					+ "boson/node.yaml and the system's boson/node.yaml that exists.")
	Path configFile;

	@Option(names = {"-4", "--host4"}, paramLabel = "<address>", description = "The IPv4 address to listen on.")
	String host4;

	@Option(names = {"-6", "--host6"}, paramLabel = "<address>", description = "The IPv6 address to listen on.")
	String host6;

	@Option(names = {"-p", "--port"}, paramLabel = "<port>", description = "The UDP port to listen on.")
	Integer port;

	@Option(names = {"-d", "--data-dir"}, paramLabel = "<dir>",
			description = "The directory for the node's data: its routing table, caches and database.")
	Path dataDir;

	@Option(names = {"-b", "--bootstrap"}, paramLabel = "<id:address:port>",
			description = "An entry point into the DHT. Repeat for more than one.")
	List<String> bootstraps = new ArrayList<>();

	@Option(names = "--developer-mode",
			description = "Take part in the DHT from a private address. For development only: a node on a "
					+ "private address cannot be reached by the network at large.")
	boolean developerMode;

	/**
	 * Returns the configuration file the command works with.
	 *
	 * @return the file named with {@code --config}, the first of the default locations that exists, or
	 *         {@code null} when there is none
	 */
	public Path configFile() {
		if (configFile != null)
			return configFile;

		for (Path candidate : defaultConfigFiles())
			if (Files.isRegularFile(candidate))
				return candidate;

		return null;
	}

	/**
	 * Returns where a configuration file is looked for, in order.
	 *
	 * @return the locations
	 */
	public static List<Path> defaultConfigFiles() {
		return List.of(Path.of(CONFIG_FILE_NAME).toAbsolutePath(),
				FileUtils.getUserConfigDir().resolve("boson").resolve(CONFIG_FILE_NAME),
				FileUtils.getSiteConfigDir().resolve("boson").resolve(CONFIG_FILE_NAME),
				FileUtils.getSystemConfigDir().resolve("boson").resolve(CONFIG_FILE_NAME));
	}

	/**
	 * Tells whether the configuration file was named on the command line.
	 *
	 * @return {@code true} if {@code --config} was given
	 */
	public boolean hasExplicitConfigFile() {
		return configFile != null;
	}

	/**
	 * Builds the configuration of the node: the file, then what the options override.
	 *
	 * @param vertx the Vert.x instance the node runs on
	 * @return the configuration
	 * @throws CliException if the file cannot be read, or an option is not valid
	 */
	public NodeConfiguration configuration(Vertx vertx) {
		NodeConfiguration.Builder builder = NodeConfiguration.builder();

		Path file = configFile();
		if (file != null)
			builder.fromMap(load(file));
		else if (configFile != null)
			throw CliException.config("The configuration file " + configFile + " does not exist.", null);

		if (host4 != null)
			builder.host4(host4);
		if (host6 != null)
			builder.host6(host6);
		if (port != null)
			builder.port(port);
		if (dataDir != null)
			builder.dataDir(dataDir);
		if (developerMode)
			builder.developerMode(true);

		for (String bootstrap : bootstraps)
			builder.addBootstrap(parseBootstrap(bootstrap));

		// A node without a key would have no identity at all; one generated here lasts as long as the
		// data directory it is written to.
		if (!builder.hasKeyPair())
			builder.generateKeyPair();

		builder.vertx(vertx);

		try {
			return builder.build();
		} catch (IllegalArgumentException | IllegalStateException e) {
			throw CliException.config("The node configuration is not usable: " + e.getMessage(), null);
		}
	}

	/**
	 * Reads a node configuration file.
	 *
	 * @param file the file
	 * @return the settings it holds
	 * @throws CliException if it cannot be read, or is not a configuration
	 */
	public static Map<String, Object> load(Path file) {
		try {
			Map<String, Object> map = Json.yamlMapper().readValue(file.toFile(), Json.mapType());
			return map != null ? map : Map.of();
		} catch (IOException e) {
			throw CliException.config("Cannot read the configuration file " + file + ": " + e.getMessage(),
					"A node configuration is YAML; " + "'boson-node config check " + file + "' says what is wrong with it.");
		}
	}

	/**
	 * Creates the Vert.x instance a node runs on.
	 *
	 * @return the Vert.x instance
	 */
	public static Vertx vertx() {
		return Vertx.vertx(new VertxOptions()
				.setEventLoopPoolSize(4)
				.setWorkerPoolSize(4)
				.setPreferNativeTransport(true));
	}

	/**
	 * Reads a bootstrap node: {@code <id>:<address>:<port>}, the address an IPv4 address, an IPv6
	 * address in brackets, or a host name.
	 *
	 * @param value the value
	 * @return the node
	 * @throws CliException if the value is not a bootstrap node
	 */
	public static NodeInfo parseBootstrap(String value) {
		String text = value.strip();
		int firstColon = text.indexOf(':');
		int lastColon = text.lastIndexOf(':');
		if (firstColon < 0 || firstColon == lastColon)
			throw invalidBootstrap(value, "it needs an id, an address and a port");

		String id = text.substring(0, firstColon);
		String address = text.substring(firstColon + 1, lastColon);
		String port = text.substring(lastColon + 1);

		// An IPv6 address is bracketed inside a bootstrap entry, as it would be in a URL.
		if (address.startsWith("[") && address.endsWith("]"))
			address = address.substring(1, address.length() - 1);

		Id nodeId;
		try {
			nodeId = Id.of(id);
		} catch (IllegalArgumentException e) {
			throw invalidBootstrap(value, "'" + id + "' is not a node id");
		}

		int portNumber;
		try {
			portNumber = Integer.parseInt(port);
			if (portNumber <= 0 || portNumber > 65535)
				throw new NumberFormatException();
		} catch (NumberFormatException e) {
			throw invalidBootstrap(value, "'" + port + "' is not a port");
		}

		if (address.isEmpty())
			throw invalidBootstrap(value, "it has no address");

		try {
			return NodeInfo.of(nodeId, address, portNumber);
		} catch (RuntimeException e) {
			throw invalidBootstrap(value, "'" + address + "' is not an address");
		}
	}

	private static CliException invalidBootstrap(String value, String problem) {
		return CliException.usage("The bootstrap node '" + value + "' is not valid: " + problem + ".",
				"Write it as <id>:<address>:<port>, such as "
						+ "2dLbPsaySh9EGWwpgreYiLEPG3NDhaojj7DBBfSsRr6k:203.0.113.5:39001.");
	}
}
