/*
 * Copyright (c) 2022, Research Institutes of Sweden. All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer. 2. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. 3. Neither the name of the
 * Institute nor the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.contikios.cooja;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.contikios.cooja.Cooja.Config;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Contains the command line parameters and is the main entry point for Cooja.
 */
@Command(version = {
        "Cooja " + Cooja.VERSION + ", Contiki-NG build interface version " + Cooja.CONTIKI_NG_BUILD_VERSION,
        "JVM: ${java.version} (${java.vendor} ${java.vm.name} ${java.vm.version})",
        "OS: ${os.name} ${os.version} ${os.arch}"}, sortOptions = false, sortSynopsis = false,
        parameterListHeading = "%n")
class Main {
  /**
   * Option for specifying Cooja path.
   */
  @Option(names = "--cooja", paramLabel = "DIR", description = "Cooja directory", required = true)
  String coojaPath;

  /**
   * Option for specifying javac path.
   */
  @Option(names = "--javac", paramLabel = "FILE", description = "javac binary", required = true)
  String javac;

  /**
   * Option for specifying if a GUI should be used.
   */
  @Option(names = "--gui", description = "use graphical mode", negatable = true)
  Boolean gui;

  /**
   * Option for specifying simulation files to load.
   */
  @Parameters(paramLabel = "FILE", description = "one or more simulation files")
  String[] simulationFiles;

  @ArgGroup(validate = false, heading = "%nCooja paths:%n")
  CoojaConfig path;

  static class CoojaConfig {
    /**
     * Option for specifying external user config file.
     */
    @Option(names = "--config", paramLabel = "FILE", description = "filename for external user config")
    String externalUserConfig;

    /**
     * Option for specifying Contiki-NG path.
     */
    @Option(names = "--contiki", paramLabel = "DIR", description = "Contiki-NG directory")
    String contikiPath;
  }

  @ArgGroup(validate = false, heading = "%nLog configuration:%n")
  LogConfig log;

  static class LogConfig {
    /**
     * Option for specifying log directory.
     */
    @Option(names = "--logdir", paramLabel = "DIR", description = "log directory use")
    String logDir = ".";

    /**
     * Option for also logging stdout output to a file.
     */
    @Option(names = "--logname", paramLabel = "NAME", description = "filename for the log")
    String logName;

    /**
     * Option for specifying log4j2 config file.
     */
    @Option(names = "--log4j2", paramLabel = "FILE", description = "log4j2 config file")
    String logConfigFile;
  }

  @ArgGroup(validate = false, heading = "%nSimulation config:%n")
  SimulationConfig sim;

  static class SimulationConfig {
    /**
     * Automatically start simulations.
     */
    @Option(names = "--autostart", description = "automatically start simulations")
    boolean autoStart;

    /**
     * Option for specifying seed used for simulation.
     */
    @Option(names = "--random-seed", paramLabel = "SEED", description = "random seed")
    Long randomSeed;

    /**
     * Option for instructing Cooja to update the simulation file (.csc).
     */
    @Option(names = "--update-simulation", description = "write an updated simulation file (.csc) and exit")
    boolean updateSimulation;
  }

  @ArgGroup(validate = false, heading = "%nQuery options:%n")
  Info info;

  static class Info {
    @Option(names = "--version", versionHelp = true, description = "print version information and exit")
    boolean versionRequested;

    @Option(names = "--help", usageHelp = true, description = "display a help message")
    boolean helpRequested;
  }

  public static void main(String[] args) {
    Main options = new Main();
    CommandLine commandLine = new CommandLine(options);
    try {
      commandLine.parseArgs(args);
    } catch (CommandLine.ParameterException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }

    if (options.info.helpRequested) {
      commandLine.usage(System.out);
      return;
    }
    if (options.info.versionRequested) {
      commandLine.printVersionHelp(System.out);
      return;
    }
    options.gui = options.gui == null || options.gui;

    if (options.gui && GraphicsEnvironment.isHeadless()) {
      System.err.println("Trying to start GUI in headless environment, aborting");
      System.exit(1);
    }

    if (options.sim.updateSimulation && !options.gui) {
      System.err.println("Can only update simulation with --gui");
      System.exit(1);
    }

    if (!options.gui) {
      // Ensure no UI is used by Java
      System.setProperty("java.awt.headless", "true");
      Path logDirPath = Path.of(options.log.logDir);
      if (!Files.exists(logDirPath)) {
        try {
          Files.createDirectory(logDirPath);
        } catch (IOException e) {
          System.err.println("Could not create log directory '" + options.log.logDir + "'");
          System.exit(1);
        }
      }
    }

    ArrayList<String> simulationFiles = new ArrayList<>();
    if (options.simulationFiles != null) {
      Collections.addAll(simulationFiles, options.simulationFiles);
    }
    // Parse and verify soundness of simulation files argument.
    ArrayList<Simulation.SimConfig> simConfigs = new ArrayList<>();
    for (String arg : simulationFiles) {
      // Argument on the form "file.csc[,key1=value1,key2=value2, ..]"
      var map = new HashMap<String, String>();
      String file = null;
      for (var item : arg.split(",", -1)) {
        if (file == null) {
          file = item;
          continue;
        }
        var pair = item.split("=", -1);
        if (pair.length != 2) {
          System.err.println("Faulty key=value specification: " + item);
          System.exit(1);
        }
        map.put(pair[0], pair[1]);
      }
      if (file == null) {
        System.err.println("Failed argument parsing of simulation file " + arg);
        System.exit(1);
      }
      if (!file.endsWith(".csc") && !file.endsWith(".csc.gz")) {
        System.err.println("Cooja expects simulation filenames to have an extension of '.csc' or '.csc.gz");
        System.exit(1);
      }
      if (!Files.exists(Path.of(file))) {
        System.err.println("File '" + file + "' does not exist");
        System.exit(1);
      }
      var autoStart = map.getOrDefault("autostart", Boolean.toString(options.sim.autoStart || !options.gui));
      var updateSim = map.getOrDefault("update-simulation", Boolean.toString(options.sim.updateSimulation));
      var logDir = map.getOrDefault("logdir", options.log.logDir);
      simConfigs.add(new Simulation.SimConfig(file, Boolean.parseBoolean(autoStart), Boolean.parseBoolean(updateSim),
                                              logDir, map));
    }

    if (options.log.logConfigFile != null && !Files.exists(Path.of(options.log.logConfigFile))) {
      System.err.println("Configuration file '" + options.log.logConfigFile + "' does not exist");
      System.exit(1);
    }

    if (options.path.contikiPath != null && !Files.exists(Path.of(options.path.contikiPath))) {
      System.err.println("Contiki-NG path '" + options.path.contikiPath + "' does not exist");
      System.exit(1);
    }

    if (!options.coojaPath.endsWith("/")) {
      options.coojaPath += '/';
    }

    if (!Files.exists(Path.of(options.coojaPath))) {
      System.err.println("Cooja path '" + options.coojaPath + "' does not exist");
      System.exit(1);
    }

    if (!Files.exists(Path.of(options.javac))) {
      System.err.println("Java compiler '" + options.javac + "' does not exist");
      System.exit(1);
    }

    if (options.log.logName != null && !options.log.logName.endsWith(".log")) {
      options.log.logName += ".log";
    }

    var cfg = new Config(options.gui, options.sim.randomSeed, options.path.externalUserConfig,
            options.log.logDir, options.path.contikiPath, options.coojaPath, options.javac);
    // Configure logger
    if (options.log.logConfigFile == null) {
      ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
      builder.setStatusLevel(Level.INFO);
      builder.setConfigurationName("DefaultConfig");
      builder.add(builder.newFilter("ThresholdFilter", Filter.Result.ACCEPT, Filter.Result.NEUTRAL)
              .addAttribute("level", Level.INFO));
      // Configure console appender.
      AppenderComponentBuilder appenderBuilder = builder.newAppender("Stdout", "CONSOLE")
              .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT);
      appenderBuilder.add(builder.newLayout("PatternLayout")
              .addAttribute("pattern", "%5p [%t] (%F:%L) - %m%n"));
      appenderBuilder.add(builder.newFilter("MarkerFilter", Filter.Result.DENY, Filter.Result.NEUTRAL)
              .addAttribute("marker", "FLOW"));
      builder.add(appenderBuilder);
      builder.add(builder.newLogger("org.apache.logging.log4j", Level.DEBUG)
              .add(builder.newAppenderRef("Stdout")).addAttribute("additivity", false));
      if (options.log.logName != null) {
        // Configure logfile file appender.
        appenderBuilder = builder.newAppender("File", "FILE")
                .addAttribute("fileName", options.log.logDir + "/" + options.log.logName)
                .addAttribute("Append", "false");
        appenderBuilder.add(builder.newLayout("PatternLayout")
                .addAttribute("pattern", "[%d{HH:mm:ss} - %t] [%F:%L] [%p] - %m%n"));
        appenderBuilder.add(builder.newFilter("MarkerFilter", Filter.Result.DENY, Filter.Result.NEUTRAL)
                .addAttribute("marker", "FLOW"));
        builder.add(appenderBuilder);
        builder.add(builder.newLogger("org.apache.logging.log4j", Level.DEBUG)
                .add(builder.newAppenderRef("File")).addAttribute("additivity", false));
        // Construct the root logger and initialize the configurator
        builder.add(builder.newRootLogger(Level.INFO).add(builder.newAppenderRef("Stdout"))
                .add(builder.newAppenderRef("File")));
      } else {
        builder.add(builder.newRootLogger(Level.INFO).add(builder.newAppenderRef("Stdout")));
      }
      // FIXME: This should be try (LoggerContext cxt = Configurator.initialize(..)),
      //        but go immediately returns which causes the log file to be closed
      //        while the simulation is still running.
      Configurator.initialize(builder.build());
      Cooja.go(cfg, simConfigs);
    } else {
      Configurator.initialize("ConfigFile", options.log.logConfigFile);
      Cooja.go(cfg, simConfigs);
    }
  }
}
