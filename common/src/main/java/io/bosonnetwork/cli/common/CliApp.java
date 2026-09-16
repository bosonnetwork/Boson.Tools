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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.UsageMessageSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Spec;
import picocli.CommandLine.UnmatchedArgumentException;

import io.bosonnetwork.json.Json;

/**
 * The root command of a Boson tool: the options every command takes, the help and error conventions,
 * and the output and terminal the commands write to.
 * <p>
 * A tool subclasses this, adds its own inherited options, and says how to translate the failures of
 * its domain ({@link #errorTranslator()}) and what to close on the way out ({@link #close()}).
 */
@Command(mixinStandardHelpOptions = true, versionProvider = VersionProvider.class, scope = ScopeType.INHERIT,
		usageHelpAutoWidth = true, sortOptions = false)
public abstract class CliApp implements Callable<Integer> {
	/** The options every tool takes, listed in full by the root command's help only. */
	protected static final List<String> COMMON_OPTIONS = List.of("--json", "--verbose", "--help", "--version");

	@Spec
	CommandSpec spec;

	@Mixin
	CommonOptions commonOptions;

	private final CliEnvironment environment;
	private Output output;
	private Terminal terminal;

	/**
	 * Creates the root command.
	 *
	 * @param environment the environment the tool runs in
	 */
	protected CliApp(CliEnvironment environment) {
		this.environment = environment;
		Json.initializeBosonJsonModule();
	}

	/**
	 * Returns the environment the tool runs in.
	 *
	 * @return the environment
	 */
	public final CliEnvironment environment() {
		return environment;
	}

	/**
	 * Returns the options every command takes.
	 *
	 * @return the options
	 */
	public final CommonOptions commonOptions() {
		return commonOptions;
	}

	/**
	 * Returns the output of this run.
	 *
	 * @return the output
	 */
	public final Output output() {
		if (output == null)
			output = new Output(environment.out(), environment.err(), commonOptions.json());
		return output;
	}

	/**
	 * Returns the terminal of this run.
	 *
	 * @return the terminal
	 */
	public final Terminal terminal() {
		if (terminal == null)
			terminal = new Terminal(environment);
		return terminal;
	}

	/**
	 * Names the options every command of this tool takes, for the help. A tool with inherited options
	 * of its own lists them here, so that they are shown with the command's own options rather than
	 * among them.
	 *
	 * @return the option names, longest form
	 */
	protected List<String> globalOptionNames() {
		return COMMON_OPTIONS;
	}

	/**
	 * Translates the failures of this tool's domain into what the user is told. The generic failures -
	 * {@link CliException}, invalid arguments, files - are handled by {@link ErrorReporter}.
	 *
	 * @return the translator; by default one that handles nothing
	 */
	protected ErrorTranslator errorTranslator() {
		return error -> null;
	}

	/**
	 * Closes what the run opened. Called however the run ends.
	 */
	protected void close() {
	}

	/**
	 * Without a command, shows the help.
	 *
	 * @return the exit code
	 */
	@Override
	public Integer call() {
		spec.commandLine().usage(environment.out());
		return ExitCode.OK;
	}

	/**
	 * Runs the tool.
	 *
	 * @param args the command line
	 * @return the exit code
	 */
	public final int execute(String... args) {
		CommandLine commandLine = new CommandLine(this);
		commandLine.setOut(environment.out());
		commandLine.setErr(environment.err());
		commandLine.setCaseInsensitiveEnumValuesAllowed(true);
		Converters.registerAll(commandLine);
		commandLine.setParameterExceptionHandler(CliApp::handleParameterException);
		commandLine.setExecutionExceptionHandler((e, cl, parseResult) ->
				ErrorReporter.report(e, errorTranslator(), cl.getErr(), commonOptions.verbose()));
		commandLine.getCommandSpec().usageMessage()
				.exitCodeListHeading("%nExit codes:%n")
				.exitCodeList(ExitCode.descriptions());
		configureHelp(commandLine, true);

		try {
			return commandLine.execute(args);
		} finally {
			close();
			environment.out().flush();
			environment.err().flush();
		}
	}

	/**
	 * Runs a tool in this process, and exits with its exit code.
	 *
	 * @param args    the command line
	 * @param factory creates the tool's root command
	 */
	public static void launch(String[] args, Function<CliEnvironment, CliApp> factory) {
		// Before anything creates a logger: logback reads its configuration once, on first use.
		CliLogging.configure(args);
		int exitCode = factory.apply(CliEnvironment.system()).execute(args);
		System.exit(exitCode);
	}

	private void configureHelp(CommandLine commandLine, boolean root) {
		UsageMessageSpec usage = commandLine.getCommandSpec().usageMessage();
		// Every command takes the global options: listing them in each synopsis would bury the command's own.
		usage.abbreviateSynopsis(true);
		usage.parameterListHeading("%nArguments:%n");
		usage.commandListHeading("%nCommands:%n");
		if (!commandLine.getSubcommands().isEmpty())
			usage.synopsisSubcommandLabel("<command>");

		commandLine.getHelpSectionMap().put(UsageMessageSpec.SECTION_KEY_OPTION_LIST, help -> optionList(help, root));

		for (CommandLine subcommand : commandLine.getSubcommands().values())
			configureHelp(subcommand, false);
	}

	// The command's own options, then the global ones: in full for the root command, and named for the
	// others.
	private String optionList(Help help, boolean root) {
		List<String> globalNames = globalOptionNames();
		List<OptionSpec> own = new ArrayList<>();
		List<OptionSpec> global = new ArrayList<>();
		for (OptionSpec option : help.commandSpec().options()) {
			if (option.hidden() || option.group() != null)
				continue;
			if (globalNames.contains(option.longestName()))
				global.add(option);
			else
				own.add(option);
		}

		String n = System.lineSeparator();
		StringBuilder text = new StringBuilder();
		if (!own.isEmpty())
			text.append(n).append("Options:").append(n).append(help.optionListExcludingGroups(own));

		text.append(help.optionListGroupSections());

		if (root) {
			text.append(n).append("Global options, taken by every command:").append(n)
					.append(help.optionListExcludingGroups(global));
		} else {
			text.append(n).append("Global options: ").append(String.join(", ", globalNames)).append(n)
					.append("  See '").append(help.commandSpec().root().name()).append(" --help'.").append(n);
		}

		return text.toString();
	}

	private static int handleParameterException(ParameterException e, String[] args) {
		CommandLine commandLine = e.getCommandLine();
		PrintWriter err = commandLine.getErr();
		err.println("Error: " + ErrorReporter.sentence(e.getMessage()));
		UnmatchedArgumentException.printSuggestions(e, err);
		err.println("Run '" + commandLine.getCommandSpec().qualifiedName() + " --help' for usage.");
		err.flush();
		return ExitCode.USAGE;
	}
}
