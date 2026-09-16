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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import picocli.CommandLine.Option;

import io.bosonnetwork.web.PaginatedResult;

/**
 * The paging options of the list commands: one page, or {@code --all}.
 * <p>
 * Lists are paged rather than scrolled interactively, so that they work the same in a terminal and in
 * a script; the output names the next page.
 */
public class PageOptions {
	/** The page size used unless another is given. */
	public static final long DEFAULT_PAGE_SIZE = 20;
	/** The largest page size. */
	public static final long MAX_PAGE_SIZE = 1000;

	// Pages fetched by --all: few requests, none of them large.
	private static final long ALL_PAGE_SIZE = 100;

	@Option(names = "--page", paramLabel = "<n>", description = "The page to list, from 1. Default: 1.")
	Long page;

	@Option(names = "--page-size", paramLabel = "<n>", description = "How many to list per page, up to 1000. Default: 20.")
	Long pageSize;

	@Option(names = "--all", description = "List everything, instead of one page.")
	boolean all;

	/**
	 * Fetches one page by one page request.
	 *
	 * @param <T> the item type
	 */
	@FunctionalInterface
	public interface PageFetcher<T> {
		/**
		 * Fetches a page.
		 *
		 * @param page     the page number, from 1
		 * @param pageSize the page size
		 * @return the page
		 */
		CompletableFuture<PaginatedResult<T>> fetch(long page, long pageSize);
	}

	/**
	 * Tells whether everything is listed.
	 *
	 * @return {@code true} with {@code --all}
	 */
	public boolean isAll() {
		return all;
	}

	/**
	 * Fetches what the options ask for: the page, or every page with {@code --all}.
	 *
	 * @param context the context of the run
	 * @param fetcher fetches one page
	 * @param <T>     the item type
	 * @return the page; with {@code --all}, one page holding everything
	 * @throws Exception the failure of a request
	 */
	public <T> PaginatedResult<T> fetch(CliContext context, PageFetcher<T> fetcher) throws Exception {
		if (all) {
			if (page != null || pageSize != null)
				throw CliException.usage("--all lists everything, so it cannot be combined with --page or --page-size.", null);

			List<T> items = new ArrayList<>();
			for (long p = 1; ; p++) {
				PaginatedResult<T> result = context.await(fetcher.fetch(p, ALL_PAGE_SIZE));
				items.addAll(result.items());
				if (result.items().isEmpty() || p >= result.totalPages())
					break;
			}
			return PaginatedResult.of(1, Math.max(items.size(), 1), items.size(), items);
		}

		long p = page != null ? page : 1;
		long size = pageSize != null ? pageSize : DEFAULT_PAGE_SIZE;
		if (p < 1)
			throw CliException.usage("The page must be 1 or more, not " + p + ".", null);
		if (size < 1 || size > MAX_PAGE_SIZE)
			throw CliException.usage("The page size must be from 1 to " + MAX_PAGE_SIZE + ", not " + size + ".", null);

		return context.await(fetcher.fetch(p, size));
	}
}
