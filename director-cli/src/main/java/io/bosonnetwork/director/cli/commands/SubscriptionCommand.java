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
import io.bosonnetwork.cli.common.Formats;
import io.bosonnetwork.cli.common.Listing;
import io.bosonnetwork.cli.common.PageOptions;
import io.bosonnetwork.director.cli.AdminViews;
import io.bosonnetwork.director.cli.Arguments;
import io.bosonnetwork.director.cli.Arguments.PlanRef;
import io.bosonnetwork.director.client.DirectorAdmin;
import io.bosonnetwork.director.client.Subscription;
import io.bosonnetwork.director.client.SubscriptionUpdate;
import io.bosonnetwork.director.client.exceptions.ConflictException;
import io.bosonnetwork.director.client.exceptions.ForbiddenException;
import io.bosonnetwork.director.client.exceptions.NotFoundException;
import io.bosonnetwork.web.PaginatedResult;

/**
 * The {@code subscription} commands of {@code boson-director-cli}.
 */
@Command(name = "subscription", description = {"Manage users' subscriptions to plans.",
		"A user has one active subscription at most; a user without one is on the node's free plan."},
		subcommands = {SubscriptionCommand.ListCommand.class, SubscriptionCommand.ShowCommand.class,
				SubscriptionCommand.ActiveCommand.class, SubscriptionCommand.AddCommand.class,
				SubscriptionCommand.UpdateCommand.class, SubscriptionCommand.CancelCommand.class})
public class SubscriptionCommand extends CliGroup {

	@Command(name = "list", description = "List a user's subscriptions, past and present.")
	public static class ListCommand extends DirectorCommand {
		@Parameters(paramLabel = "<user-id>", description = "The user.")
		Id userId;

		@Mixin
		PageOptions paging;

		@Override
		protected void run() throws Exception {
			DirectorAdmin admin = context().directorAdmin();
			PaginatedResult<Subscription> page;
			try {
				page = paging.fetch(this, (p, size) -> admin.listSubscriptions(userId, p, size));
			} catch (NotFoundException e) {
				throw UserCommand.noSuchUser(userId);
			}
			Listing.page(output(), page, paging, "subscriptions", AdminViews.SUBSCRIPTION_HEADERS,
					AdminViews::subscriptionRow, AdminViews::subscriptionJson, "User " + userId + " has no subscriptions.");
		}
	}

	@Command(name = "show", description = "Show a subscription.")
	public static class ShowCommand extends DirectorCommand {
		@Parameters(paramLabel = "<subscription-id>", description = "The subscription, as 'subscription list' shows it.")
		long subscriptionId;

		@Override
		protected void run() throws Exception {
			Subscription subscription = await(context().directorAdmin().getSubscription(subscriptionId))
					.orElseThrow(() -> noSuchSubscription(subscriptionId));
			print(context(), subscription);
		}
	}

	@Command(name = "active", description = "Show a user's active subscription.")
	public static class ActiveCommand extends DirectorCommand {
		@Parameters(paramLabel = "<user-id>", description = "The user.")
		Id userId;

		@Override
		protected void run() throws Exception {
			Subscription subscription = await(context().directorAdmin().getActiveSubscription(userId))
					.orElseThrow(() -> CliException.notFound("User " + userId + " has no active subscription.", null));
			print(context(), subscription);
		}
	}

	@Command(name = "add", description = "Subscribe a user to a plan.")
	public static class AddCommand extends DirectorCommand {
		@Parameters(paramLabel = "<user-id>", description = "The user.")
		Id userId;

		@Option(names = "--plan", paramLabel = "<plan>", required = true, converter = Arguments.PlanRefConverter.class,
				description = "The plan's id or name.")
		PlanRef plan;

		@Option(names = "--status", paramLabel = "<state>", defaultValue = "active",
				converter = Arguments.SubscriptionStatusConverter.class,
				description = "The state it starts in: " + Arguments.SUBSCRIPTION_STATES + ". Default: active.")
		Subscription.Status status;

		@Option(names = "--start", paramLabel = "<time>", converter = Arguments.TimeConverter.class,
				description = "When it starts: " + Arguments.TIME_FORMATS + ". Default: now.")
		Long start;

		@Option(names = "--end", paramLabel = "<time>", required = true, converter = Arguments.TimeConverter.class,
				description = "When it ends: " + Arguments.TIME_FORMATS + ".")
		Long end;

		@Override
		protected void run() throws Exception {
			long from = start != null ? start : System.currentTimeMillis();
			if (end <= from)
				throw CliException.usage("The end, " + Formats.time(end) + ", is not after the start, " + Formats.time(from) + ".", null);

			DirectorAdmin admin = context().directorAdmin();
			long startDate = start != null ? start : 0;
			Subscription added;
			try {
				added = await(plan.isId() ?
						admin.addSubscription(userId, plan.id(), status, startDate, end) :
						admin.addSubscription(userId, plan.name(), status, startDate, end));
			} catch (ConflictException e) {
				throw new CliException(ExitCode.CONFLICT, "User " + userId + " already has an active subscription.",
						"Show it with " + tool().command("subscription active " + userId) + ".");
			} catch (NotFoundException e) {
				throw CliException.notFound("There is no user " + userId + ", or no plan " + plan + ", on this node.", null);
			}

			if (output().isJson()) {
				output().json(AdminViews.subscriptionJson(added));
				return;
			}

			output().message("Subscribed user " + userId + " to plan " + added.getPlanName().orElse(plan.toString()) +
					" until " + Formats.time(added.getEndDate()) + " (subscription " + added.getId() + ", " + added.getStatus() + ").");
		}
	}

	@Command(name = "update", description = "Change a subscription's state, end or plan.")
	public static class UpdateCommand extends DirectorCommand {
		@Parameters(paramLabel = "<subscription-id>", description = "The subscription, as 'subscription list' shows it.")
		long subscriptionId;

		@Option(names = "--status", paramLabel = "<state>", converter = Arguments.SubscriptionStatusConverter.class,
				description = "The new state: " + Arguments.SUBSCRIPTION_STATES + ".")
		Subscription.Status status;

		@Option(names = "--end", paramLabel = "<time>", converter = Arguments.TimeConverter.class,
				description = "When it ends: " + Arguments.TIME_FORMATS + ".")
		Long end;

		@Option(names = "--plan-id", paramLabel = "<id>", description = "The id of the plan to move it to.")
		Integer planId;

		@Override
		protected void run() throws Exception {
			SubscriptionUpdate update = new SubscriptionUpdate();
			List<String> changed = new ArrayList<>();
			if (status != null) {
				update.status(status);
				changed.add("state");
			}
			if (end != null) {
				update.endDate(end);
				changed.add("end");
			}
			if (planId != null) {
				update.planId(planId);
				changed.add("plan");
			}
			if (update.isEmpty())
				throw CliException.usage("Nothing to update.", "Pass --status, --end or --plan-id.");

			try {
				await(context().directorAdmin().updateSubscription(subscriptionId, update));
			} catch (NotFoundException e) {
				throw noSuchSubscription(subscriptionId);
			}

			if (output().isJson())
				output().json(Map.of("subscriptionId", subscriptionId, "updated", changed));
			else
				output().message("Updated subscription " + subscriptionId + ": " + String.join(", ", changed) + ".");
		}
	}

	@Command(name = "cancel", description = "Cancel a subscription.")
	public static class CancelCommand extends DirectorCommand {
		@Parameters(paramLabel = "<subscription-id>", description = "The subscription, as 'subscription list' shows it.")
		long subscriptionId;

		@Option(names = {"-y", "--yes"}, description = "Do not ask for confirmation.")
		boolean yes;

		@Override
		protected void run() throws Exception {
			DirectorAdmin admin = context().directorAdmin();
			terminal().confirm("Cancel subscription " + subscriptionId + "?", yes);

			try {
				await(admin.cancelSubscription(subscriptionId));
			} catch (NotFoundException e) {
				throw noSuchSubscription(subscriptionId);
			} catch (ForbiddenException e) {
				throw CliException.failed("Subscription " + subscriptionId + " has already ended.", null);
			}

			if (output().isJson())
				output().json(Map.of("subscriptionId", subscriptionId, "canceled", true));
			else
				output().message("Canceled subscription " + subscriptionId + ".");
		}
	}

	private static void print(CliContext context, Subscription subscription) {
		if (context.output().isJson())
			context.output().json(AdminViews.subscriptionJson(subscription));
		else
			context.output().details(AdminViews.subscription(subscription));
	}

	private static CliException noSuchSubscription(long subscriptionId) {
		return CliException.notFound("There is no subscription " + subscriptionId + " on this node.",
				"List a user's subscriptions with 'boson-director-cli subscription list <user-id>'.");
	}
}
