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

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import io.bosonnetwork.cli.common.CliLogging;

/**
 * Chooses where a command's logging goes, which is not the same for all of them.
 * <ul>
 *   <li>{@code run} is a node in the foreground: its logging is the operator's business, so a
 *       configuration named on the JVM command line is left alone, and otherwise logback's own
 *       default applies - a daemon that logs nothing is worse than one that logs to the console.</li>
 *   <li>{@code shell} starts a node while the user types at a prompt, so its logging goes to a file:
 *       anything written to the console lands in the middle of the prompt.</li>
 *   <li>Every other command is a short question with an answer: logging is off unless
 *       {@code --verbose} asks for it ({@link CliLogging}).</li>
 * </ul>
 */
public final class NodeLogging {
	/** The shell's logging configuration, on the class path. */
	static final String SHELL_CONFIGURATION = "io/bosonnetwork/node/cli/logback-shell.xml";
	/** Where the shell logs, unless LOG_DIR says otherwise. */
	static final String LOG_DIR_VARIABLE = "LOG_DIR";

	private NodeLogging() {
	}

	/**
	 * Selects the logging configuration for a command line. Must run before the first logger is
	 * created.
	 *
	 * @param args the command line
	 */
	public static void configure(String[] args) {
		// Help and version are output like any other, whatever command they are asked of: a node's
		// logging must not land in front of them.
		if (asksForHelp(args)) {
			CliLogging.configure(args);
			return;
		}

		String command = firstCommand(args);

		if ("run".equals(command))
			return;

		if ("shell".equals(command)) {
			selectShellConfiguration();
			return;
		}

		CliLogging.configure(args);
	}

	private static boolean asksForHelp(String[] args) {
		return Arrays.stream(args).anyMatch(arg ->
				arg.equals("-h") || arg.equals("--help") || arg.equals("-V") || arg.equals("--version"));
	}

	// The first argument that is not an option or an option's value; good enough to tell the commands
	// apart before picocli has parsed anything.
	private static String firstCommand(String[] args) {
		for (String arg : args) {
			if (arg.equals("--"))
				break;
			if (!arg.startsWith("-"))
				return arg;
		}
		return null;
	}

	private static void selectShellConfiguration() {
		String named = System.getProperty(CliLogging.CONFIGURATION_PROPERTY);
		if (named != null && exists(named))
			return;

		if (System.getProperty(LOG_DIR_VARIABLE) == null && System.getenv(LOG_DIR_VARIABLE) == null)
			System.setProperty(LOG_DIR_VARIABLE,
					Path.of(System.getProperty("user.home"), ".cache", "boson", "shell", "logs").toString());

		System.setProperty(CliLogging.CONFIGURATION_PROPERTY, SHELL_CONFIGURATION);
	}

	/**
	 * Whether logback can load a configuration from a location, which - as for logback - may be a URL,
	 * a class path resource or a file.
	 *
	 * @param location the location
	 * @return {@code true} if it can be loaded
	 */
	static boolean exists(String location) {
		if (NodeLogging.class.getClassLoader().getResource(location) != null)
			return true;

		try {
			URI uri = new URI(location);
			// A one-letter scheme is a Windows drive, not a URL.
			if (uri.getScheme() != null && uri.getScheme().length() > 1)
				return !uri.getScheme().equalsIgnoreCase("file") || Files.isRegularFile(Path.of(uri));
		} catch (Exception ignored) {
			// Not a URL: try it as a file
		}

		try {
			return Files.isRegularFile(Path.of(location));
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Tells whether a command line asks for the interactive shell, for tests.
	 *
	 * @param args the command line
	 * @return {@code true} for the shell
	 */
	static boolean isShell(String... args) {
		return "shell".equals(firstCommand(Arrays.copyOf(args, args.length)));
	}
}
