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
import java.util.LinkedHashMap;
import java.util.Map;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import io.bosonnetwork.Id;
import io.bosonnetwork.cli.common.CliCommand;
import io.bosonnetwork.cli.common.CliException;
import io.bosonnetwork.cli.common.Keys;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.node.cli.NodeOptions;

/**
 * {@code boson-node id}: the id a configured node has on the network, without starting it.
 */
@Command(name = "id", description = {"Show the id of the node a configuration file describes.",
		"The id is the public key of the node's private key, so this needs no running node - and no network."})
public class IdCommand extends CliCommand {
	@Option(names = {"-c", "--config"}, paramLabel = "<file>",
			description = "The configuration file. Without it, the first of the default locations that exists.")
	Path configFile;

	@Override
	protected void run() {
		Path file = configFile;
		if (file != null && !Files.isRegularFile(file))
			throw CliException.config("The configuration file " + file + " does not exist.", null);

		if (file == null) {
			for (Path candidate : NodeOptions.defaultConfigFiles()) {
				if (Files.isRegularFile(candidate)) {
					file = candidate;
					break;
				}
			}
		}

		if (file == null)
			throw CliException.config("No node configuration found.",
					"Name one with --config, or create one with 'boson-node config init'.");

		Map<String, Object> config = NodeOptions.load(file);
		Object privateKey = config.get("privateKey");
		if (privateKey == null || privateKey.toString().isBlank())
			throw CliException.config("The configuration file " + file + " has no privateKey.",
					"A node without a key has no identity; 'boson-node config init' writes one.");

		Signature.KeyPair key = Keys.privateKey(privateKey.toString(), "privateKey in " + file);
		Id id = Id.of(key.publicKey().bytes());

		if (output().isJson()) {
			Map<String, Object> json = new LinkedHashMap<>();
			json.put("nodeId", id);
			json.put("configFile", file.toString());
			output().json(json);
			return;
		}

		output().message(id.toBase58String());
	}
}
