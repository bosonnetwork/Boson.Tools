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

/**
 * Waits for the asynchronous calls a command makes, and unwraps their failures.
 * <p>
 * Commands are synchronous from the user's point of view, while every Boson client API is not. This
 * is what the helpers shared between tools - paging, for one - use to wait, rather than each tool's
 * own context.
 */
public interface Awaiter {
	/**
	 * Waits for a call to complete.
	 *
	 * @param future the call
	 * @param <T>    the result type
	 * @return the result
	 * @throws Exception the failure of the call, unwrapped from its {@code ExecutionException}
	 */
	<T> T await(CompletableFuture<T> future) throws Exception;
}
