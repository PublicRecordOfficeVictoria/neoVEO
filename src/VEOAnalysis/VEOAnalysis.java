/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 */
package VEOAnalysis;

import VERSCommon.AnalysisReportCSV;
import VERSCommon.AnalysisClassifyVEOs;
import VERSCommon.LTSF;
import VERSCommon.ResultSummary;
import VERSCommon.VEOError;
import VERSCommon.VEOFailure;
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
    private Config config;      // configuration of this run
    private String runDateTime; // run date/time
    private int totalIOs;       // total IOs counted in VEO
    boolean hasErrors;          // true if VEO had errors
    private final static Logger LOG = Logger.getLogger("VEOAnalysis.VEOAnalysis");
    private AnalysisReportCSV arCSV; // class to handle construction of a CSV report
    private ResultSummary resultSummary;  // summary of the errors & warnings
    private AnalysisClassifyVEOs classifyVEOs; // directory in which to classify the VEOs

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
     * 20231110 4.01 Added tests to ensure that the xsd, xsi, & vers namespaces were defined correctly
     * 20231113 4.02 Fixed test checking if an IO had an AGLS or ANZS5478 MP & removed the xsd & xsi namespaces
     * 20231115 4.03 Moved the VEO result classification & CSV generation code to VERSCommon
     * 20231120 4.04 Fixed bug in errors and warnings
     * 20231129 4.05 Fixed bug when VEO name differed from zip entry names
     * 20231130 4.06 Added test to check that an RDF MP had an rdf:Description element with an rdf:about attribute
     * 20231130 4.07 Deleted Repn.java (replaced by AnalysisBase.java in VERSCommon)
     * 20231130 4.08 Added support for vers:CanUseFor element in a metadata package
     * 20231215 4.09 Cleaned up command line options & make default to only process VEOs
     * 20231222 4.10 No longer accept xmlns:rdf attribute missing # on end - causes rdf parsing to fail
     * 20240205 4.11 Checks entire DOM subtree for xmlns:rdf attributes, complains if xmls:rdf attribute is missing # on end and bails out of parsing RDF
     * 20240209 4.12 Fixed bug in reporting on certificate chain validation failures (issuer and subject didn't change with certificates)
     * 20240313 4.13 Altered RepnSignature so that it can be used standalone to validate signatures and certificates
     * 20240424 4.14 Adjust validation of AS5478 relationships so that role could be either 1 or 2
     * 20240515 4.15 RepnSignature now stores the Signature filename & can report on it
     * 20240516 4.16 Moved initialistion to after printing help, so that help can always done
     * 20240516 4.17 Removed RepnSignature.getSignatureFile()
     * 20240703 4.18 Moved to latest version of Netbeans resulting in correcting warnings
     * 20241113 4.19 Simplified some VEOFailure messages when processing a dcterms:Description
     * 20241113 4.20 Changed checking for dcterms:Description - now handled like other recommended elements (test suppressed if -norec set)
     * 20241113 4.21 Now prints the time run even if no command line arguments present
     * 20241127 4.22 Major rewrite of XML namespace handling, and interface between XML validation and RDF validation in handling metadata packages
     * 20250219 4.23 Linting of error messages and making the handling of metadata packages more robust
     * 20250228 4.24 Changed handling of inheriting Loggers from calling classes
     * 20250303 4.25 Changed handling of Handlers when V3Analysis is being used as a package
     * 20250321 4.26 Now gets unique identifier for TestVEOResult later when the signatures have been processed
     * 20250402 4.27 Make VPA and VEOAnalysis tests for ANZS5478 schema URL identical & corrected namespace references for AGLS metadata
     * 20250514 4.28 Made contextPathDomain optional when testing metadata package (as per spec 5)
     * </pre>
     */
    static String version() {
        return ("4.28");
    }

    static String copyright = "Copyright 2015-2024 Public Record Office Victoria";

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
        runDateTime = getISODateTime('-', ':');

        config = new Config();
        config.configure(args);

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
        if (config.help) {
            config.outputHelp();
        }

        // initialise & report on what has been asked to do
        init(config);
        config.reportConfig();
    }

    /**
     * Initialise via API. In this mode, VEOAnalysis is called by another
     * program to unpack and validate VEOs. Once an instance of a VEOAnalysis
     * class has been created it can be used to validate multiple VEOs.
     *
     * @param c configuration parameters
     * @param parentLogger the parent logger to use
     * @throws VEOError
     */
    public VEOAnalysis(Config c, Logger parentLogger) throws VEOError {
        if (parentLogger != null) {
            LOG.setParent(parentLogger);
        }
        init(c);
    }

    /**
     * Instantiate an VEOAnalysis instance to be used as an API (Old version
     * without csvReport). In this mode, VEOAnalysis is called by another
     * program to unpack and validate VEOs. Once an instance of a VEOAnalysis
     * class has been created it can be used to validate multiple VEOs.
     *
     * Note on logging. Switch off logging in handlers created by this package
     * using the calling package's log configuration file (i.e.
     * V3Analysis.V3Analysis.level=OFF)
     *
     * @param schemaDir directory in which VERS3 support information is found
     * @param ltsfs long term sustainable formats
     * @param outputDir directory in which the VEO will be unpacked
     * @param parentLogger send log messages to the calling class' logger
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
            Logger parentLogger, boolean chatty, boolean genErrorReport, boolean genHTMLReport, boolean unpack,
            boolean debug, boolean verbose, boolean norec, boolean vpa, ResultSummary results) throws VEOError {
        if (parentLogger != null) {
            LOG.setParent(parentLogger);
        }

        config = new Config();
        config.chatty = chatty;
        config.classifyVEOs = false;
        config.debug = debug;
        config.genErrorReport = genErrorReport;
        config.genHTMLReport = genHTMLReport;
        config.genResultSummary = (results != null);
        config.genCSVReport = false;
        config.help = false;
        config.ltsfs = ltsfs;
        config.norec = norec;
        config.outputDir = outputDir;
        config.reportIOcnt = false;
        config.supportDir = schemaDir;
        config.unpack = unpack;
        config.veos = null;
        config.verbose = verbose;
        config.vpa = vpa;

        init(config);
    }

    /**
     * Instantiate an VEOAnalysis instance.
     *
     * @param c configuration structure
     * @throws VEOError if something goes wrong
     */
    private void init(Config c) throws VEOError {

        totalIOs = 0;
        hasErrors = false;

        this.config = c;

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

        arCSV = null;
        if (c.genCSVReport) {
            try {
                arCSV = new AnalysisReportCSV(c.outputDir, runDateTime);
            } catch (IOException ioe) {
                throw new VEOError(CLASSNAME, 4, ioe.getMessage());
            }
        }

        classifyVEOs = null;
        if (c.classifyVEOs) {
            try {
                classifyVEOs = new AnalysisClassifyVEOs(c.outputDir, runDateTime);
            } catch (IOException ioe) {
                throw new VEOError(CLASSNAME, 5, ioe.getMessage());
            }
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
        String veo;
        Path veoFile;

        // go through the list of VEOs
        for (i = 0; i < config.veos.size(); i++) {
            veo = config.veos.get(i);
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

        // close CSV report
        if (arCSV != null) {
            try {
                arCSV.close();
            } catch (IOException ioe) {
                LOG.log(Level.WARNING, "Failed to close the TSV report: {0}", ioe.getMessage());
            }
        }

        // go through classErr directory and rename directories to include count of instances
        if (classifyVEOs != null) {
            try {
                classifyVEOs.includeCount();
            } catch (IOException ioe) {
                LOG.log(Level.WARNING, ioe.getMessage());
            }
        }

        // report total IOs generated in run
        if (config.reportIOcnt) {
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

            // ignore files that don't end in '.veo.zip'
            if (config.onlyVEOs && !p.getFileName().toString().toLowerCase().endsWith(".veo.zip")) {
                return;
            }

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
        config.outputDir = outputDir;
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
        ArrayList<VEOFailure> errors = new ArrayList<>();
        ArrayList<VEOFailure> warnings = new ArrayList<>();
        String result;

        if (veo == null) {
            throw new VEOFatal(CLASSNAME, "testVEO", 1, "VEO path is null");
        }

        // if in error mode, print the header for this VEO
        if (config.genErrorReport) {
            LOG.info("******************************************************************************");
            LOG.info("*                                                                            *");
            LOG.log(Level.INFO, "* V3 VEO analysed: {0} at {1}", new Object[]{veo.getFileName().toString(), getISODateTime('T', ':')});
            LOG.info("*                                                                            *");
            LOG.info("******************************************************************************");
            LOG.info("");
        } else if (config.chatty) {
            LOG.log(Level.INFO, "{0}: {1}", new Object[]{System.currentTimeMillis() / 1000, veo});
        }

        // set this VEO id in the results summary
        if (resultSummary != null) {
            resultSummary.setId(veo.getFileName().toString());
        }

        // perform the analysis
        hasErrors = false;
        rv = new RepnVEO(config.supportDir, veo, config.debug, config.outputDir, resultSummary);
        result = null;

        // get number of IOs seen in this VEO
        totalIOs += rv.getIOCount();

        try {
            // construct the internal representation of the VEO
            if (rv.constructRepn()) {

                // if validating, do so...
                if (config.genErrorReport || config.genHTMLReport || config.genCSVReport || config.classifyVEOs) {
                    rv.validate(config.ltsfs, config.norec, config.vpa); // note originally this was rv.validate(ltsfs, false, norec) with norec always being true when called from VPA
                }

                // if generating HTML report, do so...
                if (config.genHTMLReport) {
                    rv.genReport(config.verbose, version(), copyright);
                }
            }

            // collect the errors and warnings (note these will not survive the call to abandon())
            rv.getProblems(true, errors);
            rv.getProblems(false, warnings);

            // if in error mode, print the results for this VEO
            if (config.genErrorReport) {
                LOG.info(getStatus(errors, warnings));
            }

            // if classifying the VEOs by error category, do so
            if (classifyVEOs != null) {
                classifyVEOs.classifyVEO(veo, errors, warnings);
            }

            // if producing a CSV file of the results, do so
            if (arCSV != null) {
                try {
                    arCSV.write(veo, errors, warnings);
                } catch (IOException ioe) {
                    LOG.log(Level.WARNING, ioe.getMessage());
                }
            }

            // capture results of processing this VEO
            tvr = new TestVEOResult(rv.getVEODir(), rv.getUniqueId(), 0, !errors.isEmpty(), !warnings.isEmpty(), result);

        } finally {
            hasErrors = rv.hasErrors();

            // delete the unpacked VEO
            if (!config.unpack && !config.genHTMLReport) {
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
    private String getISODateTime(char dateTimeSep, char timeSep) {
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
