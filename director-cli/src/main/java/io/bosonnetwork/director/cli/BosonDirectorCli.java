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

package io.bosonnetwork.director.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

import io.bosonnetwork.cli.director.ConfigCommand;
import io.bosonnetwork.cli.director.IdentityCommand;
import io.bosonnetwork.cli.common.CliApp;
import io.bosonnetwork.cli.common.CliEnvironment;
import io.bosonnetwork.cli.director.DirectorApp;
import io.bosonnetwork.cli.director.ToolSpec;
import io.bosonnetwork.director.cli.commands.BlacklistCommand;
import io.bosonnetwork.director.cli.commands.DeviceCommand;
import io.bosonnetwork.director.cli.commands.FeatureCommand;
import io.bosonnetwork.director.cli.commands.FederationCommand;
import io.bosonnetwork.director.cli.commands.NodeCommand;
import io.bosonnetwork.director.cli.commands.PlanCommand;
import io.bosonnetwork.director.cli.commands.SubscriptionCommand;
import io.bosonnetwork.director.cli.commands.UserCommand;

/**
 * {@code boson-director-cli}: the administration tool of a Boson super node, on the Director's admin
 * API. It acts as the node's root user or another administrator.
 */
@Command(name = "boson-director-cli",
		description = {"The administration tool of a Boson super node.",
				"Manages users and devices, plans and their features, subscriptions, the blacklist and federation, "
						+ "through the Director's admin API. It acts as the node's root user or another administrator.",
				"",
				"The setup wizard writes the configuration of the account that runs it; 'boson-director-cli config show' "
						+ "shows it."},
		subcommands = {UserCommand.class, DeviceCommand.class, PlanCommand.class, FeatureCommand.class,
				SubscriptionCommand.class, BlacklistCommand.class, FederationCommand.class, NodeCommand.class,
				ConfigCommand.class, IdentityCommand.class, HelpCommand.class})
public class BosonDirectorCli extends DirectorApp {
	/**
	 * Creates the tool.
	 *
	 * @param environment the environment it runs in
	 */
	public BosonDirectorCli(CliEnvironment environment) {
		super(environment);
	}

	@Override
	protected ToolSpec createTool(CliEnvironment environment) {
		return new ToolSpec("boson-director-cli",
				environment.userConfigDir().resolve("boson").resolve("director-cli.yaml"),
				"admin.identity",
				"administrator",
				"Only the node's root user and its administrators can use the admin API. Check the identity with " +
						"'boson-director-cli identity show', and the node id against 'boson-director-cli node id'.");
	}

	/**
	 * Runs {@code boson-director-cli}.
	 *
	 * @param args the command line
	 */
	public static void main(String[] args) {
		CliApp.launch(args, BosonDirectorCli::new);
	}
}
