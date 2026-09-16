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

package io.bosonnetwork.cli.support;

import java.nio.file.Path;
import java.util.Objects;

/**
 * What sets one command line tool apart from the other where they share code: its name, where its
 * configuration and identity live by default, and whose identity it acts with.
 */
public final class ToolSpec {
	private final String name;
	private final Path defaultConfigFile;
	private final String defaultIdentityFileName;
	private final String identityRole;
	private final String unauthorizedHint;

	/**
	 * Describes a tool.
	 *
	 * @param name                    the command name, such as {@code boson-cli}
	 * @param defaultConfigFile       the configuration file used unless another is named
	 * @param defaultIdentityFileName the identity file used unless another is named, beside the
	 *                                configuration file
	 * @param identityRole            whose identity the tool acts with, such as {@code "user"}
	 * @param unauthorizedHint        how to fix an identity the Director does not accept
	 */
	public ToolSpec(String name, Path defaultConfigFile, String defaultIdentityFileName, String identityRole,
			String unauthorizedHint) {
		this.name = Objects.requireNonNull(name, "name");
		this.defaultConfigFile = Objects.requireNonNull(defaultConfigFile, "defaultConfigFile");
		this.defaultIdentityFileName = Objects.requireNonNull(defaultIdentityFileName, "defaultIdentityFileName");
		this.identityRole = Objects.requireNonNull(identityRole, "identityRole");
		this.unauthorizedHint = Objects.requireNonNull(unauthorizedHint, "unauthorizedHint");
	}

	/**
	 * Returns the command name.
	 *
	 * @return the name, such as {@code boson-cli}
	 */
	public String name() {
		return name;
	}

	/**
	 * Returns the configuration file used unless another is named.
	 *
	 * @return the file
	 */
	public Path defaultConfigFile() {
		return defaultConfigFile;
	}

	/**
	 * Returns the name of the identity file used unless another is named, beside the configuration
	 * file.
	 *
	 * @return the file name, such as {@code user.identity}
	 */
	public String defaultIdentityFileName() {
		return defaultIdentityFileName;
	}

	/**
	 * Returns whose identity the tool acts with.
	 *
	 * @return the role, such as {@code "user"} or {@code "administrator"}
	 */
	public String identityRole() {
		return identityRole;
	}

	/**
	 * Returns how to fix an identity the Director does not accept.
	 *
	 * @return the hint
	 */
	public String unauthorizedHint() {
		return unauthorizedHint;
	}

	/**
	 * Returns a command line of this tool, for hints.
	 *
	 * @param arguments the arguments, such as {@code "identity create"}
	 * @return the command, quoted, such as {@code 'boson-cli identity create'}
	 */
	public String command(String arguments) {
		return "'" + name + " " + arguments + "'";
	}
}
