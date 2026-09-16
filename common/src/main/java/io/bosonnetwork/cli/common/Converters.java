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

import picocli.CommandLine;

import io.bosonnetwork.Id;

/**
 * The Boson types the tools read from a command line.
 * <p>
 * picocli builds an argument's type from a registered converter, a constructor taking a string, or a
 * static {@code valueOf}; a Boson {@link Id} has none of those, so it needs one registered. Every
 * {@code CommandLine} that parses arguments needs them - a tool's root command, and the interactive
 * shell, which builds one of its own - which is why they are listed here rather than at each.
 */
public final class Converters {
	private Converters() {
	}

	/**
	 * Registers the converters with a command and the subcommands it already has, so this must be
	 * called once the command line is complete.
	 *
	 * @param commandLine the command line
	 * @return the same command line
	 */
	public static CommandLine registerAll(CommandLine commandLine) {
		return commandLine.registerConverter(Id.class, new IdConverter());
	}
}
