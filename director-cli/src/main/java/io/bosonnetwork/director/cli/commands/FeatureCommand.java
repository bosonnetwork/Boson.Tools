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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import io.bosonnetwork.cli.director.DirectorCommand;
import io.bosonnetwork.cli.common.CliException;
import io.bosonnetwork.cli.common.CliGroup;
import io.bosonnetwork.cli.common.ExitCode;
import io.bosonnetwork.cli.common.Listing;
import io.bosonnetwork.director.cli.AdminViews;
import io.bosonnetwork.director.cli.Arguments;
import io.bosonnetwork.director.cli.Arguments.PlanRef;
import io.bosonnetwork.director.client.DirectorAdmin;
import io.bosonnetwork.director.client.Feature;
import io.bosonnetwork.director.client.FeatureFilter;
import io.bosonnetwork.director.client.FeatureUpdate;
import io.bosonnetwork.director.client.exceptions.ConflictException;
import io.bosonnetwork.director.client.exceptions.NotFoundException;

/**
 * The {@code feature} commands of {@code boson-director-cli}.
 */
@Command(name = "feature", description = {"Manage what each plan grants on each service.",
		"A feature is a JSON document, one per plan and service. The service reads it; the Director stores it as it is. "
				+ "A plan without a feature for a service gets the service's defaults."},
		subcommands = {FeatureCommand.ListCommand.class, FeatureCommand.ShowCommand.class, FeatureCommand.AddCommand.class,
				FeatureCommand.UpdateCommand.class, FeatureCommand.RemoveCommand.class})
public class FeatureCommand extends CliGroup {

	/**
	 * A feature document, given inline or in a file.
	 */
	static class Document {
		@Option(names = "--document", paramLabel = "<json>", required = true, description = "The document, as a JSON object.")
		String inline;

		@Option(names = "--file", paramLabel = "<file>", required = true, description = "A file holding the document.")
		Path file;
	}

	@Command(name = "list", description = "List the features, of every plan or of one.")
	public static class ListCommand extends DirectorCommand {
		@Option(names = "--plan", paramLabel = "<plan>", converter = Arguments.PlanRefConverter.class,
				description = "Only the features of this plan, by id or name.")
		PlanRef plan;

		@Option(names = "--service", paramLabel = "<service-id>", description = "Only the features for this service.")
		String service;

		@Override
		protected void run() throws Exception {
			DirectorAdmin admin = context().directorAdmin();
			List<Feature> features;
			if (plan == null && service == null) {
				features = await(admin.listFeatures());
			} else {
				FeatureFilter filter = new FeatureFilter();
				if (plan != null) {
					if (plan.isId())
						filter.plan(plan.id());
					else
						filter.plan(plan.name());
				}
				if (service != null)
					filter.service(service);
				features = await(admin.listFeatures(filter));
			}

			Listing.list(output(), features, AdminViews.FEATURE_HEADERS, AdminViews::featureRow, AdminViews::featureJson,
					plan == null && service == null ? "No plan has features." : "No features match.");
		}
	}

	@Command(name = "show", description = "Show a feature and its document.")
	public static class ShowCommand extends DirectorCommand {
		@Parameters(paramLabel = "<feature-id>", description = "The feature, as 'feature list' shows it.")
		int featureId;

		@Override
		protected void run() throws Exception {
			Feature feature = await(context().directorAdmin().getFeature(featureId))
					.orElseThrow(() -> noSuchFeature(featureId));
			if (output().isJson())
				output().json(AdminViews.featureJson(feature));
			else
				output().details(AdminViews.feature(feature));
		}
	}

	@Command(name = "add", description = "Set what a plan grants on a service.")
	public static class AddCommand extends DirectorCommand {
		@Parameters(index = "0", paramLabel = "<plan>", converter = Arguments.PlanRefConverter.class,
				description = "The plan's id or name.")
		PlanRef plan;

		@Parameters(index = "1", paramLabel = "<service-id>", description = "The service, such as io.bosonnetwork.ionstore.")
		String service;

		@ArgGroup(exclusive = true, multiplicity = "1")
		Document document;

		@Override
		protected void run() throws Exception {
			Map<String, Object> content = Arguments.jsonObject(document.inline, document.file, "feature document");
			DirectorAdmin admin = context().directorAdmin();

			Feature added;
			try {
				added = await(plan.isId() ? admin.addFeature(plan.id(), service, content) :
						admin.addFeature(plan.name(), service, content));
			} catch (ConflictException e) {
				throw new CliException(ExitCode.CONFLICT, "Plan " + plan + " already has a feature for " + service + ".",
						"Change it with " + tool().command("feature update <feature-id>") + "; " +
								tool().command("feature list --plan " + plan) + " shows its id.");
			} catch (NotFoundException e) {
				throw PlanCommand.noSuchPlan(plan);
			}

			if (output().isJson())
				output().json(AdminViews.featureJson(added));
			else
				output().message("Added the " + service + " feature to plan " + plan + " (feature id " + added.getId() + ").");
		}
	}

	@Command(name = "update", description = "Change a feature: its service, its document, or both.")
	public static class UpdateCommand extends DirectorCommand {
		@Parameters(paramLabel = "<feature-id>", description = "The feature, as 'feature list' shows it.")
		int featureId;

		@Option(names = "--service", paramLabel = "<service-id>", description = "The service the feature is for.")
		String service;

		@ArgGroup(exclusive = true, multiplicity = "0..1")
		Document document;

		@Override
		protected void run() throws Exception {
			FeatureUpdate update = new FeatureUpdate();
			List<String> changed = new ArrayList<>();
			if (service != null) {
				update.serviceId(service);
				changed.add("service");
			}
			if (document != null) {
				update.feature(Arguments.jsonObject(document.inline, document.file, "feature document"));
				changed.add("document");
			}
			if (update.isEmpty())
				throw CliException.usage("Nothing to update.", "Pass --service, --document or --file.");

			try {
				await(context().directorAdmin().updateFeature(featureId, update));
			} catch (NotFoundException e) {
				throw noSuchFeature(featureId);
			}

			if (output().isJson())
				output().json(Map.of("featureId", featureId, "updated", changed));
			else
				output().message("Updated feature " + featureId + ": " + String.join(", ", changed) + ".");
		}
	}

	@Command(name = "remove", description = "Remove a feature. Its plan gets the service's defaults.")
	public static class RemoveCommand extends DirectorCommand {
		@Parameters(paramLabel = "<feature-id>", description = "The feature, as 'feature list' shows it.")
		int featureId;

		@Option(names = {"-y", "--yes"}, description = "Do not ask for confirmation.")
		boolean yes;

		@Override
		protected void run() throws Exception {
			DirectorAdmin admin = context().directorAdmin();
			terminal().confirm("Remove feature " + featureId + "? Its plan gets the service's defaults.", yes);

			try {
				await(admin.removeFeature(featureId));
			} catch (NotFoundException e) {
				throw noSuchFeature(featureId);
			}

			if (output().isJson())
				output().json(Map.of("featureId", featureId, "removed", true));
			else
				output().message("Removed feature " + featureId + ".");
		}
	}

	private static CliException noSuchFeature(int featureId) {
		return CliException.notFound("There is no feature " + featureId + " on this node.",
				"List the features with 'boson-director-cli feature list'.");
	}
}
