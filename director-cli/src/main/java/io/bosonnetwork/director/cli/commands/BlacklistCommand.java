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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import io.bosonnetwork.cli.support.CliCommand;
import io.bosonnetwork.cli.support.CliException;
import io.bosonnetwork.cli.support.CliGroup;
import io.bosonnetwork.cli.support.ExitCode;
import io.bosonnetwork.cli.support.Listing;
import io.bosonnetwork.cli.support.PageOptions;
import io.bosonnetwork.director.cli.AdminViews;
import io.bosonnetwork.director.cli.Arguments;
import io.bosonnetwork.director.cli.Arguments.BlacklistTarget;
import io.bosonnetwork.director.client.BlacklistUpdate;
import io.bosonnetwork.director.client.BlacklistedNode;
import io.bosonnetwork.director.client.DirectorAdmin;
import io.bosonnetwork.director.client.exceptions.ConflictException;
import io.bosonnetwork.director.client.exceptions.NotFoundException;
import io.bosonnetwork.web.PaginatedResult;

/**
 * The {@code blacklist} commands of {@code boson-director-cli}.
 */
@Command(name = "blacklist", description = {"Manage the nodes and hosts this node refuses.",
		"An entry names a node by its id, or a host by its name or address. The Director adds automatic entries "
				+ "of its own, which it lifts after a while."},
		subcommands = {BlacklistCommand.ListCommand.class, BlacklistCommand.ShowCommand.class,
				BlacklistCommand.AddCommand.class, BlacklistCommand.UpdateCommand.class, BlacklistCommand.RemoveCommand.class})
public class BlacklistCommand extends CliGroup {
	private static final String ENTRY_DESCRIPTION =
			"The entry: a node id, a host, or an entry id as 'blacklist list' shows it.";
	private static final String HOST_DESCRIPTION =
			"Read the value as a host, even if it looks like a node id.";

	@Command(name = "list", description = "List the blacklist.")
	public static class ListCommand extends CliCommand {
		@Mixin
		PageOptions paging;

		@Override
		protected void run() throws Exception {
			DirectorAdmin admin = context().directorAdmin();
			PaginatedResult<BlacklistedNode> page = paging.fetch(context(), admin::listBlacklistedNodes);
			Listing.page(output(), page, paging, "entries", AdminViews.BLACKLIST_HEADERS, AdminViews::blacklistRow,
					AdminViews::blacklistJson, "The blacklist is empty.");
		}
	}

	@Command(name = "show", description = "Show a blacklist entry.")
	public static class ShowCommand extends CliCommand {
		@Parameters(paramLabel = "<entry>", description = ENTRY_DESCRIPTION)
		String entry;

		@Option(names = "--host", description = HOST_DESCRIPTION)
		boolean host;

		@Override
		protected void run() throws Exception {
			BlacklistTarget target = BlacklistTarget.of(entry, host, true);
			DirectorAdmin admin = context().directorAdmin();
			CompletableFuture<Optional<BlacklistedNode>> lookup = switch (target.kind()) {
				case ENTRY -> admin.getBlacklistedNode(target.entryId());
				case NODE -> admin.getBlacklistedNode(target.nodeId());
				case HOST -> admin.getBlacklistedHost(target.host());
			};

			BlacklistedNode found = await(lookup).orElseThrow(() -> notListed(target));
			if (output().isJson())
				output().json(AdminViews.blacklistJson(found));
			else
				output().details(AdminViews.blacklist(found));
		}
	}

	@Command(name = "add", description = "Add a node or a host to the blacklist.")
	public static class AddCommand extends CliCommand {
		@Parameters(paramLabel = "<node-or-host>", description = "The node's id, or the host's name or address.")
		String value;

		@Option(names = "--host", description = HOST_DESCRIPTION)
		boolean host;

		@Option(names = "--reason", paramLabel = "<text>", description = "Why it is blacklisted.")
		String reason;

		@Override
		protected void run() throws Exception {
			BlacklistTarget target = BlacklistTarget.of(value, host, false);
			DirectorAdmin admin = context().directorAdmin();
			String why = Arguments.blankToNull(reason);

			BlacklistedNode added;
			try {
				added = await(target.kind() == BlacklistTarget.Kind.NODE ?
						admin.addBlacklistedNode(target.nodeId(), why) :
						admin.addBlacklistedHost(target.host(), why));
			} catch (ConflictException e) {
				throw new CliException(ExitCode.CONFLICT, capitalize(target.toString()) + " is already on the blacklist.",
						"Show the entry with " + tool().command("blacklist show " + value) + ".");
			}

			if (output().isJson())
				output().json(AdminViews.blacklistJson(added));
			else
				output().message("Added " + target + " to the blacklist (entry " + added.getId() + ").");
		}
	}

	@Command(name = "update", description = "Change a blacklist entry's reason, or whether it is automatic.")
	public static class UpdateCommand extends CliCommand {
		@Parameters(paramLabel = "<entry>", description = ENTRY_DESCRIPTION)
		String entry;

		@Option(names = "--host", description = HOST_DESCRIPTION)
		boolean host;

		@Option(names = "--reason", paramLabel = "<text>", description = "Why it is blacklisted; an empty value clears it.")
		String reason;

		@ArgGroup(exclusive = true)
		Mode mode;

		static class Mode {
			@Option(names = "--auto", required = true, description = "Make the entry automatic, lifted after a while.")
			boolean auto;

			@Option(names = "--permanent", required = true, description = "Make the entry permanent.")
			boolean permanent;
		}

		@Override
		protected void run() throws Exception {
			BlacklistTarget target = BlacklistTarget.of(entry, host, true);

			BlacklistUpdate update = new BlacklistUpdate();
			List<String> changed = new ArrayList<>();
			if (reason != null) {
				update.reason(Arguments.blankToNull(reason));
				changed.add("reason");
			}
			if (mode != null) {
				update.auto(mode.auto);
				changed.add(mode.auto ? "automatic" : "permanent");
			}
			if (update.isEmpty())
				throw CliException.usage("Nothing to update.", "Pass --reason, --auto or --permanent.");

			DirectorAdmin admin = context().directorAdmin();
			try {
				await(switch (target.kind()) {
					case ENTRY -> admin.updateBlacklistedNode(target.entryId(), update);
					case NODE -> admin.updateBlacklistedNode(target.nodeId(), update);
					case HOST -> admin.updateBlacklistedHost(target.host(), update);
				});
			} catch (NotFoundException e) {
				throw notListed(target);
			}

			if (output().isJson())
				output().json(Map.of("entry", entry, "updated", changed));
			else
				output().message("Updated the blacklist entry of " + target + ": " + String.join(", ", changed) + ".");
		}
	}

	@Command(name = "remove", description = "Remove a node or a host from the blacklist.")
	public static class RemoveCommand extends CliCommand {
		@Parameters(paramLabel = "<entry>", description = ENTRY_DESCRIPTION)
		String entry;

		@Option(names = "--host", description = HOST_DESCRIPTION)
		boolean host;

		@Override
		protected void run() throws Exception {
			BlacklistTarget target = BlacklistTarget.of(entry, host, true);
			DirectorAdmin admin = context().directorAdmin();
			try {
				await(switch (target.kind()) {
					case ENTRY -> admin.removeBlacklistedNode(target.entryId());
					case NODE -> admin.removeBlacklistedNode(target.nodeId());
					case HOST -> admin.removeBlacklistedHost(target.host());
				});
			} catch (NotFoundException e) {
				throw notListed(target);
			}

			if (output().isJson())
				output().json(Map.of("entry", entry, "removed", true));
			else
				output().message("Removed " + target + " from the blacklist.");
		}
	}

	private static CliException notListed(BlacklistTarget target) {
		return CliException.notFound(capitalize(target.toString()) + " is not on the blacklist.",
				"List the blacklist with 'boson-director-cli blacklist list'.");
	}

	private static String capitalize(String text) {
		return Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}
}
