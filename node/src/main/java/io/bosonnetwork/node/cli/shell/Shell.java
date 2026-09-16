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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

import io.bosonnetwork.cli.common.ExitCode;

/**
 * The shell's read-execute loop, and the root of the commands typed at its prompt.
 * <p>
 * Input is read a line at a time with no line editor: the terminal stays in its ordinary line mode,
 * so output another thread writes while a command is being typed can land in the middle of it, but
 * cannot leave the terminal in a state it does not recover from.
 */
@Command(name = "", synopsisSubcommandLabel = "COMMAND",
		description = "Commands of the Boson DHT shell.",
		footer = {"", "Type 'exit' or 'quit', or press Ctrl-D, to leave the shell."},
		subcommands = {
			HelpCommand.class,
			IdCommand.class,
			BootstrapCommand.class,
			FindCommand.class,
			StoreCommand.class,
			AnnounceCommand.class,
			RoutingCommand.class,
			StorageCommand.class,
			CacheCommand.class,
			KeygenCommand.class,
			StopCommand.class
		})
public class Shell {
	/** What the shell prompts with. */
	public static final String PROMPT = "Boson $ ";

	private final ShellSession session;
	private final BufferedReader in;
	private final PrintWriter out;
	private final CommandLine commandLine;

	/**
	 * Creates a shell.
	 *
	 * @param session what its commands work with
	 * @param in      where commands are read from
	 * @param out     where they write
	 */
	public Shell(ShellSession session, Reader in, PrintWriter out) {
		this.session = session;
		this.in = in instanceof BufferedReader reader ? reader : new BufferedReader(in);
		this.out = out;
		this.commandLine = new CommandLine(this)
				.setOut(out)
				.setErr(out)
				.setCaseInsensitiveEnumValuesAllowed(true)
				.setExecutionExceptionHandler((e, cmd, parseResult) -> report(cmd.getErr(), e));

		// Usage lines are "Usage: " followed by the command's qualified name, which starts with its
		// parent's. The root of these commands has no name, which would leave two spaces there.
		dropUsageHeadingSpace(commandLine);
	}

	// A command that knows what went wrong says so itself, hint and all. Anything else is a surprise,
	// and in a developer's shell the type of it is part of the answer.
	private static int report(PrintWriter err, Throwable error) {
		Throwable e = error;
		while ((e instanceof ExecutionException || e instanceof CompletionException) && e.getCause() != null)
			e = e.getCause();

		if (e instanceof io.bosonnetwork.cli.common.CliException failure) {
			err.println("Error: " + failure.getMessage());
			if (failure.getHint() != null)
				err.println("Hint: " + failure.getHint());
			return failure.getExitCode();
		}

		err.println("Error: " + describe(error));
		return ExitCode.FAILED;
	}

	/**
	 * Returns what the commands of this shell work with.
	 *
	 * @return the session
	 */
	public ShellSession session() {
		return session;
	}

	private static void dropUsageHeadingSpace(CommandLine commandLine) {
		commandLine.getCommandSpec().usageMessage().synopsisHeading("Usage:");
		commandLine.getSubcommands().values().forEach(Shell::dropUsageHeadingSpace);
	}

	/**
	 * Reads and executes commands until {@code exit}, {@code quit} or the end of the input.
	 *
	 * @return the exit code of the shell
	 * @throws IOException if reading the input fails
	 */
	public int run() throws IOException {
		while (true) {
			out.print(PROMPT);
			out.flush();

			String line = in.readLine();
			if (line == null) {
				// Ctrl-D, or the end of piped input: end the line the prompt left open.
				out.println();
				return ExitCode.OK;
			}

			if (!execute(line))
				return ExitCode.OK;
		}
	}

	/**
	 * Executes one line of input.
	 *
	 * @param line the line as typed
	 * @return {@code false} if the line asks to leave the shell, {@code true} otherwise
	 */
	boolean execute(String line) {
		List<String> args;
		try {
			args = tokenize(line);
		} catch (IllegalArgumentException e) {
			out.println("Error: " + e.getMessage());
			return true;
		}

		if (args.isEmpty())
			return true;

		String name = args.get(0);
		if (name.equals("exit") || name.equals("quit"))
			return false;

		if (!commandLine.getSubcommands().containsKey(name)) {
			out.println("Unknown command '" + name + "'. Type 'help' for the list of commands.");
			return true;
		}

		commandLine.execute(args.toArray(new String[0]));
		return true;
	}

	/**
	 * Splits a line the way a shell does: whitespace separates arguments, and quotes keep it.
	 * <p>
	 * Single quotes carry their content unchanged, which is what JSON arguments need; double quotes
	 * unescape {@code \"} and {@code \\} only, so a Windows path does not need doubling.
	 *
	 * @param line the line as typed
	 * @return the arguments
	 * @throws IllegalArgumentException if a quote is left open
	 */
	public static List<String> tokenize(String line) {
		List<String> args = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inArgument = false;

		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);

			if (Character.isWhitespace(c)) {
				if (inArgument) {
					args.add(current.toString());
					current.setLength(0);
					inArgument = false;
				}
				continue;
			}

			inArgument = true;
			if (c == '\'') {
				int end = line.indexOf('\'', i + 1);
				if (end < 0)
					throw new IllegalArgumentException("Unterminated single quote");
				current.append(line, i + 1, end);
				i = end;
			} else if (c == '"') {
				i = appendDoubleQuoted(line, i, current);
			} else {
				current.append(c);
			}
		}

		if (inArgument)
			args.add(current.toString());

		return args;
	}

	private static int appendDoubleQuoted(String line, int start, StringBuilder current) {
		for (int i = start + 1; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '"')
				return i;

			if (c == '\\' && i + 1 < line.length()) {
				char next = line.charAt(i + 1);
				if (next == '"' || next == '\\') {
					current.append(next);
					i++;
					continue;
				}
			}

			current.append(c);
		}

		throw new IllegalArgumentException("Unterminated double quote");
	}

	/**
	 * Describes a failure in one line: what it was, and what it said.
	 *
	 * @param error the failure
	 * @return the description
	 */
	public static String describe(Throwable error) {
		Throwable e = error;
		while ((e instanceof ExecutionException || e instanceof CompletionException) && e.getCause() != null)
			e = e.getCause();

		String message = e.getMessage();
		return message != null ? e.getClass().getSimpleName() + ": " + message : e.getClass().getSimpleName();
	}
}
