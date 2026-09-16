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

package io.bosonnetwork.node.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import io.bosonnetwork.cli.common.CliEnvironment;
import io.bosonnetwork.cli.common.CliException;
import io.bosonnetwork.cli.common.testing.CliRunner;
import io.bosonnetwork.cli.common.testing.CliRunner.Result;

/**
 * Tests of {@code boson-node}, run in process. Nothing here starts a node or touches the network.
 */
class BosonNodeTests {
	private static CliRunner node(Path configDir) {
		return new CliRunner(BosonNode::new, configDir);
	}

	@Test
	void helpListsTheCommands(@TempDir Path dir) {
		Result root = node(dir).run("--help");
		assertEquals(0, root.exitCode(), root::toString);
		for (String command : List.of("run", "shell", "setup", "cache", "id", "config"))
			assertTrue(root.out().contains(command), () -> command + " missing from:\n" + root.out());
		assertTrue(root.out().contains("Global options, taken by every command:"), root::toString);
		assertTrue(root.out().contains("Exit codes:"), root::toString);

		Result group = node(dir).run("config");
		assertEquals(2, group.exitCode(), group::toString);
		assertTrue(group.err().contains("needs one of the commands"), group::toString);
	}

	@Test
	void everyCommandHasHelp(@TempDir Path dir) {
		CliEnvironment environment = new CliEnvironment(Map.of(), dir, System.in, null,
				new PrintWriter(Writer.nullWriter()), new PrintWriter(Writer.nullWriter()));
		List<String[]> commands = new ArrayList<>();
		collect(new CommandLine(new BosonNode(environment)), new ArrayList<>(), commands);
		assertTrue(commands.size() >= 8, "commands: " + commands.size());

		for (String[] command : commands) {
			List<String> args = new ArrayList<>(List.of(command));
			args.add("--help");
			Result result = node(dir).run(args.toArray(new String[0]));
			assertEquals(0, result.exitCode(), () -> String.join(" ", command) + ": " + result);
			assertFalse(result.out().isBlank(), () -> String.join(" ", command) + " has no help");
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
	void aConfigurationIsWrittenReadBackAndChecked(@TempDir Path dir) {
		Path config = dir.resolve("node.yaml");

		Result init = node(dir).run("--json", "config", "init", "--output", config.toString(),
				"--host4", "203.0.113.5", "--data-dir", dir.resolve("data").toString());
		assertEquals(0, init.exitCode(), init::toString);
		String nodeId = (String) init.json().get("nodeId");
		assertTrue(Files.isRegularFile(config));

		Result id = node(dir).run("id", "-c", config.toString());
		assertEquals(0, id.exitCode(), id::toString);
		assertEquals(nodeId, id.out().strip());

		Result check = node(dir).run("config", "check", "-c", config.toString());
		assertEquals(0, check.exitCode(), check::toString);
		assertTrue(check.out().contains("usable"), check::toString);

		Result show = node(dir).run("config", "show", "-c", config.toString());
		assertEquals(0, show.exitCode(), show::toString);
		assertTrue(show.out().contains(nodeId), show::toString);
		assertTrue(show.out().contains("203.0.113.5"), show::toString);

		Result again = node(dir).run("config", "init", "--output", config.toString(), "--host4", "203.0.113.5");
		assertEquals(1, again.exitCode(), again::toString);
		assertTrue(again.err().contains("already exists"), again::toString);
	}

	@Test
	void anUnusableConfigurationIsExplained(@TempDir Path dir) throws Exception {
		Path config = dir.resolve("node.yaml");
		Files.writeString(config, "port: 39001\nprivateKey: not-a-key\n");

		Result check = node(dir).run("config", "check", "-c", config.toString());
		assertEquals(3, check.exitCode(), check::toString);
		assertTrue(check.err().contains("not usable"), check::toString);
		assertFalse(check.err().contains("Exception"), check::toString);

		Result id = node(dir).run("id", "-c", config.toString());
		assertEquals(2, id.exitCode(), id::toString);
	}

	@Test
	void anAddressNoNodeCanUseIsRefusedBeforeTheFileIsWritten(@TempDir Path dir) {
		Path config = dir.resolve("node.yaml");

		Result init = node(dir).run("config", "init", "--output", config.toString(), "--host4", "127.0.0.1");
		assertEquals(2, init.exitCode(), init::toString);
		assertTrue(init.err().contains("cannot listen on 127.0.0.1"), init::toString);
		assertTrue(init.err().contains("loopback"), init::toString);
		assertFalse(Files.exists(config), "a configuration no node can use was written anyway");
	}

	@Test
	void aPrivateAddressWarnsAndDeveloperModeIsWrittenIn(@TempDir Path dir) throws Exception {
		Path warned = dir.resolve("warned.yaml");
		Result plain = node(dir).run("config", "init", "--output", warned.toString(), "--host4", "192.168.10.5",
				"--data-dir", dir.resolve("data").toString());
		assertEquals(0, plain.exitCode(), plain::toString);
		assertTrue(plain.err().contains("private address"), plain::toString);
		assertTrue(Files.readString(warned).contains("developerMode: false"));

		Path config = dir.resolve("node.yaml");
		Result developer = node(dir).run("config", "init", "--output", config.toString(), "--host4", "192.168.10.5",
				"--developer-mode", "--data-dir", dir.resolve("data").toString());
		assertEquals(0, developer.exitCode(), developer::toString);
		assertFalse(developer.err().contains("private address"), developer::toString);
		assertTrue(Files.readString(config).contains("developerMode: true"), Files.readString(config));

		Result check = node(dir).run("config", "check", "-c", config.toString());
		assertEquals(0, check.exitCode(), check::toString);
	}

	@Test
	void aMissingConfigurationIsExplained(@TempDir Path dir) {
		Result id = node(dir).run("id", "-c", dir.resolve("missing.yaml").toString());
		assertEquals(3, id.exitCode(), id::toString);
		assertTrue(id.err().contains("does not exist"), id::toString);
	}

	@Test
	void aDataDirectoryWithoutASavedTableIsNotFound(@TempDir Path dir) {
		Result cache = node(dir).run("cache", "--data-dir", dir.toString());
		assertEquals(4, cache.exitCode(), cache::toString);
		assertTrue(cache.err().contains("No saved routing table"), cache::toString);
		assertTrue(cache.err().contains("Hint:"), cache::toString);
	}

	@Test
	void theServiceLauncherIsTheRunCommand() {
		assertArrayEquals(new String[] {"run", "-c", "/etc/boson/bootstrap/node.yaml"},
				NodeLauncher.asRunCommand(new String[] {"-c", "/etc/boson/bootstrap/node.yaml"}));
		assertArrayEquals(new String[] {"run"}, NodeLauncher.asRunCommand(new String[0]));
	}

	@Test
	void bootstrapNodesAreReadOrRefused() {
		assertEquals("2dLbPsaySh9EGWwpgreYiLEPG3NDhaojj7DBBfSsRr6k",
				NodeOptions.parseBootstrap("2dLbPsaySh9EGWwpgreYiLEPG3NDhaojj7DBBfSsRr6k:203.0.113.5:39001")
						.getId().toBase58String());

		assertThrows(CliException.class, () -> NodeOptions.parseBootstrap("203.0.113.5:39001"));
		assertThrows(CliException.class, () -> NodeOptions.parseBootstrap("not-an-id:203.0.113.5:39001"));
		assertThrows(CliException.class,
				() -> NodeOptions.parseBootstrap("2dLbPsaySh9EGWwpgreYiLEPG3NDhaojj7DBBfSsRr6k:203.0.113.5:port"));
	}
}
