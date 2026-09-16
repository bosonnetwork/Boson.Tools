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

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Turns whatever a command fails with into what the user needs: a sentence saying what went wrong, a
 * hint saying how to fix it when there is one, and the exit code.
 * <p>
 * The failures of a tool's own domain are translated by its {@link ErrorTranslator}; what it does not
 * recognise is handled here. Stack traces, exception class names and protocol details stay out of it;
 * {@code --verbose} shows them.
 */
public final class ErrorReporter {
	private ErrorReporter() {
	}

	/**
	 * Reports a failure on standard error.
	 *
	 * @param error   the failure
	 * @param domain  translates the failures of the tool's domain
	 * @param err     standard error
	 * @param verbose whether to show the details of the failure
	 * @return the exit code
	 */
	public static int report(Throwable error, ErrorTranslator domain, PrintWriter err, boolean verbose) {
		CliException failure = translate(error, domain);
		err.println("Error: " + failure.getMessage());
		if (failure.getHint() != null)
			err.println("Hint: " + failure.getHint());

		Throwable cause = unwrap(error);
		if (verbose && !(cause instanceof CliException)) {
			err.println();
			cause.printStackTrace(err);
		}

		err.flush();
		return failure.getExitCode();
	}

	/**
	 * Translates a failure: what the tool's domain makes of it, or the generic handling.
	 *
	 * @param error  the failure
	 * @param domain translates the failures of the tool's domain
	 * @return the failure as it is reported
	 */
	public static CliException translate(Throwable error, ErrorTranslator domain) {
		Throwable e = unwrap(error);

		if (e instanceof CliException cli)
			return cli;

		if (domain != null) {
			CliException translated = domain.translate(e);
			if (translated != null)
				return translated;
		}

		// What the client APIs throw for arguments they refuse, before sending anything.
		if (e instanceof IllegalArgumentException || e instanceof IllegalStateException)
			return CliException.usage(sentence(e.getMessage()), null);
		if (e instanceof NoSuchFileException file)
			return CliException.failed("No such file: " + file.getFile() + ".", null);
		if (e instanceof AccessDeniedException file)
			return CliException.failed("Permission denied: " + file.getFile() + ".", null);
		if (e instanceof IOException)
			return CliException.failed(sentence(e.getMessage()), null);

		return new CliException(ExitCode.FAILED, "Unexpected error: " + Causes.describe(e),
				"Run the command again with --verbose for the details.");
	}

	/**
	 * Unwraps the exceptions that only say a call failed somewhere else.
	 *
	 * @param error the failure
	 * @return the failure itself
	 */
	public static Throwable unwrap(Throwable error) {
		Throwable t = error;
		while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null)
			t = t.getCause();
		return t;
	}

	/**
	 * Makes a sentence of a message: a capital letter at the start, a full stop at the end.
	 *
	 * @param text the message
	 * @return the sentence
	 */
	public static String sentence(String text) {
		if (text == null || text.isBlank())
			return "Unknown error.";

		String s = text.strip();
		s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
		char last = s.charAt(s.length() - 1);
		return last == '.' || last == '!' || last == '?' ? s : s + ".";
	}
}
