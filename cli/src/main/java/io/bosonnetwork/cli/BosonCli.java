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

package io.bosonnetwork.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

import io.bosonnetwork.cli.commands.DeviceCommand;
import io.bosonnetwork.cli.commands.NodeCommand;
import io.bosonnetwork.cli.commands.UserCommand;
import io.bosonnetwork.cli.commands.UtilCommand;
import io.bosonnetwork.cli.director.ConfigCommand;
import io.bosonnetwork.cli.director.IdentityCommand;
import io.bosonnetwork.cli.common.CliApp;
import io.bosonnetwork.cli.director.DirectorApp;
import io.bosonnetwork.cli.common.CliEnvironment;
import io.bosonnetwork.cli.director.ToolSpec;

/**
 * {@code boson-cli}: the Boson command line client. It acts as one user of a super node - registering
 * the user, and managing the profile, the passphrase and the devices - and works with Boson keys
 * offline.
 */
@Command(name = "boson-cli",
		description = {"The Boson command line client.",
				"Registers you with a Boson super node and manages your account and devices, and works with Boson keys offline.",
				"",
				"Getting started:",
				"  boson-cli config init --url https://node.example.com:9000",
				"  boson-cli identity create",
				"  boson-cli user register --name Alice"},
		subcommands = {UserCommand.class, DeviceCommand.class, NodeCommand.class, ConfigCommand.class,
				IdentityCommand.class, UtilCommand.class, HelpCommand.class})
public class BosonCli extends DirectorApp {
	/**
	 * Creates the tool.
	 *
	 * @param environment the environment it runs in
	 */
	public BosonCli(CliEnvironment environment) {
		super(environment);
	}

	@Override
	protected ToolSpec createTool(CliEnvironment environment) {
		return new ToolSpec("boson-cli",
				environment.userConfigDir().resolve("boson").resolve("client").resolve("boson.yaml"),
				"user.identity",
				"user",
				"Check that this user is registered with the node ('boson-cli user register'), and that the node id is this node's.");
	}

	/**
	 * Runs {@code boson-cli}.
	 *
	 * @param args the command line
	 */
	public static void main(String[] args) {
		CliApp.launch(args, BosonCli::new);
	}
}
