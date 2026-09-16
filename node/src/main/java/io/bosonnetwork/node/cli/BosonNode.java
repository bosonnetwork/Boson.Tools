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

import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

import io.bosonnetwork.cli.common.CliApp;
import io.bosonnetwork.cli.common.CliEnvironment;
import io.bosonnetwork.cli.common.ErrorTranslator;
import io.bosonnetwork.node.cli.commands.CacheCommand;
import io.bosonnetwork.node.cli.commands.ConfigCommand;
import io.bosonnetwork.node.cli.commands.IdCommand;
import io.bosonnetwork.node.cli.commands.RunCommand;
import io.bosonnetwork.node.cli.commands.SetupCommand;
import io.bosonnetwork.node.cli.commands.ShellCommand;

/**
 * {@code boson-node}: the command line tool of a Boson DHT node. It runs a node, configures one, and
 * explores the network with an interactive shell and offline inspectors.
 */
@Command(name = "boson-node",
		description = {"The Boson DHT node.",
				"Runs a node, writes and checks its configuration, and explores the DHT with an interactive shell.",
				"",
				"Getting started:",
				"  boson-node config init --output node.yaml",
				"  boson-node run -c node.yaml",
				"  boson-node shell -b <id>:<address>:<port>"},
		subcommands = {RunCommand.class, ShellCommand.class, SetupCommand.class, CacheCommand.class, IdCommand.class,
				ConfigCommand.class, HelpCommand.class})
public class BosonNode extends CliApp {
	/**
	 * Creates the tool.
	 *
	 * @param environment the environment it runs in
	 */
	public BosonNode(CliEnvironment environment) {
		super(environment);
	}

	@Override
	protected ErrorTranslator errorTranslator() {
		return new NodeErrors();
	}

	/**
	 * Runs {@code boson-node}.
	 *
	 * @param args the command line
	 */
	public static void main(String[] args) {
		// The node logs, and where that goes depends on the command: see NodeLogging.
		NodeLogging.configure(args);
		System.exit(new BosonNode(CliEnvironment.system()).execute(args));
	}
}
