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

package io.bosonnetwork.cli.support;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Signature;

/**
 * The settings a command runs with, each taken from the first place that has it: the command line,
 * then the environment, then the configuration file, then the default.
 * <p>
 * Nothing is checked until it is used, so that a command that does not need a setting is never
 * failed by it, and {@code config show} can display a configuration that does not work.
 */
public final class Settings {
	/** The environment variable naming the configuration file. */
	public static final String ENV_CONFIG = "BOSON_CONFIG";
	/** The environment variable holding the Director URL. */
	public static final String ENV_URL = "BOSON_DIRECTOR_URL";
	/** The environment variable holding the node id. */
	public static final String ENV_NODE_ID = "BOSON_NODE_ID";
	/** The environment variable holding the address to connect to. */
	public static final String ENV_RESOLVE = "BOSON_DIRECTOR_RESOLVE";
	/** The environment variable naming the identity file. */
	public static final String ENV_IDENTITY = "BOSON_IDENTITY";
	/** The environment variable holding the private key itself. */
	public static final String ENV_PRIVATE_KEY = "BOSON_PRIVATE_KEY";

	/**
	 * Where a setting comes from.
	 */
	public enum Source {
		/** A command-line option. */
		OPTION("command line"),
		/** An environment variable. */
		ENVIRONMENT("environment"),
		/** The configuration file. */
		FILE("configuration file"),
		/** The built-in default. */
		DEFAULT("default");

		private final String label;

		Source(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	/**
	 * One setting: its value, and where it comes from.
	 */
	public static final class Setting {
		private final String key;
		private final String value;
		private final Source source;
		private final String origin;

		Setting(String key, String value, Source source, String origin) {
			this.key = key;
			this.value = value;
			this.source = source;
			this.origin = origin;
		}

		/**
		 * Returns the setting's key: one of {@link ConfigFile#KEYS}, or {@code config} for the
		 * configuration file itself.
		 *
		 * @return the key
		 */
		public String key() {
			return key;
		}

		/**
		 * Returns the value.
		 *
		 * @return the value
		 */
		public String value() {
			return value;
		}

		/**
		 * Returns where the setting comes from.
		 *
		 * @return the source
		 */
		public Source source() {
			return source;
		}

		/**
		 * Names exactly where the setting comes from, for messages: the option, the variable, or the
		 * setting in the file.
		 *
		 * @return the origin, such as {@code --url}, {@code BOSON_DIRECTOR_URL} or
		 *         {@code url in /home/alice/.config/boson/client/boson.yaml}
		 */
		public String origin() {
			return origin;
		}
	}

	private final ToolSpec tool;
	private final CliEnvironment environment;
	private final GlobalOptions options;
	private final Setting configFile;
	private final ConfigFile config;
	private final Setting url;
	private final Setting nodeId;
	private final Setting resolve;

	private Settings(ToolSpec tool, CliEnvironment environment, GlobalOptions options, Setting configFile, ConfigFile config) {
		this.tool = tool;
		this.environment = environment;
		this.options = options;
		this.configFile = configFile;
		this.config = config;
		this.url = pick(ConfigFile.URL, options.url(), "--url", ENV_URL);
		this.nodeId = pick(ConfigFile.NODE_ID, options.nodeId(), "--node-id", ENV_NODE_ID);
		this.resolve = pick(ConfigFile.RESOLVE, options.resolve(), "--resolve", ENV_RESOLVE);
	}

	/**
	 * Resolves the settings of a command.
	 *
	 * @param tool        the tool
	 * @param environment the environment
	 * @param options     the global options given on the command line
	 * @return the settings
	 * @throws CliException if the configuration file cannot be read
	 */
	public static Settings resolve(ToolSpec tool, CliEnvironment environment, GlobalOptions options) {
		Setting configFile = configFileSetting(tool, environment, options);
		Path path = expandHome(configFile.value(), null);
		return new Settings(tool, environment, options, configFile, ConfigFile.load(path));
	}

	/**
	 * Returns where the configuration file is, without reading it: for the commands that create or
	 * edit it, which must work whatever it holds.
	 *
	 * @param tool        the tool
	 * @param environment the environment
	 * @param options     the global options given on the command line
	 * @return the file, which may not exist
	 */
	public static Path configFileLocation(ToolSpec tool, CliEnvironment environment, GlobalOptions options) {
		return expandHome(configFileSetting(tool, environment, options).value(), null);
	}

	private static Setting configFileSetting(ToolSpec tool, CliEnvironment environment, GlobalOptions options) {
		if (options.configFile() != null)
			return new Setting("config", options.configFile().toString(), Source.OPTION, "--config");
		if (environment.variable(ENV_CONFIG) != null)
			return new Setting("config", environment.variable(ENV_CONFIG), Source.ENVIRONMENT, ENV_CONFIG);
		return new Setting("config", tool.defaultConfigFile().toString(), Source.DEFAULT, "default");
	}

	private Setting pick(String key, String option, String optionName, String variable) {
		if (option != null && !option.isBlank())
			return new Setting(key, option.strip(), Source.OPTION, optionName);
		if (environment.variable(variable) != null)
			return new Setting(key, environment.variable(variable), Source.ENVIRONMENT, variable);
		if (config.get(key) != null)
			return new Setting(key, config.get(key), Source.FILE, key + " in " + config.path());
		return null;
	}

	/**
	 * Returns the configuration file setting.
	 *
	 * @return the setting
	 */
	public Setting configFileSetting() {
		return configFile;
	}

	/**
	 * Returns the configuration file.
	 *
	 * @return the file, which may not exist
	 */
	public Path configFile() {
		return config.path();
	}

	/**
	 * Tells whether the configuration file exists.
	 *
	 * @return {@code true} if it exists
	 */
	public boolean configFileExists() {
		return config.exists();
	}

	/**
	 * Returns the Director URL setting.
	 *
	 * @return the setting, or {@code null} if it is not set
	 */
	public Setting url() {
		return url;
	}

	/**
	 * Returns the node id setting.
	 *
	 * @return the setting, or {@code null} if it is not set
	 */
	public Setting nodeId() {
		return nodeId;
	}

	/**
	 * Returns the setting of the address to connect to.
	 *
	 * @return the setting, or {@code null} if it is not set
	 */
	public Setting resolve() {
		return resolve;
	}

	/**
	 * Returns the identity setting: an identity file ({@link ConfigFile#IDENTITY}), or the private key
	 * itself ({@link ConfigFile#PRIVATE_KEY}). Without either, the default identity file beside the
	 * configuration file.
	 *
	 * @return the setting
	 * @throws CliException if one place sets both an identity file and a private key
	 */
	public Setting identity() {
		if (options.identity() != null)
			return new Setting(ConfigFile.IDENTITY, options.identity().toString(), Source.OPTION, "--identity");

		String file = environment.variable(ENV_IDENTITY);
		String key = environment.variable(ENV_PRIVATE_KEY);
		if (file != null && key != null)
			throw CliException.config("Both " + ENV_IDENTITY + " and " + ENV_PRIVATE_KEY + " are set.",
					"Unset one of them.");
		if (key != null)
			return new Setting(ConfigFile.PRIVATE_KEY, key, Source.ENVIRONMENT, ENV_PRIVATE_KEY);
		if (file != null)
			return new Setting(ConfigFile.IDENTITY, file, Source.ENVIRONMENT, ENV_IDENTITY);

		file = config.get(ConfigFile.IDENTITY);
		key = config.get(ConfigFile.PRIVATE_KEY);
		if (file != null && key != null)
			throw CliException.config("The configuration file " + config.path() + " sets both identity and privateKey.",
					"Remove one of them.");
		if (key != null)
			return new Setting(ConfigFile.PRIVATE_KEY, key, Source.FILE, "privateKey in " + config.path());
		if (file != null)
			return new Setting(ConfigFile.IDENTITY, file, Source.FILE, "identity in " + config.path());

		return new Setting(ConfigFile.IDENTITY, configDirectory().resolve(tool.defaultIdentityFileName()).toString(),
				Source.DEFAULT, "default");
	}

	/**
	 * Returns the identity file.
	 *
	 * @return the file, or {@code null} if the identity is a private key given inline
	 * @throws CliException if one place sets both an identity file and a private key
	 */
	public Path identityFile() {
		Setting identity = identity();
		if (!identity.key().equals(ConfigFile.IDENTITY))
			return null;

		// A file named in the configuration is relative to it; one named on the command line or in the
		// environment, to the working directory.
		return expandHome(identity.value(), identity.source() == Source.FILE ? configDirectory() : null);
	}

	/**
	 * Returns the Director URL.
	 *
	 * @return the URL
	 * @throws CliException if none is configured, or it is not a valid http(s) URL
	 */
	public URL directorUrl() {
		if (url == null) {
			String hint = "Set it with " + tool.command("config set url https://node.example.com:9000") + ", or pass --url.";
			if (configFile.source() != Source.DEFAULT && !config.exists())
				throw CliException.config("No Director URL is configured: the configuration file " + config.path() +
						" (from " + configFile.origin() + ") does not exist.", hint);
			throw CliException.config("No Director URL is configured.", hint);
		}

		String hint = "Use a URL such as https://node.example.com:9000.";
		try {
			URL parsed = new URL(url.value());
			if (!parsed.getProtocol().equals("http") && !parsed.getProtocol().equals("https"))
				throw CliException.config("The Director URL '" + url.value() + "' (from " + url.origin() +
						") is not an http or https URL.", hint);
			if (parsed.getHost() == null || parsed.getHost().isEmpty())
				throw CliException.config("The Director URL '" + url.value() + "' (from " + url.origin() +
						") has no host.", hint);
			return parsed;
		} catch (MalformedURLException e) {
			throw CliException.config("The Director URL '" + url.value() + "' (from " + url.origin() +
					") is not a valid URL.", hint);
		}
	}

	/**
	 * Returns the node id.
	 *
	 * @return the node id, or {@code null} if it is not set
	 * @throws CliException if it is not a valid id
	 */
	public Id nodeIdValue() {
		if (nodeId == null)
			return null;

		try {
			return Id.of(nodeId.value());
		} catch (IllegalArgumentException e) {
			throw CliException.config("The node id '" + nodeId.value() + "' (from " + nodeId.origin() + ") is not a valid id.",
					"A node id is Base58, such as the one '" + tool.name() + " node id' prints.");
		}
	}

	/**
	 * Returns the address to connect to instead of looking up the host of a URL.
	 *
	 * @param directorUrl the Director URL
	 * @return the address, or {@code null} if it is not set
	 * @throws CliException if it is not an IP address, or the URL host is already an address
	 */
	public InetSocketAddress connectAddress(URL directorUrl) {
		if (resolve == null)
			return null;

		if (ConnectAddress.isAddress(directorUrl.getHost()))
			throw CliException.config("The resolve address (from " + resolve.origin() + ") needs a host name in the " +
					"Director URL, but " + directorUrl.getHost() + " is already an address.",
					"Remove the resolve setting, or use the Director's host name in the URL.");

		int port = directorUrl.getPort() > 0 ? directorUrl.getPort() : directorUrl.getDefaultPort();
		return ConnectAddress.parse(resolve.value(), port, resolve.origin());
	}

	/**
	 * Returns the key the tool acts with.
	 *
	 * @return the key pair
	 * @throws CliException if there is no identity, or it is not valid
	 */
	public Signature.KeyPair identityKey() {
		Setting identity = identity();
		if (identity.key().equals(ConfigFile.PRIVATE_KEY)) {
			try {
				return Keys.privateKey(identity.value(), "private key (from " + identity.origin() + ")");
			} catch (CliException e) {
				throw CliException.config(e.getMessage(), null);
			}
		}

		Path file = Objects.requireNonNull(identityFile());
		if (!Files.exists(file)) {
			String hint = "Create one with " + tool.command("identity create") + ", or import an existing private key with " +
					tool.command("identity import") + ".";
			if (identity.source() == Source.DEFAULT)
				throw CliException.config("No " + tool.identityRole() + " identity: " + file + " does not exist.", hint);
			throw CliException.config("The identity file " + file + " (from " + identity.origin() + ") does not exist.", hint);
		}

		return IdentityFile.read(file, "identity file");
	}

	/**
	 * Returns the key the tool acts with, if there is one: the default identity file may be missing.
	 *
	 * @return the key pair, or {@code null} if no identity is configured and the default file does not
	 *         exist
	 * @throws CliException if a configured identity is missing or not valid
	 */
	public Signature.KeyPair identityKeyIfConfigured() {
		Setting identity = identity();
		if (identity.source() == Source.DEFAULT && !Files.exists(Objects.requireNonNull(identityFile())))
			return null;
		return identityKey();
	}

	private Path configDirectory() {
		Path parent = config.path().toAbsolutePath().getParent();
		return parent != null ? parent : Path.of("").toAbsolutePath();
	}

	// Expands a leading ~ to the home directory, which a shell does not do inside a configuration
	// file or a quoted option, and resolves a relative path against a base directory.
	static Path expandHome(String value, Path base) {
		String home = System.getProperty("user.home");
		Path path;
		if (value.equals("~"))
			path = Path.of(home);
		else if (value.startsWith("~/") || value.startsWith("~\\"))
			path = Path.of(home, value.substring(2));
		else
			path = Path.of(value);

		if (base != null && !path.isAbsolute())
			path = base.resolve(path);
		return path.normalize();
	}
}
