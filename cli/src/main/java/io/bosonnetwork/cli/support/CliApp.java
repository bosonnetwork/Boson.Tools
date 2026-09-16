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

import io.bosonnetwork.Id;

/**
 * The root command of a tool: the global options, the help and error conventions, and the context of a
 * run, which every command reaches through it.
 */
@Command(mixinStandardHelpOptions = true, versionProvider = VersionProvider.class, scope = ScopeType.INHERIT,
		usageHelpAutoWidth = true, sortOptions = false)
public abstract class CliApp implements Callable<Integer> {
	// The options every command takes, listed in full by the root command's help only.
	private static final List<String> GLOBAL_OPTIONS = List.of("--config", "--url", "--node-id", "--resolve",
			"--identity", "--json", "--verbose", "--help", "--version");

	@Spec
	CommandSpec spec;

	@Mixin
	GlobalOptions options;

	private final CliEnvironment environment;
	private ToolSpec tool;
	private CliContext context;

	/**
	 * Creates the root command.
	 *
	 * @param environment the environment the tool runs in
	 */
	protected CliApp(CliEnvironment environment) {
		this.environment = environment;
	}

	/**
	 * Describes the tool.
	 *
	 * @param environment the environment the tool runs in
	 * @return the tool
	 */
	protected abstract ToolSpec createTool(CliEnvironment environment);

	/**
	 * Returns the tool.
	 *
	 * @return the tool
	 */
	public final ToolSpec tool() {
		if (tool == null)
			tool = createTool(environment);
		return tool;
	}

	/**
	 * Returns the context of this run.
	 *
	 * @return the context
	 */
	public final CliContext context() {
		if (context == null)
			context = new CliContext(tool(), environment, options);
		return context;
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
		commandLine.registerConverter(Id.class, new IdConverter());
		commandLine.setParameterExceptionHandler(CliApp::handleParameterException);
		commandLine.setExecutionExceptionHandler((e, cl, parseResult) ->
				ErrorReporter.report(e, context(), cl.getErr(), options.verbose()));
		commandLine.getCommandSpec().usageMessage()
				.exitCodeListHeading("%nExit codes:%n")
				.exitCodeList(ExitCode.descriptions());
		configureHelp(commandLine, true);

		try {
			return commandLine.execute(args);
		} finally {
			if (context != null)
				context.close();
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

	private static void configureHelp(CommandLine commandLine, boolean root) {
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
	private static String optionList(Help help, boolean root) {
		List<OptionSpec> own = new ArrayList<>();
		List<OptionSpec> global = new ArrayList<>();
		for (OptionSpec option : help.commandSpec().options()) {
			if (option.hidden() || option.group() != null)
				continue;
			if (GLOBAL_OPTIONS.contains(option.longestName()))
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
			text.append(n).append("Global options: ").append(String.join(", ", GLOBAL_OPTIONS)).append(n)
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
