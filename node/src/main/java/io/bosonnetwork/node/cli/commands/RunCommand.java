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

package io.bosonnetwork.node.cli.commands;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import io.vertx.core.Vertx;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import io.bosonnetwork.NodeConfiguration;
import io.bosonnetwork.cli.common.CliCommand;
import io.bosonnetwork.cli.common.CliException;
import io.bosonnetwork.cli.common.ExitCode;
import io.bosonnetwork.kademlia.KadNode;
import io.bosonnetwork.node.cli.NodeOptions;
import io.bosonnetwork.utils.ApplicationLock;

/**
 * {@code boson-node run}: the node itself, in the foreground.
 */
@Command(name = "run", description = {"Run a Boson DHT node.",
		"Runs in the foreground until it is stopped, which is what a service manager expects. The node's own "
				+ "logging is left to its logback configuration, so a bootstrap node logs as its operator set it up."})
public class RunCommand extends CliCommand {
	@Mixin
	NodeOptions options;

	@Override
	protected void run() throws Exception {
		Vertx vertx = NodeOptions.vertx();
		NodeConfiguration config = options.configuration(vertx);
		Path dataDir = config.dataDir();

		try (ApplicationLock lock = new ApplicationLock(dataDir.resolve("lock"))) {
			KadNode node = new KadNode(config);
			CountDownLatch stopped = new CountDownLatch(1);

			// Ctrl-C and a service manager's TERM both arrive here; the node is given the chance to
			// save its routing table rather than being cut off mid-write.
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					output().message("Stopping the node...");
					if (node.isRunning())
						node.stop().get();
					output().message("Stopped.");
				} catch (Exception e) {
					output().warning("The node did not stop cleanly: " + e.getMessage());
				} finally {
					stopped.countDown();
				}
			}, "boson-node-shutdown"));

			await(node.start());
			report(node, config, vertx);

			// Nothing else to do here: the node runs on its own threads until it is stopped.
			stopped.await();
		} catch (IOException | IllegalStateException e) {
			throw new CliException(ExitCode.UNAVAILABLE,
					"Another node is already running with the data directory " + dataDir + ".",
					"Stop it first, or run this one with another data directory (--data-dir).");
		} finally {
			vertx.close();
		}
	}

	private void report(KadNode node, NodeConfiguration config, Vertx vertx) {
		if (output().isJson()) {
			Map<String, Object> json = new LinkedHashMap<>();
			json.put("nodeId", node.getId());
			json.put("host4", config.listen().host4());
			json.put("host6", config.listen().host6());
			json.put("port", config.listen().port());
			json.put("dataDir", config.dataDir().toString());
			output().json(json);
			return;
		}

		Map<String, String> rows = new LinkedHashMap<>();
		rows.put("Node id", node.getId().toBase58String());
		int port = config.listen().port();
		rows.put("IPv4", config.listen().host4() != null ? config.listen().host4() + ":" + port : "not enabled");
		rows.put("IPv6", config.listen().host6() != null ? config.listen().host6() + ":" + port : "not enabled");
		rows.put("Data directory", config.dataDir().toAbsolutePath().toString());
		rows.put("Native transport", vertx.isNativeTransportEnabled() ? "yes" : "no");
		output().message("The node is running.");
		output().details(rows);
	}
}
