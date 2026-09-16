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

package io.bosonnetwork.cli.director;

import java.util.concurrent.CompletableFuture;

import io.bosonnetwork.cli.common.CliCommand;
import io.bosonnetwork.cli.common.CliException;
import io.bosonnetwork.cli.common.ExitCode;
import io.bosonnetwork.director.client.exceptions.ForbiddenException;
import io.bosonnetwork.director.client.exceptions.PassphraseRequiredException;

/**
 * A command of a tool that talks to a Director: it works with the run's {@link CliContext}, and knows
 * how the account passphrase gates a call.
 */
public abstract class DirectorCommand extends CliCommand {
	/**
	 * A client call that takes the account passphrase.
	 *
	 * @param <T> the result type
	 */
	@FunctionalInterface
	protected interface PassphraseCall<T> {
		/**
		 * Makes the call.
		 *
		 * @param passphrase the passphrase, or {@code null}
		 * @return the call
		 */
		CompletableFuture<T> call(String passphrase);
	}

	/**
	 * Returns the context of the run.
	 *
	 * @return the context
	 */
	protected final CliContext context() {
		return ((DirectorApp) app()).context();
	}

	/**
	 * Returns the tool.
	 *
	 * @return the tool
	 */
	protected final ToolSpec tool() {
		return context().tool();
	}

	/**
	 * Makes a call the account passphrase may gate. With {@code ask}, the passphrase is asked for
	 * first. Without it the call is made without one, and if the Director answers that the account
	 * needs it, the user is asked on a terminal and the call is made again.
	 *
	 * @param ask  whether to ask for the passphrase up front ({@code --passphrase})
	 * @param call the call
	 * @param <T>  the result type
	 * @return the result
	 * @throws Exception the failure of the call
	 */
	protected final <T> T withPassphrase(boolean ask, PassphraseCall<T> call) throws Exception {
		String passphrase = ask ? terminal().readSecret("Passphrase: ", "passphrase") : null;
		try {
			return await(call.call(passphrase));
		} catch (PassphraseRequiredException e) {
			if (passphrase != null || !terminal().isInteractive())
				throw e;

			passphrase = terminal().readSecret("This account is protected by a passphrase. Passphrase: ", "passphrase");
			try {
				return await(call.call(passphrase));
			} catch (ForbiddenException wrong) {
				throw wrongPassphrase();
			}
		} catch (ForbiddenException e) {
			if (passphrase != null)
				throw wrongPassphrase();
			throw e;
		}
	}

	private static CliException wrongPassphrase() {
		return new CliException(ExitCode.NOT_AUTHORIZED, "The passphrase is not correct; nothing was changed.");
	}
}
