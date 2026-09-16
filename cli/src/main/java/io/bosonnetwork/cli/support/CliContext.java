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

package io.bosonnetwork.cli.support;

import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.director.client.DirectorAdmin;
import io.bosonnetwork.director.client.DirectorClient;

/**
 * What one run of a tool works with: its settings, identity, output and terminal, and the Director
 * clients built from them.
 * <p>
 * Everything is created when first needed, so that a command that never talks to a Director - or never
 * needs an identity - does not fail for the lack of one. Closing the context closes the clients and the
 * Vert.x instance they run on.
 */
public final class CliContext implements AutoCloseable {
	private static final long CLOSE_TIMEOUT_SECONDS = 5;

	private final ToolSpec tool;
	private final CliEnvironment environment;
	private final GlobalOptions options;
	private final Output output;
	private final Terminal terminal;

	private Settings settings;
	private Vertx vertx;
	private boolean exposureChecked;
	private Id identityId;
	private URL directorUrl;
	private final List<Supplier<CompletableFuture<Void>>> clients = new ArrayList<>();

	/**
	 * Creates the context of a run.
	 *
	 * @param tool        the tool
	 * @param environment the environment
	 * @param options     the global options given on the command line
	 */
	public CliContext(ToolSpec tool, CliEnvironment environment, GlobalOptions options) {
		this.tool = tool;
		this.environment = environment;
		this.options = options;
		this.output = new Output(environment.out(), environment.err(), options.json());
		this.terminal = new Terminal(environment);
	}

	/**
	 * Returns the tool.
	 *
	 * @return the tool
	 */
	public ToolSpec tool() {
		return tool;
	}

	/**
	 * Returns the environment.
	 *
	 * @return the environment
	 */
	public CliEnvironment environment() {
		return environment;
	}

	/**
	 * Returns the global options given on the command line.
	 *
	 * @return the options
	 */
	public GlobalOptions options() {
		return options;
	}

	/**
	 * Returns the output.
	 *
	 * @return the output
	 */
	public Output output() {
		return output;
	}

	/**
	 * Returns the terminal.
	 *
	 * @return the terminal
	 */
	public Terminal terminal() {
		return terminal;
	}

	/**
	 * Returns the settings, reading the configuration file the first time.
	 *
	 * @return the settings
	 * @throws CliException if the configuration file cannot be read
	 */
	public Settings settings() {
		if (settings == null)
			settings = Settings.resolve(tool, environment, options);
		return settings;
	}

	/**
	 * Returns the key the tool acts with. Warns once if its identity file is open to other users.
	 *
	 * @return the key pair
	 * @throws CliException if there is no identity, or it is not valid
	 */
	public Signature.KeyPair identity() {
		Signature.KeyPair key = settings().identityKey();
		identityId = Id.of(key.publicKey().bytes());

		if (!exposureChecked) {
			exposureChecked = true;
			Path file = settings().identityFile();
			if (file != null && IdentityFile.isExposed(file))
				output.warning("The identity file " + file + " can be read by other users. Restrict it with: chmod 600 " + file);
		}

		return key;
	}

	/**
	 * Returns the id of the identity the tool last acted with, for messages.
	 *
	 * @return the id, or {@code null} if no identity was used
	 */
	public Id identityId() {
		return identityId;
	}

	/**
	 * Returns the URL of the Director the tool last connected to, for messages.
	 *
	 * @return the URL, or {@code null} if no client was built
	 */
	public URL directorUrl() {
		return directorUrl;
	}

	/**
	 * Returns the Vert.x instance the clients run on, creating it the first time.
	 *
	 * @return the Vert.x instance
	 */
	public Vertx vertx() {
		if (vertx == null) {
			vertx = Vertx.vertx(new VertxOptions()
					.setEventLoopPoolSize(1)
					// Proof-of-work runs on a worker for seconds at a time; that is expected, not blocked.
					.setWorkerPoolSize(2)
					.setMaxWorkerExecuteTime(10)
					.setMaxWorkerExecuteTimeUnit(TimeUnit.MINUTES));
		}
		return vertx;
	}

	/**
	 * Builds a client acting as the configured user.
	 *
	 * @return the client
	 * @throws CliException if the settings or the identity are missing or invalid
	 */
	public DirectorClient directorClient() {
		return directorClient(null);
	}

	/**
	 * Builds a client acting as the configured user, with a device key.
	 *
	 * @param deviceKey the device key, or {@code null}
	 * @return the client
	 * @throws CliException if the settings or the identity are missing or invalid
	 */
	public DirectorClient directorClient(Signature.KeyPair deviceKey) {
		// Where to connect is reported before who connects: without a Director, no identity helps.
		checkConnection();
		return buildClient(identity(), deviceKey);
	}

	/**
	 * Builds a client for the calls the Director answers without authentication, which therefore work
	 * without an identity: the node id and status.
	 *
	 * @return the client
	 * @throws CliException if the connection settings are missing or invalid
	 */
	public DirectorClient anonymousDirectorClient() {
		// The client insists on a key, but these calls carry no token, so a throwaway one signs nothing.
		return buildClient(Signature.KeyPair.random(), null);
	}

	private DirectorClient buildClient(Signature.KeyPair userKey, Signature.KeyPair deviceKey) {
		URL url = settings().directorUrl();
		Id nodeId = settings().nodeIdValue();
		InetSocketAddress address = settings().connectAddress(url);

		DirectorClient.Builder builder = DirectorClient.builder()
				.vertx(vertx())
				.directorUrl(url)
				.userKey(userKey);
		if (nodeId != null)
			builder.nodeId(nodeId);
		if (address != null)
			builder.connectAddress(address);
		if (deviceKey != null)
			builder.deviceKey(deviceKey);

		DirectorClient client = builder.build();
		directorUrl = url;
		clients.add(client::close);
		return client;
	}

	/**
	 * Builds an admin client acting as the configured administrator.
	 *
	 * @return the client
	 * @throws CliException if the settings or the identity are missing or invalid
	 */
	public DirectorAdmin directorAdmin() {
		checkConnection();
		return buildAdmin(identity());
	}

	/**
	 * Builds an admin client for the calls the Director answers without authentication: the node id.
	 *
	 * @return the client
	 * @throws CliException if the connection settings are missing or invalid
	 */
	public DirectorAdmin anonymousDirectorAdmin() {
		return buildAdmin(Signature.KeyPair.random());
	}

	private DirectorAdmin buildAdmin(Signature.KeyPair userKey) {
		URL url = settings().directorUrl();
		Id nodeId = settings().nodeIdValue();
		InetSocketAddress address = settings().connectAddress(url);

		DirectorAdmin.Builder builder = DirectorAdmin.builder()
				.vertx(vertx())
				.directorUrl(url)
				.userKey(userKey);
		if (nodeId != null)
			builder.nodeId(nodeId);
		if (address != null)
			builder.connectAddress(address);

		DirectorAdmin admin = builder.build();
		directorUrl = url;
		clients.add(admin::close);
		return admin;
	}

	// Checks the settings that say where to connect.
	private void checkConnection() {
		URL url = settings().directorUrl();
		settings().nodeIdValue();
		settings().connectAddress(url);
	}

	/**
	 * Waits for a client call.
	 *
	 * @param future the call
	 * @param <T>    the result type
	 * @return the result
	 * @throws Exception the failure of the call, unwrapped
	 */
	public <T> T await(CompletableFuture<T> future) throws Exception {
		try {
			return future.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new CliException(ExitCode.FAILED, "Interrupted.");
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception exception)
				throw exception;
			if (cause instanceof Error error)
				throw error;
			throw e;
		}
	}

	/**
	 * Closes the clients and the Vert.x instance.
	 */
	@Override
	public void close() {
		for (Supplier<CompletableFuture<Void>> client : clients) {
			try {
				client.get().get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			} catch (Exception ignored) {
				// Closing on the way out: nothing left to report it to.
			}
		}
		clients.clear();

		if (vertx != null) {
			try {
				vertx.close().toCompletionStage().toCompletableFuture().get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			} catch (Exception ignored) {
				// As above.
			}
			vertx = null;
		}

		output.flush();
	}
}
