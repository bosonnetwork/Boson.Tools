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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import io.bosonnetwork.Id;
import io.bosonnetwork.cli.support.CliCommand;
import io.bosonnetwork.cli.support.CliException;
import io.bosonnetwork.cli.support.CliGroup;
import io.bosonnetwork.cli.support.ExitCode;
import io.bosonnetwork.cli.support.Listing;
import io.bosonnetwork.cli.support.PageOptions;
import io.bosonnetwork.director.cli.AdminViews;
import io.bosonnetwork.director.cli.Arguments;
import io.bosonnetwork.director.client.DirectorAdmin;
import io.bosonnetwork.director.client.FederatedNode;
import io.bosonnetwork.director.client.FederatedNodeUpdate;
import io.bosonnetwork.director.client.FederatedService;
import io.bosonnetwork.director.client.FederationProposal;
import io.bosonnetwork.director.client.ProposalFilter;
import io.bosonnetwork.director.client.Sort;
import io.bosonnetwork.director.client.exceptions.ConflictException;
import io.bosonnetwork.director.client.exceptions.DirectorException;
import io.bosonnetwork.director.client.exceptions.ForbiddenException;
import io.bosonnetwork.director.client.exceptions.NotFoundException;
import io.bosonnetwork.director.client.exceptions.RateLimitException;
import io.bosonnetwork.web.PaginatedResult;

/**
 * The {@code federation} commands of {@code boson-director-cli}.
 */
@Command(name = "federation", description = {"Inspect and manage this node's federation with other super nodes.",
		"Federated nodes share services with each other. A node that does not federate answers every command "
				+ "here with an error."},
		subcommands = {FederationCommand.NodeGroup.class, FederationCommand.ServiceGroup.class,
				FederationCommand.ProposalGroup.class, FederationCommand.ProposeCommand.class})
public class FederationCommand extends CliGroup {

	@Command(name = "node", description = "The nodes this node has federated with.",
			subcommands = {NodeGroup.ListCommand.class, NodeGroup.ShowCommand.class, NodeGroup.UpdateCommand.class,
					NodeGroup.RemoveCommand.class})
	public static class NodeGroup extends CliGroup {
		@Command(name = "list", description = "List the federated nodes.")
		public static class ListCommand extends CliCommand {
			@Mixin
			PageOptions paging;

			@Override
			protected void run() throws Exception {
				DirectorAdmin admin = context().directorAdmin();
				PaginatedResult<FederatedNode> page = paging.fetch(context(), admin::listFederatedNodes);
				Listing.page(output(), page, paging, "nodes", AdminViews.FEDERATED_NODE_HEADERS, AdminViews::federatedNodeRow,
						AdminViews::federatedNodeJson, "This node has not federated with any node.");
			}
		}

		@Command(name = "show", description = "Show a federated node.")
		public static class ShowCommand extends CliCommand {
			@Parameters(paramLabel = "<node-id>", description = "The node.")
			Id nodeId;

			@Override
			protected void run() throws Exception {
				FederatedNode node = await(context().directorAdmin().getFederatedNode(nodeId))
						.orElseThrow(() -> notFederated(nodeId));
				if (output().isJson())
					output().json(AdminViews.federatedNodeJson(node));
				else
					output().details(AdminViews.federatedNode(node));
			}
		}

		@Command(name = "update", description = "Suspend or resume the federation with a node, or change what this node records about it.")
		public static class UpdateCommand extends CliCommand {
			@Parameters(paramLabel = "<node-id>", description = "The node.")
			Id nodeId;

			@ArgGroup(exclusive = true)
			State state;

			static class State {
				@Option(names = "--suspend", required = true, description = "Suspend the federation with the node.")
				boolean suspend;

				@Option(names = "--resume", required = true, description = "Resume the federation with the node.")
				boolean resume;
			}

			@Option(names = "--reputation", paramLabel = "<n>", description = "The reputation this node gives the node.")
			Integer reputation;

			@Option(names = "--description", paramLabel = "<text>", description = "This node's description of the node; an empty value clears it.")
			String description;

			@Override
			protected void run() throws Exception {
				FederatedNodeUpdate update = new FederatedNodeUpdate();
				List<String> changed = new ArrayList<>();
				if (state != null) {
					update.federated(state.resume);
					changed.add(state.resume ? "federation resumed" : "federation suspended");
				}
				if (reputation != null) {
					update.reputation(reputation);
					changed.add("reputation");
				}
				if (description != null) {
					update.description(Arguments.blankToNull(description));
					changed.add("description");
				}
				if (update.isEmpty())
					throw CliException.usage("Nothing to update.", "Pass --suspend, --resume, --reputation or --description.");

				try {
					await(context().directorAdmin().updateFederatedNode(nodeId, update));
				} catch (NotFoundException e) {
					throw notFederated(nodeId);
				}

				if (output().isJson())
					output().json(Map.of("nodeId", nodeId, "updated", changed));
				else
					output().message("Updated federated node " + nodeId + ": " + String.join(", ", changed) + ".");
			}
		}

		@Command(name = "remove", description = "Remove a node from this node's federation.")
		public static class RemoveCommand extends CliCommand {
			@Parameters(paramLabel = "<node-id>", description = "The node.")
			Id nodeId;

			@Option(names = {"-y", "--yes"}, description = "Do not ask for confirmation.")
			boolean yes;

			@Override
			protected void run() throws Exception {
				DirectorAdmin admin = context().directorAdmin();
				terminal().confirm("Remove node " + nodeId + " from the federation? It no longer shares services with this node.", yes);

				try {
					await(admin.removeFederatedNode(nodeId));
				} catch (NotFoundException e) {
					throw notFederated(nodeId);
				}

				if (output().isJson())
					output().json(Map.of("nodeId", nodeId, "removed", true));
				else
					output().message("Removed node " + nodeId + " from the federation.");
			}
		}
	}

	@Command(name = "service", description = "The services federated nodes share with this node.",
			subcommands = {ServiceGroup.ListCommand.class})
	public static class ServiceGroup extends CliGroup {
		@Command(name = "list", description = "List the services a federated node shares with this node.")
		public static class ListCommand extends CliCommand {
			@Parameters(paramLabel = "<node-id>", description = "The node.")
			Id nodeId;

			@Override
			protected void run() throws Exception {
				List<FederatedService> services = await(context().directorAdmin().listFederatedServices(nodeId));
				Listing.list(output(), services, AdminViews.FEDERATED_SERVICE_HEADERS, AdminViews::federatedServiceRow,
						AdminViews::federatedServiceJson, "Node " + nodeId + " shares no services with this node.");
			}
		}
	}

	@Command(name = "proposal", description = "The federation proposals this node has made and received.",
			subcommands = {ProposalGroup.ListCommand.class, ProposalGroup.ShowCommand.class, ProposalGroup.RemoveCommand.class})
	public static class ProposalGroup extends CliGroup {
		@Command(name = "list", description = "List the federation proposals.")
		public static class ListCommand extends CliCommand {
			@Mixin
			PageOptions paging;

			@Option(names = "--node", paramLabel = "<node-id>", description = "Only the proposals made to or by this node.")
			Id nodeId;

			@Option(names = "--role", paramLabel = "<role>",
					description = "Only the proposals this node made (offer) or received (answer).")
			FederationProposal.Role role;

			@Option(names = "--status", paramLabel = "<state>", split = ",",
					description = "Only the proposals in these states: created, proposed, accepted, declined or failed.")
			List<FederationProposal.Status> statuses = new ArrayList<>();

			@Option(names = "--sort", paramLabel = "<field>[:desc]", split = ",", converter = Arguments.SortConverter.class,
					description = "Order by id, nodeId, name, role, status, proposedAt, confirmedAt, createdAt or updatedAt; "
							+ "add :desc for the reverse order.")
			List<Sort> sort = new ArrayList<>();

			@Override
			protected void run() throws Exception {
				ProposalFilter filter = new ProposalFilter();
				if (nodeId != null)
					filter.nodeId(nodeId);
				if (role != null)
					filter.role(role);
				if (!statuses.isEmpty())
					filter.statuses(statuses.toArray(new FederationProposal.Status[0]));
				Sort[] keys = sort.toArray(new Sort[0]);

				DirectorAdmin admin = context().directorAdmin();
				PaginatedResult<FederationProposal> page = paging.fetch(context(),
						(p, size) -> admin.listFederationProposals(filter, p, size, keys));
				Listing.page(output(), page, paging, "proposals", AdminViews.PROPOSAL_HEADERS, AdminViews::proposalRow,
						AdminViews::proposalJson, "No proposals match.");
			}
		}

		@Command(name = "show", description = "Show a federation proposal.")
		public static class ShowCommand extends CliCommand {
			@Parameters(paramLabel = "<proposal-id>", description = "The proposal, as 'federation proposal list' shows it.")
			long proposalId;

			@Override
			protected void run() throws Exception {
				FederationProposal proposal = await(context().directorAdmin().getFederationProposal(proposalId))
						.orElseThrow(() -> noSuchProposal(proposalId));
				if (output().isJson())
					output().json(AdminViews.proposalJson(proposal));
				else
					output().details(AdminViews.proposal(proposal));
			}
		}

		@Command(name = "remove", description = "Remove a federation proposal from the record. A federation it established is not affected.")
		public static class RemoveCommand extends CliCommand {
			@Parameters(paramLabel = "<proposal-id>", description = "The proposal, as 'federation proposal list' shows it.")
			long proposalId;

			@Option(names = {"-y", "--yes"}, description = "Do not ask for confirmation.")
			boolean yes;

			@Override
			protected void run() throws Exception {
				DirectorAdmin admin = context().directorAdmin();
				terminal().confirm("Remove proposal " + proposalId + " from the record? A federation it established is not affected.", yes);

				try {
					await(admin.removeFederationProposal(proposalId));
				} catch (NotFoundException e) {
					throw noSuchProposal(proposalId);
				}

				if (output().isJson())
					output().json(Map.of("proposalId", proposalId, "removed", true));
				else
					output().message("Removed proposal " + proposalId + ".");
			}
		}
	}

	@Command(name = "propose", description = {"Propose federation to another super node.",
			"This node looks the other one up on the DHT, checks it, sends it the proposal, and waits for its answer. "
					+ "Every proposal is recorded; see 'federation proposal list'."})
	public static class ProposeCommand extends CliCommand {
		// What the Director answers when it cannot find or validate the node proposed to.
		private static final int UNPROCESSABLE = 422;

		@Parameters(paramLabel = "<node-id>", description = "The node to federate with.")
		Id nodeId;

		@Override
		protected void run() throws Exception {
			DirectorAdmin admin = context().directorAdmin();
			output().progress("Proposing federation to node " + nodeId + " (looking it up and waiting for its answer)...");

			Optional<FederatedNode> result;
			try {
				result = await(admin.proposeFederation(nodeId));
			} catch (ForbiddenException e) {
				throw new CliException(ExitCode.NOT_AUTHORIZED, "Node " + nodeId + " is on the blacklist.",
						"Remove it from the blacklist first with " + tool().command("blacklist remove " + nodeId) + ".");
			} catch (ConflictException e) {
				throw new CliException(ExitCode.CONFLICT, "This node is already federated with node " + nodeId + ".", null);
			} catch (RateLimitException e) {
				throw new CliException(ExitCode.UNAVAILABLE, "A proposal to node " + nodeId + " was made recently.",
						e.getRetryAfter() > 0 ? "Try again in " + e.getRetryAfter() + " seconds." : "Try again later.");
			} catch (DirectorException e) {
				if (e.getStatus() != UNPROCESSABLE)
					throw e;
				throw CliException.failed("Node " + nodeId + " could not be found on the DHT, or is not a super node that federates.",
						"Check the node id, and that the node is online.");
			}

			if (output().isJson()) {
				Map<String, Object> json = new LinkedHashMap<>();
				json.put("nodeId", nodeId);
				json.put("federated", result.isPresent());
				json.put("node", result.map(AdminViews::federatedNodeJson).orElse(null));
				output().json(json);
			} else if (result.isPresent()) {
				FederatedNode node = result.get();
				output().message("Federated with node " + nodeId + node.getName().map(name -> " (" + name + ")").orElse("") + ".");
			} else {
				output().message("The proposal did not federate the nodes.");
				output().message("See how it ended with " + tool().command("federation proposal list --node " + nodeId) + ".");
			}

			if (result.isEmpty())
				setExitCode(ExitCode.FAILED);
		}
	}

	private static CliException notFederated(Id nodeId) {
		return CliException.notFound("This node has not federated with node " + nodeId + ".",
				"List the federated nodes with 'boson-director-cli federation node list'.");
	}

	private static CliException noSuchProposal(long proposalId) {
		return CliException.notFound("There is no proposal " + proposalId + " on this node.",
				"List the proposals with 'boson-director-cli federation proposal list'.");
	}
}
