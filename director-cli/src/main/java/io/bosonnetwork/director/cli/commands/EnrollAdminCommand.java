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

package io.bosonnetwork.director.cli.commands;

import java.io.Console;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import io.bosonnetwork.Id;
import io.bosonnetwork.cli.common.AsciiQRCode;
import io.bosonnetwork.cli.common.CliEnvironment;
import io.bosonnetwork.cli.common.CliException;
import io.bosonnetwork.cli.common.ExitCode;
import io.bosonnetwork.cli.director.DirectorCommand;
import io.bosonnetwork.director.client.DirectorAdmin;
import io.bosonnetwork.director.client.Enrollment;
import io.bosonnetwork.director.client.EnrollmentApproval;
import io.bosonnetwork.director.client.EnrollmentCode;
import io.bosonnetwork.director.client.exceptions.DirectorException;

/**
 * {@code boson-director-cli user enroll-admin}: makes the user of a Boson Identity app an administrator of
 * the node, by QR code. The QR way to what {@code user grant-admin} does, for a user who may not have an
 * account yet.
 */
@Command(name = "enroll-admin", description = {
		"Make the user of a Boson Identity app an administrator of the node, by QR code.",
		"Shows a code to scan with Boson Identity. The code holds no credential: the first app to scan it claims "
				+ "it and shows a number, which you type here to approve. Approve only your own phone. The user gets "
				+ "an account if it has none, and the phone is registered to it. The code expires in 10 minutes."})
public class EnrollAdminCommand extends DirectorCommand {
	// The status of a request that ended: approved, denied or expired.
	private static final int GONE = 410;

	@Option(names = "--public-url", paramLabel = "<url>",
			description = "The Director's address as the phone reaches it, when the Director doesn't know it or "
					+ "the phone reaches it at another one.")
	String url;

	@Option(names = "--qr", paramLabel = "<style>",
			description = "How to draw the QR code: ${COMPLETION-CANDIDATES}. By default ANSI on a terminal that takes "
					+ "colors, else DARK (for a dark background; LIGHT for a light one).")
	AsciiQRCode.Style style;

	@Override
	protected void run() throws Exception {
		if (output().isJson())
			throw CliException.usage("enroll-admin is interactive: you type the number the phone shows, so it has no "
					+ "--json output.", "Run it without --json.");

		DirectorAdmin admin = context().directorAdmin();
		Id nodeId = await(admin.getNodeId());
		Enrollment request = await(admin.createEnrollment());
		Id requestId = request.getRequestId();

		// Ctrl+C ends the process with the hooks: this one denies the request, so no phone is left holding it.
		Thread hook = new Thread(() -> {
			try {
				admin.cancelEnrollment(requestId).get(3, TimeUnit.SECONDS);
			} catch (Exception e) {
				// Ended already, or the Director is gone: the request expires on its own.
			}
		}, "boson-director-cli-enroll-cancel");
		Runtime.getRuntime().addShutdownHook(hook);
		boolean done = false;
		try {
			EnrollmentCode code = code(requestId, nodeId, request);
			show(code);

			output().progress("Waiting for the phone to scan the code; Ctrl+C cancels...");
			Enrollment claim = await(admin.awaitEnrollmentClaim(requestId));
			if (claim.getState() != Enrollment.State.CLAIMED)
				throw ended(claim.getState());

			output().blank();
			output().message("Claimed by:");
			Map<String, String> rows = new LinkedHashMap<>();
			rows.put("User", claim.getUserId().map(Id::toBase58String).orElse("?"));
			rows.put("Account", claim.isMember() ? "a member of this node, made an administrator" :
					"new: an administrator's account is created");
			rows.put("Device", claim.getDeviceName().orElse("?") + " (" + claim.getApp().orElse("?") + ")");
			claim.getRequestedFrom().ifPresent(from -> rows.put("From", from));
			output().details(rows);
			output().blank();

			approve(admin, requestId, claim);
			done = true;
		} finally {
			try {
				Runtime.getRuntime().removeShutdownHook(hook);
			} catch (IllegalStateException e) {
				// Shutting down already: the hook runs.
			}
			if (!done) {
				try {
					admin.cancelEnrollment(requestId).get(3, TimeUnit.SECONDS);
				} catch (Exception e) {
					// Ended already.
				}
			}
		}
	}

	private EnrollmentCode code(Id requestId, Id nodeId, Enrollment request) {
		String address = url != null ? url : request.getUrl().orElseThrow(() -> CliException.usage(
				"The Director doesn't know the address phones reach it at.",
				"Pass --public-url with it, such as --public-url https://node.example.com."));
		try {
			return EnrollmentCode.of(requestId, nodeId, address);
		} catch (IllegalArgumentException e) {
			throw CliException.usage("Not an address the phone can use: " + address + ".",
					"Pass --public-url with the Director's http(s) address, without user info, query or fragment.");
		}
	}

	// Draws the code as a QR code, unless the terminal is known to be too narrow for it, and as text.
	private void show(EnrollmentCode code) {
		String text = code.toString();
		CliEnvironment environment = context().environment();
		Console console = environment.console();
		AsciiQRCode.Style drawn = style;
		if (drawn == null) {
			Charset charset = console != null ? console.charset() : Charset.defaultCharset();
			drawn = AsciiQRCode.styleFor(charset, console != null &&
					AsciiQRCode.colorsAllowed(environment.variable("NO_COLOR"), environment.variable("TERM")));
		}

		int width = AsciiQRCode.width(text) * (drawn == AsciiQRCode.Style.ASCII ? 2 : 1);
		int columns = columns(environment);
		output().message("Scan this code with Boson Identity (Scan, on the Identity tab):");
		output().blank();
		if (columns > 0 && width > columns) {
			output().message("(The terminal is " + columns + " columns wide, and the QR code needs " + width +
					": widen the terminal, or enter the code below in the app.)");
		} else {
			String qr = AsciiQRCode.render(text, drawn);
			output().message(qr.stripTrailing());
		}
		output().blank();
		output().message("Or enter it in the app: " + text);
		output().blank();
		output().message("The code holds no credential: the first phone to scan it claims it. Approve only your own.");
	}

	// The terminal's width, if the environment says.
	private static int columns(CliEnvironment environment) {
		String value = environment.variable("COLUMNS");
		try {
			return value == null ? 0 : Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	// Asks for the number the phone shows, until it is right or the tries run out; n denies.
	private void approve(DirectorAdmin admin, Id requestId, Enrollment claim) throws Exception {
		while (true) {
			String answer = terminal().readLine("Type the number the phone shows to approve, or n to deny: ");
			if (answer == null || answer.isEmpty() || answer.toLowerCase(Locale.ROOT).startsWith("n")) {
				await(admin.cancelEnrollment(requestId));
				throw new CliException(ExitCode.FAILED, "Denied; nothing was changed.");
			}

			int number;
			try {
				number = Integer.parseInt(answer);
			} catch (NumberFormatException e) {
				output().message("That is not a number: type the two digits the phone shows.");
				continue;
			}

			EnrollmentApproval approval;
			try {
				approval = await(admin.approveEnrollment(requestId, number));
			} catch (DirectorException e) {
				if (e.getStatus() == GONE)
					throw ended(await(admin.getEnrollment(requestId)).map(Enrollment::getState)
							.orElse(Enrollment.State.EXPIRED));
				throw e;
			}

			if (approval.isApproved()) {
				Id userId = approval.getUserId().orElseThrow();
				output().message("User " + userId + " is now an administrator of this node" +
						(approval.isCreated() ? ", with a new account." : "."));
				return;
			}
			if (approval.getTriesLeft() == 0)
				throw new CliException(ExitCode.FAILED, "That is not the number on the phone, again: the request is " +
						"denied, and nothing was changed.", "Run " + tool().command("user enroll-admin") +
						" again to start over.");
			output().message("That is not the number on the phone; " + approval.getTriesLeft() +
					(approval.getTriesLeft() == 1 ? " try" : " tries") + " left.");
		}
	}

	private CliException ended(Enrollment.State state) {
		String what = switch (state) {
			case EXPIRED -> "expired before it was approved";
			case DENIED -> "was denied";
			case APPROVED -> "was approved already";
			default -> "ended";
		};
		return new CliException(ExitCode.FAILED, "The enrollment request " + what + ".",
				"Run " + tool().command("user enroll-admin") + " again to start over.");
	}
}
