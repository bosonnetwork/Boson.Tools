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

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.utils.Base58;

/**
 * An identity file: a Boson private key on one line, Base58-encoded - the 64-byte Ed25519 private key,
 * the seed followed by the public key. The same format serves user, administrator and device keys.
 * <p>
 * Reading also accepts {@code 0x}-prefixed hex, and skips blank lines and {@code #} comments.
 * Writing never replaces an existing file, and creates the file readable by its owner only.
 */
public final class IdentityFile {
	private static final String FORMAT_HINT = "An identity file holds a Base58 64-byte private key on one line.";

	private IdentityFile() {
	}

	/**
	 * Reads the key of an identity file.
	 *
	 * @param file the file
	 * @param what what the file is, for error messages, such as {@code "identity file"}
	 * @return the key pair
	 * @throws CliException if the file cannot be read, or does not hold a valid key
	 */
	public static Signature.KeyPair read(Path file, String what) {
		String content;
		try {
			content = Files.readString(file);
		} catch (NoSuchFileException e) {
			throw CliException.config("The " + what + " " + file + " does not exist.", null);
		} catch (AccessDeniedException e) {
			throw CliException.config("Cannot read the " + what + " " + file + ": permission denied.",
					"Run the command as the user who owns the file.");
		} catch (IOException e) {
			throw CliException.config("Cannot read the " + what + " " + file + ": " + e.getMessage(), null);
		}

		String key = content.lines()
				.map(String::strip)
				.filter(line -> !line.isEmpty() && !line.startsWith("#"))
				.findFirst()
				.orElse(null);
		if (key == null)
			throw CliException.config("The " + what + " " + file + " holds no key.", FORMAT_HINT);

		try {
			return Keys.privateKey(key, "key in " + file);
		} catch (CliException e) {
			throw CliException.config(e.getMessage(), FORMAT_HINT);
		}
	}

	/**
	 * Writes a new identity file, readable by its owner only.
	 *
	 * @param file the file; it must not exist
	 * @param key  the key pair to write
	 * @throws CliException if the file exists, which is left unchanged, or cannot be written
	 */
	public static void create(Path file, Signature.KeyPair key) {
		try {
			FilePermissions.writeNewPrivateFile(file, Base58.encode(key.privateKey().bytes()) + System.lineSeparator());
		} catch (FileAlreadyExistsException e) {
			throw CliException.failed("The file " + file + " already exists; it was not changed.",
					"Choose another file. Remove the existing one yourself only if you are sure its key is no longer needed.");
		} catch (IOException e) {
			throw CliException.failed("Cannot write " + file + ": " + e.getMessage(), null);
		}
	}

	/**
	 * Tells whether users other than the owner can read or write an identity file.
	 *
	 * @param file the file
	 * @return {@code true} if the file is open to other users
	 */
	public static boolean isExposed(Path file) {
		return FilePermissions.isAccessibleByOthers(file);
	}
}
