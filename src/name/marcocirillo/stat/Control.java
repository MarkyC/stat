package name.marcocirillo.stat;

import java.io.File;
import java.util.EnumMap;
import java.util.List;

/**
 * User: Marco
 * Email: cirillom@yorku.ca
 * Date: 12/17/13 5:04 PM.
 * <p/>
 * Description
 * <p/>
 * <p/>
 * <p/>
 * Created with IntelliJ IDEA.
 */
public class Control {

    private static File inputFile;
    private static File outputFile;
    private static List<Stat> stats;

    private static EnumMap<Arguments, String> arguments;

    /**
     * All the arguments available to the program.
     * These can be passed as a key to getArgument(
     */
    public static enum Arguments {
        INPUT, OUTPUT, LIVE
    }

    private static boolean addArg(Arguments key, String val) {
        arguments.put(key, val);
        return !((val == null) || (val.length() == 0));
    }

    private static boolean parseArgs(String[] args) {
        arguments = new EnumMap<Arguments, String>(Arguments.class);

        for (int i = 0; i < args.length; i+=2) {
            switch (args[i].charAt(0)) {
                // We parse arguments by checking for -'s representing the start of an arg
                case '-':
                    if (args[i].length() < 2) return false;

                    // Purposely not using Java 7's String switches for backwards compatibility
                    String s = args[i].substring(1);
                    if (s.equals("i")) {
                        if (!addArg(Arguments.INPUT, args[i+1])) return false;
                    } else if (s.equals("o")) {
                        if (!addArg(Arguments.OUTPUT, args[i+1])) return false;
                    } else if (s.equals("live")) {
                        if (!addArg(Arguments.LIVE, "live")) return false;
                    } else {
                        return false;
                    }
                break;

                default:
                   return false;
            }
        }

        return arguments.containsKey(Arguments.INPUT) || arguments.containsKey(Arguments.LIVE);
    }

    public static void printUsage() {
        System.out.println("Usage: java -jar stat.jar [-i <InputFile.blg>] [-live] [-o <OutputFile.csv>]");
        System.out.println("       Either -live must be chosen (for live monitoring of the system) ");
        System.out.println("       or -i <InputFile.blg> (for creating output from an input file)  ");
        System.out.println("       must be used. If an output file is not specified, the results   ");
        System.out.println("       outputted to stdout                                             ");
    }

    public static void setInputFile(String filename) {
        inputFile = (filename != null) ? new File(filename) : null;
    }

    public static void setOutputFile(String filename) {
        outputFile = (filename != null) ? new File(filename) : null;
    }

    public static void main(String[] args) {

        if (!parseArgs(args)) exit(true);

        setInputFile(arguments.get(Arguments.INPUT));
        setOutputFile(arguments.get(Arguments.OUTPUT));

        if (arguments.containsKey(Arguments.INPUT)) {
            // Input File Mode
            try {
                stats = Stat.createFromInput(inputFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Live Mode
            try {
                stats = Stat.collect(outputFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        for (Stat s : stats) {
            System.out.println(s);
        }

    }

    public static void exit(boolean printUsage) {
        if (printUsage) printUsage();
        System.exit(1);
    }
}
