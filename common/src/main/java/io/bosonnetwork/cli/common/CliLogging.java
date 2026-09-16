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

package io.bosonnetwork.cli.common;

import java.util.Arrays;

/**
 * Chooses the tools' logging configuration.
 * <p>
 * The Director clients, Vert.x and Netty log through SLF4J, and with no configuration logback writes
 * everything to the console - where it would mix with, and bury, what the command has to say. So
 * logging is off, and {@code --verbose} turns it on, to standard error.
 * <p>
 * The configuration is chosen here rather than by a {@code logback.xml} on the class path, which the
 * Director and the services sharing a distribution's {@code lib/} would pick up too. It overrides a
 * configuration named on the JVM command line for the same reason: a launcher's logging setup is
 * meant for the servers, never for a command's output.
 */
public final class CliLogging {
	/** The system property naming logback's configuration, which a tool may set for itself. */
	public static final String CONFIGURATION_PROPERTY = "logback.configurationFile";
	/** The system property naming logback's status listener, which keeps its own startup quiet. */
	public static final String STATUS_LISTENER_PROPERTY = "logback.statusListenerClass";
	static final String QUIET = "io/bosonnetwork/cli/logback-quiet.xml";
	static final String VERBOSE = "io/bosonnetwork/cli/logback-verbose.xml";

	private CliLogging() {
	}

	/**
	 * Selects the logging configuration for a command line. Must run before the first logger is
	 * created.
	 *
	 * @param args the command line
	 */
	public static void configure(String[] args) {
		// Picocli has not parsed anything yet, and cannot before the first logger exists, so the flag
		// is looked for directly. After "--" it would be a value, not an option.
		boolean verbose = Arrays.stream(args)
				.takeWhile(arg -> !arg.equals("--"))
				.anyMatch(arg -> arg.equals("-v") || arg.equals("--verbose"));

		System.setProperty(CONFIGURATION_PROPERTY, verbose ? VERBOSE : QUIET);
		if (!verbose)
			System.setProperty(STATUS_LISTENER_PROPERTY, "ch.qos.logback.core.status.NopStatusListener");
	}
}
