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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import io.bosonnetwork.Id;
import io.bosonnetwork.cli.director.DirectorCommand;
import io.bosonnetwork.cli.director.CliContext;
import io.bosonnetwork.cli.common.CliException;
import io.bosonnetwork.cli.common.CliGroup;
import io.bosonnetwork.cli.common.ExitCode;
import io.bosonnetwork.cli.common.IdentityFile;
import io.bosonnetwork.cli.common.Listing;
import io.bosonnetwork.cli.director.Views;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.director.client.Device;
import io.bosonnetwork.director.client.DirectorClient;
import io.bosonnetwork.director.client.exceptions.ConflictException;
import io.bosonnetwork.director.client.exceptions.NotFoundException;

/**
 * The {@code device} commands of {@code boson-cli}: the devices of the user this tool acts as.
 */
@Command(name = "device", description = {"List, register and remove the devices of your account.",
		"A device has a key of its own, and acts for you with it, so that apps never need your user key."},
		subcommands = {DeviceCommand.ListCommand.class, DeviceCommand.AddCommand.class, DeviceCommand.RemoveCommand.class})
public class DeviceCommand extends CliGroup {

	@Command(name = "list", description = "List the devices registered to your account.")
	public static class ListCommand extends DirectorCommand {
		@Override
		protected void run() throws Exception {
			List<Device> devices = await(context().directorClient().listDevices());
			Listing.list(output(), devices, Views.DEVICE_HEADERS, Views::deviceRow, Views::deviceJson,
					"No devices are registered to your account.");
		}
	}

	@Command(name = "add", description = {"Register a device to your account.",
			"The device key signs the registration, proving the device holds it; it is not sent."})
	public static class AddCommand extends DirectorCommand {
		@Option(names = "--key", paramLabel = "<file>", required = true,
				description = "The identity file of the device. Create one with 'boson-cli util keygen --output <file>'.")
		Path keyFile;

		@Option(names = "--name", paramLabel = "<name>", required = true,
				description = "A name for the device, such as \"Alice's phone\".")
		String name;

		@Option(names = "--app", paramLabel = "<name>", required = true, description = "The app the device runs.")
		String app;

		@Option(names = "--passphrase", description = "Ask for the account passphrase up front. "
				+ "Without it, you are asked only if the account has one.")
		boolean passphrase;

		@Override
		protected void run() throws Exception {
			Signature.KeyPair key = readDeviceKey(context(), keyFile);
			Id deviceId = Id.of(key.publicKey().bytes());
			DirectorClient client = context().directorClient();

			try {
				withPassphrase(passphrase, p -> client.registerDevice(key, name, app, p));
			} catch (ConflictException e) {
				throw new CliException(ExitCode.CONFLICT, "The device " + deviceId + " is already registered.",
						"A device key belongs to one user only. Generate a new one with " +
								tool().command("util keygen --output <file>") + ".");
			}

			if (output().isJson()) {
				Map<String, Object> json = new LinkedHashMap<>();
				json.put("deviceId", deviceId);
				json.put("name", name);
				json.put("app", app);
				output().json(json);
				return;
			}

			output().message("Registered device " + deviceId + " (" + name + ", " + app + ") to your account.");
		}
	}

	@Command(name = "remove", description = "Remove a device from your account. It can no longer act for you.")
	public static class RemoveCommand extends DirectorCommand {
		@Parameters(paramLabel = "<device-id>", description = "The device, as 'boson-cli device list' shows it.")
		Id deviceId;

		@Option(names = {"-y", "--yes"}, description = "Do not ask for confirmation.")
		boolean yes;

		@Option(names = "--passphrase", description = "Ask for the account passphrase up front. "
				+ "Without it, you are asked only if the account has one.")
		boolean passphrase;

		@Override
		protected void run() throws Exception {
			DirectorClient client = context().directorClient();
			terminal().confirm("Remove device " + deviceId + " from your account? It will no longer be able to act for you.", yes);

			try {
				withPassphrase(passphrase, p -> client.removeDevice(deviceId, p));
			} catch (NotFoundException e) {
				throw CliException.notFound("Your account has no device " + deviceId + ".",
						"List your devices with " + tool().command("device list") + ".");
			}

			if (output().isJson())
				output().json(Map.of("deviceId", deviceId, "removed", true));
			else
				output().message("Removed device " + deviceId + " from your account.");
		}
	}

	/**
	 * Reads a device key, which must not be the user key itself.
	 *
	 * @param context the context of the run
	 * @param file    the identity file of the device
	 * @return the device key pair
	 * @throws CliException if the file is missing or invalid, or holds the user key
	 */
	static Signature.KeyPair readDeviceKey(CliContext context, Path file) {
		if (!Files.exists(file))
			throw CliException.usage("The device key file " + file + " does not exist.",
					"Create a device key with " + context.tool().command("util keygen --output " + file) + ".");

		Signature.KeyPair key = IdentityFile.read(file, "device key file");
		if (Arrays.equals(key.publicKey().bytes(), context.identity().publicKey().bytes()))
			throw CliException.usage("The device key is your user key.",
					"Generate a separate key for the device with " + context.tool().command("util keygen --output <file>") + ".");
		return key;
	}
}
