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

import java.util.Locale;

/**
 * Looking through a failure's causes, which is how the tools tell a refused connection from an
 * untrusted certificate from a name that does not resolve - the things a user can act on.
 */
public final class Causes {
	private Causes() {
	}

	/**
	 * Tells whether a failure, or anything that caused it, is of a type.
	 *
	 * @param error the failure
	 * @param type  the type
	 * @return {@code true} if the type appears in the chain
	 */
	public static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
		for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause())
			if (type.isInstance(t))
				return true;
		return false;
	}

	/**
	 * Tells whether a failure, or anything that caused it, has a class of this simple name - for the
	 * types a tool does not compile against, such as Netty's.
	 *
	 * @param error      the failure
	 * @param simpleName the class name, without its package
	 * @return {@code true} if the name appears in the chain, including as a supertype
	 */
	public static boolean hasCause(Throwable error, String simpleName) {
		for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause())
			for (Class<?> c = t.getClass(); c != null; c = c.getSuperclass())
				if (c.getSimpleName().equals(simpleName))
					return true;
		return false;
	}

	/**
	 * Returns the failure at the end of the chain, which usually says what actually happened.
	 *
	 * @param error the failure
	 * @return the root cause
	 */
	public static Throwable rootCause(Throwable error) {
		Throwable t = error;
		while (t.getCause() != null && t.getCause() != t)
			t = t.getCause();
		return t;
	}

	/**
	 * Returns the message of a failure, or its class name when it has none.
	 *
	 * @param error the failure
	 * @return the message
	 */
	public static String describe(Throwable error) {
		return error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
	}

	/**
	 * Returns every message in the chain, in lower case, for matching against what a library says
	 * when its exception types say nothing useful.
	 *
	 * @param error the failure
	 * @return the messages, one per line
	 */
	public static String messages(Throwable error) {
		StringBuilder text = new StringBuilder();
		for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause())
			text.append(t.getMessage()).append('\n');
		return text.toString().toLowerCase(Locale.ROOT);
	}
}
