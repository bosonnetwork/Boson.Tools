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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Waiting for the asynchronous calls a command makes.
 */
public final class Futures {
	private Futures() {
	}

	/**
	 * Waits for a call to complete, and unwraps its failure: a command catches what the client threw,
	 * not an {@link ExecutionException} wrapping it.
	 *
	 * @param future the call
	 * @param <T>    the result type
	 * @return the result
	 * @throws Exception the failure of the call
	 */
	public static <T> T await(CompletableFuture<T> future) throws Exception {
		try {
			return future.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new CliException(ExitCode.FAILED, "Interrupted.");
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception exception)
				throw exception;
			if (cause instanceof Error error)
				throw error;
			throw e;
		}
	}
}
