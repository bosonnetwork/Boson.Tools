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

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import io.bosonnetwork.director.client.exceptions.ForbiddenException;
import io.bosonnetwork.director.client.exceptions.PassphraseRequiredException;

/**
 * A command that does something: the base of every leaf command of both tools.
 * <p>
 * A command throws for any failure - a {@link CliException} for the ones it can explain better than
 * {@link ErrorReporter} would - and otherwise exits with {@link ExitCode#OK}.
 */
public abstract class CliCommand implements Callable<Integer> {
	/** The command's specification, injected by picocli. */
	@Spec
	protected CommandSpec spec;

	private int exitCode = ExitCode.OK;

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

	@Override
	public final Integer call() throws Exception {
		run();
		return exitCode;
	}

	/**
	 * Does what the command does.
	 *
	 * @throws Exception any failure, reported by {@link ErrorReporter}
	 */
	protected abstract void run() throws Exception;

	/**
	 * Sets the exit code of a command that completes without throwing, but must not exit with
	 * {@link ExitCode#OK}.
	 *
	 * @param exitCode the exit code
	 */
	protected final void setExitCode(int exitCode) {
		this.exitCode = exitCode;
	}

	/**
	 * Returns the context of the run.
	 *
	 * @return the context
	 */
	protected final CliContext context() {
		return ((CliApp) spec.root().userObject()).context();
	}

	/**
	 * Returns the output.
	 *
	 * @return the output
	 */
	protected final Output output() {
		return context().output();
	}

	/**
	 * Returns the terminal.
	 *
	 * @return the terminal
	 */
	protected final Terminal terminal() {
		return context().terminal();
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
	 * Waits for a client call.
	 *
	 * @param future the call
	 * @param <T>    the result type
	 * @return the result
	 * @throws Exception the failure of the call
	 */
	protected final <T> T await(CompletableFuture<T> future) throws Exception {
		return context().await(future);
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
