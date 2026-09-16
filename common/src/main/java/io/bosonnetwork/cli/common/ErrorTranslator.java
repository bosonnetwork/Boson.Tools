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
 * Turns the failures of one tool's domain - a refused request, an unreachable server, a node that
 * will not start - into what the user is told: what went wrong, how to fix it, and the exit code.
 * <p>
 * A tool supplies one through {@link CliApp#errorTranslator()}. Anything it does not recognise falls
 * through to {@link ErrorReporter}'s generic handling.
 */
@FunctionalInterface
public interface ErrorTranslator {
	/**
	 * Translates a failure.
	 *
	 * @param error the failure, already unwrapped
	 * @return what to tell the user, or {@code null} if this is not a failure of this tool's domain
	 */
	CliException translate(Throwable error);
}
