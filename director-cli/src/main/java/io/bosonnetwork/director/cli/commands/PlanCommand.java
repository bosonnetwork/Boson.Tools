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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import io.bosonnetwork.cli.director.DirectorCommand;
import io.bosonnetwork.cli.director.CliContext;
import io.bosonnetwork.cli.common.CliException;
import io.bosonnetwork.cli.common.CliGroup;
import io.bosonnetwork.cli.common.ExitCode;
import io.bosonnetwork.cli.common.Listing;
import io.bosonnetwork.director.cli.AdminViews;
import io.bosonnetwork.director.cli.Arguments;
import io.bosonnetwork.director.cli.Arguments.PlanRef;
import io.bosonnetwork.director.client.DirectorAdmin;
import io.bosonnetwork.director.client.NewPlan;
import io.bosonnetwork.director.client.Plan;
import io.bosonnetwork.director.client.PlanUpdate;
import io.bosonnetwork.director.client.exceptions.ConflictException;
import io.bosonnetwork.director.client.exceptions.NotFoundException;

/**
 * The {@code plan} commands of {@code boson-director-cli}.
 */
@Command(name = "plan", description = {"Manage the plans the node offers.",
		"A plan is named by its id or its name. What a plan grants on each service is its features; see 'feature'."},
		subcommands = {PlanCommand.ListCommand.class, PlanCommand.ShowCommand.class, PlanCommand.AddCommand.class,
				PlanCommand.UpdateCommand.class, PlanCommand.ActivateCommand.class, PlanCommand.DeactivateCommand.class})
public class PlanCommand extends CliGroup {

	@Command(name = "list", description = "List every plan, including the inactive ones.")
	public static class ListCommand extends DirectorCommand {
		@Override
		protected void run() throws Exception {
			List<Plan> plans = await(context().directorAdmin().listPlans());
			Listing.list(output(), plans, AdminViews.PLAN_HEADERS, AdminViews::planRow, AdminViews::planJson,
					"The node has no plans.");
		}
	}

	@Command(name = "show", description = "Show a plan.")
	public static class ShowCommand extends DirectorCommand {
		@Parameters(paramLabel = "<plan>", converter = Arguments.PlanRefConverter.class, description = "The plan's id or name.")
		PlanRef plan;

		@Override
		protected void run() throws Exception {
			DirectorAdmin admin = context().directorAdmin();
			Optional<Plan> found = await(plan.isId() ? admin.getPlan(plan.id()) : admin.getPlan(plan.name()));
			Plan p = found.orElseThrow(() -> noSuchPlan(plan));
			if (output().isJson())
				output().json(AdminViews.planJson(p));
			else
				output().details(AdminViews.plan(p));
		}
	}

	@Command(name = "add", description = {"Add a plan, billed monthly.",
			"A new plan is not offered to subscribers until it is active: pass --active, or activate it later."})
	public static class AddCommand extends DirectorCommand {
		@Parameters(paramLabel = "<name>", description = "The plan's name.")
		String name;

		@Option(names = "--price", paramLabel = "<amount>", required = true, description = "The monthly price, such as 9.99.")
		BigDecimal price;

		@Option(names = "--currency", paramLabel = "<code>", defaultValue = "USD",
				description = "The currency of the price. Default: ${DEFAULT-VALUE}.")
		String currency;

		@Option(names = "--annual-discount", paramLabel = "<amount>", description = "The discount for each full year billed.")
		BigDecimal annualDiscount;

		@Option(names = "--description", paramLabel = "<text>", description = "A short description.")
		String description;

		@Option(names = "--detail", paramLabel = "<text>", description = "The detailed description.")
		String detail;

		@Option(names = "--active", description = "Offer the plan to new subscribers at once.")
		boolean active;

		@Override
		protected void run() throws Exception {
			NewPlan plan = new NewPlan(name, price, currency)
					.description(Arguments.blankToNull(description))
					.detail(Arguments.blankToNull(detail))
					.active(active);
			if (annualDiscount != null)
				plan.annuallyDiscount(annualDiscount);

			Plan added;
			try {
				added = await(context().directorAdmin().addPlan(plan));
			} catch (ConflictException e) {
				throw new CliException(ExitCode.CONFLICT, "A plan named " + name + " already exists.",
						"Change it with " + tool().command("plan update " + name) + ".");
			}

			if (output().isJson()) {
				output().json(AdminViews.planJson(added));
				return;
			}

			output().message("Added plan " + added.getName() + " (id " + added.getId() + ")" +
					(added.isActive() ? ", offered to new subscribers." : "."));
			if (!added.isActive())
				output().message("Offer it to subscribers with " + tool().command("plan activate " + added.getId()) + ".");
		}
	}

	@Command(name = "update", description = {"Change a plan.",
			"Only the fields given change; an empty description or detail clears it. A plan's currency cannot change. "
					+ "To offer or withdraw a plan, use activate and deactivate."})
	public static class UpdateCommand extends DirectorCommand {
		@Parameters(paramLabel = "<plan>", converter = Arguments.PlanRefConverter.class, description = "The plan's id or name.")
		PlanRef plan;

		@Option(names = "--name", paramLabel = "<name>", description = "The plan's new name.")
		String name;

		@Option(names = "--price", paramLabel = "<amount>", description = "The monthly price.")
		BigDecimal price;

		@Option(names = "--annual-discount", paramLabel = "<amount>", description = "The discount for each full year billed.")
		BigDecimal annualDiscount;

		@Option(names = "--description", paramLabel = "<text>", description = "A short description.")
		String description;

		@Option(names = "--detail", paramLabel = "<text>", description = "The detailed description.")
		String detail;

		@Override
		protected void run() throws Exception {
			PlanUpdate update = new PlanUpdate();
			List<String> changed = new ArrayList<>();
			if (name != null) {
				update.name(name);
				changed.add("name");
			}
			if (price != null) {
				update.price(price);
				changed.add("price");
			}
			if (annualDiscount != null) {
				update.annuallyDiscount(annualDiscount);
				changed.add("annual discount");
			}
			if (description != null) {
				update.description(Arguments.blankToNull(description));
				changed.add("description");
			}
			if (detail != null) {
				update.detail(Arguments.blankToNull(detail));
				changed.add("detail");
			}
			if (update.isEmpty())
				throw CliException.usage("Nothing to update.",
						"Pass --name, --price, --annual-discount, --description or --detail.");

			updatePlan(context(), plan, update);

			if (output().isJson())
				output().json(Map.of("plan", plan.toString(), "updated", changed));
			else
				output().message("Updated plan " + plan + ": " + String.join(", ", changed) + ".");
		}
	}

	@Command(name = "activate", description = "Offer a plan to new subscribers.")
	public static class ActivateCommand extends DirectorCommand {
		@Parameters(paramLabel = "<plan>", converter = Arguments.PlanRefConverter.class, description = "The plan's id or name.")
		PlanRef plan;

		@Override
		protected void run() throws Exception {
			updatePlan(context(), plan, new PlanUpdate().active(true));

			if (output().isJson())
				output().json(Map.of("plan", plan.toString(), "active", true));
			else
				output().message("Plan " + plan + " is offered to new subscribers.");
		}
	}

	@Command(name = "deactivate", description = "Withdraw a plan from new subscribers. Existing subscriptions are not affected.")
	public static class DeactivateCommand extends DirectorCommand {
		@Parameters(paramLabel = "<plan>", converter = Arguments.PlanRefConverter.class, description = "The plan's id or name.")
		PlanRef plan;

		@Override
		protected void run() throws Exception {
			DirectorAdmin admin = context().directorAdmin();
			try {
				await(plan.isId() ? admin.deactivatePlan(plan.id()) : admin.deactivatePlan(plan.name()));
			} catch (NotFoundException e) {
				throw noSuchPlan(plan);
			}

			if (output().isJson())
				output().json(Map.of("plan", plan.toString(), "active", false));
			else
				output().message("Plan " + plan + " is no longer offered to new subscribers. Existing subscriptions are not affected.");
		}
	}

	private static void updatePlan(CliContext context, PlanRef plan, PlanUpdate update) throws Exception {
		DirectorAdmin admin = context.directorAdmin();
		try {
			context.await(plan.isId() ? admin.updatePlan(plan.id(), update) : admin.updatePlan(plan.name(), update));
		} catch (NotFoundException e) {
			throw noSuchPlan(plan);
		}
	}

	static CliException noSuchPlan(PlanRef plan) {
		return CliException.notFound("There is no plan " + plan + " on this node.",
				"List the plans with 'boson-director-cli plan list'.");
	}
}
