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
import java.io.InputStream;
import java.util.Properties;

import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Answers {@code --version} with the tool's name and the Boson version it was built from.
 */
public class VersionProvider implements IVersionProvider {
	private static final String VERSION_RESOURCE = "/io/bosonnetwork/cli/version.properties";

	@Spec
	CommandSpec spec;

	@Override
	public String[] getVersion() {
		return new String[] { spec.root().name() + " " + version() };
	}

	/**
	 * Returns the Boson version the tools were built from.
	 *
	 * @return the version, or {@code "development"} when run from sources
	 */
	public static String version() {
		try (InputStream in = VersionProvider.class.getResourceAsStream(VERSION_RESOURCE)) {
			if (in != null) {
				Properties properties = new Properties();
				properties.load(in);
				String version = properties.getProperty("version");
				if (version != null && !version.startsWith("${"))
					return version;
			}
		} catch (IOException ignored) {
			// fall through
		}
		return "development";
	}
}
