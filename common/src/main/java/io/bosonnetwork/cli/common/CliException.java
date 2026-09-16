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

/**
 * A failure a command reports to the user as it is: a message saying what went wrong, an optional
 * hint saying how to fix it, and the exit code.
 * <p>
 * Commands throw it for the failures they understand; {@link ErrorReporter} turns every other
 * exception into the same form.
 */
public class CliException extends RuntimeException {
	private static final long serialVersionUID = -1754085462391720863L;

	private final int exitCode;
	private final String hint;

	/**
	 * Creates the exception.
	 *
	 * @param exitCode the exit code, one of {@link ExitCode}
	 * @param message  what went wrong, as a sentence
	 * @param hint     how to fix it, as a sentence, or {@code null}
	 */
	public CliException(int exitCode, String message, String hint) {
		super(message);
		this.exitCode = exitCode;
		this.hint = hint;
	}

	/**
	 * Creates the exception without a hint.
	 *
	 * @param exitCode the exit code, one of {@link ExitCode}
	 * @param message  what went wrong, as a sentence
	 */
	public CliException(int exitCode, String message) {
		this(exitCode, message, null);
	}

	/**
	 * Returns the exit code.
	 *
	 * @return the exit code
	 */
	public int getExitCode() {
		return exitCode;
	}

	/**
	 * Returns how to fix the failure.
	 *
	 * @return the hint, or {@code null}
	 */
	public String getHint() {
		return hint;
	}

	/**
	 * Creates a failure of the command line.
	 *
	 * @param message what is wrong
	 * @param hint    how to fix it, or {@code null}
	 * @return the exception
	 */
	public static CliException usage(String message, String hint) {
		return new CliException(ExitCode.USAGE, message, hint);
	}

	/**
	 * Creates a failure of the configuration or identity.
	 *
	 * @param message what is wrong
	 * @param hint    how to fix it, or {@code null}
	 * @return the exception
	 */
	public static CliException config(String message, String hint) {
		return new CliException(ExitCode.CONFIG, message, hint);
	}

	/**
	 * Creates a failure for something that does not exist.
	 *
	 * @param message what is missing
	 * @param hint    how to fix it, or {@code null}
	 * @return the exception
	 */
	public static CliException notFound(String message, String hint) {
		return new CliException(ExitCode.NOT_FOUND, message, hint);
	}

	/**
	 * Creates a general failure.
	 *
	 * @param message what went wrong
	 * @param hint    how to fix it, or {@code null}
	 * @return the exception
	 */
	public static CliException failed(String message, String hint) {
		return new CliException(ExitCode.FAILED, message, hint);
	}
}
