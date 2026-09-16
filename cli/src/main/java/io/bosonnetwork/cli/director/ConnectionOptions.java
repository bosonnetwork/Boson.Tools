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

package io.bosonnetwork.cli.director;

import java.nio.file.Path;

import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;

/**
 * The options every command of the Director tools takes, before or after the command name: where the
 * configuration is, and the connection and identity settings that override it.
 */
public class ConnectionOptions {
	@Option(names = {"-c", "--config"}, paramLabel = "<file>", scope = ScopeType.INHERIT,
			description = "The configuration file (env: BOSON_CONFIG).")
	Path configFile;

	@Option(names = {"-u", "--url"}, paramLabel = "<url>", scope = ScopeType.INHERIT,
			description = "The Director URL: scheme, host and port (env: BOSON_DIRECTOR_URL).")
	String url;

	@Option(names = {"-n", "--node-id"}, paramLabel = "<id>", scope = ScopeType.INHERIT,
			description = "The super node's id (env: BOSON_NODE_ID).")
	String nodeId;

	@Option(names = "--resolve", paramLabel = "<address>", scope = ScopeType.INHERIT,
			description = "Connect to this IP address, with an optional port, instead of looking up the URL's host name. "
					+ "TLS is still verified against the name (env: BOSON_DIRECTOR_RESOLVE).")
	String resolve;

	@Option(names = {"-i", "--identity"}, paramLabel = "<file>", scope = ScopeType.INHERIT,
			description = "The identity file to act with (env: BOSON_IDENTITY, or BOSON_PRIVATE_KEY for the key itself).")
	Path identity;

	/**
	 * Returns the configuration file named on the command line.
	 *
	 * @return the file, or {@code null}
	 */
	public Path configFile() {
		return configFile;
	}

	/**
	 * Returns the Director URL given on the command line.
	 *
	 * @return the URL, or {@code null}
	 */
	public String url() {
		return url;
	}

	/**
	 * Returns the node id given on the command line.
	 *
	 * @return the node id, or {@code null}
	 */
	public String nodeId() {
		return nodeId;
	}

	/**
	 * Returns the address to connect to given on the command line.
	 *
	 * @return the address, or {@code null}
	 */
	public String resolve() {
		return resolve;
	}

	/**
	 * Returns the identity file named on the command line.
	 *
	 * @return the file, or {@code null}
	 */
	public Path identity() {
		return identity;
	}
}
