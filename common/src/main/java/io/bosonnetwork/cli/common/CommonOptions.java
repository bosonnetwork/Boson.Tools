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

import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;

/**
 * The options every command of every Boson tool takes, before or after the command name: how to
 * write the output, and how much to say about failures.
 */
public class CommonOptions {
	@Option(names = "--json", scope = ScopeType.INHERIT,
			description = "Print results as JSON, for scripts.")
	boolean json;

	@Option(names = {"-v", "--verbose"}, scope = ScopeType.INHERIT,
			description = "Log what happens, and show the details of errors.")
	boolean verbose;

	/**
	 * Tells whether results are printed as JSON.
	 *
	 * @return {@code true} with {@code --json}
	 */
	public boolean json() {
		return json;
	}

	/**
	 * Tells whether logging and error details are shown.
	 *
	 * @return {@code true} with {@code --verbose}
	 */
	public boolean verbose() {
		return verbose;
	}
}
