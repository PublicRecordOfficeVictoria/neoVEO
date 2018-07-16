/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 * 20150911 v1.0.1 Set default for output directory to be "."
 * 20180119 v1.1 Provided support for headless mode for new DA
 */
package VEOAnalysis;

import VERSCommon.VEOError;
import VERSCommon.VEOFatal;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Test and visualise VEOs. The class has several operating modes which can be
 * used together or separately. These are:
 * <ul>
 * <li>'-e': produce a summary of the errors and warnings found in the listed
 * VEOs on standard out. The VEO directories are removed after execution unless
 * the '-u' argument is specified.</li>
 * <li>'-r': unpack the VEOs into VEO directories and include a full report
 * expressed as HTML files in the VEO directory. The VEO directories remain
 * after execution. The '-u' argument is ignored.</li>
 * <li>'-u': just unpack the VEO into VEO directories. No summary or report is
 * produced unless one of '-e' or '-r' is present.
 * </ul>
 * The default mode is '-r' if none of these arguments are specified. The
 * mandatory command line arguments are:
 * <ul>
 * <li> '-s schemaDir': specifies the directory in which the XML schemas will be
 * found.</li>
 * <li> list of VEOs (or directories of VEOs) to process.</li>
 * </ul>
 * The other optional command line arguments are:
 * <ul>
 * <li>'-c': chatty mode. Report on stderr when a new VEO is commenced.
 * <li>'-v': verbose output. Include additional details in the report generated
 * by the '-r' option.</li>
 * <li>'-d': debug output. Include lots more detail - mainly intended to debug
 * problems with the program.</li>
 * <li>'-o directory'. Create the VEO directories in this output directory</li>
 * </ul>
 *
 * @author Andrew Waugh
 */
public class VEOAnalysis {

    String classname = "VEOAnalysis";
    Path schemaDir;     // directory in which XML schemas are to be found
    Path outputDir;     // directory in which the VEOs are generated
    boolean chatty;     // true if report when starting a new VEO
    boolean error;      // true if produce a summary error report
    boolean report;     // true if produce HTML reports
    boolean unpack;     // true if leave the VEO directories after execution
    boolean debug;      // true if debugging information is to be generated
    boolean verbose;    // true if verbose descriptions are to be generated
    boolean norec;      // true if asked to not complain about missing recommended metadata elements
    boolean hasErrors;  // true if VEO had errors
    ArrayList<String> veos; // The representation of the signature files
    HashMap<String, String> ltpfs; // valid long term preservation formats
    private final static Logger LOG = Logger.getLogger("VEOAnalysis.VEOAnalysis");

    /**
     * Initialise the analysis regime for the headless mode. In this mode,
     * VEOAnalysis is called by another program to unpack and validate the VEO.
     *
     * @param schemaDir directory in which VERS3 schema information is found
     * @param outputDir directory in which the VEO will be unpacked
     * @param hndlr where to send the LOG reports
     * @param chatty true if report when starting a new VEO
     * @param error true if produce a summary error report
     * @param report true if produce HTML reports
     * @param unpack true if leave the VEO directories after execution
     * @param debug true if debugging information is to be generated
     * @param verbose true if verbose descriptions are to be generated
     * @param norec true if asked to not complain about missing recommended
     * metadata elements
     * @throws VEOError if something goes wrong
     */
    public VEOAnalysis(Path schemaDir, Path outputDir,
            Handler hndlr, boolean chatty, boolean error, boolean report, boolean unpack,
            boolean debug, boolean verbose, boolean norec) throws VEOError {
        Handler h[];
        int i;

        // remove any handlers associated with the LOG & LOG messages aren't to
        // go to the parent
        h = LOG.getHandlers();
        for (i = 0; i < h.length; i++) {
            LOG.removeHandler(h[i]);
        }
        LOG.setUseParentHandlers(false);

        // add LOG handler from calling program
        LOG.addHandler(hndlr);

        // default logging
        LOG.getParent().setLevel(Level.WARNING);
        LOG.setLevel(null);

        if (schemaDir == null || !Files.isDirectory(schemaDir)) {
            throw new VEOError("Specified schema directory is null or is not a directory");
        }
        this.schemaDir = schemaDir;
        if (outputDir == null || !Files.isDirectory(outputDir)) {
            throw new VEOError("Specified output directory is null or is not a directory");
        }
        this.outputDir = outputDir;
        this.chatty = chatty;
        this.error = error;
        this.report = report;
        this.unpack = unpack;
        this.verbose = verbose;
        if (verbose) {
            LOG.getParent().setLevel(Level.INFO);
        }
        this.debug = debug;
        if (debug) {
            LOG.getParent().setLevel(Level.FINE);
        }
        this.norec = norec;
        veos = null;
        ltpfs = new HashMap<>();
        hasErrors = false;
        readValidLTPFs(schemaDir);
    }

    /**
     * Initialise the analysis regime.
     *
     * @param args the command line arguments
     * @throws VEOError if something goes wrong
     */
    public VEOAnalysis(String args[]) throws VEOError {
        LOG.getParent().setLevel(Level.WARNING);
        LOG.setLevel(null);
        configure(args);
    }

    /**
     * This method configures the VEO analysis from the arguments on the command
     * line. See the comment at the start of this file for the command line
     * arguments.
     *
     * @param args[] the command line arguments
     * @throws VEOFatal if any errors are found in the command line arguments
     */
    private void configure(String args[]) throws VEOFatal {
        int i;
        String usage = "AnalyseVEOs [-e] [-r] [-u] [-v] [-d] [-c] [-norec] -s schemaDir [-o outputDir] [files*]";

        schemaDir = null;
        outputDir = Paths.get(".");
        chatty = false;
        error = false;
        report = false;
        unpack = false;
        debug = false;
        verbose = false;
        norec = false;
        veos = new ArrayList<>();
        ltpfs = new HashMap<>();

        // process command line arguments
        i = 0;
        try {
            while (i < args.length) {
                switch (args[i].toLowerCase()) {
                    // if chatty mode...
                    case "-c":
                        chatty = true;
                        i++;
                        LOG.log(Level.INFO, "Report when staring new VEO mode is selected");
                        break;

                    // if debugging...
                    case "-d":
                        debug = true;
                        i++;
                        LOG.getParent().setLevel(Level.FINE);
                        LOG.log(Level.INFO, "Debug mode is selected");
                        break;

                    // produce summary report containing errors and warnings
                    case "-e":
                        error = true;
                        i++;
                        LOG.log(Level.INFO, "Summary report mode is selected");
                        break;

                    // do not complain about missing recommended metadata elements
                    case "-norec":
                        norec = true;
                        i++;
                        LOG.log(Level.INFO, "Do not complain about missing recommended metadata elements");
                        break;

                    // get output directory
                    case "-o":
                        i++;
                        outputDir = checkFile("output directory", args[i], true);
                        LOG.log(Level.INFO, "Output directory is ''{0}''", outputDir.toString());
                        i++;
                        break;

                    // get schema directory
                    case "-s":
                        i++;
                        schemaDir = checkFile("schema directory", args[i], true);
                        LOG.log(Level.INFO, "Schema directory is ''{0}''", schemaDir.toString());
                        i++;
                        break;

                    // produce HMTL report for each VEO
                    case "-r":
                        report = true;
                        i++;
                        LOG.log(Level.INFO, "Produce HTML report for each VEO mode is selected");
                        break;

                    // leave unpacked VEOs after the run
                    case "-u":
                        unpack = true;
                        i++;
                        LOG.log(Level.INFO, "Leave unpacked VEOs after each run");
                        break;

                    // if verbose...
                    case "-v":
                        verbose = true;
                        i++;
                        LOG.getParent().setLevel(Level.INFO);
                        LOG.log(Level.INFO, "Verbose output is selected");
                        break;

                    // otherwise, check if it starts with a '-' and complain, otherwise assume it is a VEO pathname
                    default:
                        if (args[i].startsWith("-")) {
                            throw new VEOFatal(classname, 2, "Unrecognised argument '" + args[i] + "'. Usage: " + usage);
                        } else {
                            veos.add(args[i]);
                            i++;
                        }
                }
            }
        } catch (ArrayIndexOutOfBoundsException ae) {
            throw new VEOFatal(classname, 3, "Missing argument. Usage: " + usage);
        }

        // check to see that user wants to do something
        if (!error && !report && !unpack) {
            throw new VEOFatal(classname, 5, "Must specify at least one of -e, -r, and -u");
        }

        // check to see that user specified a schema directory
        if (schemaDir == null) {
            throw new VEOFatal(classname, 4, "No schema directory specified. Usage: " + usage);
        }

        // read valid long term preservation formats
        readValidLTPFs(schemaDir);
    }

    /**
     * Check a file to see that it exists and is of the correct type (regular
     * file or directory). The program terminates if an error is encountered.
     *
     * @param type a String describing the file to be opened
     * @param name the file name to be opened
     * @param isDirectory true if the file is supposed to be a directory
     * @throws VEOFatal if the file does not exist, or is of the correct type
     * @return the File opened
     */
    private Path checkFile(String type, String name, boolean isDirectory) throws VEOFatal {
        Path p;

        String safe = name.replaceAll("\\\\", "/");
        p = Paths.get(safe);

        if (!Files.exists(p)) {
            throw new VEOFatal(classname, 6, type + " '" + p.toAbsolutePath().toString() + "' does not exist");
        }
        if (isDirectory && !Files.isDirectory(p)) {
            throw new VEOFatal(classname, 7, type + " '" + p.toAbsolutePath().toString() + "' is a file not a directory");
        }
        if (!isDirectory && Files.isDirectory(p)) {
            throw new VEOFatal(classname, 8, type + " '" + p.toAbsolutePath().toString() + "' is a directory not a file");
        }
        LOG.log(Level.INFO, "{0} is ''{1}''", new Object[]{type, p.toAbsolutePath().toString()});
        return p;
    }

    /**
     * Test the VEOs listed in the command line argument...
     *
     * @throws VEOError if a fatal error occurred
     */
    public void test() throws VEOError {
        int i;
        String veo;
        DirectoryStream<Path> ds;
        Path veoFile;

        // go through the list of VEOs
        for (i = 0; i < veos.size(); i++) {
            veo = veos.get(i);
            if (veo == null) {
                continue;
            }
            String safe = veo.replaceAll("\\\\", "/");

            // if veo is a directory, go through directory and test all the VEOs
            // otherwise just test the VEO
            try {
                veoFile = Paths.get(safe);
            } catch (InvalidPathException ipe) {
                System.out.println("Failed trying to open file '" + safe + "': " + ipe.getMessage());
                continue;
            }
            if (Files.isDirectory(veoFile)) {
                try {
                    ds = Files.newDirectoryStream(veoFile);
                    for (Path p : ds) {
                        if (Files.isRegularFile(p) && p.toString().toLowerCase().endsWith(".veo.zip")) {
                            testVEOint(p.toString(), outputDir);
                        }
                    }
                    ds.close();
                } catch (IOException e) {
                    System.out.println("Failed to process directory '" + safe + "': " + e.getMessage());
                }
            } else {
                testVEOint(veo, outputDir);
            }
        }
    }

    /**
     * Test a specific VEO for the internal
     *
     * @param veo the file name of the zip file containing the VEO
     * @param dir the directory in which to unpack this VEO
     * @return the path of the created VEO directory
     * @throws VEOError if something went wrong
     */
    private Path testVEOint(String veo, Path dir) throws VEOError {
        Path p;
        RepnVEO rv;

        // report time and VEO if in chatty mode
        if (chatty && !error) {
            System.out.println((System.currentTimeMillis() / 1000) + ": " + veo);
        }

        // if in error mode, print the header for this VEO
        if (error) {
            printHeader(veo);
        }

        // perform the analysis
        try {
            rv = new RepnVEO(veo, debug, dir);
        } catch (VEOError e) {
            System.out.println(e.getMessage());
            return null;
        }
        try {
            // if validating, do so...
            if (error || report) {
                rv.constructRepn(schemaDir);
                rv.validate(ltpfs, norec);
            }

            // if generating HTML report, do so...
            if (report) {
                rv.genReport(verbose);
            }

            // if in error mode, print the results for this VEO
            if (error) {
                System.out.println(rv.getStatus());
            }
        } catch (VEOError e) {
            System.out.println(e.getMessage());
        } finally {
            if (rv.hasErrors()) {
                hasErrors = true;
            }
            
            p = rv.getVEODir();

            // delete the unpacked VEO
            if (!unpack && !report) {
                rv.deleteVEO();
            }

            // clean up
            rv.abandon();
        }
        return p;
    }

    /**
     * Test a specific VEO called programmatically
     *
     * @param veo the file name of the zip file containing the VEO
     * @param dir the directory in which to unpack this VEO
     * @return the path of the created VEO directory
     * @throws VEOError if something went wrong
     */
    public Path testVEO(String veo, Path dir) throws VEOError {
        Path p;
        RepnVEO rv;

        // perform the analysis
        hasErrors = false;
        rv = new RepnVEO(veo, debug, dir);
        try {
            // if validating, do so...
            if (error || report) {
                rv.constructRepn(schemaDir);
                rv.validate(ltpfs, norec);
            }

            // if generating HTML report, do so...
            if (report) {
                rv.genReport(verbose);
            }

            // if in error mode, print the results for this VEO
            if (error) {
                LOG.log(Level.INFO, rv.getStatus());
            }

        } finally {
            hasErrors = rv.hasErrors();
            
            p = rv.getVEODir();

            // delete the unpacked VEO
            if (!unpack && !report) {
                rv.deleteVEO();
            }

            // clean up
            rv.abandon();
        }
        return p;
    }
    
    /**
     * Was the VEO error free?
     * @return true if a VEO had errors
     */
    public boolean isErrorFree() {
        return !hasErrors;
    }

    /**
     * Print a header about this VEO test on the standard output
     *
     * @param veo The VEO.veo.zip file being tested
     */
    private void printHeader(String veo) {
        SimpleDateFormat sdf;
        TimeZone tz;

        System.out.println("******************************************************************************");
        System.out.println("*                                                                            *");
        System.out.println("*                     V E O   A N A L Y S I S   T O O L                      *");
        System.out.println("*                                                                            *");
        System.out.println("*                                Version 2.0                                 *");
        System.out.println("*               Copyright 2015 Public Record Office Victoria                 *");
        System.out.println("*                                                                            *");
        System.out.println("******************************************************************************");
        System.out.println("");
        System.out.print("VEO analysed: '" + veo + "' at ");
        tz = TimeZone.getTimeZone("GMT+10:00");
        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss+10:00");
        sdf.setTimeZone(tz);
        System.out.println(sdf.format(new Date()));
        System.out.println("");
    }

    /**
     * Read a file containing a list of accepted LTPFs. The file is
     * 'validLTPF.txt' located in schemaDir. The file will contain multiple
     * lines. Each line will contain one file format extension string (e.g.
     * '.pdf').
     *
     * @param schemaDir the directory in which the file is to be found
     * @throws VEOFatal if the file could not be read
     */
    private void readValidLTPFs(Path schemaDir) throws VEOFatal {
        String method = "readValidLTPF";
        Path f;
        FileReader fr;
        BufferedReader br;
        String s;

        f = schemaDir.resolve("validLTPF.txt");

        // open validLTPF.txt for reading
        fr = null;
        br = null;
        try {
            fr = new FileReader(f.toString());
            br = new BufferedReader(fr);

            // go through validLTPF.txt line by line, copying patterns into hash map
            // ignore lines that do begin with a '!' - these are comment lines
            while ((s = br.readLine()) != null) {
                s = s.trim();
                if (s.length() == 0 || s.charAt(0) == '!') {
                    continue;
                }
                ltpfs.put(s, s);
            }
        } catch (FileNotFoundException e) {
            throw new VEOFatal(classname, method, 2, "Failed to open LTPF file '" + f.toAbsolutePath().toString() + "'" + e.toString());
        } catch (IOException ioe) {
            throw new VEOFatal(classname, method, 1, "unexpected error: " + ioe.toString());
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    /* ignore */ }
            }
            if (fr != null) {
                try {
                    fr.close();
                } catch (IOException e) {
                    /* ignore */ }
            }
        }
    }

    /**
     * Main entry point for the VEOAnalysis program.
     *
     * @param args A set of command line arguments. See the introduction for
     * details.
     */
    public static void main(String args[]) {
        VEOAnalysis va;

        if (args.length == 0) {
            args = new String[]{"-r", "-s", "../VERS Std 2013/Release/test/neoVEOSchemas", "-o", "../VERS Std 2013/Release/test/testOutput", "../VERS Std 2013/Release/test/testOutput/demoVEO1.veo.zip"};
            // args = new String[]{"-r", "-s", "Test/Demo/Schemas", "-o", "../neoVEOOutput/TestAnalysis", "../neoVEOOutput/TestAnalysis/parseError.veo.zip"};
        }
        try {
            va = new VEOAnalysis(args);
            va.test();
        } catch (VEOFatal e) {
            LOG.log(Level.SEVERE, e.getMessage());
        } catch (VEOError e) {
            LOG.log(Level.WARNING, e.getMessage());
        }
    }
}
