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

/**
 * Runs a node as a service: exactly what {@code boson-node run} does, under a name a unit file can
 * name directly.
 * <p>
 * A packaged node is started by something that is not a person - systemd, a container - and such a
 * command line is easier to read, and to keep working across releases, when it names a class rather
 * than a subcommand. This adds no behaviour of its own: it prepends {@code run} and hands over, so
 * the options, the exit codes and the logging are the ones {@code boson-node run} documents, and
 * cannot drift from them.
 * <p>
 * Example, for a unit file:
 * <pre>{@code
 * ExecStart=/usr/lib/boson/jre/bin/java -cp /usr/lib/boson/lib/* \
 *     io.bosonnetwork.node.cli.NodeLauncher -c /etc/boson/bootstrap/node.yaml
 * }</pre>
 */
public final class NodeLauncher {
	private NodeLauncher() {
	}

	/**
	 * Runs a node with the given options.
	 *
	 * @param args the options of {@code boson-node run}
	 */
	public static void main(String[] args) {
		BosonNode.main(asRunCommand(args));
	}

	/**
	 * Returns the command line this launcher stands for.
	 *
	 * @param args the options given
	 * @return the same options, as the {@code run} command
	 */
	static String[] asRunCommand(String[] args) {
		String[] command = new String[args.length + 1];
		command[0] = "run";
		System.arraycopy(args, 0, command, 1, args.length);
		return command;
	}
}
