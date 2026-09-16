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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code resolve} setting: an IP address, with an optional port, to connect to instead of looking
 * up the Director URL's host name - {@code 127.0.0.1}, {@code 127.0.0.1:19000}, {@code ::1},
 * {@code [::1]} or {@code [::1]:19000}.
 * <p>
 * Only addresses are accepted: a host name would itself need looking up, which is what the setting
 * exists to avoid.
 */
public final class ConnectAddress {
	private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
	private static final Pattern IPV4_WITH_PORT = Pattern.compile("(\\d{1,3}(?:\\.\\d{1,3}){3}):(\\d+)");
	private static final Pattern BRACKETED_IPV6 = Pattern.compile("\\[([0-9A-Fa-f:.]+)](?::(\\d+))?");
	private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*");

	private ConnectAddress() {
	}

	/**
	 * Parses the setting.
	 *
	 * @param value       the setting
	 * @param defaultPort the port to use when the setting names none: the URL's
	 * @param origin      where the setting comes from, for the error message
	 * @return the address
	 * @throws CliException if the value is not an IP address with an optional port
	 */
	public static InetSocketAddress parse(String value, int defaultPort, String origin) {
		String text = value.strip();
		String host;
		int port = defaultPort;

		Matcher m;
		if (IPV4.matcher(text).matches()) {
			host = text;
		} else if ((m = IPV4_WITH_PORT.matcher(text)).matches()) {
			host = m.group(1);
			port = parsePort(m.group(2), value, origin);
		} else if ((m = BRACKETED_IPV6.matcher(text)).matches()) {
			host = m.group(1);
			if (m.group(2) != null)
				port = parsePort(m.group(2), value, origin);
		} else if (IPV6.matcher(text).matches()) {
			host = text;
		} else {
			throw invalid(value, origin);
		}

		try {
			// A literal address is parsed, never looked up.
			return new InetSocketAddress(InetAddress.getByName(host), port);
		} catch (UnknownHostException | IllegalArgumentException e) {
			throw invalid(value, origin);
		}
	}

	/**
	 * Tells whether a URL host is an IP address rather than a name.
	 *
	 * @param host the host, as a URL carries it; IPv6 in brackets
	 * @return {@code true} for an address
	 */
	public static boolean isAddress(String host) {
		return IPV4.matcher(host).matches() || host.startsWith("[") || IPV6.matcher(host).matches();
	}

	private static int parsePort(String port, String value, String origin) {
		try {
			int p = Integer.parseInt(port);
			if (p > 0 && p <= 65535)
				return p;
		} catch (NumberFormatException ignored) {
			// reported below
		}
		throw invalid(value, origin);
	}

	private static CliException invalid(String value, String origin) {
		return CliException.config("The resolve address '" + value + "' (from " + origin + ") is not an IP address.",
				"Use an IP address with an optional port, such as 127.0.0.1 or 127.0.0.1:9000.");
	}
}
