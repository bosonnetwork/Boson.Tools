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

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * A command that does something: the base of every leaf command of every Boson tool.
 * <p>
 * A command throws for any failure - a {@link CliException} for the ones it can explain better than
 * {@link ErrorReporter} would - and otherwise exits with {@link ExitCode#OK}.
 */
public abstract class CliCommand implements Callable<Integer>, Awaiter {
	/** The command's specification, injected by picocli. */
	@Spec
	protected CommandSpec spec;

	private int exitCode = ExitCode.OK;

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
	 * Returns the tool this command belongs to.
	 *
	 * @return the root command
	 */
	protected final CliApp app() {
		return (CliApp) spec.root().userObject();
	}

	/**
	 * Returns the output.
	 *
	 * @return the output
	 */
	protected final Output output() {
		return app().output();
	}

	/**
	 * Returns the terminal.
	 *
	 * @return the terminal
	 */
	protected final Terminal terminal() {
		return app().terminal();
	}

	@Override
	public <T> T await(CompletableFuture<T> future) throws Exception {
		return Futures.await(future);
	}
}
