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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.bosonnetwork.cli.common.Formats;
import io.bosonnetwork.director.client.BlacklistedNode;
import io.bosonnetwork.director.client.Feature;
import io.bosonnetwork.director.client.FederatedNode;
import io.bosonnetwork.director.client.FederatedService;
import io.bosonnetwork.director.client.FederationProposal;
import io.bosonnetwork.director.client.Plan;
import io.bosonnetwork.director.client.Profile;
import io.bosonnetwork.director.client.Subscription;
import io.bosonnetwork.json.Json;

/**
 * How {@code boson-director-cli} shows the admin API's objects: table rows, details and JSON. The JSON
 * forms are built field by field, so that the output scripts rely on is stated here.
 */
public final class AdminViews {
	/** The table headers of a user list. */
	public static final List<String> USER_HEADERS = List.of("USER ID", "NAME", "EMAIL", "ADMIN", "PLAN", "CREATED");
	/** The table headers of a plan list. */
	public static final List<String> PLAN_HEADERS = List.of("ID", "NAME", "PRICE", "ANNUAL DISCOUNT", "ACTIVE", "DESCRIPTION");
	/** The table headers of a feature list. */
	public static final List<String> FEATURE_HEADERS = List.of("ID", "PLAN", "SERVICE", "UPDATED", "FEATURE");
	/** The table headers of a subscription list. */
	public static final List<String> SUBSCRIPTION_HEADERS = List.of("ID", "PLAN", "STATUS", "START", "END");
	/** The table headers of a blacklist. */
	public static final List<String> BLACKLIST_HEADERS = List.of("ID", "NODE ID", "HOST", "AUTO", "REASON", "CREATED");
	/** The table headers of a federated node list. */
	public static final List<String> FEDERATED_NODE_HEADERS = List.of("NODE ID", "NAME", "FEDERATED", "REPUTATION", "API ENDPOINT", "UPDATED");
	/** The table headers of a federated service list. */
	public static final List<String> FEDERATED_SERVICE_HEADERS = List.of("SERVICE ID", "NAME", "PEER ID", "ENDPOINT", "FEDERATION");
	/** The table headers of a proposal list. */
	public static final List<String> PROPOSAL_HEADERS = List.of("ID", "NODE ID", "NAME", "ROLE", "STATUS", "PROPOSED", "CONFIRMED");

	private static final int BRIEF = 40;

	private AdminViews() {
	}

	// ---- Users ---------------------------------------------------------------------------------

	/**
	 * Returns the table row of a user.
	 *
	 * @param user the user
	 * @return the row
	 */
	public static List<String> userRow(Profile user) {
		return List.of(user.getId().toBase58String(),
				Formats.text(user.getName()),
				Formats.text(user.getEmail()),
				Formats.yesNo(user.isAdmin()),
				Formats.text(user.getPlanName()),
				Formats.time(user.getCreatedAt()));
	}

	// ---- Plans ---------------------------------------------------------------------------------

	/**
	 * Returns the table row of a plan.
	 *
	 * @param plan the plan
	 * @return the row
	 */
	public static List<String> planRow(Plan plan) {
		return List.of(Integer.toString(plan.getId()),
				plan.getName(),
				price(plan),
				plan.getAnnuallyDiscount().toPlainString(),
				Formats.yesNo(plan.isActive()),
				Formats.brief(plan.getDescription().orElse(null), BRIEF));
	}

	/**
	 * Returns the details of a plan.
	 *
	 * @param plan the plan
	 * @return the details, keyed by label
	 */
	public static Map<String, String> plan(Plan plan) {
		Map<String, String> rows = new LinkedHashMap<>();
		rows.put("Plan id", Integer.toString(plan.getId()));
		rows.put("Name", plan.getName());
		rows.put("Price", price(plan));
		rows.put("Annual discount", plan.getAnnuallyDiscount().toPlainString());
		rows.put("Active", Formats.yesNo(plan.isActive()));
		rows.put("Description", Formats.text(plan.getDescription()));
		rows.put("Detail", Formats.text(plan.getDetail()));
		rows.put("Created", Formats.time(plan.getCreatedAt()));
		rows.put("Updated", Formats.time(plan.getUpdatedAt()));
		return rows;
	}

	/**
	 * Returns the JSON of a plan.
	 *
	 * @param plan the plan
	 * @return the JSON object
	 */
	public static Map<String, Object> planJson(Plan plan) {
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("id", plan.getId());
		json.put("name", plan.getName());
		json.put("price", plan.getPrice().toPlainString());
		json.put("currency", plan.getCurrency());
		json.put("cycle", plan.getCycle().toString());
		json.put("annuallyDiscount", plan.getAnnuallyDiscount().toPlainString());
		json.put("active", plan.isActive());
		json.put("description", plan.getDescription().orElse(null));
		json.put("detail", plan.getDetail().orElse(null));
		json.put("createdAt", plan.getCreatedAt());
		json.put("updatedAt", plan.getUpdatedAt());
		return json;
	}

	private static String price(Plan plan) {
		return plan.getPrice().toPlainString() + " " + plan.getCurrency() + " " + plan.getCycle();
	}

	// ---- Features ------------------------------------------------------------------------------

	/**
	 * Returns the table row of a feature.
	 *
	 * @param feature the feature
	 * @return the row
	 */
	public static List<String> featureRow(Feature feature) {
		return List.of(Integer.toString(feature.getId()),
				feature.getPlanName().orElse(Integer.toString(feature.getPlanId())),
				feature.getServiceId(),
				Formats.time(feature.getUpdatedAt()),
				Formats.brief(Json.toString(feature.getFeature()), BRIEF));
	}

	/**
	 * Returns the details of a feature.
	 *
	 * @param feature the feature
	 * @return the details, keyed by label
	 */
	public static Map<String, String> feature(Feature feature) {
		Map<String, String> rows = new LinkedHashMap<>();
		rows.put("Feature id", Integer.toString(feature.getId()));
		rows.put("Plan", feature.getPlanName().map(name -> name + " (id " + feature.getPlanId() + ")")
				.orElse("id " + feature.getPlanId()));
		rows.put("Service", feature.getServiceId());
		rows.put("Created", Formats.time(feature.getCreatedAt()));
		rows.put("Updated", Formats.time(feature.getUpdatedAt()));
		rows.put("Feature", Json.toPrettyString(feature.getFeature()));
		return rows;
	}

	/**
	 * Returns the JSON of a feature.
	 *
	 * @param feature the feature
	 * @return the JSON object
	 */
	public static Map<String, Object> featureJson(Feature feature) {
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("id", feature.getId());
		json.put("planId", feature.getPlanId());
		json.put("planName", feature.getPlanName().orElse(null));
		json.put("serviceId", feature.getServiceId());
		json.put("feature", feature.getFeature());
		json.put("createdAt", feature.getCreatedAt());
		json.put("updatedAt", feature.getUpdatedAt());
		return json;
	}

	// ---- Subscriptions -------------------------------------------------------------------------

	/**
	 * Returns the table row of a subscription.
	 *
	 * @param subscription the subscription
	 * @return the row
	 */
	public static List<String> subscriptionRow(Subscription subscription) {
		return List.of(Long.toString(subscription.getId()),
				subscription.getPlanName().orElse(Integer.toString(subscription.getPlanId())),
				subscription.getStatus().toString(),
				Formats.time(subscription.getStartDate()),
				Formats.time(subscription.getEndDate()));
	}

	/**
	 * Returns the details of a subscription.
	 *
	 * @param subscription the subscription
	 * @return the details, keyed by label
	 */
	public static Map<String, String> subscription(Subscription subscription) {
		Map<String, String> rows = new LinkedHashMap<>();
		rows.put("Subscription id", Long.toString(subscription.getId()));
		rows.put("User id", subscription.getUserId().toBase58String());
		rows.put("Plan", subscription.getPlanName().map(name -> name + " (id " + subscription.getPlanId() + ")")
				.orElse("id " + subscription.getPlanId()));
		rows.put("Status", subscription.getStatus().toString());
		rows.put("Start", Formats.time(subscription.getStartDate()));
		rows.put("End", Formats.time(subscription.getEndDate()));
		rows.put("Created", Formats.time(subscription.getCreatedAt()));
		rows.put("Updated", Formats.time(subscription.getUpdatedAt()));
		return rows;
	}

	/**
	 * Returns the JSON of a subscription.
	 *
	 * @param subscription the subscription
	 * @return the JSON object
	 */
	public static Map<String, Object> subscriptionJson(Subscription subscription) {
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("id", subscription.getId());
		json.put("userId", subscription.getUserId());
		json.put("planId", subscription.getPlanId());
		json.put("planName", subscription.getPlanName().orElse(null));
		json.put("status", subscription.getStatus().toString());
		json.put("startDate", subscription.getStartDate());
		json.put("endDate", subscription.getEndDate());
		json.put("createdAt", subscription.getCreatedAt());
		json.put("updatedAt", subscription.getUpdatedAt());
		return json;
	}

	// ---- Blacklist -----------------------------------------------------------------------------

	/**
	 * Returns the table row of a blacklist entry.
	 *
	 * @param entry the entry
	 * @return the row
	 */
	public static List<String> blacklistRow(BlacklistedNode entry) {
		return List.of(Long.toString(entry.getId()),
				entry.getNodeId().map(id -> id.toBase58String()).orElse(Formats.NONE),
				Formats.text(entry.getNodeHost()),
				Formats.yesNo(entry.isAuto()),
				Formats.brief(entry.getReason().orElse(null), BRIEF),
				Formats.time(entry.getCreatedAt()));
	}

	/**
	 * Returns the details of a blacklist entry.
	 *
	 * @param entry the entry
	 * @return the details, keyed by label
	 */
	public static Map<String, String> blacklist(BlacklistedNode entry) {
		Map<String, String> rows = new LinkedHashMap<>();
		rows.put("Entry id", Long.toString(entry.getId()));
		rows.put("Node id", entry.getNodeId().map(id -> id.toBase58String()).orElse(Formats.NONE));
		rows.put("Host", Formats.text(entry.getNodeHost()));
		rows.put("Automatic", Formats.yesNo(entry.isAuto()));
		rows.put("Reason", Formats.text(entry.getReason()));
		rows.put("Created", Formats.time(entry.getCreatedAt()));
		rows.put("Updated", Formats.time(entry.getUpdatedAt()));
		return rows;
	}

	/**
	 * Returns the JSON of a blacklist entry.
	 *
	 * @param entry the entry
	 * @return the JSON object
	 */
	public static Map<String, Object> blacklistJson(BlacklistedNode entry) {
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("id", entry.getId());
		json.put("nodeId", entry.getNodeId().orElse(null));
		json.put("nodeHost", entry.getNodeHost().orElse(null));
		json.put("auto", entry.isAuto());
		json.put("reason", entry.getReason().orElse(null));
		json.put("createdAt", entry.getCreatedAt());
		json.put("updatedAt", entry.getUpdatedAt());
		return json;
	}

	// ---- Federation ----------------------------------------------------------------------------

	/**
	 * Returns the table row of a federated node.
	 *
	 * @param node the node
	 * @return the row
	 */
	public static List<String> federatedNodeRow(FederatedNode node) {
		return List.of(node.getId().toBase58String(),
				Formats.text(node.getName()),
				Formats.yesNo(node.isFederated()),
				Integer.toString(node.getReputation()),
				Formats.text(node.getApiEndpoint()),
				Formats.time(node.getUpdatedAt()));
	}

	/**
	 * Returns the details of a federated node.
	 *
	 * @param node the node
	 * @return the details, keyed by label
	 */
	public static Map<String, String> federatedNode(FederatedNode node) {
		Map<String, String> rows = new LinkedHashMap<>();
		rows.put("Node id", node.getId().toBase58String());
		rows.put("Name", Formats.text(node.getName()));
		rows.put("Federated", Formats.yesNo(node.isFederated()));
		rows.put("Reputation", Integer.toString(node.getReputation()));
		rows.put("API endpoint", Formats.text(node.getApiEndpoint()));
		rows.put("Addresses", Formats.list(node.getAddresses()));
		rows.put("Software", Formats.text((node.getSoftware().orElse("") + " " + node.getVersion().orElse("")).strip()));
		rows.put("Website", Formats.text(node.getWebsite()));
		rows.put("Contact", Formats.text(node.getContact()));
		rows.put("Description", Formats.text(node.getDescription()));
		rows.put("Created", Formats.time(node.getCreatedAt()));
		rows.put("Updated", Formats.time(node.getUpdatedAt()));
		return rows;
	}

	/**
	 * Returns the JSON of a federated node.
	 *
	 * @param node the node
	 * @return the JSON object
	 */
	public static Map<String, Object> federatedNodeJson(FederatedNode node) {
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("id", node.getId());
		json.put("name", node.getName().orElse(null));
		json.put("federated", node.isFederated());
		json.put("reputation", node.getReputation());
		json.put("apiEndpoint", node.getApiEndpoint().orElse(null));
		json.put("addresses", node.getAddresses());
		json.put("software", node.getSoftware().orElse(null));
		json.put("version", node.getVersion().orElse(null));
		json.put("logo", node.getLogo().orElse(null));
		json.put("website", node.getWebsite().orElse(null));
		json.put("contact", node.getContact().orElse(null));
		json.put("description", node.getDescription().orElse(null));
		json.put("createdAt", node.getCreatedAt());
		json.put("updatedAt", node.getUpdatedAt());
		return json;
	}

	/**
	 * Returns the table row of a federated service.
	 *
	 * @param service the service
	 * @return the row
	 */
	public static List<String> federatedServiceRow(FederatedService service) {
		return List.of(Formats.text(service.getServiceId()),
				Formats.text(service.getServiceName()),
				service.getPeerId().toBase58String(),
				Formats.text(service.getEndpoint()),
				service.isFederationEnabled() ? "enabled" : "disabled");
	}

	/**
	 * Returns the JSON of a federated service.
	 *
	 * @param service the service
	 * @return the JSON object
	 */
	public static Map<String, Object> federatedServiceJson(FederatedService service) {
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("serviceId", service.getServiceId().orElse(null));
		json.put("serviceName", service.getServiceName().orElse(null));
		json.put("peerId", service.getPeerId());
		json.put("nodeId", service.getNodeId().orElse(null));
		json.put("endpoint", service.getEndpoint().orElse(null));
		json.put("fingerprint", service.getFingerprint());
		json.put("federationEnabled", service.isFederationEnabled());
		json.put("extra", service.getExtra().orElse(null));
		return json;
	}

	/**
	 * Returns the table row of a federation proposal.
	 *
	 * @param proposal the proposal
	 * @return the row
	 */
	public static List<String> proposalRow(FederationProposal proposal) {
		return List.of(Long.toString(proposal.getId()),
				proposal.getNodeId().toBase58String(),
				Formats.text(proposal.getName()),
				proposal.getRole().toString(),
				proposal.getStatus().toString(),
				Formats.time(proposal.getProposedAt()),
				Formats.time(proposal.getConfirmedAt()));
	}

	/**
	 * Returns the details of a federation proposal.
	 *
	 * @param proposal the proposal
	 * @return the details, keyed by label
	 */
	public static Map<String, String> proposal(FederationProposal proposal) {
		Map<String, String> rows = new LinkedHashMap<>();
		rows.put("Proposal id", Long.toString(proposal.getId()));
		rows.put("Node id", proposal.getNodeId().toBase58String());
		rows.put("Name", Formats.text(proposal.getName()));
		rows.put("Role", proposal.getRole() == FederationProposal.Role.OFFER ? "offer (this node proposed)" :
				"answer (this node was proposed to)");
		rows.put("Status", proposal.getStatus().toString());
		rows.put("API endpoint", Formats.text(proposal.getApiEndpoint()));
		rows.put("Addresses", Formats.list(proposal.getAddresses()));
		rows.put("Software", Formats.text((proposal.getSoftware().orElse("") + " " + proposal.getVersion().orElse("")).strip()));
		rows.put("Website", Formats.text(proposal.getWebsite()));
		rows.put("Contact", Formats.text(proposal.getContact()));
		rows.put("Proposed", Formats.time(proposal.getProposedAt()));
		rows.put("Confirmed", Formats.time(proposal.getConfirmedAt()));
		rows.put("Created", Formats.time(proposal.getCreatedAt()));
		rows.put("Updated", Formats.time(proposal.getUpdatedAt()));
		return rows;
	}

	/**
	 * Returns the JSON of a federation proposal.
	 *
	 * @param proposal the proposal
	 * @return the JSON object
	 */
	public static Map<String, Object> proposalJson(FederationProposal proposal) {
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("id", proposal.getId());
		json.put("nodeId", proposal.getNodeId());
		json.put("name", proposal.getName().orElse(null));
		json.put("role", proposal.getRole().toString());
		json.put("status", proposal.getStatus().toString());
		json.put("apiEndpoint", proposal.getApiEndpoint().orElse(null));
		json.put("addresses", proposal.getAddresses());
		json.put("software", proposal.getSoftware().orElse(null));
		json.put("version", proposal.getVersion().orElse(null));
		json.put("logo", proposal.getLogo().orElse(null));
		json.put("website", proposal.getWebsite().orElse(null));
		json.put("contact", proposal.getContact().orElse(null));
		json.put("proposedAt", proposal.getProposedAt());
		json.put("confirmedAt", proposal.getConfirmedAt());
		json.put("createdAt", proposal.getCreatedAt());
		json.put("updatedAt", proposal.getUpdatedAt());
		return json;
	}
}
