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

package io.bosonnetwork.node.cli.shell;

import io.bosonnetwork.cli.common.CliException;
import io.bosonnetwork.cli.common.ExitCode;
import io.bosonnetwork.cli.common.Output;
import io.bosonnetwork.cli.common.Terminal;
import io.bosonnetwork.kademlia.KadNode;

/**
 * What the shell's commands work with: the node the shell started, and where to write.
 */
public final class ShellSession {
	private final KadNode node;
	private final Output output;
	private final Terminal terminal;

	/**
	 * Creates the session of a shell.
	 *
	 * @param node     the node the shell runs, or {@code null} for a shell with none, as the tests use
	 * @param output   where results are written
	 * @param terminal where the user is asked
	 */
	public ShellSession(KadNode node, Output output, Terminal terminal) {
		this.node = node;
		this.output = output;
		this.terminal = terminal;
	}

	/**
	 * Returns the node, which must be running.
	 *
	 * @return the node
	 * @throws CliException if there is no node, or it has been stopped
	 */
	public KadNode node() {
		if (node == null || !node.isRunning())
			throw new CliException(ExitCode.FAILED, "The node is not running.",
					"Leave the shell with 'exit' and start it again.");
		return node;
	}

	/**
	 * Returns the node, running or not.
	 *
	 * @return the node, or {@code null} if the shell has none
	 */
	public KadNode nodeOrStopped() {
		return node;
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
}
