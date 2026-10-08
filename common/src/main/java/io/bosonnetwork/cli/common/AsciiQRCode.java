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

import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.EnumMap;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * A QR code drawn in a text console, for a phone to scan off the screen.
 * <p>
 * A scanner needs dark modules on a light ground, with a light quiet zone around them. A terminal draws
 * characters in its text color on its own background, which may be dark or light, so how a QR code comes
 * out depends on the {@link Style}:
 * <ul>
 *   <li>{@link Style#ANSI}: sets black on white itself with ANSI colors, so it is right on any terminal.
 *       The default whenever the output is a terminal that takes colors.</li>
 *   <li>{@link Style#DARK} and {@link Style#LIGHT}: no colors, for a terminal with a dark or a light
 *       background: the code is drawn in the light or the dark modules accordingly.</li>
 *   <li>{@link Style#ASCII}: plain {@code #} characters, for a console that can't print the block
 *       characters (some Windows code pages), or for a log.</li>
 * </ul>
 * The Unicode styles draw two rows of modules per line with half blocks, so the code is about as tall as
 * it is wide. A code wider than the terminal wraps and can't be scanned: check {@link #width(String)}
 * against the terminal's width first.
 */
public final class AsciiQRCode {
	/** How the code is drawn; see the class description. */
	public enum Style {
		/** Black on white set with ANSI colors: right on any terminal that takes colors. */
		ANSI,
		/** No colors, for a dark background: the light modules are drawn. */
		DARK,
		/** No colors, for a light background: the dark modules are drawn. */
		LIGHT,
		/** Plain ASCII, two characters per module, for a dark background like {@link #DARK}. */
		ASCII
	}

	// Kept as escapes: source is ASCII.
	private static final char FULL_BLOCK = '\u2588';
	private static final char UPPER_HALF = '\u2580';
	private static final char LOWER_HALF = '\u2584';

	// Black text on a bright white background, then back to the terminal's own colors.
	private static final String ANSI_BLACK_ON_WHITE = "\u001b[30;107m";
	private static final String ANSI_RESET = "\u001b[0m";

	// The standard quiet zone, which a scanner needs to find the code.
	private static final int QUIET_ZONE = 4;

	private AsciiQRCode() {
	}

	/**
	 * Prints a QR code to stdout, in the style that suits it ({@link #styleFor(PrintStream)}).
	 *
	 * @param content content to encode
	 */
	public static void print(String content) {
		print(content, System.out);
	}

	/**
	 * Prints a QR code to {@code out}, in the style that suits it ({@link #styleFor(PrintStream)}).
	 *
	 * @param content content to encode
	 * @param out     where to print it
	 */
	public static void print(String content, PrintStream out) {
		print(content, out, styleFor(out));
	}

	/**
	 * Prints a QR code to {@code out} in {@code style}.
	 *
	 * @param content content to encode
	 * @param out     where to print it
	 * @param style   how to draw it
	 */
	public static void print(String content, PrintStream out, Style style) {
		out.print(render(content, style));
		out.flush();
	}

	/**
	 * Draws a QR code as text, one line per row of characters, each line ending with a line separator.
	 *
	 * @param content content to encode
	 * @param style   how to draw it
	 * @return the text
	 * @throws IllegalArgumentException if the content is empty or too long for a QR code
	 */
	public static String render(String content, Style style) {
		BitMatrix matrix = encode(content);
		StringBuilder sb = new StringBuilder();
		String eol = System.lineSeparator();
		int width = matrix.getWidth();
		int height = matrix.getHeight();

		if (style == Style.ASCII) {
			// One line per row of modules, two characters per module, so a module is about square.
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++)
					sb.append(matrix.get(x, y) ? "  " : "##");
				sb.append(eol);
			}
			return sb.toString();
		}

		// Which modules the characters draw: the dark ones where the drawing color is dark (ANSI, LIGHT),
		// the light ones where it is light (DARK).
		boolean drawDark = style != Style.DARK;
		for (int y = 0; y < height; y += 2) {
			if (style == Style.ANSI)
				sb.append(ANSI_BLACK_ON_WHITE);
			for (int x = 0; x < width; x++) {
				// Past the last row, the quiet zone continues: a light module.
				boolean upper = matrix.get(x, y) == drawDark;
				boolean lower = (y + 1 < height ? matrix.get(x, y + 1) : false) == drawDark;
				sb.append(upper && lower ? FULL_BLOCK : upper ? UPPER_HALF : lower ? LOWER_HALF : ' ');
			}
			if (style == Style.ANSI)
				sb.append(ANSI_RESET);
			sb.append(eol);
		}
		return sb.toString();
	}

	/**
	 * How many columns a QR code of {@code content} takes, the quiet zone included; the same in every
	 * style but {@link Style#ASCII}, which takes twice as many.
	 *
	 * @param content content to encode
	 * @return the width in columns
	 */
	public static int width(String content) {
		return encode(content).getWidth();
	}

	/**
	 * The style that suits {@code out}: {@link Style#ASCII} if its encoding can't print the block
	 * characters; {@link Style#ANSI} if it is the terminal and colors are allowed (no {@code NO_COLOR}, a
	 * {@code TERM} other than {@code dumb}); {@link Style#DARK} otherwise, as most terminals have a dark
	 * background.
	 *
	 * @param out where the code goes
	 * @return the style
	 */
	public static Style styleFor(PrintStream out) {
		boolean terminal = out == System.out && System.console() != null;
		return styleFor(charsetOf(out), terminal && colorsAllowed(System.getenv("NO_COLOR"), System.getenv("TERM")));
	}

	/**
	 * The style for output in {@code charset}: {@link Style#ASCII} if it can't encode the block characters;
	 * else {@link Style#ANSI} if the output takes colors, {@link Style#DARK} if not.
	 *
	 * @param charset what the output encodes text with
	 * @param colors  whether the output is a terminal that takes colors (see
	 *                {@link #colorsAllowed(String, String)})
	 * @return the style
	 */
	public static Style styleFor(Charset charset, boolean colors) {
		if (!charset.newEncoder().canEncode("" + FULL_BLOCK + UPPER_HALF + LOWER_HALF))
			return Style.ASCII;
		return colors ? Style.ANSI : Style.DARK;
	}

	/**
	 * Tells whether the environment allows colors: {@code NO_COLOR} is not set, and {@code TERM} is not
	 * {@code dumb}.
	 *
	 * @param noColor the {@code NO_COLOR} variable, or null
	 * @param term    the {@code TERM} variable, or null
	 * @return whether colors are allowed
	 */
	public static boolean colorsAllowed(String noColor, String term) {
		return (noColor == null || noColor.isEmpty()) && !"dumb".equals(term);
	}

	// What out encodes text with. Java 17 has no PrintStream.charset(): the console's, or what the JDK
	// says stdout uses, or the default.
	private static Charset charsetOf(PrintStream out) {
		if (out == System.out && System.console() != null)
			return System.console().charset();
		for (String property : new String[] { "stdout.encoding", "sun.stdout.encoding" }) {
			String name = System.getProperty(property);
			if (name != null) {
				try {
					return Charset.forName(name);
				} catch (IllegalArgumentException e) {
					// An unknown name: try the next one.
				}
			}
		}
		return Charset.defaultCharset();
	}

	private static BitMatrix encode(String content) {
		if (content == null || content.isEmpty())
			throw new IllegalArgumentException("QR content must not be empty");

		Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
		// M: recovers from about 15% damage, which covers the glare and moire of a screen, at a moderate
		// size cost over L.
		hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
		hints.put(EncodeHintType.MARGIN, QUIET_ZONE);
		hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

		try {
			// Size 0: one matrix cell per module.
			return new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints);
		} catch (WriterException e) {
			throw new IllegalArgumentException("Unable to generate QR code", e);
		}
	}
}
