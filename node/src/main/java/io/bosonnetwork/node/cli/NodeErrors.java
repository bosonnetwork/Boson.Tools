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

import java.net.BindException;
import java.net.UnknownHostException;

import io.bosonnetwork.BosonException;
import io.bosonnetwork.cli.common.Causes;
import io.bosonnetwork.cli.common.CliException;
import io.bosonnetwork.cli.common.ErrorReporter;
import io.bosonnetwork.cli.common.ErrorTranslator;
import io.bosonnetwork.cli.common.ExitCode;

/**
 * What a node's failures mean to the user: a port already taken, an address that is not this host's,
 * a DHT operation that did not complete. Stack traces and Netty's wording stay out of the message.
 */
public class NodeErrors implements ErrorTranslator {
	@Override
	public CliException translate(Throwable error) {
		String chain = Causes.messages(error);

		if (Causes.hasCause(error, BindException.class) || chain.contains("address already in use"))
			return new CliException(ExitCode.UNAVAILABLE, "The node cannot listen: the port is already in use.",
					"Stop whatever holds the port, or run with another one (--port).");

		if (chain.contains("cannot assign requested address"))
			return new CliException(ExitCode.CONFIG, "The node cannot listen: the address is not one of this host's.",
					"Set host4 or host6 to an address of this machine, or name an interface instead.");

		// The node refuses an address it could never be reached at, which is a configuration mistake
		// rather than a failure to start.
		if (chain.contains("not a unicast address"))
			return new CliException(ExitCode.CONFIG, ErrorReporter.sentence(Causes.describe(Causes.rootCause(error))),
					io.bosonnetwork.node.cli.commands.ConfigCommand.ADDRESS_HINT);

		if (Causes.hasCause(error, UnknownHostException.class))
			return new CliException(ExitCode.CONFIG, "Cannot find the host " + Causes.describe(Causes.rootCause(error)) + ".",
					"Check the addresses in the configuration, and the bootstrap nodes.");

		if (error instanceof BosonException)
			return CliException.failed(ErrorReporter.sentence(Causes.describe(error)), null);

		return null;
	}
}
