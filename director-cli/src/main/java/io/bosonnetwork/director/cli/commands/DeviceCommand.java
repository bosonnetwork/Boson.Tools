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

package io.bosonnetwork.director.cli.commands;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import io.bosonnetwork.Id;
import io.bosonnetwork.cli.support.CliCommand;
import io.bosonnetwork.cli.support.CliException;
import io.bosonnetwork.cli.support.CliGroup;
import io.bosonnetwork.cli.support.ExitCode;
import io.bosonnetwork.cli.support.Listing;
import io.bosonnetwork.cli.support.Views;
import io.bosonnetwork.director.client.Device;
import io.bosonnetwork.director.client.DirectorAdmin;
import io.bosonnetwork.director.client.exceptions.ConflictException;
import io.bosonnetwork.director.client.exceptions.NotFoundException;

/**
 * The {@code device} commands of {@code boson-director-cli}.
 */
@Command(name = "device", description = "Manage the devices of the node's users.",
		subcommands = {DeviceCommand.ListCommand.class, DeviceCommand.ShowCommand.class, DeviceCommand.AddCommand.class,
				DeviceCommand.RemoveCommand.class})
public class DeviceCommand extends CliGroup {

	@Command(name = "list", description = "List a user's devices.")
	public static class ListCommand extends CliCommand {
		@Parameters(paramLabel = "<user-id>", description = "The user.")
		Id userId;

		@Override
		protected void run() throws Exception {
			List<Device> devices;
			try {
				devices = await(context().directorAdmin().listDevices(userId));
			} catch (NotFoundException e) {
				throw UserCommand.noSuchUser(userId);
			}
			Listing.list(output(), devices, Views.DEVICE_HEADERS, Views::deviceRow, Views::deviceJson,
					"User " + userId + " has no devices.");
		}
	}

	@Command(name = "show", description = "Show a device.")
	public static class ShowCommand extends CliCommand {
		@Parameters(paramLabel = "<device-id>", description = "The device.")
		Id deviceId;

		@Override
		protected void run() throws Exception {
			Device device = await(context().directorAdmin().getDevice(deviceId)).orElseThrow(() -> noSuchDevice(deviceId));
			if (output().isJson())
				output().json(Views.deviceJson(device));
			else
				output().details(Views.device(device));
		}
	}

	@Command(name = "add", description = {"Register a device to a user.",
			"No signature from the device is involved: you vouch for it. A user registers devices themselves with "
					+ "'boson-cli device add'."})
	public static class AddCommand extends CliCommand {
		@Parameters(index = "0", paramLabel = "<user-id>", description = "The user the device belongs to.")
		Id userId;

		@Parameters(index = "1", paramLabel = "<device-id>", description = "The device's id: the public key of its identity.")
		Id deviceId;

		@Option(names = "--name", paramLabel = "<name>", required = true, description = "A name for the device, shown to the user.")
		String name;

		@Option(names = "--app", paramLabel = "<name>", required = true, description = "The app the device runs.")
		String app;

		@Override
		protected void run() throws Exception {
			try {
				await(context().directorAdmin().addDevice(userId, deviceId, name, app));
			} catch (NotFoundException e) {
				throw UserCommand.noSuchUser(userId);
			} catch (ConflictException e) {
				throw new CliException(ExitCode.CONFLICT, "Device " + deviceId + " is already registered.",
						"Show it with " + tool().command("device show " + deviceId) + ".");
			}

			if (output().isJson()) {
				Map<String, Object> json = new LinkedHashMap<>();
				json.put("userId", userId);
				json.put("deviceId", deviceId);
				json.put("name", name);
				json.put("app", app);
				output().json(json);
				return;
			}

			output().message("Added device " + deviceId + " (" + name + ", " + app + ") to user " + userId + ".");
		}
	}

	@Command(name = "remove", description = "Remove a device. It can no longer act for its user.")
	public static class RemoveCommand extends CliCommand {
		@Parameters(paramLabel = "<device-id>", description = "The device.")
		Id deviceId;

		@Option(names = {"-y", "--yes"}, description = "Do not ask for confirmation.")
		boolean yes;

		@Override
		protected void run() throws Exception {
			DirectorAdmin admin = context().directorAdmin();
			terminal().confirm("Remove device " + deviceId + "? It will no longer be able to act for its user.", yes);

			try {
				await(admin.removeDevice(deviceId));
			} catch (NotFoundException e) {
				throw noSuchDevice(deviceId);
			}

			if (output().isJson())
				output().json(Map.of("deviceId", deviceId, "removed", true));
			else
				output().message("Removed device " + deviceId + ".");
		}
	}

	private static CliException noSuchDevice(Id deviceId) {
		return CliException.notFound("There is no device " + deviceId + " on this node.",
				"List a user's devices with 'boson-director-cli device list <user-id>'.");
	}
}
