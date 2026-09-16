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

package io.bosonnetwork.cli.commands;

import java.util.Map;

import picocli.CommandLine.Command;

import io.bosonnetwork.Id;
import io.bosonnetwork.cli.support.CliCommand;
import io.bosonnetwork.cli.support.CliContext;
import io.bosonnetwork.cli.support.CliGroup;
import io.bosonnetwork.cli.support.Settings;
import io.bosonnetwork.cli.support.Views;
import io.bosonnetwork.director.client.NodeStatus;

/**
 * The {@code node} commands of {@code boson-cli}: what the super node says about itself. They need no
 * identity.
 */
@Command(name = "node", description = "Show the super node's id and status. These need no identity.",
		subcommands = {NodeCommand.IdCommand.class, NodeCommand.StatusCommand.class})
public class NodeCommand extends CliGroup {

	@Command(name = "id", description = {"Show the super node's id, as its Director reports it.",
			"Warns if it differs from the node id configured."})
	public static class IdCommand extends CliCommand {
		@Override
		protected void run() throws Exception {
			Id nodeId = await(context().anonymousDirectorClient().getNodeId());
			printNodeId(context(), nodeId);
		}
	}

	@Command(name = "status", description = "Show what the super node runs and which services it offers.")
	public static class StatusCommand extends CliCommand {
		@Override
		protected void run() throws Exception {
			NodeStatus status = await(context().anonymousDirectorClient().getNodeStatus());
			if (output().isJson())
				output().json(Views.nodeStatusJson(status));
			else
				Views.nodeStatus(output(), status);
		}
	}

	/**
	 * Prints a node id the Director reported, warning if it is not the one configured.
	 *
	 * @param context the context of the run
	 * @param nodeId  the node id
	 */
	public static void printNodeId(CliContext context, Id nodeId) {
		Settings settings = context.settings();
		Id configured = settings.nodeIdValue();
		if (configured != null && !configured.equals(nodeId))
			context.output().warning("The Director reports the node id " + nodeId + ", but the node id configured is " +
					configured + " (from " + settings.nodeId().origin() + ").");

		if (context.output().isJson())
			context.output().json(Map.of("nodeId", nodeId));
		else
			context.output().message(nodeId.toBase58String());
	}
}
