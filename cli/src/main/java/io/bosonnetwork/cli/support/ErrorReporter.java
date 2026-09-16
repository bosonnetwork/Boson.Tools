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

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.security.cert.CertificateException;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import io.bosonnetwork.Id;
import io.bosonnetwork.director.client.exceptions.ConflictException;
import io.bosonnetwork.director.client.exceptions.DirectorException;
import io.bosonnetwork.director.client.exceptions.DirectorServerException;
import io.bosonnetwork.director.client.exceptions.ForbiddenException;
import io.bosonnetwork.director.client.exceptions.InvalidRequestException;
import io.bosonnetwork.director.client.exceptions.NotEnabledException;
import io.bosonnetwork.director.client.exceptions.NotFoundException;
import io.bosonnetwork.director.client.exceptions.PassphraseRequiredException;
import io.bosonnetwork.director.client.exceptions.RateLimitException;
import io.bosonnetwork.director.client.exceptions.RegistrationDisabledException;
import io.bosonnetwork.director.client.exceptions.ServiceBusyException;
import io.bosonnetwork.director.client.exceptions.UnauthorizedException;

/**
 * Turns whatever a command fails with into what the user needs: a sentence saying what went wrong, a
 * hint saying how to fix it when there is one, and the exit code.
 * <p>
 * Stack traces, exception class names and HTTP details stay out of it; {@code --verbose} shows them.
 */
public final class ErrorReporter {
	private ErrorReporter() {
	}

	/**
	 * Reports a failure on standard error.
	 *
	 * @param error   the failure
	 * @param context the context of the run
	 * @param err     standard error
	 * @param verbose whether to show the details of the failure
	 * @return the exit code
	 */
	public static int report(Throwable error, CliContext context, PrintWriter err, boolean verbose) {
		CliException failure = translate(error, context);
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
	 * Translates a failure.
	 *
	 * @param error   the failure
	 * @param context the context of the run
	 * @return the failure as it is reported
	 */
	public static CliException translate(Throwable error, CliContext context) {
		Throwable e = unwrap(error);

		if (e instanceof CliException cli)
			return cli;
		if (e instanceof DirectorException director)
			return director(director, context);
		// What the clients throw for arguments they refuse, before sending anything.
		if (e instanceof IllegalArgumentException || e instanceof IllegalStateException)
			return CliException.usage(sentence(e.getMessage()), null);
		if (e instanceof NoSuchFileException file)
			return CliException.failed("No such file: " + file.getFile() + ".", null);
		if (e instanceof AccessDeniedException file)
			return CliException.failed("Permission denied: " + file.getFile() + ".", null);
		if (e instanceof IOException)
			return CliException.failed(sentence(e.getMessage()), null);

		return new CliException(ExitCode.FAILED, "Unexpected error: " + describe(e),
				"Run the command again with --verbose for the details.");
	}

	private static CliException director(DirectorException e, CliContext context) {
		String detail = detail(e);

		if (e instanceof PassphraseRequiredException)
			return new CliException(ExitCode.NOT_AUTHORIZED, "This account is protected by a passphrase.",
					"Pass --passphrase to be asked for it.");

		if (e instanceof UnauthorizedException) {
			Id id = context.identityId();
			String role = context.tool().identityRole();
			String who = id != null ? "the " + role + " identity " + id : "this " + role + " identity";
			return new CliException(ExitCode.NOT_AUTHORIZED, "The Director did not accept " + who + suffix(detail),
					context.tool().unauthorizedHint());
		}

		if (e instanceof ForbiddenException)
			return new CliException(ExitCode.NOT_AUTHORIZED, "The Director refused the request" + suffix(detail), null);
		if (e instanceof NotFoundException)
			return CliException.notFound(detail != null ? sentence(detail) : "Not found.", null);
		if (e instanceof ConflictException)
			return new CliException(ExitCode.CONFLICT, detail != null ? sentence(detail) : "It already exists.", null);
		if (e instanceof RateLimitException limit)
			return new CliException(ExitCode.UNAVAILABLE, "The Director is limiting the rate of requests" + suffix(detail),
					retryHint(limit.getRetryAfter()));
		if (e instanceof ServiceBusyException busy)
			return new CliException(ExitCode.UNAVAILABLE, "The Director is too busy to handle the request" + suffix(detail),
					retryHint(busy.getRetryAfter()));
		if (e instanceof RegistrationDisabledException)
			return CliException.failed(sentence(detail != null ? detail : e.getMessage()), null);
		if (e instanceof NotEnabledException)
			return CliException.failed("The super node does not offer this" + suffix(detail), null);
		if (e instanceof InvalidRequestException)
			return CliException.failed("The Director rejected the request as invalid" + suffix(detail), null);
		if (e instanceof DirectorServerException)
			return new CliException(ExitCode.UNAVAILABLE,
					"The Director failed to handle the request (HTTP " + e.getStatus() + ")" + suffix(detail),
					"The super node's log has the details.");

		int status = e.getStatus();
		if (status == DirectorException.NO_HTTP_STATUS)
			return connectionFailure(e, context);
		if (status >= 200 && status < 300)
			return CliException.failed("The Director answered with something this tool cannot read.",
					"Check that the URL is the Director of a Boson super node, of a version this tool supports.");
		if (status >= 300 && status < 400)
			return CliException.failed("The Director URL redirects elsewhere (HTTP " + status + "), which this tool does not follow.",
					"Use the URL it redirects to; often that is the https:// one.");

		return CliException.failed("The Director refused the request (HTTP " + status + ")" + suffix(detail), null);
	}

	private static CliException connectionFailure(DirectorException e, CliContext context) {
		URL url = context.directorUrl();
		String where = url != null ? url.toString() : "the Director";
		String host = url != null ? url.getHost() : "the Director";
		String chain = messages(e).toLowerCase(Locale.ROOT);
		Throwable root = rootCause(e);
		String reason = root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();

		if (hasCause(e, "NotSslRecordException") || chain.contains("not an ssl/tls record"))
			return new CliException(ExitCode.UNAVAILABLE,
					host + " did not answer the TLS handshake with TLS: it serves plain HTTP on this port.",
					"Use an http:// URL for a Director with ssl: false or behind a TLS-terminating proxy, and https:// for one serving TLS itself.");

		if (hasCause(e, CertificateException.class) ||
				(hasCause(e, SSLHandshakeException.class) && (chain.contains("certificate") || chain.contains("pkix") || chain.contains("trust"))))
			return new CliException(ExitCode.UNAVAILABLE, "The TLS certificate of " + host + " is not trusted: " + reason,
					"For a self-signed Director certificate, set the node id (" + context.tool().command("config set nodeId <id>") +
					"); for a certificate from a CA, use the host name it was issued for.");

		if (hasCause(e, SSLException.class))
			return new CliException(ExitCode.UNAVAILABLE, "The TLS connection to " + where + " failed: " + reason,
					"Check the URL scheme: https:// needs a Director that serves TLS.");

		if (hasCause(e, UnknownHostException.class))
			return new CliException(ExitCode.UNAVAILABLE, "Cannot find the host " + host + ".",
					"Check the host name in the URL, or connect to an address with --resolve.");

		if (hasCause(e, ConnectException.class))
			return new CliException(ExitCode.UNAVAILABLE,
					"Cannot connect to " + where + ": " + (chain.contains("refused") ? "connection refused." : reason),
					"Check the URL, and that the super node is running and reachable from here.");

		if (hasCause(e, TimeoutException.class) || hasCause(e, "TimeoutException") || chain.contains("timed out"))
			return new CliException(ExitCode.UNAVAILABLE, "Timed out connecting to " + where + ".",
					"Check the URL, and that no firewall blocks the port.");

		if (hasCause(e, ClosedChannelException.class) || chain.contains("connection reset") || chain.contains("closed"))
			return new CliException(ExitCode.UNAVAILABLE, "The connection to " + where + " closed before the Director answered.",
					"Check the URL scheme and port: an https:// URL for a plain HTTP port, or the reverse, ends this way.");

		return new CliException(ExitCode.UNAVAILABLE, "Cannot reach the Director at " + where + ": " + reason,
				"Run the command again with --verbose for the details.");
	}

	// The Director explains a refusal as "<reason> - <detail>"; the reason only restates the status.
	private static String detail(DirectorException e) {
		String message = e.getMessage();
		if (message == null || message.isBlank() || message.equals("HTTP " + e.getStatus()))
			return null;

		// An HTML error page from a proxy in front of the Director says nothing useful here.
		if (message.startsWith("<"))
			return null;

		int separator = message.indexOf(" - ");
		String detail = separator >= 0 ? message.substring(separator + 3) : message;
		return detail.isBlank() ? null : detail.strip();
	}

	private static String suffix(String detail) {
		return detail == null ? "." : ": " + sentence(detail);
	}

	private static String retryHint(long seconds) {
		return seconds > 0 ? "Try again in " + seconds + (seconds == 1 ? " second." : " seconds.") : "Try again later.";
	}

	// Starts with a capital letter and ends with a full stop.
	static String sentence(String text) {
		if (text == null || text.isBlank())
			return "Unknown error.";

		String s = text.strip();
		s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
		char last = s.charAt(s.length() - 1);
		return last == '.' || last == '!' || last == '?' ? s : s + ".";
	}

	private static Throwable unwrap(Throwable e) {
		Throwable t = e;
		while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null)
			t = t.getCause();
		return t;
	}

	private static Throwable rootCause(Throwable e) {
		Throwable t = e;
		while (t.getCause() != null && t.getCause() != t)
			t = t.getCause();
		return t;
	}

	private static boolean hasCause(Throwable e, Class<? extends Throwable> type) {
		for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause())
			if (type.isInstance(t))
				return true;
		return false;
	}

	// By simple name, for the Netty types this module does not compile against.
	private static boolean hasCause(Throwable e, String simpleName) {
		for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause())
			for (Class<?> c = t.getClass(); c != null; c = c.getSuperclass())
				if (c.getSimpleName().equals(simpleName))
					return true;
		return false;
	}

	private static String messages(Throwable e) {
		StringBuilder text = new StringBuilder();
		for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause())
			text.append(t.getMessage()).append('\n');
		return text.toString();
	}

	private static String describe(Throwable e) {
		return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
	}
}
