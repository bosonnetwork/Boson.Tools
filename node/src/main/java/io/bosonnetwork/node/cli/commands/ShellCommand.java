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

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import io.bosonnetwork.NodeConfiguration;
import io.bosonnetwork.cli.common.CliCommand;
import io.bosonnetwork.cli.common.CliException;
import io.bosonnetwork.cli.common.ExitCode;
import io.bosonnetwork.kademlia.KadNode;
import io.bosonnetwork.node.cli.NodeOptions;
import io.bosonnetwork.node.cli.shell.Shell;
import io.bosonnetwork.node.cli.shell.ShellSession;
import io.bosonnetwork.utils.ApplicationLock;

/**
 * {@code boson-node shell}: an interactive shell on a node of its own, for exploring the DHT.
 */
@Command(name = "shell", description = {"Explore the DHT from an interactive shell.",
		"Starts a node of its own - it cannot attach to a node already running - and takes commands at a prompt. "
				+ "The node's logging goes to a file, so that it does not land on the prompt."})
public class ShellCommand extends CliCommand {
	private static final long STOP_TIMEOUT_SECONDS = 10;

	@Mixin
	NodeOptions options;

	@Override
	protected void run() throws Exception {
		Vertx vertx = NodeOptions.vertx();
		NodeConfiguration config = options.configuration(vertx);
		Path dataDir = config.dataDir();

		try (ApplicationLock lock = new ApplicationLock(dataDir.resolve("lock"))) {
			KadNode node = new KadNode(config);
			await(node.start());
			output().message("Boson node started.");
			output().message("Node id: " + node.getId());
			output().message("Type 'help' for the commands, 'exit' to leave.");

			try {
				ShellSession session = new ShellSession(node, output(), terminal());
				setExitCode(new Shell(session, app().environment().stdin(), app().environment().out()).run());
			} finally {
				stop(node);
			}
		} catch (IOException | IllegalStateException e) {
			throw new CliException(ExitCode.UNAVAILABLE,
					"Another node is already running with the data directory " + dataDir + ".",
					"Leave it running and use another data directory (--data-dir), or stop it first.");
		} finally {
			vertx.close();
		}
	}

	private void stop(KadNode node) {
		try {
			if (node.isRunning())
				node.stop().get(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (Exception e) {
			output().warning("The node did not stop cleanly: " + e.getMessage());
		}
	}
}
