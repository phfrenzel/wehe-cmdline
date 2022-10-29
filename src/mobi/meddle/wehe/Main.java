package mobi.meddle.wehe;

import mobi.meddle.wehe.constant.Consts;
import mobi.meddle.wehe.constant.S;
import mobi.meddle.wehe.util.Config;
import mobi.meddle.wehe.util.Log;
import mobi.meddle.wehe.bean.Server;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.ArrayList;

/**
 * Main class for Wehe command line client.
 */
public class Main {

  private static ArrayList<String> testNames;
  private static String logLevel = "UI";

  /**
   * Main starting point of Wehe command line client.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    testNames = getTestNames();

    if (testNames == null) {
      System.exit(Consts.ERR_GENERAL);
    }

    parseArgs(args);
    Log.ui("Configs", "\n\tReplay name: " + Config.appName
            + "\n\tServer name: " + Config.serverDisplay
            + "\n\tM-Lab Server API: " + Config.mLabServers
            + "\n\tNumber servers: " + Config.numServers
            + "\n\tConfirmation replays: " + Config.confirmationReplays
            + "\n\tArea threshold: " + Config.a_threshold
            + "\n\tKS2P-value threshold: " + Config.ks2pvalue_threshold
            + "\n\tResources root: " + Config.RESOURCES_ROOT
            + "\n\tResults root: " + Config.RESULTS_ROOT
            + "\n\tLogging level: " + logLevel
            + "\n\tVersion: " + Consts.VERSION_NAME);

    Replay replay = new Replay();
    int exitCode = replay.beginTest();
    exitCode = Log.writeLogs(exitCode);
    System.exit(exitCode);
  }

  /**
   * Prints an error.
   *
   * @param error error message
   */
  private static void printError(String error) {
    System.out.println(error + "\nUse the -h option for additional help.");
    System.exit(Consts.ERR_CMDLINE);
  }

  /**
   * Check to make sure option is valid.
   *
   * @param arg the option to check
   * @return true if option is valid; false otherwise
   */
  private static boolean isValidArg(String arg) {
    return arg.equals("-n") || arg.equals("-s") || arg.equals("-t")
            || arg.equals("-r") || arg.equals("-l");
  }

  /**
   * The command line argument parser.
   *
   * @param args args from the command line
   */
  private static void parseArgs(String[] args) {
    boolean testSpecified = false;
    for (String arg : args) {
      if (arg.equals("-h")) {
        System.out.println(S.USAGE);
        System.exit(Consts.SUCCESS);
      }
      if (arg.equals("-v")) {
        System.out.println("Wehe command line client\nVersion: " + Consts.VERSION_NAME);
        System.exit(Consts.SUCCESS);
      }
      if (arg.equals("-n")) { //test name must be specified
        testSpecified = true;
      }
    }
    if (!testSpecified) {
      printError("Test name (-n) must be specified.");
    }

    String opt;
    String arg = "";
    for (int i = 0; i < args.length; i++) {
      //get args 2 at a time ([-opt] [arg]), except for -c which doesn't take an arg
      opt = args[i];
      if (!isValidArg(opt)) { //get option
        printError("The \"" + opt + "\" option is not a valid.");
      }

      if (i == args.length - 1) { //option (except for -c) cannot be the last arg
        printError("The \"" + opt + "\" option requires an argument.");
      } else {
        arg = args[++i]; //get the argument for the option
      }

      switch (opt) {
        case "-n": //name of test
          boolean found = false;
          String name = arg.toLowerCase().strip();
          for (String id : testNames) { //make sure test name is valid
            if (name.equals(id)) {
              found = true;
              break;
            }
          }
          if (found) {
            Config.appName = name;
          } else {
            printError("\"" + name + "\" is not a valid test name.");
          }
          break;
        case "-s": //url of server
          if (arg.contains(",")) {
            String[] parts = arg.split(",");

            if (parts.length != 2) {
              // TODO validate ipv4 + ipv6
              printError("\"" + arg + "\" does not conform to \"[ipv4],[ipv6]\".");
            }

            Config.serverDisplay = new Server(parts[0], parts[1]);
          } else {
            if (!arg.toLowerCase().matches("[a-z0-9.-]+")) {
              printError("\"" + arg + "\" is not a valid server name. Can only contain alphanumerics, "
                      + "period, and hyphen.");
            }
            Config.serverDisplay = new Server(arg.toLowerCase());
          }
          break;
        case "-t": //set root directory containing the tests and test info
          if (!arg.endsWith("/")) {
            arg += "/";
          }
          Config.RESOURCES_ROOT = arg;
          Config.updateResourcesRoot();
          break;
        case "-r": //set root directory to place results/logs
          if (!arg.endsWith("/")) {
            arg += "/";
          }
          Config.RESULTS_ROOT = arg;
          Config.updateResultsRoot();
          break;
        case "-l":
          logLevel = arg.toLowerCase();
          switch (logLevel) {
            case "wtf":
              Config.logLev = 1;
              break;
            case "error":
              Config.logLev = 2;
              break;
            case "warn":
              Config.logLev = 3;
              break;
            case "info":
              Config.logLev = 4;
              break;
            case "debug":
              Config.logLev = 5;
              break;
            default:
              printError("\"" + arg + "\" is not a log level. Choose from wtf, error, warn, info, or debug.");
          }
          break;
        default: //options should have already been checked for validity before switch
          printError("Something went horribly wrong parsing arguments.");
      }
    }
  }

  private static ArrayList<String> getTestNames() {
    try {
      //get apps_list.json
      StringBuilder buf = new StringBuilder();
      File appInfo = new File(Config.APPS_FILENAME);
      Path tests_info_file = Paths.get(Config.APPS_FILENAME);
      if (!Files.exists(tests_info_file)) {
        Log.e("Load test names", "\"" + Config.APPS_FILENAME + "\" file not found.");
        return null;
      }

      //read the apps/ports in the file
      Scanner scanner = new Scanner(appInfo);
      while (scanner.hasNextLine()) {
        buf.append(scanner.nextLine());
      }

      JSONObject jObject = new JSONObject(buf.toString());
      JSONArray jArray = jObject.getJSONArray("apps");

      JSONObject appObj;
      ArrayList<String> names = new ArrayList();
      for (int i = 0; i < jArray.length(); i++) {
        appObj = jArray.getJSONObject(i);
        names.add(appObj.getString("image"));
      }

      if (names.isEmpty()) {
        Log.e("Load test names", "No tests configured in " + Config.APPS_FILENAME);
        System.out.println("No tests configured in " + Config.APPS_FILENAME);
        return null;
      }

      return names;
    } catch (IOException e) {
      Log.e(Consts.LOG_APP_NAME, "IOException while reading file " + Config.APPS_FILENAME, e);
    } catch (JSONException e) {
      Log.e(Consts.LOG_APP_NAME, "JSONException while parsing file " + Config.APPS_FILENAME, e);
    }
    return null;
  }
}
