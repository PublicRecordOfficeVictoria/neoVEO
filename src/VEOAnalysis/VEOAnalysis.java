/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
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
import java.nio.charset.Charset;
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
 * <li>'-iocnt'. Report on the number of IOs in the VEO</li>
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
    boolean reportIOcnt;// true if requested to report on number of IOs in VEO
    int totalIOs;       // total IOs counted in VEO
    boolean help;       // if true, generate a help summary of command line arguements
    ArrayList<String> veos; // The list of VEOS to process
    LTSF ltsfs;         // valid long term preservation formats
    private final static Logger LOG = Logger.getLogger("VEOAnalysis.VEOAnalysis");
    private ResultSummary results;  // summary of the errors & warnings

    private final static String USAGE
            = "AnalyseVEOs [-help] [-e] [-sr] [-r|-u] [-v] [-d] [-c] [-iocnt] [-norec] -s supportDir [-o outputDir] [files*]";

    /**
     * Report on version...
     *
     * <pre>
     * 201502   1.0 Initial release
     * 20150911 1.1 Set default for output directory to be "."
     * 20180119 2.1 Provided support for headless mode for new DA
     * 20180711 2.1 Fixed bug extracting ZIP files
     * 20180716 2.2 Handles Windows style filenames in UNIX environment
     * 20191007 2.3 Improved signature error messages and removed redundant code
     * 20191024 2.4 Ensure that unpacking cannot write anywhere in file system & minor bug fixes & improvements
     * 20191122 2.5 Minor bug fixes (see GIT log)
     * 20191209 2.6 Cleaned up libraries
     * 20200220 2.7 Fixed bug re non RDF metadata packages
     * 20200414 3.0 Packaged for release. Lots of minor alterations
     * 20200620 3.1 Made checking of VEOReadme.txt more flexible
     * 20200716 3.2 V2 & V3 now use a common code base for checking LTSF
     * 20200816 3.3 Improved checks to ensure ZIP not creating files anywhere in file system
     * 20200306 3.4 Added result summary report option
     * 20210407 3.5 Standardised reporting of run, added versions
     * 20210625 3.6 Added additional valid metadata package schemas
     * 20210709 3.7 Change Base64 handling routines & provided support for PISA
     * 20210927 3.8 Updated standard metadata package syntax ids
     * 20211117 3.9 Fixed bug in RepnVEO that crashed if couldn't decode a certificate
     * 20211201 3.10 Adjusted some AGLS namespace prefixes to conform with standard
     * 20220107 3.11 Upgraded Jena4 & Log4j to deal with Log4j security issue
     * 20220107 3.12 Will now accept, but warn, if the five elements with the incorrect namespace prefixes are present
     * 20220124 3.13 Moved to using Apache ZIP
     * 20220127 3.14 Now test in RepnMetadataPackage if vers:MetadataPackage includes RDF namespace if syntax is RDF
     * 20220127 3.15 Now reports on the number of IOs in VEO
     * 20220214 3.16 xmlns:rdf namespace can be defined in any of the top level elements
     * 20220310 3.17 Don't assume metadata package is RDF if xmlns:rdf is defined
     * 20220314 3.18 Rejigged reports for IOs so that they are a linked structure rather than one document
     * 20220315 3.19 Added total count of IOs generated in run
     * 20220408 3.20 Forced reading of XML files to be UTF-8 & output of HTML files to be UTF-8
     * 20220422 3.21 Provided option to use JDK8/Jena2/Log4j or JDK11/Jena4/Log4j2. Updated to the last version of Jena2.
     * 20220520 3.22 Changed to catch invalid file names (e.g. Paths.get() & in resolve())
     * 20220615 3.23 Added 4746 & 5062 to the valid VEOReadme.txt file sizes
     * </pre>
     */
    static String version() {
        return ("3.23");
    }

    static String copyright = "Copyright 2015, 2022 Public Record Office Victoria";

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
     * @param results if not null, create a summary of the errors &amp; warnings
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
        this.help = false;
        this.reportIOcnt = false;
        this.totalIOs = 0;
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
        SimpleDateFormat sdf;
        TimeZone tz;

        // configure the run
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s%n");
        LOG.getParent().setLevel(Level.WARNING);
        LOG.setLevel(null);
        configure(args);

        // say what we are doing
        System.out.println("******************************************************************************");
        System.out.println("*                                                                            *");
        System.out.println("*                 V E O ( V 3 )   A N A L Y S I S   T O O L                  *");
        System.out.println("*                                                                            *");
        System.out.println("*                                Version " + version() + "                                *");
        System.out.println("*               " + copyright + "                 *");
        System.out.println("*                                                                            *");
        System.out.println("******************************************************************************");
        System.out.println("");
        System.out.print("Run at ");
        tz = TimeZone.getTimeZone("GMT+10:00");
        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss+10:00");
        sdf.setTimeZone(tz);
        System.out.println(sdf.format(new Date()));
        System.out.println("");

        // "AnalyseVEOs [-e] [-sr] [-r] [-u] [-v] [-d] [-c] [-norec] -s supportDir [-o outputDir] [files*]
        if (help) {
            System.out.println("Command line arguments:");
            System.out.println(" Mandatory:");
            System.out.println("  one or more VEO files, or directories where VEOs are to be found");
            System.out.println("  -s <support directory>: file path to where the support files are located");
            System.out.println("");
            System.out.println(" Optional:");
            System.out.println("  -e: generate a list of errors and warnings as each VEO is processed");
            System.out.println("  -sr: as for -e, but also generate a summary report of all the unique errors and warnings");
            System.out.println("  -r: generate a HTML report describing each VEO (implies '-u')");
            System.out.println("  -u: leave the unpacked VEOs in the file system at the end of the run");
            System.out.println("  -norec: do not warn about missing recommended metadata elements");
            System.out.println("  -o <directory>: the directory in which the VEOs are unpacked");
            System.out.println("  -iocnt: report on the number of IOs in VEO");
            System.out.println("");
            System.out.println("  -c: chatty mode: report when starting a new VEO when using -r or -u");
            System.out.println("  -v: verbose mode: give more details about processing");
            System.out.println("  -d: debug mode: give a lot of details about processing");
            System.out.println("  -help: print this listing");
            System.out.println("");
        }

        // check to see that user wants to do something
        if (!error && !report && !unpack) {
            throw new VEOFatal(classname, 5, "Must specify at least one of -e, -r, and -u. Usage: " + USAGE);
        }

        // check to see that user specified a support directory
        if (supportDir == null) {
            throw new VEOFatal(classname, 4, "No support directory specified. Usage: " + USAGE);
        }

        // read valid long term preservation formats
        ltsfs = new LTSF(supportDir.resolve("validLTSF.txt"));

        // report on what has been asked to do
        System.out.println("Output mode:");
        if (error || results != null) {
            System.out.println(" Report on each VEO processed, including any errors or warnings (-e or -sr set)");
            if (norec) {
                System.out.println(" Do not warn about missing recommended metadata elements (-norec set)");
            } else {
                System.out.println(" Warn about missing recommended metadata elements");
            }
        } else {
            System.out.println(" Do not list VEOs as they are processed, nor on any errors and warnings (-e not set)");
        }
        if (report) {
            System.out.println(" Unpack each VEO and produce a HTML report for each VEO processed (-r set)");
            if (!error) {
                if (norec) {
                    System.out.println(" Do not warn about missing recommended metadata elements (-norec set)");
                } else {
                    System.out.println(" Warn about missing recommended metadata elements");
                }
                if (chatty) {
                    System.out.println(" Report processing each VEO (-c set)");
                } else {
                    System.out.println(" Do not report processing each VEO (-c not set)");
                }
            }
        } else if (unpack) {
            System.out.println(" Leave an unpacked copy of each VEO processed (-u set)");
            if (!error) {
                if (chatty) {
                    System.out.println(" Report processing each VEO (-c set)");
                } else {
                    System.out.println(" Do not report processing each VEO (-c not set)");
                }
            }
        } else {
            System.out.println(" Do not unpack or produce a final HTML report for each VEO processed (neither -u or -r set)");
        }
        if (reportIOcnt) {
            System.out.println(" Report on number of IOs in VEO (-iocnt set)");
        }
        if (results != null) {
            System.out.println(" Produce a summary report of errors and warnings at the end (-sr set)");
        } else {
            System.out.println(" Do not produce a summary report of errors and warnings at the end (-sr not set)");
        }

        System.out.println("Configuration:");
        System.out.println(" Output directory: " + outputDir.toString());
        if (supportDir != null) {
            System.out.println(" Support directory: " + supportDir.toString());
        } else {
            System.out.println(" Support directory is not set");
        }

        if (debug) {
            System.out.println(" Debug mode is selected");
        }
        if (verbose) {
            System.out.println(" Verbose output is selected");
        }
        System.out.println("");
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
        help = false;
        reportIOcnt = false;
        totalIOs = 0;

        // process command line arguments
        i = 0;
        try {
            while (i < args.length) {
                switch (args[i].toLowerCase()) {
                    // if chatty mode...
                    case "-c":
                        chatty = true;
                        i++;
                        break;

                    // if debugging...
                    case "-d":
                        debug = true;
                        i++;
                        LOG.getParent().setLevel(Level.FINE);
                        break;

                    // write a summary of the command line options to the std out
                    case "-help":
                        help = true;
                        i++;
                        break;

                    // produce report containing errors and warnings
                    case "-e":
                        error = true;
                        i++;
                        break;

                    // produce summary report summarising the errors and warnings
                    case "-sr":
                        error = true;
                        results = new ResultSummary();
                        i++;
                        break;

                    // do not complain about missing recommended metadata elements
                    case "-norec":
                        norec = true;
                        i++;
                        break;

                    // get output directory
                    case "-o":
                        i++;
                        outputDir = checkFile("output directory", args[i], true);
                        outputDir = outputDir.toAbsolutePath();
                        i++;
                        break;

                    // get support directory
                    case "-s":
                        i++;
                        supportDir = checkFile("support directory", args[i], true);
                        i++;
                        break;

                    // produce HMTL report for each VEO
                    case "-r":
                        report = true;
                        i++;
                        break;

                    // report on number of IOs in VEO
                    case "-iocnt":
                        reportIOcnt = true;
                        i++;
                        break;

                    // leave unpacked VEOs after the run
                    case "-u":
                        unpack = true;
                        i++;
                        break;

                    // if verbose...
                    case "-v":
                        verbose = true;
                        i++;
                        LOG.getParent().setLevel(Level.INFO);
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
        try {
            p = Paths.get(safe);
        } catch (InvalidPathException ipe) {
            throw new VEOFatal(classname, 9, type + " '" + safe + "' is not a valid file name." + ipe.getMessage());
        }

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

        // report total IOs generated in run
        if (reportIOcnt) {
            System.out.println("Total IOs encountered in run: " + totalIOs);
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
                rv.genReport(verbose, version(), copyright);
            }

            // if in error mode, print the results for this VEO
            if (error) {
                System.out.println(rv.getStatus());
            }

            // if reporting the number of IOs in this VEO...
            if (reportIOcnt) {
                System.out.print("Number of information objects in VEO: ");
                if (rv.veoContent != null) {
                    System.out.println(rv.veoContent.ioCnt);
                    totalIOs += rv.veoContent.ioCnt;
                } else {
                    System.out.println("0");
                }
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
        public int ioCnt;       // number of IOs in VEO

        public TestVEOResult(Path veoDir, String uniqueID, int ioCnt, boolean hasErrors, String result) {
            this.veoDir = veoDir;
            this.uniqueID = uniqueID;
            this.hasErrors = hasErrors;
            this.result = result;
            this.ioCnt = ioCnt;
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
        RepnVEO rv;
        TestVEOResult tvr;
        String uniqueId;
        ArrayList<RepnSignature> rs;
        String result;
        int cnt;

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
                rv.genReport(verbose, version(), copyright);
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

            if (rv.veoContent != null) {
                cnt = rv.veoContent.ioCnt;
            } else {
                cnt = 0;
            }
            tvr = new TestVEOResult(rv.getVEODir(), uniqueId, cnt, hasErrors, result);

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
        System.out.print("* VEO analysed: '" + veo + "' at ");
        tz = TimeZone.getTimeZone("GMT+10:00");
        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss+10:00");
        sdf.setTimeZone(tz);
        System.out.println(sdf.format(new Date()));
        System.out.println("*                                                                            *");
        System.out.println("******************************************************************************");
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
        OutputStreamWriter osw;

        try {
            va  = new VEOAnalysis(args);
            System.out.println("Starting analysis:");
            va.test();
            System.out.println("Finished");
            osw = new OutputStreamWriter(System.out, Charset.forName("UTF-8"));
            va.resultSummary(osw);
            try {
                osw.close();
            } catch (IOException ioe) {
                /* ignore */
            }
        } catch (VEOFatal e) {
            LOG.log(Level.SEVERE, e.getMessage());
        } catch (VEOError e) {
            LOG.log(Level.WARNING, e.getMessage());
        }
    }
}
