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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Set;

/**
 * Creates the files that hold secrets - identity files, and configuration files that may carry a
 * private key - readable by their owner only.
 * <p>
 * On a file system without POSIX permissions (Windows) files are created with the defaults, which on
 * Windows already restrict a user's profile directory to that user.
 */
final class FilePermissions {
	private static final Set<PosixFilePermission> PRIVATE_FILE =
			EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
	private static final Set<PosixFilePermission> PRIVATE_DIRECTORY =
			EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);

	private FilePermissions() {
	}

	/**
	 * Writes a new file readable by its owner only, creating the directories leading to it. The
	 * directories this creates are private too; existing ones are left as they are.
	 *
	 * @param file    the file
	 * @param content the content
	 * @throws java.nio.file.FileAlreadyExistsException if the file exists; it is left unchanged
	 * @throws IOException if the file cannot be written
	 */
	static void writeNewPrivateFile(Path file, String content) throws IOException {
		Path parent = file.toAbsolutePath().getParent();
		if (parent != null)
			createPrivateDirectories(parent);

		// CREATE_NEW semantics: never replaces an existing file, checked atomically by the file system.
		if (isPosix(file.toAbsolutePath()))
			Files.createFile(file, PosixFilePermissions.asFileAttribute(PRIVATE_FILE));
		else
			Files.createFile(file);

		Files.writeString(file, content, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
	}

	/**
	 * Tells whether users other than the owner can read or write a file.
	 *
	 * @param file the file
	 * @return {@code true} if the file is open to other users; {@code false} if not, or unknown
	 */
	static boolean isAccessibleByOthers(Path file) {
		try {
			if (!isPosix(file))
				return false;

			Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS);
			return permissions.contains(PosixFilePermission.GROUP_READ) ||
					permissions.contains(PosixFilePermission.GROUP_WRITE) ||
					permissions.contains(PosixFilePermission.OTHERS_READ) ||
					permissions.contains(PosixFilePermission.OTHERS_WRITE);
		} catch (IOException | UnsupportedOperationException e) {
			return false;
		}
	}

	private static void createPrivateDirectories(Path directory) throws IOException {
		Deque<Path> missing = new ArrayDeque<>();
		for (Path d = directory; d != null && Files.notExists(d); d = d.getParent())
			missing.push(d);

		while (!missing.isEmpty()) {
			Path d = missing.pop();
			if (isPosix(d))
				Files.createDirectory(d, PosixFilePermissions.asFileAttribute(PRIVATE_DIRECTORY));
			else
				Files.createDirectory(d);
		}
	}

	private static boolean isPosix(Path path) {
		return path.getFileSystem().supportedFileAttributeViews().contains("posix");
	}
}
