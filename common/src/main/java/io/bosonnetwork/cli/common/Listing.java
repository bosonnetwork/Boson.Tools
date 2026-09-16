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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.bosonnetwork.web.PaginatedResult;

/**
 * Writes the result of a list command: a table for people, a JSON array or page for scripts.
 */
public final class Listing {
	private Listing() {
	}

	/**
	 * Writes a list that is not paged.
	 *
	 * @param output  the output
	 * @param items   the items
	 * @param headers the table headers
	 * @param row     the table row of an item
	 * @param json    the JSON of an item
	 * @param empty   the message when there are no items
	 * @param <T>     the item type
	 */
	public static <T> void list(Output output, List<T> items, List<String> headers, Function<T, List<String>> row,
			Function<T, Object> json, String empty) {
		if (output.isJson()) {
			output.json(items.stream().map(json).toList());
			return;
		}

		if (items.isEmpty()) {
			output.message(empty);
			return;
		}

		output.table(headers, items.stream().map(row).toList());
	}

	/**
	 * Writes a page of a list, with a footer naming the next page.
	 *
	 * @param output  the output
	 * @param page    the page
	 * @param options the paging options it was fetched with
	 * @param plural  what the items are, such as {@code "users"}
	 * @param headers the table headers
	 * @param row     the table row of an item
	 * @param json    the JSON of an item
	 * @param empty   the message when there are no items at all
	 * @param <T>     the item type
	 */
	public static <T> void page(Output output, PaginatedResult<T> page, PageOptions options, String plural,
			List<String> headers, Function<T, List<String>> row, Function<T, Object> json, String empty) {
		if (output.isJson()) {
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("page", page.page());
			result.put("pageSize", page.pageSize());
			result.put("totalPages", page.totalPages());
			result.put("totalItems", page.totalItems());
			result.put("items", page.items().stream().map(json).toList());
			output.json(result);
			return;
		}

		if (page.items().isEmpty()) {
			if (options.isAll() || page.page() <= 1 || page.totalItems() == 0)
				output.message(empty);
			else
				output.message("Page " + page.page() + " is past the end: there " +
						(page.totalPages() == 1 ? "is 1 page" : "are " + page.totalPages() + " pages") + " of " + plural + ".");
			return;
		}

		output.table(headers, page.items().stream().map(row).toList());

		if (!options.isAll() && page.totalPages() > 1) {
			output.blank();
			StringBuilder footer = new StringBuilder("Page ").append(page.page()).append(" of ").append(page.totalPages())
					.append(", ").append(page.totalItems()).append(' ').append(plural).append(" in all.");
			if (page.page() < page.totalPages())
				footer.append(" Next page: --page ").append(page.page() + 1).append(". Everything: --all.");
			output.message(footer.toString());
		}
	}
}
