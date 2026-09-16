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

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import io.bosonnetwork.Id;
import io.bosonnetwork.cli.director.DirectorCommand;
import io.bosonnetwork.cli.director.CliContext;
import io.bosonnetwork.cli.common.CliException;
import io.bosonnetwork.cli.common.CliGroup;
import io.bosonnetwork.cli.common.ExitCode;
import io.bosonnetwork.cli.common.Listing;
import io.bosonnetwork.cli.common.PageOptions;
import io.bosonnetwork.cli.director.Views;
import io.bosonnetwork.director.cli.AdminViews;
import io.bosonnetwork.director.cli.Arguments;
import io.bosonnetwork.director.client.DirectorAdmin;
import io.bosonnetwork.director.client.NewUser;
import io.bosonnetwork.director.client.Profile;
import io.bosonnetwork.director.client.Sort;
import io.bosonnetwork.director.client.UserUpdate;
import io.bosonnetwork.director.client.exceptions.ConflictException;
import io.bosonnetwork.director.client.exceptions.NotFoundException;
import io.bosonnetwork.web.PaginatedResult;

/**
 * The {@code user} commands of {@code boson-director-cli}.
 */
@Command(name = "user", description = "Manage the users of the node.",
		subcommands = {UserCommand.ListCommand.class, UserCommand.ShowCommand.class, UserCommand.AddCommand.class,
				UserCommand.UpdateCommand.class, UserCommand.RemoveCommand.class, UserCommand.GrantAdminCommand.class,
				UserCommand.RevokeAdminCommand.class})
public class UserCommand extends CliGroup {

	@Command(name = "list", description = "List the users.")
	public static class ListCommand extends DirectorCommand {
		@Mixin
		PageOptions paging;

		@Option(names = "--sort", paramLabel = "<field>[:desc]", split = ",", converter = Arguments.SortConverter.class,
				description = "Order by name, email, admin, planName, createdAt or updatedAt; add :desc for the reverse order.")
		List<Sort> sort = new ArrayList<>();

		@Override
		protected void run() throws Exception {
			DirectorAdmin admin = context().directorAdmin();
			Sort[] keys = sort.toArray(new Sort[0]);
			PaginatedResult<Profile> page = paging.fetch(this, (p, size) -> admin.listUsers(p, size, keys));
			Listing.page(output(), page, paging, "users", AdminViews.USER_HEADERS, AdminViews::userRow,
					Views::profileJson, "The node has no users.");
		}
	}

	@Command(name = "show", description = "Show a user's account.")
	public static class ShowCommand extends DirectorCommand {
		@Parameters(paramLabel = "<user-id>", description = "The user.")
		Id userId;

		@Override
		protected void run() throws Exception {
			Profile user = await(context().directorAdmin().getUser(userId)).orElseThrow(() -> noSuchUser(userId));
			if (output().isJson())
				output().json(Views.profileJson(user));
			else
				output().details(Views.profile(user));
		}
	}

	@Command(name = "add", description = {"Create a user account.",
			"The Director requires an account created this way to have a passphrase, which you are asked for. "
					+ "Give it to the user, who can change it with 'boson-cli user passphrase change'."})
	public static class AddCommand extends DirectorCommand {
		@Parameters(paramLabel = "<user-id>", description = "The user's id: the public key of the user's identity.")
		Id userId;

		@Option(names = "--name", paramLabel = "<name>", description = "The user's display name.")
		String name;

		@Option(names = "--email", paramLabel = "<email>", description = "The user's email address.")
		String email;

		@Option(names = "--bio", paramLabel = "<text>", description = "A few words about the user.")
		String bio;

		@Option(names = "--admin", description = "Make the user an administrator of the node.")
		boolean admin;

		@Override
		protected void run() throws Exception {
			DirectorAdmin client = context().directorAdmin();
			String passphrase = terminal().readNewSecret("Passphrase for the new account", "passphrase");

			NewUser user = new NewUser(userId, passphrase)
					.name(Arguments.blankToNull(name))
					.email(Arguments.blankToNull(email))
					.bio(Arguments.blankToNull(bio))
					.admin(admin);
			try {
				await(client.addUser(user));
			} catch (ConflictException e) {
				throw new CliException(ExitCode.CONFLICT, "User " + userId + " already exists.",
						"Change the account with " + tool().command("user update " + userId) + ".");
			}

			if (output().isJson()) {
				Map<String, Object> json = new LinkedHashMap<>();
				json.put("userId", userId);
				json.put("admin", admin);
				output().json(json);
				return;
			}

			output().message("Added user " + userId + (admin ? " as an administrator." : "."));
		}
	}

	@Command(name = "update", description = {"Change a user's account.",
			"Only the fields given change. An empty value clears a field, as in --bio \"\". To change the administrator "
					+ "role, use grant-admin and revoke-admin."})
	public static class UpdateCommand extends DirectorCommand {
		@Parameters(paramLabel = "<user-id>", description = "The user.")
		Id userId;

		@Option(names = "--name", paramLabel = "<name>", description = "The user's display name.")
		String name;

		@Option(names = "--email", paramLabel = "<email>", description = "The user's email address.")
		String email;

		@Option(names = "--bio", paramLabel = "<text>", description = "A few words about the user.")
		String bio;

		@Option(names = "--avatar", paramLabel = "<uri>", description = "The URI of the user's avatar.")
		String avatar;

		@Option(names = "--passphrase", description = "Replace the account passphrase with a new one, which you are asked "
				+ "for. The current one is not needed.")
		boolean passphrase;

		@Override
		protected void run() throws Exception {
			UserUpdate update = new UserUpdate();
			List<String> changed = new ArrayList<>();
			if (name != null) {
				update.name(Arguments.blankToNull(name));
				changed.add("name");
			}
			if (email != null) {
				update.email(Arguments.blankToNull(email));
				changed.add("email");
			}
			if (bio != null) {
				update.bio(Arguments.blankToNull(bio));
				changed.add("bio");
			}
			if (avatar != null) {
				update.avatar(Arguments.blankToNull(avatar));
				changed.add("avatar");
			}
			if (!passphrase && update.isEmpty())
				throw CliException.usage("Nothing to update.",
						"Pass --name, --email, --bio, --avatar or --passphrase. An empty value clears a field, as in --bio \"\".");

			DirectorAdmin client = context().directorAdmin();
			if (passphrase) {
				update.passphrase(terminal().readNewSecret("New passphrase for the account", "passphrase"));
				changed.add("passphrase");
			}

			try {
				await(client.updateUser(userId, update));
			} catch (NotFoundException e) {
				throw noSuchUser(userId);
			}

			if (output().isJson()) {
				Profile user = await(client.getUser(userId)).orElseThrow(() -> noSuchUser(userId));
				output().json(Views.profileJson(user));
				return;
			}

			output().message("Updated user " + userId + ": " + String.join(", ", changed) + ".");
		}
	}

	@Command(name = "remove", description = "Remove a user's account.")
	public static class RemoveCommand extends DirectorCommand {
		@Parameters(paramLabel = "<user-id>", description = "The user.")
		Id userId;

		@Option(names = {"-y", "--yes"}, description = "Do not ask for confirmation.")
		boolean yes;

		@Override
		protected void run() throws Exception {
			DirectorAdmin client = context().directorAdmin();
			String self = userId.equals(client.getUserId()) ? " It is the administrator this tool acts as." : "";
			terminal().confirm("Remove user " + userId + "? This cannot be undone." + self, yes);

			try {
				await(client.removeUser(userId));
			} catch (NotFoundException e) {
				throw noSuchUser(userId);
			}

			if (output().isJson())
				output().json(Map.of("userId", userId, "removed", true));
			else
				output().message("Removed user " + userId + ".");
		}
	}

	@Command(name = "grant-admin", description = "Make a user an administrator, with full control of the node.")
	public static class GrantAdminCommand extends DirectorCommand {
		@Parameters(paramLabel = "<user-id>", description = "The user.")
		Id userId;

		@Option(names = {"-y", "--yes"}, description = "Do not ask for confirmation.")
		boolean yes;

		@Override
		protected void run() throws Exception {
			context().directorAdmin();
			terminal().confirm("Make user " + userId + " an administrator, with full control of this node?", yes);
			setAdmin(context(), userId, true);

			if (output().isJson())
				output().json(Map.of("userId", userId, "admin", true));
			else
				output().message("User " + userId + " is now an administrator.");
		}
	}

	@Command(name = "revoke-admin", description = "Take the administrator role from a user.")
	public static class RevokeAdminCommand extends DirectorCommand {
		@Parameters(paramLabel = "<user-id>", description = "The user.")
		Id userId;

		@Option(names = {"-y", "--yes"},
				description = "Do not ask for confirmation, which is asked only when you revoke your own role.")
		boolean yes;

		@Override
		protected void run() throws Exception {
			DirectorAdmin client = context().directorAdmin();
			if (userId.equals(client.getUserId()))
				terminal().confirm("Revoke your own administrator role? This tool will lose access to the admin API.", yes);

			setAdmin(context(), userId, false);

			if (output().isJson())
				output().json(Map.of("userId", userId, "admin", false));
			else
				output().message("User " + userId + " is no longer an administrator.");
		}
	}

	private static void setAdmin(CliContext context, Id userId, boolean admin) throws Exception {
		try {
			context.await(context.directorAdmin().updateUser(userId, new UserUpdate().admin(admin)));
		} catch (NotFoundException e) {
			throw noSuchUser(userId);
		}
	}

	static CliException noSuchUser(Id userId) {
		return CliException.notFound("There is no user " + userId + " on this node.",
				"List the users with 'boson-director-cli user list'.");
	}
}
