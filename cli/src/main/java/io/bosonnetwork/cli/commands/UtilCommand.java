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

package io.bosonnetwork.cli.commands;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import io.bosonnetwork.Id;
import io.bosonnetwork.cli.director.DirectorCommand;
import io.bosonnetwork.cli.common.CliException;
import io.bosonnetwork.cli.common.CliGroup;
import io.bosonnetwork.cli.common.ExitCode;
import io.bosonnetwork.cli.common.IdentityFile;
import io.bosonnetwork.cli.common.Keys;
import io.bosonnetwork.crypto.CryptoBox;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.utils.Base58;
import io.bosonnetwork.utils.Hex;

/**
 * The {@code util} commands of {@code boson-cli}: keys and encodings, offline.
 */
@Command(name = "util", description = {"Work with Boson keys and encodings, offline.",
		"None of these talks to a super node. Only sign uses your identity."},
		subcommands = {UtilCommand.KeygenCommand.class, UtilCommand.CheckKeyCommand.class, UtilCommand.PublicKeyCommand.class,
				UtilCommand.SignCommand.class, UtilCommand.HexToBase58Command.class, UtilCommand.Base58ToHexCommand.class})
public class UtilCommand extends CliGroup {
	private static final String KEY_PROMPT_HINT = "Omit it to be asked for it, which keeps it out of your shell history.";

	@Command(name = "keygen", description = {"Generate a new key pair.",
			"Prints the Ed25519 signature key pair, whose public key is an id, and the Curve25519 encryption key pair "
					+ "derived from it. With --output, writes the private key to a new identity file instead, and prints the id."})
	public static class KeygenCommand extends DirectorCommand {
		@Option(names = "--hex", description = "Print keys as hex rather than Base58.")
		boolean hex;

		@Option(names = {"-o", "--output"}, paramLabel = "<file>",
				description = "Write the private key to this new identity file, readable by you only.")
		Path file;

		@Override
		protected void run() {
			Signature.KeyPair signature = Signature.KeyPair.random();

			if (file != null) {
				IdentityFile.create(file, signature);
				Id id = Id.of(signature.publicKey().bytes());
				if (output().isJson()) {
					Map<String, Object> json = new LinkedHashMap<>();
					json.put("id", id);
					json.put("file", file.toString());
					output().json(json);
				} else {
					output().message("Wrote a new key to " + file + ".");
					output().message("Id: " + id);
				}
				return;
			}

			CryptoBox.KeyPair encryption = CryptoBox.KeyPair.fromSignatureKeyPair(signature);
			String signaturePrivate = Keys.encode(signature.privateKey().bytes(), hex);
			String signaturePublic = Keys.encode(signature.publicKey().bytes(), hex);
			String encryptionPrivate = Keys.encode(encryption.privateKey().bytes(), hex);
			String encryptionPublic = Keys.encode(encryption.publicKey().bytes(), hex);

			if (output().isJson()) {
				Map<String, Object> json = new LinkedHashMap<>();
				json.put("signature", keyPairJson(signaturePrivate, signaturePublic));
				json.put("encryption", keyPairJson(encryptionPrivate, encryptionPublic));
				output().json(json);
				return;
			}

			output().message("Signature key pair (Ed25519)");
			output().message("  Private key: " + signaturePrivate);
			output().message("  Public key:  " + signaturePublic);
			output().blank();
			output().message("Encryption key pair (Curve25519)");
			output().message("  Private key: " + encryptionPrivate);
			output().message("  Public key:  " + encryptionPublic);
		}

		private static Map<String, Object> keyPairJson(String privateKey, String publicKey) {
			Map<String, Object> json = new LinkedHashMap<>();
			json.put("privateKey", privateKey);
			json.put("publicKey", publicKey);
			return json;
		}
	}

	@Command(name = "check-key", description = {"Check that a key is valid.",
			"Exits with 0 for a valid key, and 1 for an invalid one."})
	public static class CheckKeyCommand extends DirectorCommand {
		@Parameters(paramLabel = "<key>", arity = "0..1", description = "The key: Base58, or hex with 0x. " + KEY_PROMPT_HINT)
		String key;

		@ArgGroup(exclusive = true, multiplicity = "1")
		KeyType type;

		static class KeyType {
			@Option(names = "--private", required = true, description = "The key is a private key.")
			boolean privateKey;

			@Option(names = "--public", required = true, description = "The key is a public key.")
			boolean publicKey;
		}

		@Option(names = "--curve25519", description = "The key is a Curve25519 encryption key, rather than an Ed25519 signature key.")
		boolean curve25519;

		@Override
		protected void run() {
			String algorithm = curve25519 ? "Curve25519" : "Ed25519";
			String kind = type.privateKey ? "private key" : "public key";
			String text = key != null ? key : terminal().readSecret("Key (Base58, or hex with 0x): ", "key");

			String problem;
			try {
				problem = check(Keys.decode(text, "key"));
			} catch (CliException e) {
				problem = "it is not Base58, or hex with 0x";
			}

			if (problem != null)
				setExitCode(ExitCode.FAILED);

			if (output().isJson()) {
				Map<String, Object> json = new LinkedHashMap<>();
				json.put("valid", problem == null);
				json.put("algorithm", algorithm.toLowerCase());
				json.put("type", type.privateKey ? "private" : "public");
				json.put("problem", problem);
				output().json(json);
				return;
			}

			output().message(problem == null ? "Valid " + algorithm + " " + kind + "." :
					"Invalid " + algorithm + " " + kind + ": " + problem + ".");
		}

		private String check(byte[] bytes) {
			if (!curve25519 && type.privateKey)
				return Keys.checkPrivateKey(bytes);

			int expected = curve25519 ?
					(type.privateKey ? CryptoBox.PrivateKey.BYTES : CryptoBox.PublicKey.BYTES) : Signature.PublicKey.BYTES;
			if (bytes.length != expected)
				return "expected " + expected + " bytes, got " + bytes.length;

			if (!curve25519) {
				try {
					Signature.PublicKey.fromBytes(bytes);
				} catch (RuntimeException e) {
					return "it is not a point on the Ed25519 curve";
				}
			}

			return null;
		}
	}

	@Command(name = "public-key", description = {"Print the public key of a private key.",
			"The public key of an Ed25519 private key is also its id: for instance, a node's id from the privateKey in its node.yaml."})
	public static class PublicKeyCommand extends DirectorCommand {
		@Parameters(paramLabel = "<private-key>", arity = "0..1",
				description = "The private key: Base58, or hex with 0x. " + KEY_PROMPT_HINT)
		String key;

		@Option(names = "--curve25519", description = "The key is a Curve25519 encryption key, rather than an Ed25519 signature key.")
		boolean curve25519;

		@Option(names = "--hex", description = "Print the public key as hex rather than Base58.")
		boolean hex;

		@Override
		protected void run() {
			String text = key != null ? key : terminal().readSecret("Private key (Base58, or hex with 0x): ", "private key");
			byte[] bytes = Keys.decode(text, "private key");

			byte[] publicKey;
			if (curve25519) {
				if (bytes.length != CryptoBox.PrivateKey.BYTES)
					throw CliException.usage("The Curve25519 private key is not valid: expected " + CryptoBox.PrivateKey.BYTES +
							" bytes, got " + bytes.length + ".", null);
				publicKey = CryptoBox.KeyPair.fromPrivateKey(bytes).publicKey().bytes();
			} else {
				publicKey = Keys.privateKey(bytes, "Ed25519 private key").publicKey().bytes();
			}

			String encoded = Keys.encode(publicKey, hex);
			if (output().isJson())
				output().json(Map.of("publicKey", encoded));
			else
				output().message(encoded);
		}
	}

	@Command(name = "sign", description = {"Sign a binding nonce with your identity.",
			"Proves that you hold the identity, to link it to an OAuth sign-in: the Director issues the nonce, and takes "
					+ "back the publicKey and signature printed. Only 32-byte nonces are signed, so that this never signs "
					+ "anything else."})
	public static class SignCommand extends DirectorCommand {
		private static final int NONCE_BYTES = 32;

		@Parameters(paramLabel = "<nonce>", description = "The nonce: Base58, or hex with 0x.")
		String nonce;

		@Option(names = "--hex", description = "Print the public key and signature as hex rather than Base58.")
		boolean hex;

		@Override
		protected void run() {
			byte[] bytes = Keys.decode(nonce, "nonce");
			if (bytes.length != NONCE_BYTES)
				throw CliException.usage("The nonce must be " + NONCE_BYTES + " bytes, not " + bytes.length + ".",
						"Sign only the nonce the Director issues for linking an identity.");

			Signature.KeyPair key = context().identity();
			String publicKey = Keys.encode(key.publicKey().bytes(), hex);
			String signature = Keys.encode(key.privateKey().sign(bytes), hex);

			if (output().isJson()) {
				Map<String, Object> json = new LinkedHashMap<>();
				json.put("publicKey", publicKey);
				json.put("signature", signature);
				output().json(json);
				return;
			}

			output().message("publicKey: " + publicKey);
			output().message("signature: " + signature);
		}
	}

	@Command(name = "hex-to-base58", description = "Convert a hex value to Base58.")
	public static class HexToBase58Command extends DirectorCommand {
		@Parameters(paramLabel = "<hex>", description = "The value, with or without 0x.")
		String value;

		@Override
		protected void run() {
			String hex = value.strip();
			if (hex.startsWith("0x") || hex.startsWith("0X"))
				hex = hex.substring(2);

			byte[] bytes;
			try {
				if (hex.isEmpty() || hex.length() % 2 != 0)
					throw new IllegalArgumentException();
				bytes = Hex.decode(hex);
			} catch (RuntimeException e) {
				throw CliException.usage("'" + value + "' is not a valid hex value.", null);
			}

			String base58 = Base58.encode(bytes);
			if (output().isJson())
				output().json(Map.of("base58", base58));
			else
				output().message(base58);
		}
	}

	@Command(name = "base58-to-hex", description = "Convert a Base58 value to hex, with 0x.")
	public static class Base58ToHexCommand extends DirectorCommand {
		@Parameters(paramLabel = "<base58>", description = "The value.")
		String value;

		@Override
		protected void run() {
			byte[] bytes;
			try {
				if (value.isBlank())
					throw new IllegalArgumentException();
				bytes = Base58.decode(value.strip());
			} catch (RuntimeException e) {
				throw CliException.usage("'" + value + "' is not a valid Base58 value.", null);
			}

			String hex = "0x" + Hex.encode(bytes);
			if (output().isJson())
				output().json(Map.of("hex", hex));
			else
				output().message(hex);
		}
	}
}
