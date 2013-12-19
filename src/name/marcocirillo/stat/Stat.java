package name.marcocirillo.stat;

import java.io.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: Marco Cirillo
 * Email: cirillom@yorku.ca
 * <p/>
 * Description
 * <p/>
 * <p/>
 * <p/>
 * Created with IntelliJ IDEA.
 */
public class Stat {

    private final String name;
    private final String process;
    private final Map<Date, Double> values;

    public Stat(String process, String name) {
        this.process = process;
        this.name = name;
        this.values = new HashMap<Date, Double>();
    }

    public void addValue(Date time, Double value) {
        this.values.put(time, value);
    }

    public Map<Date, Double> getValues() {
        return this.values;
    }

    public String getName() {
        return this.name;
    }

    public String getProcess() {
        return this.process;
    }

    @Override
    public String toString() {
        String result = "Stat [process=" + getProcess() + " name=" + getName() + " values=[";

        // Show values
        for (Map.Entry<Date, Double> item : getValues().entrySet()) {
            result += item.getKey() + "=" + item.getValue() + ", ";
        }

        // Remove trailing comma and space and add square closing brackets
        result = result.substring(0, result.length() - 2) + "]]";

        return result;
    }

    public static List<Stat> createFromInput(File input)
            throws IOException, IllegalArgumentException, UnsupportedOperationException {

        // Verify the file is okay to use
        if (!input.exists() || input.length() == 0) {
            throw new IllegalArgumentException("Input File is invalid.");
        }

        // Handle input file types primitively (by extension)
        if (getExtension(input).equalsIgnoreCase("blg")) {
            return createFromBLG(input);
        } else {
            // TODO: Handle more file types
            throw new UnsupportedOperationException("Currently, only Windows BLG files are supported.");
        }
    }

    private static String getExtension(File input) {
        return input.getName().substring(input.getName().indexOf(".") + 1);
    }

    private static List<Stat> createFromBLG(File input) throws IOException {
        // Currently windows only, as relog is required
        if (!System.getProperty("os.name").contains("Windows"))
            throw new UnsupportedOperationException("Linux input is not implemented yet.");

        List<Stat> result = new ArrayList<Stat>();

        // Parse CSV for Stats

        // Create input stream, throws FileNotFoundException if input cannot be found
        BufferedReader in = new BufferedReader(new FileReader(BLGtoCSV(input)));

        // Read and create Stat objects
        String line = in.readLine();
        if (line == null) throw new IllegalArgumentException("Could not parse CSV file for Stats");

        String[] names = line.split(",");       // split along commans
        for (int i = 1; i < names.length; i++) {// skip first column (header for timestamps)

            // Match \\COMPUTER\Process(processname)\Attribute via the parenthesis
            Matcher match = Pattern.compile("\\([^)]+\\)").matcher(names[i]);
            if (match.find()) {
                result.add( new Stat(
                        match.group().replace("(", "").replace(")",""), // remove parenthesis when adding
                        names[i].substring(names[i].lastIndexOf("\\") + 1)
                ));
            } else {
                throw new IllegalArgumentException("Could not parse CSV file for Stats");
            }
        }

        // Read values into Stats
        while ( null != ( line = in.readLine() ) ) {

            String[] values   = line.split(",");

            // example: 12/05/2013 19:40:44.951
            DateFormat format = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS");
            Date time = null;
            try {
                time = format.parse( values[0].replaceAll("\"","") );
            } catch (ParseException e) {
                System.out.println("Could not parse Date from CSV");
                e.printStackTrace();
            }

            for (int i = 1; i < result.size(); i++) {
                try {
                    // Add to Stat, removing quotes around each entry
                    result.get(i).addValue( time, Double.parseDouble( values[i].replaceAll("\"", "") ));
                } catch (NumberFormatException e) {
                    // Account for "", which means no stat was reported for that time
                    result.get(i).addValue( time, 0d );
                }
            }
        }

        return result;
    }

    private static File BLGtoCSV(File blg) throws IOException {
        // Build CSV from BLG

        String filename = blg.getName().substring(0, blg.getName().length() - 4);
        File csv = File.createTempFile(filename,".csv");

        String[] cmd = {
                "relog",
                "-i", blg.getAbsolutePath(),
                "-f", "csv",
                "-o", csv.getAbsolutePath(),
                "-y"
        };

        try {
            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
        } catch (InterruptedException e) {
            System.out.println("Conversion from BLG to CSV was prematurely interrupted.");
            e.printStackTrace();
        }

        return csv;
    }

    public static List<Stat> collect(File outputFile) throws IOException {
        // Currently *nix only, as pidstat is required
        if ((System.getProperty("os.name").contains("Windows")))
            throw new UnsupportedOperationException("Windows input is not implemented yet.");

        List<Stat> result = new ArrayList<Stat>();

        String[] cmd = {
                "pidstat",
                "-d", "-r", "-u", "-h",
                "5" // TODO: Make resolution modifiable
        };

        try {
            Process p = Runtime.getRuntime().exec(cmd);
            OutputStream in = p.getOutputStream();
            if (null == outputFile) {
                BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));

                String line;
                while(( line = input.readLine() ) != null){

                    // parse data from pidstat output
                    String[] data = line.split("\\s+");

                    int time = 0;
                    try {
                        time = Integer.parseInt(data[1]);
                    } catch (Exception e) {
                        // Not a number, disregard this line
                        continue;
                    }

                    Stat cpu, ram, hdd;
                    Stat[] all;
                    if ((all = hasProcess(result, data[16])) != null) {
                        // Append to existing process

                        cpu = all[0];
                        ram = all[1];
                        hdd = all[2];

                    } else {
                        // create a new Stat for a new process

                        cpu = new Stat(data[16], "CPU");
                        ram = new Stat(data[16], "RAM");
                        hdd = new Stat(data[16], "HDD");

                        result.add(cpu);
                        result.add(ram);
                        result.add(hdd);
                    }

                    // Add new Stat values
                    cpu.addValue(new Date(time), Double.parseDouble(data[6]));
                    ram.addValue(new Date(time), Double.parseDouble(data[10]));
                    hdd.addValue(
                            new Date(time),
                            Double.parseDouble(data[13]) +
                                    Double.parseDouble(data[14]) +
                                    Double.parseDouble(data[15])
                    );

                    for (Stat s : result) System.out.println(s);
                }

                input.close();

            } /*else {

            }*/
            p.waitFor();
        } catch (/*Interrupted*/Exception e) {
            System.out.println("Collection interrupted.");
            e.printStackTrace();
        }

        return result;
    }

    private static Stat[] hasProcess(List<Stat> stats, String processName) {
        List<Stat> result = new ArrayList<Stat>();
        for (Stat s : stats) if (s.getProcess().equalsIgnoreCase(processName)) result.add(s);

        return result.size() > 0 ? result.toArray(new Stat[1]) : null ;
    }
}
