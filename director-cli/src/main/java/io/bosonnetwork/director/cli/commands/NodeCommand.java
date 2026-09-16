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

package io.bosonnetwork.director.cli.commands;

import picocli.CommandLine.Command;

import io.bosonnetwork.Id;
import io.bosonnetwork.cli.support.CliCommand;
import io.bosonnetwork.cli.support.CliGroup;
import io.bosonnetwork.cli.support.Views;
import io.bosonnetwork.director.client.NodeStatus;

/**
 * The {@code node} commands of {@code boson-director-cli}.
 */
@Command(name = "node", description = "Show the super node's id and status.",
		subcommands = {NodeCommand.IdCommand.class, NodeCommand.StatusCommand.class})
public class NodeCommand extends CliGroup {

	@Command(name = "id", description = {"Show the super node's id, as its Director reports it. Needs no identity.",
			"Warns if it differs from the node id configured."})
	public static class IdCommand extends CliCommand {
		@Override
		protected void run() throws Exception {
			Id nodeId = await(context().anonymousDirectorAdmin().getNodeId());
			io.bosonnetwork.cli.commands.NodeCommand.printNodeId(context(), nodeId);
		}
	}

	@Command(name = "status", description = "Show what the super node runs and which services it offers.")
	public static class StatusCommand extends CliCommand {
		@Override
		protected void run() throws Exception {
			NodeStatus status = await(context().directorAdmin().getNodeStatus());
			if (output().isJson())
				output().json(Views.nodeStatusJson(status));
			else
				Views.nodeStatus(output(), status);
		}
	}
}
