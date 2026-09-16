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

package io.bosonnetwork.cli.common;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import io.bosonnetwork.Id;
import io.bosonnetwork.cli.support.CliCommand;
import io.bosonnetwork.cli.support.CliException;
import io.bosonnetwork.cli.support.CliGroup;
import io.bosonnetwork.cli.support.ConfigFile;
import io.bosonnetwork.cli.support.ConnectAddress;
import io.bosonnetwork.cli.support.Formats;
import io.bosonnetwork.cli.support.GlobalOptions;
import io.bosonnetwork.cli.support.Settings;
import io.bosonnetwork.cli.support.Settings.Setting;
import io.bosonnetwork.cli.support.ToolSpec;

/**
 * The {@code config} commands, shared by both tools.
 */
@Command(name = "config", description = {"Show and change the configuration.",
		"The configuration names the Director to talk to and the identity to act with. Command-line options "
				+ "and BOSON_* environment variables override it."},
		subcommands = {ConfigCommand.InitCommand.class, ConfigCommand.ShowCommand.class,
				ConfigCommand.SetCommand.class, ConfigCommand.UnsetCommand.class})
public class ConfigCommand extends CliGroup {
	// The settings 'config set' changes; privateKey is left to 'identity import', off the command line.
	private static final List<String> SETTABLE = List.of(ConfigFile.URL, ConfigFile.NODE_ID, ConfigFile.RESOLVE,
			ConfigFile.IDENTITY);

	@Command(name = "init", description = {"Create the configuration file.",
			"Writes the settings given with --url, --node-id and --resolve, with a comment explaining each setting. "
					+ "An existing file is never replaced."})
	public static class InitCommand extends CliCommand {
		@Override
		protected void run() {
			GlobalOptions options = context().options();
			Path path = Settings.configFileLocation(tool(), context().environment(), options);
			if (Files.exists(path))
				throw CliException.failed("The configuration file " + path + " already exists; it was not changed.",
						"Change a setting in it with " + tool().command("config set <key> <value>") + ".");

			String url = options.url() != null ? checkValue(tool(), ConfigFile.URL, options.url()) : null;
			String nodeId = options.nodeId() != null ? checkValue(tool(), ConfigFile.NODE_ID, options.nodeId()) : null;
			String resolve = options.resolve() != null ? checkValue(tool(), ConfigFile.RESOLVE, options.resolve()) : null;

			ConfigFile.create(path, ConfigFile.template(tool(), url, nodeId, resolve));

			if (output().isJson()) {
				Map<String, Object> json = new LinkedHashMap<>();
				json.put("configFile", path.toString());
				json.put("url", url);
				json.put("nodeId", nodeId);
				json.put("resolve", resolve);
				output().json(json);
				return;
			}

			output().message("Created " + path + ".");
			if (url == null)
				output().message("Next, set the Director URL with " + tool().command("config set url <url>") + ".");
		}
	}

	@Command(name = "show", description = "Show the settings in effect, and where each one comes from.")
	public static class ShowCommand extends CliCommand {
		@Override
		protected void run() {
			Settings settings = context().settings();

			Setting identity = null;
			String identityProblem = null;
			try {
				identity = settings.identity();
			} catch (CliException e) {
				identityProblem = e.getMessage();
			}
			Path identityFile = identity != null && identity.key().equals(ConfigFile.IDENTITY) ? settings.identityFile() : null;

			if (output().isJson()) {
				Map<String, Object> json = new LinkedHashMap<>();
				json.put("configFile", settings.configFile().toString());
				json.put("configFileExists", settings.configFileExists());
				json.put(ConfigFile.URL, settingJson(settings.url()));
				json.put(ConfigFile.NODE_ID, settingJson(settings.nodeId()));
				json.put(ConfigFile.RESOLVE, settingJson(settings.resolve()));
				if (identity != null) {
					Map<String, Object> id = new LinkedHashMap<>();
					if (identityFile != null) {
						id.put("file", identityFile.toString());
						id.put("exists", Files.exists(identityFile));
					} else {
						id.put("privateKey", "(hidden)");
					}
					id.put("from", from(identity));
					json.put("identity", id);
				} else {
					json.put("identity", Map.of("error", identityProblem));
				}
				output().json(json);
				return;
			}

			Map<String, String> file = new LinkedHashMap<>();
			file.put("Configuration file", settings.configFile() + (settings.configFileExists() ? "" : " (does not exist)"));
			output().details(file);
			output().blank();

			List<List<String>> rows = new ArrayList<>();
			rows.add(row(ConfigFile.URL, settings.url()));
			rows.add(row(ConfigFile.NODE_ID, settings.nodeId()));
			rows.add(row(ConfigFile.RESOLVE, settings.resolve()));
			if (identity == null)
				rows.add(List.of("identity", Formats.NONE, Formats.NONE));
			else if (identityFile != null)
				rows.add(List.of("identity", identityFile + (Files.exists(identityFile) ? "" : " (does not exist)"), from(identity)));
			else
				rows.add(List.of("identity", "private key (not shown)", from(identity)));
			output().table(List.of("SETTING", "VALUE", "FROM"), rows);

			if (identityProblem != null)
				output().warning(identityProblem);
		}

		private static List<String> row(String key, Setting setting) {
			return setting == null ? List.of(key, Formats.NONE, Formats.NONE) : List.of(key, setting.value(), from(setting));
		}

		private static Object settingJson(Setting setting) {
			if (setting == null)
				return null;
			Map<String, Object> json = new LinkedHashMap<>();
			json.put("value", setting.value());
			json.put("from", from(setting));
			return json;
		}

		private static String from(Setting setting) {
			return switch (setting.source()) {
				case OPTION, ENVIRONMENT -> setting.origin();
				case FILE -> "configuration file";
				case DEFAULT -> "default";
			};
		}
	}

	@Command(name = "set", description = {"Change a setting in the configuration file.",
			"Creates the file if it does not exist. Comments and the other settings are kept."})
	public static class SetCommand extends CliCommand {
		@Parameters(index = "0", paramLabel = "<key>", completionCandidates = SettableKeys.class,
				description = "The setting: ${COMPLETION-CANDIDATES}.")
		String key;

		@Parameters(index = "1", paramLabel = "<value>", description = "The new value.")
		String value;

		@Override
		protected void run() {
			checkKey(key, true);
			String normalized = checkValue(tool(), key, value);
			Path path = Settings.configFileLocation(tool(), context().environment(), context().options());

			ConfigFile.set(path, key, normalized, ConfigFile.header(tool()));

			if (output().isJson()) {
				Map<String, Object> json = new LinkedHashMap<>();
				json.put("configFile", path.toString());
				json.put("key", key);
				json.put("value", normalized);
				output().json(json);
				return;
			}

			output().message("Set " + key + " to " + normalized + " in " + path + ".");
			if (key.equals(ConfigFile.IDENTITY) && !Path.of(normalized).isAbsolute() && !normalized.startsWith("~"))
				output().message("A relative identity file is relative to the configuration file's directory.");
		}
	}

	@Command(name = "unset", description = "Remove a setting from the configuration file.")
	public static class UnsetCommand extends CliCommand {
		@Parameters(index = "0", paramLabel = "<key>", description = "The setting: url, nodeId, resolve, identity or privateKey.")
		String key;

		@Override
		protected void run() {
			checkKey(key, false);
			Path path = Settings.configFileLocation(tool(), context().environment(), context().options());
			boolean removed = ConfigFile.unset(path, key);

			if (output().isJson()) {
				Map<String, Object> json = new LinkedHashMap<>();
				json.put("configFile", path.toString());
				json.put("key", key);
				json.put("removed", removed);
				output().json(json);
				return;
			}

			output().message(removed ? "Removed " + key + " from " + path + "." : key + " is not set in " + path + ".");
		}
	}

	/**
	 * The keys 'config set' changes, for its help and shell completion.
	 */
	static class SettableKeys implements Iterable<String> {
		@Override
		public Iterator<String> iterator() {
			return SETTABLE.iterator();
		}
	}

	private static void checkKey(String key, boolean settable) {
		if (settable && key.equals(ConfigFile.PRIVATE_KEY))
			throw CliException.usage("privateKey cannot be set from the command line, where your shell would keep it in its history.",
					"Use 'identity import', which reads the key without showing it, or edit the file yourself.");

		if (ConfigFile.KEYS.contains(key))
			return;

		for (String known : ConfigFile.KEYS)
			if (known.equalsIgnoreCase(key))
				throw CliException.usage("Unknown setting '" + key + "'. Did you mean " + known + "?", null);

		throw CliException.usage("Unknown setting '" + key + "'.",
				"The settings are: " + String.join(", ", settable ? SETTABLE : ConfigFile.KEYS) + ".");
	}

	// Checks a value before it is written, so that a mistake is reported now rather than by every
	// command that reads it later. Returns the value to write.
	static String checkValue(ToolSpec tool, String key, String value) {
		String v = value.strip();
		switch (key) {
			case ConfigFile.URL -> {
				try {
					URL url = new URL(v);
					if ((!url.getProtocol().equals("http") && !url.getProtocol().equals("https")) || url.getHost().isEmpty())
						throw new MalformedURLException();
				} catch (MalformedURLException e) {
					throw CliException.usage("'" + value + "' is not an http or https URL.",
							"Use a URL such as https://node.example.com:9000.");
				}
				return v;
			}
			case ConfigFile.NODE_ID -> {
				try {
					return Id.of(v).toBase58String();
				} catch (IllegalArgumentException e) {
					throw CliException.usage("'" + value + "' is not a valid node id.",
							"Get the node's id with " + tool.command("node id") + ", and check it with the node's operator.");
				}
			}
			case ConfigFile.RESOLVE -> {
				try {
					ConnectAddress.parse(v, 1, "the command line");
				} catch (CliException e) {
					throw CliException.usage(e.getMessage(), e.getHint());
				}
				return v;
			}
			case ConfigFile.IDENTITY -> {
				if (v.isEmpty())
					throw CliException.usage("The identity file name is empty.", null);
				return v;
			}
			default -> throw new IllegalStateException("Unchecked setting " + key);
		}
	}
}
