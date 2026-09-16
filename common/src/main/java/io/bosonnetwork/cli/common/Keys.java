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

import java.util.Arrays;

import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.utils.Base58;
import io.bosonnetwork.utils.Hex;

/**
 * How the command line tools read and print keys and other binary values.
 * <p>
 * Input is Base58, or hex with a {@code 0x} prefix - the prefix is what tells them apart, since many
 * hex strings are also valid Base58. Output is Base58, or {@code 0x}-prefixed hex on request.
 */
public final class Keys {
	private Keys() {
	}

	/**
	 * Decodes a Base58 or {@code 0x}-prefixed hex value.
	 *
	 * @param text the encoded value
	 * @param what what the value is, for the error message, such as {@code "private key"}
	 * @return the bytes
	 * @throws CliException if the value is neither
	 */
	public static byte[] decode(String text, String what) {
		String value = text.strip();
		if (value.isEmpty())
			throw CliException.usage("The " + what + " is empty.", null);

		try {
			if (value.startsWith("0x") || value.startsWith("0X"))
				return Hex.decode(value.substring(2));
			return Base58.decode(value);
		} catch (RuntimeException e) {
			throw CliException.usage("The " + what + " is not valid Base58 or 0x-prefixed hex.",
					"Hex values need the 0x prefix, as in 0x1f2e...");
		}
	}

	/**
	 * Encodes bytes as Base58, or as {@code 0x}-prefixed hex.
	 *
	 * @param bytes the bytes
	 * @param hex   whether to use hex
	 * @return the encoded value
	 */
	public static String encode(byte[] bytes, boolean hex) {
		return hex ? "0x" + Hex.encode(bytes) : Base58.encode(bytes);
	}

	/**
	 * Decodes a Boson private key: the 64-byte Ed25519 private key, the seed followed by the public
	 * key.
	 *
	 * @param text the encoded key
	 * @param what what the key is, for the error message, such as {@code "user private key"}
	 * @return the key pair
	 * @throws CliException if the key is not valid
	 */
	public static Signature.KeyPair privateKey(String text, String what) {
		byte[] bytes = decode(text, what);
		try {
			return privateKey(bytes, what);
		} finally {
			Arrays.fill(bytes, (byte) 0);
		}
	}

	/**
	 * Checks a Boson private key: the 64-byte Ed25519 private key, the seed followed by the public
	 * key.
	 *
	 * @param bytes the key
	 * @param what  what the key is, for the error message
	 * @return the key pair
	 * @throws CliException if the key is not valid
	 */
	public static Signature.KeyPair privateKey(byte[] bytes, String what) {
		String problem = checkPrivateKey(bytes);
		if (problem != null)
			throw CliException.usage("The " + what + " is not valid: " + problem + ".", null);

		return Signature.KeyPair.fromPrivateKey(bytes);
	}

	/**
	 * Checks a Boson private key, without failing.
	 *
	 * @param bytes the key
	 * @return what is wrong with it, or {@code null} if it is valid
	 */
	public static String checkPrivateKey(byte[] bytes) {
		if (bytes.length == Signature.KeyPair.SEED_BYTES)
			return "it is a 32-byte seed, not a 64-byte private key (the seed followed by the public key)";
		if (bytes.length != Signature.PrivateKey.BYTES)
			return "expected " + Signature.PrivateKey.BYTES + " bytes, got " + bytes.length;

		// The second half is the public key of the first: a key whose halves disagree has been
		// damaged or put together by hand, and would sign as an identity other than the one it names.
		Signature.KeyPair keyPair = Signature.KeyPair.fromSeed(Arrays.copyOf(bytes, Signature.KeyPair.SEED_BYTES));
		byte[] expected = keyPair.publicKey().bytes();
		byte[] actual = Arrays.copyOfRange(bytes, Signature.KeyPair.SEED_BYTES, bytes.length);
		if (!Arrays.equals(expected, actual))
			return "its public key half does not match its seed";

		return null;
	}
}
