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

import java.io.Console;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Asks the user for what a command needs: confirmation of destructive operations, and secrets such
 * as passphrases and private keys, which are never taken from the command line.
 * <p>
 * On a terminal, secrets are read without echo and confirmations are asked. Without one - a script,
 * a pipe - a secret is read as one line of standard input, and a confirmation must be given with
 * {@code --yes} up front: there is nobody to answer.
 */
public final class Terminal {
	private static final String ABORTED = "Aborted; nothing was changed.";

	private final CliEnvironment environment;

	/**
	 * Creates the terminal of an environment.
	 *
	 * @param environment the environment
	 */
	public Terminal(CliEnvironment environment) {
		this.environment = environment;
	}

	/**
	 * Tells whether a user is there to answer: the tool runs attached to a terminal.
	 *
	 * @return {@code true} on a terminal
	 */
	public boolean isInteractive() {
		return environment.console() != null;
	}

	/**
	 * Asks the user to confirm an operation, unless {@code --yes} already did.
	 *
	 * @param question  the question, such as {@code "Remove device X?"}
	 * @param confirmed whether {@code --yes} was given
	 * @throws CliException if the user declines, or there is no terminal to ask on
	 */
	public void confirm(String question, boolean confirmed) {
		if (confirmed)
			return;

		Console console = environment.console();
		if (console == null)
			throw CliException.usage("This command needs confirmation, and there is no terminal to ask on.",
					"Pass --yes to confirm it.");

		String answer = console.readLine("%s [y/N] ", question);
		String normalized = answer == null ? "" : answer.strip().toLowerCase(Locale.ROOT);
		if (!normalized.equals("y") && !normalized.equals("yes"))
			throw new CliException(ExitCode.FAILED, ABORTED);
	}

	/**
	 * Reads a secret: without echo on a terminal, otherwise one line of standard input.
	 *
	 * @param prompt the prompt, such as {@code "Passphrase: "}
	 * @param what   what the secret is, for the error message, such as {@code "passphrase"}
	 * @return the secret, not empty
	 * @throws CliException if nothing is entered
	 */
	public String readSecret(String prompt, String what) {
		Console console = environment.console();
		if (console != null) {
			char[] chars = console.readPassword("%s", prompt);
			if (chars == null)
				throw new CliException(ExitCode.FAILED, ABORTED);

			String secret = new String(chars);
			Arrays.fill(chars, ' ');
			if (secret.isEmpty())
				throw CliException.usage("No " + what + " was entered; nothing was changed.", null);
			return secret;
		}

		String line;
		try {
			line = environment.stdin().readLine();
		} catch (IOException e) {
			throw CliException.failed("Cannot read the " + what + " from standard input: " + e.getMessage(), null);
		}

		if (line == null || line.isEmpty())
			throw CliException.usage("Expected the " + what + " on standard input, but there was none.",
					"Run the command in a terminal to be asked for it, or pipe it in.");
		return line;
	}

	/**
	 * Reads a new secret. On a terminal it is asked twice, and the two must match; otherwise it is
	 * one line of standard input.
	 *
	 * @param label the prompt, without the colon, such as {@code "New passphrase"}
	 * @param what  what the secret is, such as {@code "passphrase"}
	 * @return the secret, not empty
	 * @throws CliException if nothing is entered, or the two entries differ
	 */
	public String readNewSecret(String label, String what) {
		if (environment.console() == null)
			return readSecret("", what);

		String first = readSecret(label + ": ", what);
		String second = readSecret("Repeat the " + what + ": ", what);
		if (!first.equals(second))
			throw CliException.usage("The two entries do not match; nothing was changed.", null);
		return first;
	}
}
