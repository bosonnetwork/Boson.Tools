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

package io.bosonnetwork.director.cli;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

import io.bosonnetwork.Id;
import io.bosonnetwork.cli.support.CliException;
import io.bosonnetwork.director.client.Sort;
import io.bosonnetwork.director.client.Subscription;
import io.bosonnetwork.json.Json;

/**
 * The argument types of {@code boson-director-cli} beyond ids and numbers: plans named by id or name,
 * points in time, subscription states, sort keys, blacklist entries and JSON documents.
 */
public final class Arguments {
	private Arguments() {
	}

	/**
	 * Returns a value, or {@code null} for a blank one, which clears a field.
	 *
	 * @param value the value
	 * @return the value, stripped, or {@code null}
	 */
	public static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	// ---- Plans ---------------------------------------------------------------------------------

	/**
	 * A plan, named by its id or its name. A value made only of digits is an id, as the Director reads
	 * it too.
	 */
	public static final class PlanRef {
		private final int id;
		private final String name;

		private PlanRef(int id, String name) {
			this.id = id;
			this.name = name;
		}

		/**
		 * Parses a plan reference.
		 *
		 * @param value the id or name
		 * @return the reference
		 * @throws IllegalArgumentException if the value is empty, or an id out of range
		 */
		public static PlanRef of(String value) {
			String v = value.strip();
			if (v.isEmpty())
				throw new IllegalArgumentException("the plan is empty");
			if (v.chars().allMatch(Character::isDigit)) {
				try {
					int id = Integer.parseInt(v);
					if (id > 0)
						return new PlanRef(id, null);
				} catch (NumberFormatException ignored) {
					// reported below
				}
				throw new IllegalArgumentException("'" + value + "' is not a valid plan id");
			}
			return new PlanRef(0, v);
		}

		/**
		 * Tells whether the plan is named by id.
		 *
		 * @return {@code true} for an id
		 */
		public boolean isId() {
			return name == null;
		}

		/**
		 * Returns the plan id.
		 *
		 * @return the id; only meaningful if {@link #isId()}
		 */
		public int id() {
			return id;
		}

		/**
		 * Returns the plan name.
		 *
		 * @return the name, or {@code null} if the plan is named by id
		 */
		public String name() {
			return name;
		}

		@Override
		public String toString() {
			return isId() ? Integer.toString(id) : name;
		}
	}

	/**
	 * Reads a plan reference from the command line.
	 */
	public static class PlanRefConverter implements ITypeConverter<PlanRef> {
		@Override
		public PlanRef convert(String value) {
			try {
				return PlanRef.of(value);
			} catch (IllegalArgumentException e) {
				throw new TypeConversionException(e.getMessage());
			}
		}
	}

	// ---- Time ----------------------------------------------------------------------------------

	/** How a point in time is written, for help texts. */
	public static final String TIME_FORMATS = "a date (2027-01-31), a local date and time (2027-01-31T18:00), " +
			"or a time from now (+30d, +2w, +6m, +1y)";

	private static final Pattern RELATIVE = Pattern.compile("\\+(\\d+)([hdwmy])");

	/**
	 * Reads a point in time from the command line, as epoch milliseconds: a date (start of the day,
	 * local time), a local date and time, an ISO instant or offset date and time, a time from now, or
	 * epoch milliseconds.
	 */
	public static class TimeConverter implements ITypeConverter<Long> {
		@Override
		public Long convert(String value) {
			String v = value.strip().toLowerCase(Locale.ROOT);
			ZoneId zone = ZoneId.systemDefault();

			Matcher relative = RELATIVE.matcher(v);
			if (relative.matches()) {
				long n = Long.parseLong(relative.group(1));
				ZonedDateTime now = ZonedDateTime.now(zone);
				ZonedDateTime time = switch (relative.group(2)) {
					case "h" -> now.plusHours(n);
					case "d" -> now.plusDays(n);
					case "w" -> now.plusWeeks(n);
					case "m" -> now.plusMonths(n);
					default -> now.plusYears(n);
				};
				return time.toInstant().toEpochMilli();
			}

			if (v.chars().allMatch(Character::isDigit) && v.length() >= 10)
				return Long.parseLong(v);

			String iso = value.strip();
			try {
				return LocalDate.parse(iso).atStartOfDay(zone).toInstant().toEpochMilli();
			} catch (DateTimeParseException ignored) {
				// try the next form
			}
			try {
				return LocalDateTime.parse(iso).atZone(zone).toInstant().toEpochMilli();
			} catch (DateTimeParseException ignored) {
				// try the next form
			}
			try {
				return OffsetDateTime.parse(iso).toInstant().toEpochMilli();
			} catch (DateTimeParseException ignored) {
				// try the next form
			}
			try {
				return Instant.parse(iso).toEpochMilli();
			} catch (DateTimeParseException ignored) {
				// reported below
			}

			throw new TypeConversionException("'" + value + "' is not a point in time; use " + TIME_FORMATS);
		}
	}

	// ---- Subscription states -------------------------------------------------------------------

	/** The subscription states, as they are written on the command line. */
	public static final String SUBSCRIPTION_STATES = "pending, active, past-due, expired or canceled";

	/**
	 * Reads a subscription state from the command line, in any case, with {@code -} or {@code _}.
	 */
	public static class SubscriptionStatusConverter implements ITypeConverter<Subscription.Status> {
		@Override
		public Subscription.Status convert(String value) {
			String name = value.strip().toUpperCase(Locale.ROOT).replace('-', '_');
			return Arrays.stream(Subscription.Status.values())
					.filter(status -> status.name().equals(name))
					.findFirst()
					.orElseThrow(() -> new TypeConversionException("'" + value + "' is not a subscription state; use " +
							SUBSCRIPTION_STATES));
		}
	}

	// ---- Sort keys -----------------------------------------------------------------------------

	/**
	 * Reads a sort key from the command line: a field, ascending, or {@code field:desc}.
	 */
	public static class SortConverter implements ITypeConverter<Sort> {
		@Override
		public Sort convert(String value) {
			String v = value.strip();
			int colon = v.lastIndexOf(':');
			String field = colon >= 0 ? v.substring(0, colon) : v;
			String direction = colon >= 0 ? v.substring(colon + 1).toLowerCase(Locale.ROOT) : "asc";

			try {
				return switch (direction) {
					case "asc" -> Sort.asc(field);
					case "desc" -> Sort.desc(field);
					default -> throw new TypeConversionException("'" + value + "': the order is asc or desc, not " + direction);
				};
			} catch (IllegalArgumentException e) {
				throw new TypeConversionException("'" + field + "' is not a field name");
			}
		}
	}

	// ---- Blacklist entries ---------------------------------------------------------------------

	/**
	 * What a blacklist command names: an entry by its id, a node by its id, or a host.
	 */
	public static final class BlacklistTarget {
		/** The kinds of blacklist targets. */
		public enum Kind {
			/** An entry, by its id. */
			ENTRY,
			/** A node, by its id. */
			NODE,
			/** A host, by name or address. */
			HOST
		}

		private final Kind kind;
		private final String value;

		private BlacklistTarget(Kind kind, String value) {
			this.kind = kind;
			this.value = value;
		}

		/**
		 * Reads a blacklist target: digits are an entry id, a Boson id is a node, anything else is a
		 * host.
		 *
		 * @param value    the target
		 * @param host     whether the value is a host whatever it looks like ({@code --host})
		 * @param entryIds whether an entry id is accepted here
		 * @return the target
		 * @throws CliException if the value is empty, or an entry id where one is not accepted
		 */
		public static BlacklistTarget of(String value, boolean host, boolean entryIds) {
			String v = value.strip();
			if (v.isEmpty())
				throw CliException.usage("The node or host is empty.", null);

			if (v.chars().allMatch(Character::isDigit)) {
				if (!entryIds || host)
					throw CliException.usage("'" + v + "' is only digits, which the Director reads as a blacklist entry id.",
							"Give a node id or a host name.");
				return new BlacklistTarget(Kind.ENTRY, v);
			}

			if (!host) {
				try {
					Id.of(v);
					return new BlacklistTarget(Kind.NODE, v);
				} catch (IllegalArgumentException ignored) {
					// not an id: a host
				}
			}

			return new BlacklistTarget(Kind.HOST, v);
		}

		/**
		 * Returns the kind of target.
		 *
		 * @return the kind
		 */
		public Kind kind() {
			return kind;
		}

		/**
		 * Returns the entry id.
		 *
		 * @return the id; only for {@link Kind#ENTRY}
		 */
		public long entryId() {
			return Long.parseLong(value);
		}

		/**
		 * Returns the node id.
		 *
		 * @return the id; only for {@link Kind#NODE}
		 */
		public Id nodeId() {
			return Id.of(value);
		}

		/**
		 * Returns the host.
		 *
		 * @return the host; only for {@link Kind#HOST}
		 */
		public String host() {
			return value;
		}

		@Override
		public String toString() {
			return switch (kind) {
				case ENTRY -> "blacklist entry " + value;
				case NODE -> "node " + value;
				case HOST -> "host " + value;
			};
		}
	}

	// ---- Documents -----------------------------------------------------------------------------

	/**
	 * Reads a JSON object given inline or in a file.
	 *
	 * @param inline the JSON text, or {@code null}
	 * @param file   the file holding it, or {@code null}
	 * @param what   what the document is, for error messages
	 * @return the object, or {@code null} if neither is given
	 * @throws CliException if the file cannot be read, or the text is not a JSON object
	 */
	public static Map<String, Object> jsonObject(String inline, Path file, String what) {
		if (inline == null && file == null)
			return null;

		String text;
		if (file != null) {
			try {
				text = Files.readString(file);
			} catch (NoSuchFileException e) {
				throw CliException.usage("No such file: " + file + ".", null);
			} catch (Exception e) {
				throw CliException.usage("Cannot read " + file + ": " + e.getMessage(), null);
			}
		} else {
			text = inline;
		}

		if (!text.strip().startsWith("{"))
			throw CliException.usage("The " + what + " is not a JSON object.", "A JSON object looks like {\"key\": \"value\"}.");

		try {
			return Json.parse(text);
		} catch (RuntimeException e) {
			throw CliException.usage("The " + what + " is not valid JSON: " + e.getMessage(), null);
		}
	}
}
