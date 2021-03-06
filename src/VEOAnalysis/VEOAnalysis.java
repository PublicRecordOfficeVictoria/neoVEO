/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 * 20150911 v1.0.1 Set default for output directory to be "."
 * 20180119 v1.1 Provided support for headless mode for new DA
 */
package VEOAnalysis;

import VERSCommon.LTSF;
import VERSCommon.ResultSummary;
import VERSCommon.VEOError;
import VERSCommon.VEOFatal;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Test and visualise VEOs. This class has three functions: it tests VEOs to
 * determine if they conform to the specification; it (optionally) unzips the
 * VEOs; and it (optionally) generates a set of HTML pages detailing the
 * contents of the VEO.
 * <p>
 * The class can be used in two ways: it can be run as a program with options
 * controlled from the command line; or it can be call programatically in two
 * ways as an API.
 * <h1>COMMAND LINE ARGUMENTS</h1>
 * <p>
 * The class has several operating modes which can be used together or
 * separately. These are:
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
 * <li> '-s supportDir': specifies the directory in which the VERS support files
 * (e.g. XML schemas, long term sustainable file) will be found.</li>
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
 * <h1>API</h1>
 * <P>
 * All of the options available on the command line are directly available as an
 * API.
 *
 * @author Andrew Waugh
 */
public class VEOAnalysis {

    String classname = "VEOAnalysis";
    Path supportDir;     // directory in which XML schemas are to be found
    Path outputDir;     // directory in which the VEOs are generated
    boolean chatty;     // true if report when starting a new VEO
    boolean error;      // true if produce an error report
    boolean report;     // true if produce HTML reports
    boolean unpack;     // true if leave the VEO directories after execution
    boolean debug;      // true if debugging information is to be generated
    boolean verbose;    // true if verbose descriptions are to be generated
    boolean norec;      // true if asked to not complain about missing recommended metadata elements
    boolean hasErrors;  // true if VEO had errors
    ArrayList<String> veos; // The list of VEOS to process
    LTSF ltsfs;         // valid long term preservation formats
    private final static Logger LOG = Logger.getLogger("VEOAnalysis.VEOAnalysis");
    private ResultSummary results;  // summary of the errors & warnings

    private final static String USAGE
            = "AnalyseVEOs [-e] [-sr] [-r] [-u] [-v] [-d] [-c] [-norec] -s supportDir [-o outputDir] [files*]";

    /**
     * Instantiate an VEOAnalysis instance to be used as an API. In this mode,
     * VEOAnalysis is called by another program to unpack and validate VEOs.
     * Once an instance of a VEOAnalysis class has been created it can be used
     * to validate multiple VEOs.
     *
     * @param supportDir directory in which VERS3 support information is found
     * @param ltsfs long term sustainable formats
     * @param outputDir directory in which the VEO will be unpacked
     * @param hndlr where to send the LOG reports
     * @param error true if produce a summary error report
     * @param report true if produce HTML reports
     * @param unpack true if leave the VEO directories after execution
     * @param norec true if asked to not complain about missing recommended
     * metadata elements
     * @param chatty true if report when starting a new VEO
     * @param debug true if debugging information is to be generated
     * @param verbose true if verbose descriptions are to be generated
     * @param results if not null, create a summary of the errors & warnings
     * @throws VEOError if something goes wrong
     */
    public VEOAnalysis(Path supportDir, LTSF ltsfs, Path outputDir,
            Handler hndlr, boolean chatty, boolean error, boolean report, boolean unpack,
            boolean debug, boolean verbose, boolean norec, ResultSummary results) throws VEOError {
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

        if (supportDir == null || !Files.isDirectory(supportDir)) {
            throw new VEOError("Specified schema directory is null or is not a directory");
        }
        this.supportDir = supportDir;
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
        hasErrors = false;
        this.ltsfs = ltsfs;
        this.results = results;
    }

    /**
     * Initialise the analysis regime using command line arguments. Note that in
     * this mode *all* of the VEOs to be checked are passed in as command line
     * arguments.
     *
     * @param args the command line arguments
     * @throws VEOError if something goes wrong
     */
    public VEOAnalysis(String args[]) throws VEOError {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s%n");
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

        supportDir = null;
        outputDir = Paths.get(".").toAbsolutePath();
        chatty = false;
        error = false;
        report = false;
        unpack = false;
        debug = false;
        verbose = false;
        norec = false;
        veos = new ArrayList<>();
        ltsfs = null;
        results = null;

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

                    // produce report containing errors and warnings
                    case "-e":
                        error = true;
                        i++;
                        LOG.log(Level.INFO, "Error report mode is selected");
                        break;

                    // produce summary report summarising the errors and warnings
                    case "-sr":
                        error = true;
                        results = new ResultSummary();
                        i++;
                        LOG.log(Level.INFO, "Error and summary report mode is selected");
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
                        outputDir = outputDir.toAbsolutePath();
                        LOG.log(Level.INFO, "Output directory is ''{0}''", outputDir.toString());
                        i++;
                        break;

                    // get support directory
                    case "-s":
                        i++;
                        supportDir = checkFile("support directory", args[i], true);
                        LOG.log(Level.INFO, "support directory is ''{0}''", supportDir.toString());
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
                            throw new VEOFatal(classname, 2, "Unrecognised argument '" + args[i] + "'. Usage: " + USAGE);
                        } else {
                            veos.add(args[i]);
                            i++;
                        }
                }
            }
        } catch (ArrayIndexOutOfBoundsException ae) {
            throw new VEOFatal(classname, 3, "Missing argument. Usage: " + USAGE);
        }

        // check to see that user wants to do something
        if (!error && !report && !unpack) {
            throw new VEOFatal(classname, 5, "Must specify at least one of -e, -r, and -u");
        }

        // check to see that user specified a schema directory
        if (supportDir == null) {
            throw new VEOFatal(classname, 4, "No support directory specified. Usage: " + USAGE);
        }

        // read valid long term preservation formats
        ltsfs = new LTSF(supportDir.resolve("validLTSF.txt"));
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
        return p;
    }

    /**
     * Test the VEOs listed in the command line arguments. You can only use this
     * call if the VEOs have been passed in on the command line.
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
     * Test a VEO. This is an internal private call.
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

        // set this VEO id in the results summary
        if (results != null) {
            results.setId(veo);
        }

        // perform the analysis
        try {
            rv = new RepnVEO(veo, debug, dir, results);
        } catch (VEOError e) {
            System.out.println(e.getMessage());
            return null;
        }
        try {
            // if validating, do so...
            if (error || report) {
                rv.constructRepn(supportDir);
                rv.validate(ltsfs, norec);
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
     * Public subclass to return information about the VEO we just processed.
     */
    public class TestVEOResult {

        public Path veoDir;     // the path of the created VEO directory
        public String uniqueID; // unique id of this VEO (i.e. the B64 encoded signature
        public boolean hasErrors; // true if the VEO had errors
        public String result;   // what happened when processing the VEO

        public TestVEOResult(Path veoDir, String uniqueID, boolean hasErrors, String result) {
            this.veoDir = veoDir;
            this.uniqueID = uniqueID;
            this.hasErrors = hasErrors;
            this.result = result;
        }

        public void free() {
            veoDir = null;
            uniqueID = null;
            result = null;
        }
    }

    /**
     * Test an individual VEO.
     *
     * @param veo the file path of the VEO
     * @param outputDir the directory in which to unpack this VEO
     * @return a structure containing information about the VEO
     * @throws VEOError if something went wrong
     */
    public TestVEOResult testVEO(String veo, Path outputDir) throws VEOError {
        Path p;
        RepnVEO rv;
        TestVEOResult tvr;
        String uniqueId;
        ArrayList<RepnSignature> rs;
        String result;

        // set this VEO id in the results summary
        if (results != null) {
            results.setId(veo);
        }


        // perform the analysis
        hasErrors = false;
        rv = new RepnVEO(veo, debug, outputDir, results);
        result = null;
        try {
            // if validating, do so...
            if (error || report) {
                rv.constructRepn(supportDir);
                rv.validate(ltsfs, norec);
            }

            // if generating HTML report, do so...
            if (report) {
                rv.genReport(verbose);
            }

            // if in error mode, print the results for this VEO
            if (error) {
                result = rv.getStatus();
                // LOG.log(Level.WARNING, rv.getStatus());
            }

        } finally {
            hasErrors = rv.hasErrors();

            // if VEO had at least one signature, get it...
            rs = rv.veoContentSignatures;
            uniqueId = null;
            if (rs != null) {
                if (rs.size() >= 1) {
                    uniqueId = rs.get(0).signature.getValue();
                }
            }

            tvr = new TestVEOResult(rv.getVEODir(), uniqueId, hasErrors, result);

            // delete the unpacked VEO
            if (!unpack && !report) {
                rv.deleteVEO();
            }

            // clean up
            rv.abandon();
        }
        return tvr;
    }

    /**
     * Was the VEO error free?
     *
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
     * Write a result summary on the specified Writer. Nothing will be reported
     * unless the '-rs' flag was used when instantiating the class (or a
     * ResultSummary passed).
     * 
     * @param w the writer
     * @throws VEOError if something failed
     */
    public void resultSummary(Writer w) throws VEOError {
        BufferedWriter bw;

        if (results != null) {
            bw = new BufferedWriter(w);
            try {
                results.report(bw);
                bw.close();
            } catch (IOException ioe) {
                throw new VEOError("Error producing summary report: " + ioe.getMessage() + " (VEOAnalysis.report()");
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
            LOG.log(Level.SEVERE, USAGE);
        }
        try {
            va = new VEOAnalysis(args);
            va.test();
            va.resultSummary(new OutputStreamWriter(System.out));
        } catch (VEOFatal e) {
            LOG.log(Level.SEVERE, e.getMessage());
        } catch (VEOError e) {
            LOG.log(Level.WARNING, e.getMessage());
        }
    }
}
