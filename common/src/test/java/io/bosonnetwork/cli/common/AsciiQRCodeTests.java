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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.Decoder;

/** What is drawn decodes back to what was encoded, in every style. */
public class AsciiQRCodeTests {
	private static final String CONTENT = "boson:supernode:1:9KHWpvNRdTKB3ZBqqBEszK4oLLxocnHPwU1YcRoY4o4V:https://node.example:8443";

	// Reads the drawing back into modules (true = dark), the way a scanner would see it.
	private static boolean[][] modules(String text, AsciiQRCode.Style style) {
		List<String> lines = text.replace("\u001b[30;107m", "").replace("\u001b[0m", "").lines().toList();
		if (style == AsciiQRCode.Style.ASCII) {
			boolean[][] rows = new boolean[lines.size()][];
			for (int y = 0; y < lines.size(); y++) {
				String line = lines.get(y);
				rows[y] = new boolean[line.length() / 2];
				for (int x = 0; x < rows[y].length; x++)
					rows[y][x] = line.charAt(2 * x) == ' ';
			}
			return rows;
		}
		boolean drawDark = style != AsciiQRCode.Style.DARK;
		boolean[][] rows = new boolean[lines.size() * 2][];
		for (int y = 0; y < lines.size(); y++) {
			String line = lines.get(y);
			rows[2 * y] = new boolean[line.length()];
			rows[2 * y + 1] = new boolean[line.length()];
			for (int x = 0; x < line.length(); x++) {
				char c = line.charAt(x);
				boolean upper = c == '█' || c == '▀';
				boolean lower = c == '█' || c == '▄';
				rows[2 * y][x] = upper == drawDark;
				rows[2 * y + 1][x] = lower == drawDark;
			}
		}
		return rows;
	}

	private static String decode(boolean[][] rows) throws Exception {
		// The code inside its quiet zone: the first and last dark modules.
		int size = rows[0].length;
		int first = size, last = -1;
		for (int y = 0; y < rows.length; y++)
			for (int x = 0; x < size; x++)
				if (rows[y][x]) {
					first = Math.min(first, Math.min(x, y));
					last = Math.max(last, Math.max(x, y));
				}
		int n = last - first + 1;
		BitMatrix bits = new BitMatrix(n);
		for (int y = 0; y < n; y++)
			for (int x = 0; x < n; x++)
				if (rows[first + y][first + x])
					bits.set(x, y);
		return new Decoder().decode(bits).getText();
	}

	@ParameterizedTest
	@EnumSource(AsciiQRCode.Style.class)
	void testEveryStyleDecodesBack(AsciiQRCode.Style style) throws Exception {
		String text = AsciiQRCode.render(CONTENT, style);
		assertEquals(CONTENT, decode(modules(text, style)));
	}

	@Test
	void testTheQuietZoneIsLightInEveryStyle() {
		for (AsciiQRCode.Style style : AsciiQRCode.Style.values()) {
			boolean[][] rows = modules(AsciiQRCode.render(CONTENT, style), style);
			for (boolean dark : rows[0])
				assertFalse(dark, style + ": the first row is quiet zone");
		}
	}

	@Test
	void testOnlyAnsiUsesEscapes() {
		assertTrue(AsciiQRCode.render(CONTENT, AsciiQRCode.Style.ANSI).contains("\u001b["));
		for (AsciiQRCode.Style style : List.of(AsciiQRCode.Style.DARK, AsciiQRCode.Style.LIGHT, AsciiQRCode.Style.ASCII))
			assertFalse(AsciiQRCode.render(CONTENT, style).contains("\u001b"), style.toString());
		assertTrue(AsciiQRCode.render(CONTENT, AsciiQRCode.Style.ASCII).chars().allMatch(c -> c < 0x80));
	}

	@Test
	void testTheWidthIsWhatIsDrawn() {
		int width = AsciiQRCode.width(CONTENT);
		String firstLine = AsciiQRCode.render(CONTENT, AsciiQRCode.Style.LIGHT).lines().findFirst().orElseThrow();
		assertEquals(width, firstLine.length());
		// The code and its quiet zone of four modules on each side.
		assertEquals(0, (width - 8 - 21) % 4);
	}

	@Test
	void testEmptyContentIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> AsciiQRCode.render("", AsciiQRCode.Style.ANSI));
	}
}
