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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.Writer;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.TypeConversionException;

import io.bosonnetwork.Id;
import io.bosonnetwork.cli.common.CliEnvironment;
import io.bosonnetwork.cli.common.Formats;
import io.bosonnetwork.cli.common.testing.CliRunner;
import io.bosonnetwork.cli.common.testing.CliRunner.Result;
import io.bosonnetwork.cli.testing.StubDirector;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.cwt.Claim;
import io.bosonnetwork.cwt.SignedCwt;
import io.bosonnetwork.utils.Base58;

/**
 * Tests of {@code boson-director-cli}, run in process against a stub Director.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BosonDirectorCliTests {
	private final Id nodeId = Id.random();
	private final Signature.KeyPair adminKey = Signature.KeyPair.random();
	private StubDirector director;

	@BeforeAll
	void startDirector() throws Exception {
		director = StubDirector.start();
	}

	@AfterAll
	void stopDirector() throws Exception {
		director.close();
	}

	@BeforeEach
	void resetDirector() {
		director.reset();
		director.nodeId(nodeId);
	}

	// An administrator configured through the environment, as a script would be.
	private CliRunner admin(Path configDir) {
		return new CliRunner(BosonDirectorCli::new, configDir)
				.env("BOSON_DIRECTOR_URL", director.url())
				.env("BOSON_NODE_ID", nodeId.toBase58String())
				.env("BOSON_PRIVATE_KEY", Base58.encode(adminKey.privateKey().bytes()));
	}

	private static String profileJson(Id userId, String name) {
		return "{\"id\": \"" + userId + "\", \"name\": \"" + name + "\", \"planName\": \"Free\", \"admin\": false, " +
				"\"createdAt\": 1700000000000, \"updatedAt\": 1700000000000, \"passphraseProtected\": false}";
	}

	private static String proposalJson(Id nodeId, String role, String status, long answeredAt) {
		return "{\"id\":7,\"nodeId\":\"" + nodeId + "\",\"role\":\"" + role + "\",\"status\":\"" + status +
				"\",\"note\":\"Partner\",\"createdAt\":1,\"updatedAt\":2,\"answeredAt\":" + answeredAt + "}";
	}

	private static String hostJson(String host, int verified, int failed, int notFederating, long checkedAt) {
		return "{\"host\":" + (host == null ? "null" : "\"" + host + "\"") + ",\"verifiedNodes\":" + verified +
				",\"failedNodes\":" + failed + ",\"notFederatingNodes\":" + notFederating + ",\"lastCheckedAt\":" + checkedAt + "}";
	}

	@Test
	void everyCommandHasHelp(@TempDir Path dir) {
		CliEnvironment environment = new CliEnvironment(Map.of(), dir, System.in, null,
				new PrintWriter(Writer.nullWriter()), new PrintWriter(Writer.nullWriter()));
		List<String[]> commands = new ArrayList<>();
		collect(new CommandLine(new BosonDirectorCli(environment)), new ArrayList<>(), commands);
		assertTrue(commands.size() > 40, "commands: " + commands.size());

		for (String[] command : commands) {
			List<String> args = new ArrayList<>(List.of(command));
			args.add("--help");
			Result result = new CliRunner(BosonDirectorCli::new, dir).run(args.toArray(new String[0]));
			assertEquals(0, result.exitCode(), () -> String.join(" ", command) + ": " + result);
		}
	}

	private static void collect(CommandLine commandLine, List<String> path, List<String[]> commands) {
		for (var entry : commandLine.getSubcommands().entrySet()) {
			if (entry.getKey().equals("help"))
				continue;
			List<String> sub = new ArrayList<>(path);
			sub.add(entry.getKey());
			assertTrue(entry.getValue().getCommandSpec().usageMessage().description().length > 0,
					() -> String.join(" ", sub) + " has no description");
			commands.add(sub.toArray(new String[0]));
			collect(entry.getValue(), sub, commands);
		}
	}

	@Test
	void theClientSettingsAreNotTheAdminToolsOwn(@TempDir Path dir) throws Exception {
		CliRunner cli = new CliRunner(BosonDirectorCli::new, dir);
		Result set = cli.run("config", "set", "proxyUpstream", "localhost:8080");
		assertEquals(2, set.exitCode(), set::toString);
		assertTrue(set.err().contains("Unknown setting 'proxyUpstream'"), set::toString);

		Path config = dir.resolve("boson").resolve("director-cli.yaml");
		Files.createDirectories(config.getParent());
		Files.writeString(config, "url: " + director.url() + "\ndeviceIdentity: device.identity\n");
		Result read = cli.run("node", "id");
		assertEquals(3, read.exitCode(), read::toString);
		assertTrue(read.err().contains("Unknown setting 'deviceIdentity'"), read::toString);

		Result option = cli.run("--device-identity", "x", "node", "id");
		assertEquals(2, option.exitCode(), option::toString);
	}

	@Test
	void theSetupWizardsConfigurationIsRead(@TempDir Path dir) throws Exception {
		Path config = dir.resolve("boson").resolve("director-cli.yaml");
		Files.createDirectories(config.getParent());
		Files.writeString(config, "nodeId: " + nodeId + "\nurl: " + director.url() + "\n# resolve:\nprivateKey: " +
				Base58.encode(adminKey.privateKey().bytes()) + "\n");
		director.reply("GET", "/api/v1/admin/users/" + nodeId, 200, profileJson(nodeId, "Root"));

		Result result = new CliRunner(BosonDirectorCli::new, dir).run("--json", "identity", "show");
		assertEquals(0, result.exitCode(), result::toString);
		assertEquals(Id.of(adminKey.publicKey().bytes()).toBase58String(), result.json().get("id"));
	}

	@Test
	void usersAreListedAPageAtATime(@TempDir Path dir) {
		Id userId = Id.random();
		director.reply("GET", "/api/v1/admin/users", 200, "{\"page\": 1, \"pageSize\": 20, \"totalPages\": 3, " +
				"\"totalItems\": 45, \"items\": [" + profileJson(userId, "Bob") + "]}");

		Result page = admin(dir).run("user", "list", "--sort", "createdAt:desc");
		assertEquals(0, page.exitCode(), page::toString);
		assertTrue(page.out().contains("USER ID"), page::toString);
		assertTrue(page.out().contains(userId.toBase58String()), page::toString);
		assertTrue(page.out().contains("Page 1 of 3, 45 users in all. Next page: --page 2."), page::toString);
		String query = director.requests("GET", "/api/v1/admin/users").get(0).query();
		assertTrue(query.contains("page=1") && query.contains("pageSize=20") && query.contains("orderBy="), query);

		director.requests().clear();
		Result all = admin(dir).run("user", "list", "--all", "--json");
		assertEquals(0, all.exitCode(), all::toString);
		assertEquals(3, director.requests("GET", "/api/v1/admin/users").size());
		assertEquals(3, ((List<?>) all.json().get("items")).size());

		Result conflicting = admin(dir).run("user", "list", "--all", "--page", "2");
		assertEquals(2, conflicting.exitCode(), conflicting::toString);
	}

	@Test
	void aUserIsAddedWithThePassphraseFromStandardInput(@TempDir Path dir) {
		Id userId = Id.random();
		director.reply("POST", "/api/v1/admin/users", 200, "");

		Result result = admin(dir).runWithInput("initial secret\n", "user", "add", userId.toBase58String(), "--name", "Bob");
		assertEquals(0, result.exitCode(), result::toString);
		Map<String, Object> body = director.requests("POST", "/api/v1/admin/users").get(0).json();
		assertEquals(userId.toBase58String(), body.get("userId"));
		assertEquals("initial secret", body.get("passphrase"));
		assertEquals("Bob", body.get("userName"));
	}

	@Test
	void grantingAdminNeedsConfirmation(@TempDir Path dir) {
		Id userId = Id.random();
		director.reply("PUT", "/api/v1/admin/users/" + userId, 200, "");

		Result unconfirmed = admin(dir).run("user", "grant-admin", userId.toBase58String());
		assertEquals(2, unconfirmed.exitCode(), unconfirmed::toString);
		assertTrue(director.requests("PUT", "/api/v1/admin/users/" + userId).isEmpty());

		Result granted = admin(dir).run("user", "grant-admin", userId.toBase58String(), "--yes");
		assertEquals(0, granted.exitCode(), granted::toString);
		assertEquals(Boolean.TRUE, director.requests("PUT", "/api/v1/admin/users/" + userId).get(0).json().get("admin"));
	}

	// An enrollment request the stub opens and an app claims.
	private Id enrollment(boolean withUrl) {
		Id requestId = Id.random();
		director.reply("POST", "/api/v1/admin/enrollments", 201, "{\"requestId\": \"" + requestId + "\", \"nodeId\": \"" +
				nodeId + "\", \"expiresIn\": 600" + (withUrl ? ", \"url\": \"https://node.example\"" : "") + "}");
		director.reply("GET", "/api/v1/admin/enrollments/" + requestId, 200, "{\"requestId\": \"" + requestId +
				"\", \"state\": \"claimed\", \"expiresIn\": 590, \"triesLeft\": 3, \"userId\": \"" + Id.random() +
				"\", \"deviceId\": \"" + Id.random() + "\", \"deviceName\": \"Pixel 9\", \"app\": \"Boson Identity\", " +
				"\"member\": false, \"requestedFrom\": \"192.0.2.7\"}");
		director.reply("DELETE", "/api/v1/admin/enrollments/" + requestId, 204, "");
		return requestId;
	}

	@Test
	void anAdministratorIsEnrolledWithTheNumberOnThePhone(@TempDir Path dir) {
		Id requestId = enrollment(true);
		Id userId = Id.random();
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		director.handle("POST", "/api/v1/admin/enrollments/" + requestId + "/approve", request ->
				calls.incrementAndGet() == 1 ?
						StubDirector.Reply.json(200, "{\"approved\": false, \"triesLeft\": 2}") :
						StubDirector.Reply.json(200, "{\"approved\": true, \"userId\": \"" + userId + "\", \"created\": true}"));

		Result result = admin(dir).runWithInput("x\n12\n47\n", "user", "enroll-admin", "--qr", "ASCII");
		assertEquals(0, result.exitCode(), result::toString);
		String code = "boson:enroll:1:" + requestId + ":" + nodeId + ":https://node.example";
		assertTrue(result.out().contains(code), result::toString);
		assertTrue(result.out().contains("##"), "the QR code is drawn: " + result);
		assertTrue(result.out().contains("Pixel 9 (Boson Identity)"), result::toString);
		assertTrue(result.out().contains("That is not a number"), result::toString);
		assertTrue(result.out().contains("2 tries left"), result::toString);
		assertTrue(result.out().contains("User " + userId + " is now an administrator of this node, with a new account."),
				result::toString);

		List<StubDirector.Request> approvals = director.requests("POST", "/api/v1/admin/enrollments/" + requestId + "/approve");
		assertEquals(2, approvals.size());
		assertEquals(12, ((Number) approvals.get(0).json().get("number")).intValue());
		assertEquals(47, ((Number) approvals.get(1).json().get("number")).intValue());
		assertTrue(director.requests("DELETE", "/api/v1/admin/enrollments/" + requestId).isEmpty());
	}

	@Test
	void anEnrollmentIsDeniedWithN(@TempDir Path dir) {
		Id requestId = enrollment(true);
		Result result = admin(dir).runWithInput("n\n", "user", "enroll-admin");
		assertEquals(1, result.exitCode(), result::toString);
		assertTrue(result.err().contains("Denied; nothing was changed."), result::toString);
		assertFalse(director.requests("DELETE", "/api/v1/admin/enrollments/" + requestId).isEmpty());
		assertTrue(director.requests("POST", "/api/v1/admin/enrollments/" + requestId + "/approve").isEmpty());
	}

	@Test
	void anEnrollmentNeedsTheAddressPhonesReach(@TempDir Path dir) {
		Id requestId = enrollment(false);
		Result result = admin(dir).runWithInput("", "user", "enroll-admin");
		assertEquals(2, result.exitCode(), result::toString);
		assertTrue(result.err().contains("--public-url"), result::toString);
		// The request it opened is not left behind.
		assertFalse(director.requests("DELETE", "/api/v1/admin/enrollments/" + requestId).isEmpty());

		director.reply("POST", "/api/v1/admin/enrollments/" + requestId + "/approve", 200,
				"{\"approved\": true, \"userId\": \"" + Id.random() + "\", \"created\": false}");
		Result given = admin(dir).runWithInput("47\n", "user", "enroll-admin", "--public-url", "http://10.0.0.5:8080/");
		assertEquals(0, given.exitCode(), given::toString);
		assertTrue(given.out().contains(":" + nodeId + ":http://10.0.0.5:8080"), given::toString);
	}

	@Test
	void anEnrollmentHasNoJsonOutput(@TempDir Path dir) {
		enrollment(true);
		Result result = admin(dir).run("--json", "user", "enroll-admin");
		assertEquals(2, result.exitCode(), result::toString);
		assertTrue(director.requests("POST", "/api/v1/admin/enrollments").isEmpty());
	}

	@Test
	void anUnknownUserIsNotFound(@TempDir Path dir) {
		Id userId = Id.random();
		Result result = admin(dir).run("user", "show", userId.toBase58String());
		assertEquals(4, result.exitCode(), result::toString);
		assertTrue(result.err().contains("There is no user " + userId), result::toString);
	}

	@Test
	void theBlacklistTellsNodesFromHosts(@TempDir Path dir) {
		Id blocked = Id.random();
		director.reply("POST", "/api/v1/admin/blacklist", 200, "{\"id\": 7, \"nodeHost\": \"bad.example.com\", \"auto\": false}");

		Result node = admin(dir).run("blacklist", "add", blocked.toBase58String(), "--reason", "spam");
		assertEquals(0, node.exitCode(), node::toString);
		Map<String, Object> nodeBody = director.requests("POST", "/api/v1/admin/blacklist").get(0).json();
		assertEquals(blocked.toBase58String(), nodeBody.get("nodeId"));
		assertEquals("spam", nodeBody.get("reason"));

		Result host = admin(dir).run("blacklist", "add", "bad.example.com");
		assertEquals(0, host.exitCode(), host::toString);
		assertEquals("bad.example.com", director.requests("POST", "/api/v1/admin/blacklist").get(1).json().get("nodeHost"));

		Result digits = admin(dir).run("blacklist", "add", "12345");
		assertEquals(2, digits.exitCode(), digits::toString);
		assertEquals(2, director.requests("POST", "/api/v1/admin/blacklist").size());
	}

	@Test
	void plansAreNamedByIdOrName(@TempDir Path dir) {
		String plan = "{\"id\": 3, \"name\": \"Pro\", \"price\": 9.99, \"currency\": \"USD\", \"cycle\": \"monthly\", \"active\": true}";
		director.reply("GET", "/api/v1/admin/plans/3", 200, plan);
		director.reply("GET", "/api/v1/admin/plans/Pro", 200, plan);

		Result byId = admin(dir).run("plan", "show", "3");
		assertEquals(0, byId.exitCode(), byId::toString);
		assertTrue(byId.out().contains("9.99 USD monthly"), byId::toString);

		Result byName = admin(dir).run("--json", "plan", "show", "Pro");
		assertEquals(0, byName.exitCode(), byName::toString);
		assertEquals("9.99", byName.json().get("price"));

		Result missing = admin(dir).run("plan", "show", "Missing");
		assertEquals(4, missing.exitCode(), missing::toString);
		assertTrue(missing.err().contains("There is no plan Missing"), missing::toString);
	}

	@Test
	void aSubscriptionEndsAtARelativeTime(@TempDir Path dir) {
		Id userId = Id.random();
		director.reply("POST", "/api/v1/admin/subscriptions", 200, "{\"id\": 11, \"userId\": \"" + userId + "\", \"planId\": 3, " +
				"\"planName\": \"Pro\", \"status\": \"active\", \"startDate\": 1, \"endDate\": 4102444800000}");

		Result result = admin(dir).run("subscription", "add", userId.toBase58String(), "--plan", "Pro", "--end", "+30d");
		assertEquals(0, result.exitCode(), result::toString);
		Map<String, Object> body = director.requests("POST", "/api/v1/admin/subscriptions").get(0).json();
		assertEquals("Pro", body.get("plan"));
		assertEquals("active", body.get("status"));
		long end = ((Number) body.get("endDate")).longValue();
		long expected = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30);
		assertTrue(Math.abs(end - expected) < TimeUnit.DAYS.toMillis(1), "endDate " + end);

		Result backwards = admin(dir).run("subscription", "add", userId.toBase58String(), "--plan", "Pro",
				"--start", "2030-01-02", "--end", "2030-01-01");
		assertEquals(2, backwards.exitCode(), backwards::toString);
	}

	@Test
	void admittingANodeSendsTheStateAndNote(@TempDir Path dir) {
		Id peer = Id.random();
		director.reply("PUT", "/api/v1/admin/federation/nodes/" + peer, 200,
				"{\"nodeId\":\"" + peer + "\",\"state\":\"active\",\"origin\":\"admin\",\"note\":\"Partner\"," +
						"\"admission\":\"ACTIVE\",\"createdAt\":1,\"updatedAt\":1}");

		Result result = admin(dir).run("federation", "node", "admit", peer.toBase58String(), "--note", "Partner");
		assertEquals(0, result.exitCode(), result::toString);
		assertTrue(result.out().contains("Admitted node " + peer), result::toString);

		Map<String, Object> body = director.requests("PUT", "/api/v1/admin/federation/nodes/" + peer).get(0).json();
		assertEquals("active", body.get("state"));
		assertEquals("Partner", body.get("note"));
	}

	@Test
	void suspendingANodeSendsTheStateAndNote(@TempDir Path dir) {
		Id peer = Id.random();
		String path = "/api/v1/admin/federation/nodes/" + peer;
		director.reply("PUT", path, 200,
				"{\"nodeId\":\"" + peer + "\",\"state\":\"suspended\",\"origin\":\"admin\",\"note\":\"Maintenance\"," +
						"\"admission\":\"SUSPENDED\",\"createdAt\":1,\"updatedAt\":2}");

		Result result = admin(dir).run("federation", "node", "suspend", peer.toBase58String(), "--note", " Maintenance ");
		assertEquals(0, result.exitCode(), result::toString);
		assertTrue(result.out().contains("Suspended node " + peer + "."), result::toString);
		Map<String, Object> body = director.requests("PUT", path).get(0).json();
		assertEquals("suspended", body.get("state"));
		assertEquals("Maintenance", body.get("note"));

		Result json = admin(dir).run("federation", "node", "suspend", peer.toBase58String(), "--note", "Maintenance", "--json");
		assertEquals(0, json.exitCode(), json::toString);
		assertEquals(peer.toBase58String(), json.json().get("nodeId"));
		assertEquals("suspended", json.json().get("state"));
		assertEquals("admin", json.json().get("origin"));
		assertEquals("SUSPENDED", json.json().get("admission"));
		assertEquals(Boolean.FALSE, json.json().get("admitted"));
	}

	@Test
	void removingARelationshipNeedsConfirmation(@TempDir Path dir) {
		Id peer = Id.random();
		String path = "/api/v1/admin/federation/nodes/" + peer;
		director.reply("DELETE", path, 204, "");

		Result unconfirmed = admin(dir).run("federation", "node", "remove", peer.toBase58String());
		assertEquals(2, unconfirmed.exitCode(), unconfirmed::toString);
		assertTrue(unconfirmed.err().contains("--yes"), unconfirmed::toString);
		assertTrue(director.requests("DELETE", path).isEmpty());

		Result piped = admin(dir).runWithInput("yes\n", "federation", "node", "remove", peer.toBase58String());
		assertEquals(2, piped.exitCode(), piped::toString);
		assertTrue(director.requests("DELETE", path).isEmpty());

		Result result = admin(dir).run("federation", "node", "remove", peer.toBase58String(), "--yes");
		assertEquals(0, result.exitCode(), result::toString);
		assertTrue(result.out().contains("Removed the relationship with node " + peer + "."), result::toString);
		assertEquals(1, director.requests("DELETE", path).size());
		assertTrue(director.requests("DELETE", path).get(0).body().isEmpty());

		Result json = admin(dir).run("federation", "node", "remove", peer.toBase58String(), "--yes", "--json");
		assertEquals(0, json.exitCode(), json::toString);
		assertEquals(peer.toBase58String(), json.json().get("nodeId"));
		assertEquals(Boolean.TRUE, json.json().get("removed"));
		assertEquals(2, director.requests("DELETE", path).size());
	}

	@Test
	void removingAnUnknownRelationshipIsNotFound(@TempDir Path dir) {
		Id peer = Id.random();
		String path = "/api/v1/admin/federation/nodes/" + peer;
		director.reply("DELETE", path, 404, "Not Found");

		Result result = admin(dir).run("federation", "node", "remove", peer.toBase58String(), "--yes", "--json");
		assertEquals(4, result.exitCode(), result::toString);
		assertTrue(result.err().contains("This node has no relationship with node " + peer + "."), result::toString);
		assertTrue(result.err().contains("federation node list"), result::toString);
		assertTrue(result.out().isEmpty(), result::toString);
		assertEquals(1, director.requests("DELETE", path).size());
	}

	@Test
	void federationListsSendNodeFiltersToTheServer(@TempDir Path dir) throws Exception {
		Id home = Id.random();
		Id peer = Id.random();
		director.nodeId(home);
		String[][] commands = {{"node", "list", "nodes"}, {"node", "health", "health"}, {"proposal", "list", "proposals"}};
		for (String[] command : commands) {
			String path = "/api/v1/admin/federation/" + command[2];
			director.reply("GET", path, 200, "{\"page\":2,\"pageSize\":3,\"totalItems\":0,\"totalPages\":0,\"items\":[]}");
			for (String id : List.of(peer.toBase58String(), peer.toHexString(), "did:boson:" + peer, " " + peer + " ")) {
				List<String> args = new ArrayList<>(List.of("--node-id", home.toBase58String(), "federation", command[0], command[1],
						"--node", id, "--page", "2", "--page-size", "3", "--json"));
				if (command[0].equals("proposal"))
					args.addAll(List.of("--role", "ANSWER", "--status", "PENDING"));

				director.requests().clear();
				Result result = admin(dir).run(args.toArray(new String[0]));
				assertEquals(0, result.exitCode(), result::toString);
				StubDirector.Request request = director.requests("GET", path).get(0);
				String query = request.query();
				assertTrue(query.contains("nodeId=" + peer.toBase58String()), query);
				assertTrue(query.contains("page=2") && query.contains("pageSize=3"), query);
				if (command[0].equals("proposal"))
					assertTrue(query.contains("role=answer") && query.contains("status=pending"), query);
				SignedCwt token = SignedCwt.parse(request.header("Authorization").substring("Bearer ".length()));
				assertEquals(home, token.getClaimAsId(Claim.AUDIENCE.getValue()));
				assertEquals(2, ((Number) result.json().get("page")).longValue());
				assertEquals(3, ((Number) result.json().get("pageSize")).longValue());
				assertEquals(0, ((Number) result.json().get("totalItems")).longValue());
				assertTrue(((List<?>) result.json().get("items")).isEmpty());
			}
		}
	}

	@Test
	void invalidFederationNodeFiltersNeverContactTheDirector(@TempDir Path dir) {
		String[][] commands = {{"node", "list"}, {"node", "health"}, {"proposal", "list"}};
		for (String[] command : commands) {
			for (String id : List.of("", "invalid!", "123", "0x1234")) {
				Result result = admin(dir).run("federation", command[0], command[1], "--node", id);
				assertEquals(2, result.exitCode(), result::toString);
				assertTrue(result.err().contains("not a valid id"), result::toString);
				assertTrue(director.requests().isEmpty());
			}
		}
	}

	@Test
	void filteredFederationListsReportNoMatches(@TempDir Path dir) {
		Id peer = Id.random();
		String[][] commands = {
				{"node", "list", "nodes", "No relationship with node " + peer + "."},
				{"node", "health", "health", "No activity for node " + peer + " within the last day."},
				{"proposal", "list", "proposals", "No matching proposals."}};
		for (String[] command : commands) {
			String path = "/api/v1/admin/federation/" + command[2];
			director.reply("GET", path, 200, "{\"page\":1,\"pageSize\":100,\"totalItems\":0,\"totalPages\":0,\"items\":[]}");

			Result result = admin(dir).run("federation", command[0], command[1], "--node", peer.toBase58String(), "--all");
			assertEquals(0, result.exitCode(), result::toString);
			assertTrue(result.out().contains(command[3]), result::toString);
			String query = director.requests("GET", path).get(0).query();
			assertTrue(query.contains("nodeId=" + peer.toBase58String()) && query.contains("pageSize=100"), query);
			assertEquals(1, director.requests("GET", path).size());
			assertTrue(director.requests("GET", "/api/v1/admin/federation/nodes/" + peer).isEmpty());
		}
	}

	@Test
	void nodeListsAndDetailsKeepTheRelationshipOrigin(@TempDir Path dir) {
		Id peer = Id.random();
		String path = "/api/v1/admin/federation/nodes/" + peer;
		for (String origin : List.of("admin", "protocol", "auto", "")) {
			String node = "{\"nodeId\":\"" + peer + "\",\"state\":\"active\",\"admission\":\"ACTIVE\"" +
					(origin.isEmpty() ? "" : ",\"origin\":\"" + origin + "\"") + "}";
			director.reply("GET", path, 200, node);
			director.reply("GET", "/api/v1/admin/federation/nodes", 200,
					"{\"page\":1,\"pageSize\":20,\"totalItems\":1,\"totalPages\":1,\"items\":[" + node + "]}");

			Result listed = admin(dir).run("federation", "node", "list");
			assertEquals(0, listed.exitCode(), listed::toString);
			assertTrue(listed.out().contains("ORIGIN"), listed::toString);
			String row = listed.out().lines().filter(line -> line.startsWith(peer.toBase58String())).findFirst().orElseThrow();
			assertEquals(origin.isEmpty() ? "-" : origin, row.split("\\s{2,}")[2]);

			Result shown = admin(dir).run("federation", "node", "show", peer.toBase58String());
			assertEquals(0, shown.exitCode(), shown::toString);
			String detail = shown.out().lines().filter(line -> line.startsWith("Origin:")).findFirst().orElseThrow();
			assertEquals(origin.isEmpty() ? "-" : origin, detail.substring("Origin:".length()).strip());

			Result json = admin(dir).run("federation", "node", "show", peer.toBase58String(), "--json");
			assertEquals(0, json.exitCode(), json::toString);
			assertTrue(json.json().containsKey("origin"), json::toString);
			assertEquals(origin.isEmpty() ? null : origin, json.json().get("origin"));
			assertEquals("active", json.json().get("state"));
			assertEquals("ACTIVE", json.json().get("admission"));
			assertEquals(Boolean.TRUE, json.json().get("admitted"));

			Result page = admin(dir).run("federation", "node", "list", "--json");
			assertEquals(0, page.exitCode(), page::toString);
			Map<?, ?> item = (Map<?, ?>) ((List<?>) page.json().get("items")).get(0);
			assertTrue(item.containsKey("origin"), page::toString);
			assertEquals(origin.isEmpty() ? null : origin, item.get("origin"));
		}
	}

	@Test
	void nodeAdmissionAndReachShowThePolicyDecision(@TempDir Path dir) {
		Id peer = Id.random();
		Map<String, List<String>> policies = Map.of(
				"OPEN", List.of("open (admitted)", "open (allowed)"),
				"PENDING", List.of("pending (pending)", "pending (held)"),
				"SUSPENDED", List.of("suspended (refused)", "suspended (refused)"));
		for (var policy : policies.entrySet()) {
			director.reply("GET", "/api/v1/admin/federation/nodes/" + peer, 200,
					"{\"nodeId\":\"" + peer + "\",\"admission\":\"" + policy.getKey() + "\",\"reach\":\"" + policy.getKey() + "\"}");

			Result result = admin(dir).run("federation", "node", "show", peer.toBase58String());
			assertEquals(0, result.exitCode(), result::toString);
			assertTrue(result.out().lines().anyMatch(line -> line.startsWith("Admission:") && line.endsWith(policy.getValue().get(0))),
					result::toString);
			assertTrue(result.out().lines().anyMatch(line -> line.startsWith("Sending to it:") && line.endsWith(policy.getValue().get(1))),
					result::toString);
			assertFalse(result.out().contains("(sent)"), result::toString);

			Result json = admin(dir).run("federation", "node", "show", peer.toBase58String(), "--json");
			assertEquals(0, json.exitCode(), json::toString);
			assertEquals(policy.getKey(), json.json().get("admission"));
			assertEquals(policy.getKey(), json.json().get("reach"));
			assertEquals(policy.getKey().equals("OPEN"), json.json().get("admitted"));
		}
	}

	@Test
	void anAbsentOrUnknownAdmissionIsNotRefused(@TempDir Path dir) {
		Id peer = Id.random();
		for (String admission : List.of("", ",\"admission\":\"FUTURE_REASON\"")) {
			director.reply("GET", "/api/v1/admin/federation/nodes/" + peer, 200, "{\"nodeId\":\"" + peer + "\"" + admission + "}");

			Result result = admin(dir).run("federation", "node", "show", peer.toBase58String());
			assertEquals(0, result.exitCode(), result::toString);
			String detail = result.out().lines().filter(line -> line.startsWith("Admission:")).findFirst().orElseThrow();
			assertEquals("-", detail.substring("Admission:".length()).strip());
			assertFalse(result.out().contains("(refused)"), result::toString);
		}
	}

	@Test
	void proposingSendsTheNodeAndNote(@TempDir Path dir) {
		Id peer = Id.random();
		director.reply("POST", "/api/v1/admin/federation/proposals", 200,
				"{\"id\":7,\"nodeId\":\"" + peer + "\",\"role\":\"offer\",\"status\":\"pending\"," +
						"\"note\":\"Partner\",\"createdAt\":1,\"updatedAt\":1}");

		Result result = admin(dir).run("federation", "node", "propose", peer.toBase58String(), "--note", "Partner");
		assertEquals(0, result.exitCode(), result::toString);
		assertTrue(result.out().contains("proposal 7"), result::toString);

		Map<String, Object> body = director.requests("POST", "/api/v1/admin/federation/proposals").get(0).json();
		assertEquals(peer.toBase58String(), body.get("nodeId"));
		assertEquals("Partner", body.get("note"));
	}

	@Test
	void proposingReportsTheReturnedOutcome(@TempDir Path dir) {
		Id peer = Id.random();
		String path = "/api/v1/admin/federation/proposals";
		String[][] outcomes = {
				{"offer", "pending", "Proposed to node " + peer + " (proposal 7); waiting for its answer."},
				{"offer", "accepted", "Node " + peer + " accepted. The relationship is active."},
				{"offer", "declined", "Node " + peer + " declined."},
				{"answer", "accepted", "Node " + peer + " had proposed: accepted its proposal. The relationship is active."}};
		for (String[] outcome : outcomes) {
			director.reply("POST", path, 200, proposalJson(peer, outcome[0], outcome[1], outcome[1].equals("pending") ? 0 : 2));

			Result result = admin(dir).run("federation", "node", "propose", peer.toBase58String());
			assertEquals(0, result.exitCode(), result::toString);
			assertTrue(result.out().contains(outcome[2]), result::toString);

			Result json = admin(dir).run("federation", "node", "propose", peer.toBase58String(), "--json");
			assertEquals(0, json.exitCode(), json::toString);
			assertEquals(7, ((Number) json.json().get("id")).longValue());
			assertEquals(peer.toBase58String(), json.json().get("nodeId"));
			assertEquals(outcome[0], json.json().get("role"));
			assertEquals(outcome[1], json.json().get("status"));
		}
	}

	@Test
	void proposingToAnActiveRelationshipReportsTheConflict(@TempDir Path dir) {
		Id peer = Id.random();
		director.reply("POST", "/api/v1/admin/federation/proposals", 409, "The relationship with the node is already active");

		Result result = admin(dir).run("federation", "node", "propose", peer.toBase58String(), "--json");
		assertEquals(1, result.exitCode(), result::toString);
		assertTrue(result.err().contains("The relationship with node " + peer + " is already active."), result::toString);
		assertTrue(result.err().contains("federation node show " + peer), result::toString);
		assertTrue(result.out().isEmpty(), result::toString);
	}

	@Test
	void proposalsCanBeShownAsDetailsOrJson(@TempDir Path dir) {
		Id peer = Id.random();
		String path = "/api/v1/admin/federation/proposals/7";
		String[][] proposals = {{"offer", "pending", "offer (this node proposed)"}, {"answer", "accepted", "answer (the node proposed)"}};
		for (String[] proposal : proposals) {
			long answeredAt = proposal[1].equals("pending") ? 0 : 2;
			director.reply("GET", path, 200, proposalJson(peer, proposal[0], proposal[1], answeredAt));

			Result result = admin(dir).run("federation", "proposal", "show", "7");
			assertEquals(0, result.exitCode(), result::toString);
			assertTrue(result.out().contains(peer.toBase58String()), result::toString);
			assertTrue(result.out().contains(proposal[2]), result::toString);
			assertTrue(result.out().contains(proposal[1]), result::toString);
			assertTrue(result.out().contains("Partner"), result::toString);
			assertEquals(answeredAt > 0, result.out().contains("Answered:"), result::toString);

			Result json = admin(dir).run("federation", "proposal", "show", "7", "--json");
			assertEquals(0, json.exitCode(), json::toString);
			assertEquals(7, ((Number) json.json().get("id")).longValue());
			assertEquals(peer.toBase58String(), json.json().get("nodeId"));
			assertEquals(proposal[0], json.json().get("role"));
			assertEquals(proposal[1], json.json().get("status"));
			assertEquals("Partner", json.json().get("note"));
			assertEquals(1, ((Number) json.json().get("createdAt")).longValue());
			assertEquals(2, ((Number) json.json().get("updatedAt")).longValue());
			assertTrue(json.json().containsKey("answeredAt"), json::toString);
			if (answeredAt == 0)
				assertEquals(null, json.json().get("answeredAt"));
			else
				assertEquals(answeredAt, ((Number) json.json().get("answeredAt")).longValue());
		}
	}

	@Test
	void showingAnUnknownProposalIsNotFound(@TempDir Path dir) {
		director.reply("GET", "/api/v1/admin/federation/proposals/7", 404, "Not Found");

		Result result = admin(dir).run("federation", "proposal", "show", "7", "--json");
		assertEquals(4, result.exitCode(), result::toString);
		assertTrue(result.err().contains("No proposal 7."), result::toString);
		assertTrue(result.err().contains("federation proposal list"), result::toString);
		assertTrue(result.out().isEmpty(), result::toString);
	}

	@Test
	void receivedProposalsCanBeAcceptedOrDeclined(@TempDir Path dir) {
		Id peer = Id.random();
		String[][] answers = {{"accept", "accepted", "Accepted"}, {"decline", "declined", "Declined"}};
		for (String[] answer : answers) {
			String path = "/api/v1/admin/federation/proposals/7/" + answer[0];
			director.reply("POST", path, 200, proposalJson(peer, "answer", answer[1], 2));

			director.requests().clear();
			Result result = admin(dir).run("federation", "proposal", answer[0], "7");
			assertEquals(0, result.exitCode(), result::toString);
			assertTrue(result.out().contains(answer[2] + " node " + peer + "'s proposal."), result::toString);
			assertEquals(1, director.requests("POST", path).size());
			assertTrue(director.requests("POST", path).get(0).body().isEmpty());

			director.requests().clear();
			Result json = admin(dir).run("federation", "proposal", answer[0], "7", "--json");
			assertEquals(0, json.exitCode(), json::toString);
			assertEquals(7, ((Number) json.json().get("id")).longValue());
			assertEquals(peer.toBase58String(), json.json().get("nodeId"));
			assertEquals("answer", json.json().get("role"));
			assertEquals(answer[1], json.json().get("status"));
			assertEquals(2, ((Number) json.json().get("answeredAt")).longValue());
			assertEquals(1, director.requests("POST", path).size());
			assertTrue(director.requests("POST", path).get(0).body().isEmpty());
		}
	}

	@Test
	void answeringAnUnknownProposalIsNotFound(@TempDir Path dir) {
		for (String verb : List.of("accept", "decline")) {
			director.reply("POST", "/api/v1/admin/federation/proposals/7/" + verb, 404, "No such proposal");

			Result result = admin(dir).run("federation", "proposal", verb, "7", "--json");
			assertEquals(4, result.exitCode(), result::toString);
			assertTrue(result.err().contains("No proposal 7."), result::toString);
			assertTrue(result.err().contains("federation proposal list --role answer --status pending"), result::toString);
			assertTrue(result.out().isEmpty(), result::toString);
		}
	}

	@Test
	void answeringAProposalThatWaitsForNoAnswerFails(@TempDir Path dir) {
		for (String verb : List.of("accept", "decline")) {
			director.reply("POST", "/api/v1/admin/federation/proposals/7/" + verb, 409, "not pending");

			Result result = admin(dir).run("federation", "proposal", verb, "7", "--json");
			assertEquals(1, result.exitCode(), result::toString);
			assertTrue(result.err().contains("not one waiting for this node's answer"), result::toString);
			assertTrue(result.err().contains("federation proposal show 7"), result::toString);
			assertTrue(result.out().isEmpty(), result::toString);
		}
	}

	@Test
	void proposalsAreWithdrawnOrRemovedWithoutAnExtraLookup(@TempDir Path dir) {
		String path = "/api/v1/admin/federation/proposals/7";
		director.reply("DELETE", path, 204, "");
		for (String verb : List.of("remove", "withdraw")) {
			director.requests().clear();
			Result result = admin(dir).run("federation", "proposal", verb, "7");
			assertEquals(0, result.exitCode(), result::toString);
			assertTrue(result.out().contains("Proposal 7 withdrawn or removed."), result::toString);
			assertEquals(1, director.requests("DELETE", path).size());
			assertTrue(director.requests("GET", path).isEmpty());

			director.requests().clear();
			Result json = admin(dir).run("federation", "proposal", verb, "7", "--json");
			assertEquals(0, json.exitCode(), json::toString);
			assertEquals(7, ((Number) json.json().get("id")).longValue());
			assertEquals(Boolean.TRUE, json.json().get("processed"));
			assertFalse(json.json().containsKey("removed"), json::toString);
			assertEquals(1, director.requests("DELETE", path).size());
			assertTrue(director.requests("GET", path).isEmpty());
		}
	}

	@Test
	void removingAnUnknownProposalKeepsTheNotFoundError(@TempDir Path dir) {
		director.reply("DELETE", "/api/v1/admin/federation/proposals/7", 404, "No such proposal");

		Result result = admin(dir).run("federation", "proposal", "remove", "7");
		assertEquals(4, result.exitCode(), result::toString);
		assertTrue(result.err().contains("No proposal 7."), result::toString);
		assertTrue(result.err().contains("federation proposal list"), result::toString);
		assertTrue(result.out().isEmpty(), result::toString);
	}

	@Test
	void aReceivedPendingProposalMustBeAnsweredInsteadOfRemoved(@TempDir Path dir) {
		director.reply("DELETE", "/api/v1/admin/federation/proposals/7", 409, "Answer the proposal rather than remove it");

		Result result = admin(dir).run("federation", "proposal", "withdraw", "7");
		assertEquals(1, result.exitCode(), result::toString);
		assertTrue(result.err().contains("Proposal 7 waits for this node's answer."), result::toString);
		assertTrue(result.err().contains("federation proposal accept|decline 7"), result::toString);
		assertTrue(result.out().isEmpty(), result::toString);
	}

	@Test
	void proposalFiltersUseTheCurrentRolesAndStatuses(@TempDir Path dir) {
		Id peer = Id.random();
		String path = "/api/v1/admin/federation/proposals";
		for (String role : List.of("offer", "answer")) {
			for (String status : List.of("pending", "accepted", "declined", "withdrawn", "expired")) {
				director.reply("GET", path, 200,
						"{\"page\":1,\"pageSize\":20,\"totalItems\":1,\"totalPages\":1,\"items\":[{\"id\":7,\"nodeId\":\"" + peer +
								"\",\"role\":\"" + role + "\",\"status\":\"" + status + "\",\"createdAt\":1,\"updatedAt\":1}]}");

				director.requests().clear();
				Result result = admin(dir).run("federation", "proposal", "list", "--role", role, "--status", status, "--json");
				assertEquals(0, result.exitCode(), result::toString);
				String query = director.requests("GET", path).get(0).query();
				assertTrue(query.contains("role=" + role) && query.contains("status=" + status), query);
				Map<?, ?> item = (Map<?, ?>) ((List<?>) result.json().get("items")).get(0);
				assertEquals(role, item.get("role"));
				assertEquals(status, item.get("status"));
			}
		}
	}

	@Test
	void proposalsRetainAllFiltersAcrossPages(@TempDir Path dir) {
		Id peer = Id.random();
		String path = "/api/v1/admin/federation/proposals";
		List<String> proposals = new ArrayList<>();
		for (int id = 1; id <= 101; id++) {
			proposals.add("{\"id\":" + id + ",\"nodeId\":\"" + peer +
					"\",\"role\":\"answer\",\"status\":\"pending\",\"createdAt\":1,\"updatedAt\":1}");
		}
		String first = "{\"page\":1,\"pageSize\":100,\"totalItems\":101,\"totalPages\":2,\"items\":[" +
				String.join(",", proposals.subList(0, 100)) + "]}";
		String last = "{\"page\":2,\"pageSize\":100,\"totalItems\":101,\"totalPages\":2,\"items\":[" + proposals.get(100) + "]}";
		director.handle("GET", path, request -> StubDirector.Reply.json(200, request.query().contains("page=1&") ? first : last));

		Result result = admin(dir).run("federation", "proposal", "list", "--node", peer.toBase58String(),
				"--role", "answer", "--status", "pending", "--all", "--json");
		assertEquals(0, result.exitCode(), result::toString);
		List<StubDirector.Request> requests = director.requests("GET", path);
		assertEquals(2, requests.size());
		for (int i = 0; i < requests.size(); i++) {
			String query = requests.get(i).query();
			assertTrue(query.contains("nodeId=" + peer.toBase58String()), query);
			assertTrue(query.contains("role=answer") && query.contains("status=pending"), query);
			assertTrue(query.contains("page=" + (i + 1) + "&") && query.contains("pageSize=100"), query);
		}
		assertEquals(101, ((Number) result.json().get("totalItems")).longValue());
		List<?> items = (List<?>) result.json().get("items");
		assertEquals(101, items.size());
		assertEquals(1, ((Number) ((Map<?, ?>) items.get(0)).get("id")).longValue());
		assertEquals(101, ((Number) ((Map<?, ?>) items.get(100)).get("id")).longValue());
	}

	@Test
	void healthListsTheNodesHeardFrom(@TempDir Path dir) {
		Id peer = Id.random();
		director.reply("GET", "/api/v1/admin/federation/health", 200,
				"{\"page\":1,\"pageSize\":1,\"totalItems\":1,\"totalPages\":1,\"items\":[{\"nodeId\":\"" + peer +
						"\",\"lastSeenAt\":1,\"lastFailureAt\":1,\"lastFailure\":\"TIMEOUT\",\"consecutiveFailures\":3," +
						"\"lastHour\":{\"SUCCESS\":5,\"TIMEOUT\":3}}]}");

		Result result = admin(dir).run("federation", "node", "health");
		assertEquals(0, result.exitCode(), result::toString);
		assertTrue(result.out().contains(peer.toBase58String()), result::toString);
		assertTrue(result.out().contains("success 5, timeout 3"), result::toString);
	}

	@Test
	void hostsShowDescriptorCheckCountsAndUnresolvedChecks(@TempDir Path dir) {
		String ipv4 = hostJson("192.0.2.5", 3, 2, 1, 1700000000000L);
		String ipv6 = hostJson("2001:db8:1234:5678::/64", 0, 0, 0, 0);
		String unresolved = hostJson(null, 0, 4, 1, 1700000001000L);
		director.reply("GET", "/api/v1/admin/federation/hosts", 200,
				"{\"page\":1,\"pageSize\":20,\"totalItems\":3,\"totalPages\":1,\"items\":[" + ipv4 + "," + ipv6 + "," + unresolved + "]}");

		Result result = admin(dir).run("federation", "host", "list");
		assertEquals(0, result.exitCode(), result::toString);
		assertTrue(result.out().contains("VERIFIED NODES"), result::toString);
		assertTrue(result.out().contains("FAILED NODES"), result::toString);
		assertTrue(result.out().contains("NOT FEDERATING NODES"), result::toString);
		List<String> rows = result.out().lines().toList();
		assertEquals(List.of("192.0.2.5", "3", "2", "1", Formats.time(1700000000000L)),
				Arrays.asList(rows.get(1).split("\\s{2,}")));
		assertEquals(List.of("2001:db8:1234:5678::/64", "0", "0", "0", "-"), Arrays.asList(rows.get(2).split("\\s{2,}")));
		assertEquals(List.of("Unresolved host", "0", "4", "1", Formats.time(1700000001000L)),
				Arrays.asList(rows.get(3).split("\\s{2,}")));

		Result json = admin(dir).run("federation", "host", "list", "--json");
		assertEquals(0, json.exitCode(), json::toString);
		assertEquals(3, ((Number) json.json().get("totalItems")).longValue());
		List<?> items = (List<?>) json.json().get("items");
		assertEquals(3, items.size());
		Map<?, ?> first = (Map<?, ?>) items.get(0);
		assertEquals(Set.of("host", "verifiedNodes", "failedNodes", "notFederatingNodes", "lastCheckedAt"), first.keySet());
		assertEquals("192.0.2.5", first.get("host"));
		assertEquals(3, first.get("verifiedNodes"));
		assertEquals(2, first.get("failedNodes"));
		assertEquals(1, first.get("notFederatingNodes"));
		assertEquals(1700000000000L, first.get("lastCheckedAt"));
		assertEquals("2001:db8:1234:5678::/64", ((Map<?, ?>) items.get(1)).get("host"));
		Map<?, ?> unknown = (Map<?, ?>) items.get(2);
		assertTrue(unknown.containsKey("host"), json::toString);
		assertEquals(null, unknown.get("host"));
		assertEquals(0, unknown.get("verifiedNodes"));
		assertFalse(director.requests("GET", "/api/v1/admin/federation/hosts").get(0).query().contains("host="));
	}

	@Test
	void hostFiltersPassLiteralTextToTheServer(@TempDir Path dir) {
		String path = "/api/v1/admin/federation/hosts";
		director.reply("GET", path, 200, "{\"page\":1,\"pageSize\":20,\"totalItems\":0,\"totalPages\":0,\"items\":[]}");
		for (String host : List.of(" 2001:DB8::/64 ", " unresolved ", "192.0.2.", " a+b &%?*[] ", "   ", "")) {
			director.requests().clear();
			Result result = admin(dir).run("federation", "host", "list", "--host", host);
			assertEquals(0, result.exitCode(), result::toString);
			String query = director.requests("GET", path).get(0).query();
			String[] parameters = query.split("&");
			if (host.isBlank()) {
				assertEquals(2, parameters.length, query);
				assertFalse(query.contains("host="), query);
				assertTrue(result.out().contains("No recent host observations."), result::toString);
			} else {
				assertEquals(3, parameters.length, query);
				String value = Arrays.stream(parameters).filter(p -> p.startsWith("host=")).findFirst().orElseThrow();
				assertEquals(host.strip(), URLDecoder.decode(value.substring("host=".length()), StandardCharsets.UTF_8));
				assertTrue(result.out().contains("No matching host observations."), result::toString);
			}
		}
	}

	@Test
	void hostsCanBePagedOrDrainedWithTheSameFilter(@TempDir Path dir) {
		String path = "/api/v1/admin/federation/hosts";
		List<String> hosts = new ArrayList<>();
		for (int i = 1; i <= 101; i++)
			hosts.add(hostJson("192.0.2." + i, 1, 0, 0, 1));
		String first = "{\"page\":1,\"pageSize\":100,\"totalItems\":101,\"totalPages\":2,\"items\":[" +
				String.join(",", hosts.subList(0, 100)) + "]}";
		String last = "{\"page\":2,\"pageSize\":100,\"totalItems\":101,\"totalPages\":2,\"items\":[" + hosts.get(100) + "]}";
		String beyond = "{\"page\":3,\"pageSize\":100,\"totalItems\":101,\"totalPages\":2,\"items\":[]}";
		director.handle("GET", path, request -> StubDirector.Reply.json(200,
				request.query().contains("page=1&") ? first : request.query().contains("page=2&") ? last : beyond));

		Result page = admin(dir).run("federation", "host", "list", "--host", "192.0.2.", "--page", "2", "--page-size", "100", "--json");
		assertEquals(0, page.exitCode(), page::toString);
		assertEquals(2, ((Number) page.json().get("page")).longValue());
		assertEquals(101, ((Number) page.json().get("totalItems")).longValue());
		assertEquals(2, ((Number) page.json().get("totalPages")).longValue());
		assertEquals(1, ((List<?>) page.json().get("items")).size());

		Result empty = admin(dir).run("federation", "host", "list", "--host", "192.0.2.", "--page", "3", "--page-size", "100");
		assertEquals(0, empty.exitCode(), empty::toString);
		assertTrue(empty.out().contains("Page 3 is past the end: there are 2 pages of hosts."), empty::toString);

		director.requests().clear();
		Result all = admin(dir).run("federation", "host", "list", "--host", "192.0.2.", "--all", "--json");
		assertEquals(0, all.exitCode(), all::toString);
		List<StubDirector.Request> requests = director.requests("GET", path);
		assertEquals(2, requests.size());
		for (int i = 0; i < requests.size(); i++) {
			String query = requests.get(i).query();
			assertTrue(query.contains("host=192.0.2."), query);
			assertTrue(query.contains("page=" + (i + 1) + "&") && query.contains("pageSize=100"), query);
		}
		assertEquals(101, ((Number) all.json().get("totalItems")).longValue());
		List<?> items = (List<?>) all.json().get("items");
		assertEquals(101, items.size());
		assertEquals("192.0.2.1", ((Map<?, ?>) items.get(0)).get("host"));
		assertEquals("192.0.2.101", ((Map<?, ?>) items.get(100)).get("host"));
	}

	@Test
	void hostsKeepSharedPaginationAndErrorHandling(@TempDir Path dir) {
		String[][] invalid = {{"--page", "0"}, {"--page-size", "0"}, {"--page-size", "1001"},
				{"--all", "--page", "1"}, {"--all", "--page-size", "1"}};
		for (String[] options : invalid) {
			List<String> args = new ArrayList<>(List.of("federation", "host", "list"));
			args.addAll(List.of(options));
			Result result = admin(dir).run(args.toArray(new String[0]));
			assertEquals(2, result.exitCode(), result::toString);
			assertTrue(director.requests().isEmpty());
		}

		Map<Integer, Integer> errors = Map.of(501, 1, 403, 5, 429, 7, 503, 7);
		for (var error : errors.entrySet()) {
			director.reply("GET", "/api/v1/admin/federation/hosts", error.getKey(),
					error.getKey() == 501 ? "Not Implemented - Federation is not enabled" : "Test refusal");
			Result result = admin(dir).run("federation", "host", "list", "--host", "unresolved", "--json");
			assertEquals(error.getValue(), result.exitCode(), result::toString);
			assertTrue(result.out().isEmpty(), result::toString);
			assertFalse(result.err().contains("Exception"), result::toString);
			if (error.getKey() == 501)
				assertTrue(result.err().contains("Federation is not enabled"), result::toString);
		}
	}

	@Test
	void aNodeLocalServicesWantedShowsTheDemand(@TempDir Path dir) {
		Id peer = Id.random();
		director.reply("GET", "/api/v1/admin/federation/nodes/" + peer, 200,
				"{\"nodeId\":\"" + peer + "\",\"admission\":\"NO_RELATIONSHIP\",\"health\":{\"nodeId\":\"" + peer +
						"\",\"lastSeenAt\":0,\"consecutiveFailures\":0,\"lastHour\":{},\"demand\":4," +
						"\"lastDemandAt\":1,\"lastDemandReason\":\"NO_RELATIONSHIP\"}}");

		Result result = admin(dir).run("federation", "node", "show", peer.toBase58String());
		assertEquals(0, result.exitCode(), result::toString);
		assertTrue(result.out().contains("Wanted"), result::toString);
		assertTrue(result.out().contains("no_relationship"), result::toString);
	}

	@Test
	void aNodeWithoutADecisionShowsTheModesAdmission(@TempDir Path dir) {
		Id peer = Id.random();
		director.reply("GET", "/api/v1/admin/federation/nodes/" + peer, 200,
				"{\"nodeId\":\"" + peer + "\",\"admission\":\"OPEN\",\"reach\":\"PENDING\"}");

		Result result = admin(dir).run("federation", "node", "show", peer.toBase58String());
		assertEquals(0, result.exitCode(), result::toString);
		assertTrue(result.out().contains("none (the federation mode decides)"), result::toString);
		String origin = result.out().lines().filter(line -> line.startsWith("Origin:")).findFirst().orElseThrow();
		assertEquals("-", origin.substring("Origin:".length()).strip());
		assertTrue(result.out().contains("open (admitted)"), result::toString);
		// Admitted, but what is sent to it is held: the other node hasn't agreed yet.
		assertTrue(result.out().contains("pending (held)"), result::toString);
	}

	@Test
	void descriptorAndServiceCommandsUseTheCoreDescriptor(@TempDir Path dir) {
		Id peer = Id.random();
		Id service = Id.random();
		director.reply("GET", "/api/v1/admin/federation/nodes/" + peer + "/descriptor", 200,
				"{\"nodeId\":\"" + peer + "\",\"name\":\"Partner\",\"mode\":\"mutual\",\"protocols\":[\"federation/2\"]," +
						"\"services\":[{\"type\":\"io.bosonnetwork.photonmessaging\",\"peerId\":\"" + service +
						"\",\"endpoint\":\"https://peer.example:9084\",\"federation\":true}],\"issuedAt\":1700000000000,\"expiresAt\":1700086400000}");

		Result described = admin(dir).run("federation", "node", "describe", peer.toBase58String());
		assertEquals(0, described.exitCode(), described::toString);
		assertTrue(described.out().contains("Partner"), described::toString);
		assertTrue(described.out().contains("mutual"), described::toString);
		assertTrue(described.out().contains(service.toBase58String()), described::toString);

		Result json = admin(dir).run("federation", "node", "describe", peer.toBase58String(), "--json");
		assertEquals(0, json.exitCode(), json::toString);
		assertEquals(peer.toBase58String(), json.json().get("nodeId"));
		assertEquals("mutual", json.json().get("mode"));
		assertEquals(1700000000000L, json.json().get("issuedAt"));

		Result listed = admin(dir).run("federation", "service", "list", peer.toBase58String());
		assertEquals(0, listed.exitCode(), listed::toString);
		assertTrue(listed.out().contains("io.bosonnetwork.photonmessaging"), listed::toString);
		assertTrue(listed.out().contains("https://peer.example:9084"), listed::toString);

		Result services = admin(dir).run("federation", "service", "list", peer.toBase58String(), "--json");
		assertEquals(0, services.exitCode(), services::toString);
		Map<?, ?> item = (Map<?, ?>) services.jsonArray().get(0);
		assertEquals(service.toBase58String(), item.get("peerId"));
		assertEquals(Boolean.TRUE, item.get("federation"));
	}

	@Test
	void anUnreachableDescriptorIsNotFound(@TempDir Path dir) {
		Id peer = Id.random();
		director.reply("GET", "/api/v1/admin/federation/nodes/" + peer + "/descriptor", 404, "unreachable");

		Result result = admin(dir).run("federation", "service", "list", peer.toBase58String());
		assertTrue(result.exitCode() != 0, result::toString);
		assertTrue(result.err().contains("could not be reached or verified"), result::toString);
	}

	@Test
	void aNodeThatDoesNotFederateSaysSo(@TempDir Path dir) {
		director.reply("GET", "/api/v1/admin/federation/nodes", 501, "Not Implemented - Federation is not enabled");

		Result result = admin(dir).run("federation", "node", "list");
		assertEquals(1, result.exitCode(), result::toString);
		assertTrue(result.err().contains("Federation is not enabled"), result::toString);
		assertFalse(result.err().contains("Exception"), result::toString);
	}

	@Test
	void timesAreReadInEveryForm() {
		Arguments.TimeConverter converter = new Arguments.TimeConverter();
		assertEquals(LocalDate.of(2027, 1, 31).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
				converter.convert("2027-01-31"));
		assertEquals(1893456000000L, converter.convert("2030-01-01T00:00:00Z"));
		assertEquals(1893456000000L, converter.convert("1893456000000"));
		assertTrue(converter.convert("+1y") > System.currentTimeMillis() + TimeUnit.DAYS.toMillis(360));
		assertThrows(TypeConversionException.class, () -> converter.convert("next tuesday"));
	}
}
