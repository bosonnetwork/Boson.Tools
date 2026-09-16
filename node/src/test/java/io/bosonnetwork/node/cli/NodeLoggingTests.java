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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.bosonnetwork.cli.common.CliLogging;

/**
 * Tests of where each command's logging goes. A node logs, and what that must not do is land in the
 * middle of a command's output - or be silenced for the daemon, whose logging is the operator's.
 */
class NodeLoggingTests {
	private String configuration;

	@BeforeEach
	void takeTheProperty() {
		configuration = System.getProperty(CliLogging.CONFIGURATION_PROPERTY);
		System.clearProperty(CliLogging.CONFIGURATION_PROPERTY);
	}

	@AfterEach
	void putItBack() {
		if (configuration != null)
			System.setProperty(CliLogging.CONFIGURATION_PROPERTY, configuration);
		else
			System.clearProperty(CliLogging.CONFIGURATION_PROPERTY);
	}

	private static String selected() {
		return System.getProperty(CliLogging.CONFIGURATION_PROPERTY);
	}

	@Test
	void theDaemonsLoggingIsLeftToItsOperator() {
		NodeLogging.configure(new String[] {"run", "-c", "/etc/boson/bootstrap/node.yaml"});
		assertNull(selected(), "run must not override the logging a service manager set up");
	}

	@Test
	void theShellLogsToAFile() {
		NodeLogging.configure(new String[] {"shell", "--developer-mode"});
		assertEquals(NodeLogging.SHELL_CONFIGURATION, selected());
	}

	@Test
	void everyOtherCommandIsQuiet() {
		NodeLogging.configure(new String[] {"cache", "--data-dir", "data"});
		assertTrue(selected().contains("quiet"), selected());
	}

	@Test
	void helpAndVersionAreNeverPrecededByLogging() {
		NodeLogging.configure(new String[] {"run", "--help"});
		assertTrue(selected().contains("quiet"), selected());

		System.clearProperty(CliLogging.CONFIGURATION_PROPERTY);
		NodeLogging.configure(new String[] {"--version"});
		assertTrue(selected().contains("quiet"), selected());

		System.clearProperty(CliLogging.CONFIGURATION_PROPERTY);
		NodeLogging.configure(new String[] {"shell", "-h"});
		assertTrue(selected().contains("quiet"), selected());
	}
}
