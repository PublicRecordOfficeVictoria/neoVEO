/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 */
package VEOAnalysis;

import VERSCommon.LTSF;
import VERSCommon.ResultSummary;
import VERSCommon.VEOError;
import VERSCommon.VEOFailure;
import VERSCommon.VEOFatal;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

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
 * <li>'-classErr'. Build a shadow directory distinguishing VEOs that had
 * particular types of errors</li>
 * <li>'-vpa'. Being called from the VPA, so back off on some of the tests</li>
 * </ul>
 * <h1>API</h1>
 * <P>
 * All of the options available on the command line are directly available as an
 * API.
 *
 * @author Andrew Waugh
 */
public class VEOAnalysis {

    private static final String CLASSNAME = "VEOAnalysis";
    private Config c;           // configuration of this run
    private String runDateTime; // run date/time
    private Writer csvReportW;  // not null if asked to produce a TSV report
    private int totalIOs;       // total IOs counted in VEO
    boolean hasErrors;          // true if VEO had errors
    private final static Logger LOG = Logger.getLogger("VEOAnalysis.VEOAnalysis");
    private ResultSummary resultSummary;  // summary of the errors & warnings
    private Path classifyVEOsDir; // directory in which to classify the VEOs

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
     * 20220907 3.24 Changed the URI for ANZS5478 metadata & fixed bugs generating HTML report
     * 20230227 3.25 Added -vpa option & backed off testing for long term preservation formats in VPA
     * 20230614 3.26 Added test for skipped IO depths
     * 20230628 3.27 Added ability to record the results in a TSV file
     * 20230714 3.28 Completely recast reporting to be based around VEOErrors & provide unique ids for errors
     * 20230721 3.29 Added ability to classify VEOs in a shadow directory by error status
     * 20230725 3.30 Cleaned up top level VEOAnalysis code & added Config class
     * 20230802 3.31 Now captures as many errors as possible during parsing the VEO
     * 20230811 3.32 Cleaned up the error/failure reporting to make it consistent
     * 20230921 3.33 Added test for ZIP entry names that do not start with the VEO name
     * 20231011 3.34 Fixed bug with 3.33
     * 20231101 4.00 Completely rewrote AGLS and AS5478 metadata elements tests
     * </pre>
     */
    static String version() {
        return ("4.00");
    }

    static String copyright = "Copyright 2015, 2022, 2023 Public Record Office Victoria";

    /**
     * Initialise the analysis regime using command line arguments. Note that in
     * this mode *all* of the VEOs to be checked are passed in as command line
     * arguments.
     *
     * @param args the command line arguments
     * @throws VEOError if something goes wrong
     */
    public VEOAnalysis(String args[]) throws VEOError {

        // set up the console handler for log messages and set it to output anything
        System.setProperty("java.util.logging.SimpleFormatter.format", "%5$s%n");
        Handler[] hs = LOG.getHandlers();
        for (Handler h : hs) {
            h.setLevel(Level.FINEST);
            h.setFormatter(new SimpleFormatter());
        }
        LOG.setLevel(Level.FINEST);
        c = new Config();
        c.configure(args);
        init(c, null);

        // say what we are doing
        LOG.info("******************************************************************************");
        LOG.info("*                                                                            *");
        LOG.info("*                 V E O ( V 3 )   A N A L Y S I S   T O O L                  *");
        LOG.info("*                                                                            *");
        LOG.log(Level.INFO, "*                                Version {0}                                *", version());
        LOG.log(Level.INFO, "*               {0}                 *", copyright);
        LOG.info("*                                                                            *");
        LOG.info("******************************************************************************");
        LOG.info("");
        LOG.log(Level.INFO, "Run at {0}", runDateTime);

        // output help file
        if (c.help) {
            c.outputHelp();
        }

        // report on what has been asked to do
        c.reportConfig();
    }

    /**
     * Initialise via API. In this mode, VEOAnalysis is called by another
     * program to unpack and validate VEOs. Once an instance of a VEOAnalysis
     * class has been created it can be used to validate multiple VEOs.
     *
     * @param c configuration parameters
     * @param hndlr log handlers
     * @throws VEOError
     */
    public VEOAnalysis(Config c, Handler hndlr) throws VEOError {
        init(c, hndlr);
    }

    /**
     * Instantiate an VEOAnalysis instance to be used as an API (Old version
     * without csvReport). In this mode, VEOAnalysis is called by another
     * program to unpack and validate VEOs. Once an instance of a VEOAnalysis
     * class has been created it can be used to validate multiple VEOs.
     *
     * @param schemaDir directory in which VERS3 support information is found
     * @param ltsfs long term sustainable formats
     * @param outputDir directory in which the VEO will be unpacked
     * @param hndlr where to send the LOG reports
     * @param genErrorReport true if produce a summary error report
     * @param genHTMLReport true if produce HTML reports
     * @param unpack true if leave the VEO directories after execution
     * @param norec true if don't complain about missing recommended metadata
     * @param chatty true if report when starting a new VEO
     * @param debug true if debugging information is to be generated
     * @param verbose true if verbose descriptions are to be generated
     * @param vpa true if being called from VPA (back off on some tests)
     * @param results if not null, create a summary of the errors &amp; warnings
     * @throws VEOError if something goes wrong
     */
    public VEOAnalysis(Path schemaDir, LTSF ltsfs, Path outputDir,
            Handler hndlr, boolean chatty, boolean genErrorReport, boolean genHTMLReport, boolean unpack,
            boolean debug, boolean verbose, boolean norec, boolean vpa, ResultSummary results) throws VEOError {
        c = new Config();

        c.chatty = chatty;
        c.classifyVEOs = false;
        c.debug = debug;
        c.genErrorReport = genErrorReport;
        c.genHTMLReport = genHTMLReport;
        c.genResultSummary = (results != null);
        c.genCSVReport = false;
        c.help = false;
        c.ltsfs = ltsfs;
        c.norec = norec;
        c.outputDir = outputDir;
        c.reportIOcnt = false;
        c.supportDir = schemaDir;
        c.unpack = unpack;
        c.veos = null;
        c.verbose = verbose;
        c.vpa = vpa;
        init(c, hndlr);
    }

    /**
     * Instantiate an VEOAnalysis instance.
     *
     * @param c configuration structure
     * @param hndlr where to send the LOG reports
     * @throws VEOError if something goes wrong
     */
    private void init(Config c, Handler hndlr) throws VEOError {
        Handler h[];
        int i;

        runDateTime = getISODateTime('-', ':', false);
        csvReportW = null;
        totalIOs = 0;
        hasErrors = false;

        if (hndlr != null) {
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
        }

        this.c = c;

        // check to see that user wants to do something
        if (!c.genErrorReport && !c.genHTMLReport && !c.genCSVReport && !c.classifyVEOs && !c.unpack) {
            throw new VEOFatal(CLASSNAME, 1, "Must request at least one of generate error report, CSV report, HTML report, classifyVEOs, and unpack (-e, -csv, -r, -classerr, and -u)");
        }
        if (c.supportDir == null || !Files.isDirectory(c.supportDir)) {
            throw new VEOError(CLASSNAME, 2, "Specified schema directory is null or is not a directory");
        }
        if (c.outputDir == null || !Files.isDirectory(c.outputDir)) {
            throw new VEOError(CLASSNAME, 3, "Specified output directory is null or is not a directory");
        }

        // read valid long term preservation formats if not specified in config
        if (c.ltsfs == null) {
            c.ltsfs = new LTSF(c.supportDir.resolve("validLTSF.txt"));
        }

        if (c.classifyVEOs) {
            classifyVEOsDir = c.outputDir.resolve("Run-" + runDateTime.replaceAll(":", "-"));
            try {
                Files.createDirectory(classifyVEOsDir);
            } catch (IOException ioe) {
                throw new VEOError(CLASSNAME, 4, "Failed to create VEO classification directory '" + classifyVEOsDir.toString() + "': " + ioe.getMessage());
            }
        } else {
            classifyVEOsDir = null;
        }
        if (c.genResultSummary) {
            resultSummary = new ResultSummary();
        } else {
            resultSummary = null;
        }
        if (c.verbose) {
            LOG.getParent().setLevel(Level.INFO);
        }
        if (c.debug) {
            LOG.getParent().setLevel(Level.FINE);
        }
    }

    /**
     * Test the VEOs listed in the configuration. This call steps through the
     * list of VEOs and tests each VEO individually. If requested, the results
     * are listed in the CSVReport.
     *
     * @throws VEOFatal if a fatal error occurred
     */
    public void testVEOs() throws VEOFatal {
        int i;
        String veo, s;
        DirectoryStream<Path> ds, ds1;
        Path veoFile;
        FileOutputStream fos;
        OutputStreamWriter osw;

        // are we generating a CSV file?
        fos = null;
        osw = null;
        if (c.genCSVReport) {
            Path p = c.outputDir.resolve("Results-" + runDateTime.replaceAll(":", "-") + ".csv");
            try {
                fos = new FileOutputStream(p.toFile());
            } catch (FileNotFoundException fnfe) {
                throw new VEOFatal(CLASSNAME, 3, "Failed attempting to open the TSV report file: " + fnfe.getMessage());
            }
            osw = new OutputStreamWriter(fos, Charset.forName("UTF-8"));
            csvReportW = new BufferedWriter(osw);
        }

        // go through the list of VEOs
        for (i = 0; i < c.veos.size(); i++) {
            veo = c.veos.get(i);
            if (veo == null) {
                continue;
            }
            String safe = veo.replaceAll("\\\\", "/");

            // if veo is a directory, go through directory and test all the VEOs
            // otherwise just test the VEO
            try {
                veoFile = Paths.get(safe);
            } catch (InvalidPathException ipe) {
                LOG.log(Level.WARNING, "Failed trying to open file ''{0}'': {1}", new Object[]{safe, ipe.getMessage()});
                continue;
            }
            processFileOrDir(veoFile);
        }

        // close TSV report
        try {
            if (csvReportW != null) {
                csvReportW.close();
            }
            if (osw != null) {
                osw.close();
            }
            if (fos != null) {
                fos.close();
            }
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, "Failed to close the TSV report: {0}", ioe.getMessage());
        }

        // go through classErr directory and rename directories to include count of instances
        if (c.classifyVEOs) {
            try {
                ds = Files.newDirectoryStream(classifyVEOsDir);
                for (Path entry : ds) {
                    ds1 = Files.newDirectoryStream(entry);
                    i = 0;
                    for (Path v : ds1) {
                        if (v.getFileName().toString().toLowerCase().endsWith(".veo.zip")) {
                            i++;
                        }
                    }
                    s = entry.getFileName().toString();
                    if (s.startsWith("E-")) {
                        s = "E-" + i + "-" + s.substring(2);
                        Files.move(entry, classifyVEOsDir.resolve(s));
                    } else if (s.startsWith("W-")) {
                        s = "W-" + i + "-" + s.substring(2);
                        Files.move(entry, classifyVEOsDir.resolve(s));
                    } else if (s.startsWith("OK")) {
                        s = "OK-" + i;
                        Files.move(entry, classifyVEOsDir.resolve(s));
                    }
                }
            } catch (IOException ioe) {
                LOG.log(Level.WARNING, "Failed renaming a classification directory: {0}", ioe.getMessage());
            }
        }

        // report total IOs generated in run
        if (c.reportIOcnt) {
            LOG.log(Level.INFO, "Total IOs encountered in run: {0}", totalIOs);
        }
    }

    /**
     * Recursively process a path name
     *
     * @param p a path that could be a directory or a VEO.
     * @throws VEOError
     */
    private void processFileOrDir(Path p) throws VEOFatal {
        DirectoryStream<Path> ds;

        if (Files.isDirectory(p)) {
            try {
                ds = Files.newDirectoryStream(p);
                for (Path pd : ds) {
                    processFileOrDir(pd);
                }
                ds.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to process directory ''{0}'': {1}", new Object[]{p.toString(), e.getMessage()});
            }
        } else {
            try {
                testVEO(p);
            } catch (VEOError ve) {
                LOG.warning(ve.getMessage());
            }
        }
    }

    /**
     * Test an individual VEO (backwards compatible).
     *
     * @param veo the file path of the VEO
     * @param outputDir the directory in which to unpack this VEO (overrides
     * what was in the config)
     * @return a structure containing information about the VEO
     * @throws VEOError if something went wrong
     */
    public TestVEOResult testVEO(Path veo, Path outputDir) throws VEOError {
        c.outputDir = outputDir;
        return testVEO(veo);
    }

    /**
     * Test result for an individual VEO.
     *
     * @param veo the file path of the VEO
     * @return a structure containing information about the VEO
     * @throws VEOError if something went wrong
     */
    public TestVEOResult testVEO(Path veo) throws VEOError {
        RepnVEO rv;
        TestVEOResult tvr;
        String uniqueId;
        ArrayList<VEOFailure> errors = new ArrayList<>();
        ArrayList<VEOFailure> warnings = new ArrayList<>();
        String result;
        int i;

        if (veo == null) {
            throw new VEOFatal(CLASSNAME, "testVEO", 1, "VEO path is null");
        }

        // if in error mode, print the header for this VEO
        if (c.genErrorReport) {
            LOG.info("******************************************************************************");
            LOG.info("*                                                                            *");
            LOG.log(Level.INFO, "* V3 VEO analysed: {0} at {1}", new Object[]{veo.getFileName().toString(), getISODateTime('T', ':', false)});
            LOG.log(Level.INFO, "* ''{0}''", veo.toString());
            LOG.info("*                                                                            *");
            LOG.info("******************************************************************************");
            LOG.info("");
        } else if (c.chatty) {
            LOG.log(Level.INFO, "{0}: {1}", new Object[]{System.currentTimeMillis() / 1000, veo});
        }

        // set this VEO id in the results summary
        if (resultSummary != null) {
            resultSummary.setId(veo.getFileName().toString());
        }

        // perform the analysis
        hasErrors = false;
        rv = new RepnVEO(c.supportDir, veo, c.debug, c.outputDir, resultSummary);
        result = null;

        // if VEO had at least one signature, get it...
        uniqueId = rv.getUniqueId();

        // get number of IOs seen in this VEO
        totalIOs += rv.getIOCount();

        try {
            // construct the internal representation of the VEO
            if (rv.constructRepn()) {

                // if validating, do so...
                if (c.genErrorReport || c.genHTMLReport) {
                    rv.validate(c.ltsfs, c.norec, c.vpa); // note originally this was rv.validate(ltsfs, false, norec) with norec always being true when called from VPA
                }

                // if generating HTML report, do so...
                if (c.genHTMLReport) {
                    rv.genReport(c.verbose, version(), copyright);
                }
            }

            // collect the errors and warnings (note these will not survive the call to abandon())
            rv.getProblems(true, errors);
            rv.getProblems(false, warnings);

            // if in error mode, print the results for this VEO
            if (c.genErrorReport) {
                LOG.info(getStatus(errors, warnings));
            }

            // if classifying the VEOs by error category, do so
            if (classifyVEOsDir != null) {

                // if no errors or warnings, put in 'NoProblems' directory
                if (errors.isEmpty() && warnings.isEmpty()) {
                    linkVEOinto(veo, classifyVEOsDir, "OK-NoProblems", null);
                }

                // go through errors
                for (i = 0; i < errors.size(); i++) {
                    linkVEOinto(veo, classifyVEOsDir, "E-" + errors.get(i).getFailureId(), errors.get(i).getMessage());
                }

                // go through warnings
                for (i = 0; i < warnings.size(); i++) {
                    linkVEOinto(veo, classifyVEOsDir, "W-" + warnings.get(i).getFailureId(), warnings.get(i).getMessage());
                }
            }

            // if producing a CSV file of the results, do so
            if (csvReportW != null) {
                try {
                    if (errors.isEmpty() && warnings.isEmpty()) {
                        writeCSVReportLine(veo, null, false);
                    } else {
                        for (i = 0; i < errors.size(); i++) {
                            writeCSVReportLine(veo, errors.get(i), true);
                        }
                        for (i = 0; i < warnings.size(); i++) {
                            writeCSVReportLine(veo, warnings.get(i), false);
                        }
                    }
                    csvReportW.flush();

                } catch (IOException ioe) {
                    LOG.log(Level.WARNING, "Failed writing to TSV report. Cause: {0}", ioe.getMessage());
                }
            }

            // capture results of processing this VEO
            tvr = new TestVEOResult(rv.getVEODir(), uniqueId, 0, !errors.isEmpty(), !warnings.isEmpty(), result);

        } finally {
            hasErrors = rv.hasErrors();

            // delete the unpacked VEO
            if (!c.unpack && !c.genHTMLReport) {
                rv.deleteVEO();
            }

            // clean up
            rv.abandon();
        }
        return tvr;
    }

    /**
     * Hard link the given VEO into the specified classDir directory in the
     * outputDir directory.
     *
     * @param veo the path of the VEO being tested
     * @param outputDir the directory in which to build the classification
     * @param classDir the error identification that occurred
     * @param mesg a text description of this error
     */
    private void linkVEOinto(Path veo, Path outputDir, String classDir, String mesg) {
        Path pd, source;
        FileOutputStream fos;
        OutputStreamWriter osw;
        BufferedWriter bw;

        // get class directory, creating if necessary & add the description of the error
        pd = outputDir.resolve(classDir);
        if (!Files.exists(pd)) {
            try {
                Files.createDirectories(pd);
            } catch (IOException ioe) {
                LOG.log(Level.WARNING, "Failed creating class directory ''{0}'': {1}", new Object[]{pd.toString(), ioe.getMessage()});
                return;
            }
            if (mesg != null) {
                try {
                    fos = new FileOutputStream(pd.resolve("ErrorMessage.txt").toFile());
                    osw = new OutputStreamWriter(fos, "UTF-8");
                    bw = new BufferedWriter(osw);
                    bw.write("A typical message for this class of error/warning is: \n\n");
                    bw.write(mesg);
                    bw.close();
                    osw.close();
                    fos.close();
                } catch (IOException ioe) {
                    LOG.log(Level.WARNING, "Failed creating description of error: {0}", ioe.getMessage());
                    return;
                }
            }
        }
        if (!Files.isDirectory(pd)) {
            LOG.log(Level.WARNING, "Class directory ''{0}'' exists, but is not a directory", pd.toString());
            return;
        }

        source = pd.resolve(veo.getFileName());

        // test to see if link already exists
        if (Files.exists(source)) {
            return;
        }

        // hard link VEO into class directory
        try {
            Files.createLink(source, veo);
        } catch (IOException | UnsupportedOperationException ioe) {
            if (ioe.getMessage().trim().endsWith("Incorrect function.")) {
                LOG.log(Level.WARNING, "Failed linking ''{0}'' to ''{1}'': Might be because the file system containing the output directory is not NTSF", new Object[]{pd.resolve(veo.getFileName()), veo.toString()});
            } else if (ioe.getMessage().trim().endsWith("different disk drive.")) {
                LOG.log(Level.WARNING, "Failed linking ''{0}'' to ''{1}'': Might be because the VEOs and the output directory are on different file systems", new Object[]{pd.resolve(veo.getFileName()), veo.toString()});
            } else {
                LOG.log(Level.WARNING, "Failed linking ''{0}'' to ''{1}'': {2}", new Object[]{pd.resolve(veo.getFileName()), veo.toString(), ioe.getMessage()});
            }
        }
    }

    /**
     * Write a line in the TSV Report
     *
     * @param veo the VEO being reported on
     * @param error true if an error occurred, false if it is a warning
     * @param ve the error/warning that occurred
     * @throws IOException
     */
    private void writeCSVReportLine(Path veo, VEOFailure ve, boolean error) throws IOException {
        csvReportW.write(veo != null ? veo.getFileName().toString() : "");
        csvReportW.write(',');
        if (ve != null) {
            if (error) {
                csvReportW.write("Error,");
            } else {
                csvReportW.write("Warning,");
            }
            csvReportW.write(ve.getFailureId());
            csvReportW.write(',');
            csvReportW.write(ve.getMessage());
        } else {
            csvReportW.write("OK,,");
        }
        csvReportW.write(',');
        csvReportW.write(veo != null ? veo.toString() : "");
        csvReportW.write("\n");
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
     * Return a summary of the errors and warnings that occurred in the VEO.
     *
     * @return a String containing the errors and warnings
     */
    private String getStatus(List<VEOFailure> errors, List<VEOFailure> warnings) {
        StringBuilder sb = new StringBuilder();
        int i;

        // check for errors
        if (errors.isEmpty()) {
            sb.append("No errors detected\n");
        } else {
            sb.append("Errors detected:\n");
            for (i = 0; i < errors.size(); i++) {
                sb.append("   Error: ");
                sb.append(errors.get(i).getMessage());
                sb.append("\n");
            }
        }

        // check for warnings
        sb.append("\n");
        if (warnings.isEmpty()) {
            sb.append("No warnings detected\n");
        } else {
            sb.append("Warnings detected:\n");
            for (i = 0; i < warnings.size(); i++) {
                sb.append("   Warning: ");
                sb.append(warnings.get(i).getMessage());
                sb.append("\n");
            }
        }
        return sb.toString();
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

        if (w == null) {
            throw new VEOError(CLASSNAME, "resultSummary", 1, "Writer is null");
        }

        if (resultSummary != null) {
            bw = new BufferedWriter(w);
            try {
                resultSummary.report(bw);
                bw.close();
            } catch (IOException ioe) {
                throw new VEOError(CLASSNAME, "resultSummary", 2, "Error producing summary report: " + ioe.getMessage());
            }
        }
    }

    /**
     * Get the current date time in the ISO Format (except space between date
     * and time instead of 'T')
     *
     * @param sep the separator between the date and the time
     * @return a string containing the date time
     */
    private String getISODateTime(char dateTimeSep, char timeSep, boolean addTimeZone) {
        Instant now;
        ZonedDateTime zdt;
        DateTimeFormatter formatter;

        now = Instant.now();
        zdt = now.atZone(ZoneId.systemDefault());
        formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'" + dateTimeSep + "'HH'" + timeSep + "'mm'" + timeSep + "'ss");
        return zdt.format(formatter);
    }

    /**
     * Public subclass to return information about the VEO we just processed.
     */
    public class TestVEOResult {

        public Path veoDir;     // the path of the created VEO directory
        public String uniqueID; // unique id of this VEO (i.e. the B64 encoded signature
        public boolean hasErrors; // true if the VEO had errors
        public boolean hasWarnings; // true if the VEO had warnings
        public String result;   // what happened when processing the VEO
        public int ioCnt;       // number of IOs in VEO

        public TestVEOResult(Path veoDir, String uniqueID, int ioCnt, boolean hasErrors, boolean hasWarnings, String result) {
            this.veoDir = veoDir;
            this.uniqueID = uniqueID;
            this.hasErrors = hasErrors;
            this.hasWarnings = hasWarnings;
            this.result = result;
            this.ioCnt = ioCnt;
        }

        public void free() {
            veoDir = null;
            uniqueID = null;
            result = null;
        }

        public String toTSVstring() {
            StringBuilder sb = new StringBuilder();

            sb.append(veoDir != null ? veoDir.getFileName().toString() : "");
            sb.append('\t');
            sb.append(veoDir != null ? veoDir.toString() : "");
            sb.append('\t');
            sb.append(uniqueID != null ? uniqueID : "");
            sb.append('\t');
            sb.append(ioCnt);
            sb.append('\t');
            sb.append(hasErrors);
            sb.append('\t');
            sb.append(result != null ? result.replaceAll("\n", " ") : "");
            return sb.toString();
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
            va.testVEOs();
            System.out.println("Finished");
            osw = new OutputStreamWriter(System.out, Charset.forName("UTF-8"));
            va.resultSummary(osw);
            try {
                osw.close();
            } catch (IOException ioe) {
                LOG.log(Level.WARNING, "Failed closing output: {0}", ioe.getMessage());
            }
        } catch (VEOFatal e) {
            LOG.log(Level.SEVERE, e.getMessage());
        } catch (VEOError e) {
            LOG.log(Level.WARNING, e.getMessage());
        }
    }
}
