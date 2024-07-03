/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015.
 */
package VEOAnalysis;

import VERSCommon.LTSF;
import VERSCommon.VEOFatal;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class contains the configuration parameters for a run of VEOAnalysis.
 */
public class Config {

    private final static Logger LOG = Logger.getLogger("VEOAnalysis.Config");
    private final static String CLASSNAME = "Config";

    private final String USAGE
            = "AnalyseVEOs [-help] -s supportDir [-o outputDir] [-all] [-e|-sr|-csv] [-r|-u] [-class] [-iocnt] [-v] [-d] [-c] [-norec] [-vpa] files*";

    public Path supportDir;    // directory in which XML schemas are to be found
    public LTSF ltsfs;         // long term sustainable formats (if null, will be read from supportDir/validLTSF.txt)
    public Path outputDir;     // directory in which the output of the analysis is to be placed (default current working directory)
    public boolean genErrorReport; // if true report on output of analysis for each VEO on standard out (default false) 
    public boolean genHTMLReport; // if true produce HTML reports about each VEO (default false)
    public boolean genCSVReport; // if true generate a TSV report about analysis of each VEO (default false)
    public boolean genResultSummary; // if true summarise the results at the end of the run (default false)
    public boolean onlyVEOs;   // if true (default) only process files ending in '.veo.zip'
    public boolean unpack;     // if true leave the unpacked VEOs after analysis (default false)
    public boolean norec;      // if true asked to not complain about missing recommended metadata elements (default false)
    public boolean classifyVEOs; // if true classify the VEOs according to the analysis results in a shadown directory (default false)
    public boolean reportIOcnt;// if true requested to report on number of IOs in VEO (default false)
    public boolean vpa;        // if true being called from the VPA
    public boolean help;       // if true generate a help summary of command line arguements
    public boolean chatty;     // if true report when starting a new VEO
    public boolean debug;      // if true debugging information is to be generated
    public boolean verbose;    // if true verbose descriptions are to be generated
    public ArrayList<String> veos; // The filenames of the VEOS (and/or directories) to process

    public Config() {
        supportDir = null;
        ltsfs = null;
        outputDir = Paths.get(".").toAbsolutePath();
        chatty = false;
        genErrorReport = false;
        genHTMLReport = false;
        genCSVReport = false;
        genResultSummary = false;
        unpack = false;
        debug = false;
        verbose = false;
        norec = false;
        classifyVEOs = false;
        reportIOcnt = false;
        vpa = false;
        help = false;
        veos = new ArrayList<>();
        onlyVEOs = true;
    }

    /**
     * This method configures the VEO analysis from the arguments on the command
     * line. See the comment at the start of this file for the command line
     * arguments.
     *
     * @param args[] the command line arguments
     * @throws VEOFatal if any errors are found in the command line arguments
     */
    public void configure(String args[]) throws VEOFatal {
        int i;

        // process command line arguments
        i = 0;
        try {
            while (i < args.length) {
                switch (args[i].toLowerCase()) {
                    case "-all": // report on all files found
                        onlyVEOs = false;
                        i++;
                        break;

                    case "-c": // report on each file started
                        chatty = true;
                        i++;
                        break;

                    case "-class": // classify VEOs by result
                        classifyVEOs = true;
                        // classErrDir = outputDir.resolve("Run-" + runDateTime.replaceAll(":", "-"));
                        i++;
                        break;

                    case "-csv": // generate a CSV report on results
                        genCSVReport = true;
                        i++;
                        break;

                    case "-d": // produce debugging information
                        debug = true;
                        i++;
                        LOG.getParent().setLevel(Level.FINE);
                        break;

                    case "-e": // report on errors and warnings found
                        genErrorReport = true;
                        i++;
                        break;

                    case "-help": // write a summary of the command line options to the std out
                        help = true;
                        i++;
                        break;

                    case "-iocnt": // report on number of IOs found in VEOs
                        reportIOcnt = true;
                        i++;
                        break;

                    case "-norec": // do not complain about missing recommended metadata elements
                        norec = true;
                        i++;
                        break;

                    case "-o": // set output directory
                        i++;
                        outputDir = checkFile("output directory", args[i], true);
                        outputDir = outputDir.toAbsolutePath();
                        i++;
                        break;

                    case "-r": // produce HMTL report for each VEO
                        genHTMLReport = true;
                        i++;
                        break;

                    case "-s": // set support directory
                        i++;
                        supportDir = checkFile("support directory", args[i], true);
                        i++;
                        break;

                    // produce summary report summarising the errors and warnings (also implies -e)
                    case "-sr":
                        genErrorReport = true;
                        genResultSummary = true;
                        i++;
                        break;

                    case "-u": // leave unpacked VEOs after the run
                        unpack = true;
                        i++;
                        break;

                    case "-v": // include more information in the report
                        verbose = true;
                        i++;
                        LOG.getParent().setLevel(Level.INFO);
                        break;

                    case "-vpa": // run in VPA mode
                        vpa = true;
                        i++;
                        break;

                    // otherwise, check if it starts with a '-' and complain, otherwise assume it is a VEO pathname
                    default:
                        if (args[i].startsWith("-")) {
                            throw new VEOFatal(CLASSNAME, 2, "Unrecognised argument '" + args[i] + "'. Usage: " + USAGE);
                        } else {
                            veos.add(args[i]);
                            i++;
                        }
                }
            }
        } catch (ArrayIndexOutOfBoundsException ae) {
            throw new VEOFatal(CLASSNAME, 3, "Missing argument. Usage: " + USAGE);
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
            throw new VEOFatal(CLASSNAME, 9, type + " '" + safe + "' is not a valid file name." + ipe.getMessage());
        }

        if (!Files.exists(p)) {
            throw new VEOFatal(CLASSNAME, 6, type + " '" + p.toAbsolutePath().toString() + "' does not exist");
        }
        if (isDirectory && !Files.isDirectory(p)) {
            throw new VEOFatal(CLASSNAME, 7, type + " '" + p.toAbsolutePath().toString() + "' is a file not a directory");
        }
        if (!isDirectory && Files.isDirectory(p)) {
            throw new VEOFatal(CLASSNAME, 8, type + " '" + p.toAbsolutePath().toString() + "' is a directory not a file");
        }
        return p;
    }

    /**
     * Output a summary of the command line options
     */
    public void outputHelp() {
        LOG.info("Command line arguments:");
        LOG.info(" Mandatory:");
        LOG.info("  one or more VEO files, or directories where VEOs are to be found");
        LOG.info("  -s <support directory>: file path to where the support files are located");
        LOG.info("");
        LOG.info(" Optional:");
        LOG.info("  -e: generate a list of errors and warnings on the screen as each VEO is processed");
        LOG.info("  -sr: as for -e, but also summarise all the unique errors and warnings");
        LOG.info("  -csv <file>: generate a CSV file in the output directory with of all the errors and warnings");
        LOG.info("  -class: classify the VEOs in the output directory according to error status");
        LOG.info("  -u: leave the unpacked VEOs in the output directory at the end of the run");
        LOG.info("  -r: as for -u, but also generate a HTML report describing each VEO in the unpacked VEO");
        LOG.info("  -norec: do not warn about missing recommended metadata elements");
        LOG.info("  -all: process all files found (default is to only process files ending in '.veo.zip'");
        LOG.info("  -vpa: back off on some of the tests (being called from VPA)");
        LOG.info("  -o <directory>: the directory in which the VEOs are unpacked, classified, and CSV file placed (default is current directory)");
        LOG.info("  -iocnt: report on the number of IOs found in the VEOs");
        LOG.info("");
        LOG.info("  -c: chatty mode: report when starting a new VEO when using -r or -u");
        LOG.info("  -v: verbose mode: give more details about processing");
        LOG.info("  -d: debug mode: give a lot of details about processing");
        LOG.info("  -help: print this listing");
        LOG.info("");
    }

    public void reportConfig() {
        LOG.info("Output mode:");
        if (genErrorReport) {
            LOG.info(" Report on each VEO processed, including any errors or warnings (-e or -sr set)");
            if (norec) {
                LOG.info(" Do not warn about missing recommended metadata elements (-norec set)");
            } else {
                LOG.info(" Warn about missing recommended metadata elements");
            }
        } else {
            LOG.info(" Do not list VEOs as they are processed, nor on any errors and warnings (-e or -sr not set)");
        }
        if (genCSVReport) {
            LOG.info(" Record results in a CSV file in the output directory (-csv set)");
        } else {
            LOG.info(" Do not generate a CSV file (-csv not set)");
        }
        if (classifyVEOs) {
            LOG.info(" Classify the VEOs by result in the output directory (-class set)");
        } else {
            LOG.info(" Do not classify the VEOs by result (-class not set)");
        }
        if (genHTMLReport) {
            LOG.info(" Unpack each VEO and produce a HTML report for each VEO processed (-r set) in the output directory");
            if (!genErrorReport) {
                if (norec) {
                    LOG.info(" Do not warn about missing recommended metadata elements (-norec set)");
                } else {
                    LOG.info(" Warn about missing recommended metadata elements");
                }
                if (chatty) {
                    LOG.info(" Report processing each VEO (-c set)");
                } else {
                    LOG.info(" Do not report processing each VEO (-c not set)");
                }
            }
        } else if (unpack) {
            LOG.info(" Leave an unpacked copy of each VEO processed (-u set) in the output directory");
            if (!genErrorReport) {
                if (chatty) {
                    LOG.info(" Report processing each VEO (-c set)");
                } else {
                    LOG.info(" Do not report processing each VEO (-c not set)");
                }
            }
        } else {
            LOG.info(" Do not unpack or produce a final HTML report for each VEO processed (neither -u or -r set)");
        }
        if (reportIOcnt) {
            LOG.info(" Report on number of IOs in VEO (-iocnt set)");
        }
        if (genResultSummary) {
            LOG.info(" Produce a summary report of errors and warnings at the end (-sr set)");
        } else {
            LOG.info(" Do not produce a summary report of errors and warnings at the end (-sr not set)");
        }
        if (genCSVReport) {
            LOG.info(" Produce a CSV report of errors and warnings at the end (-csv set)");
        } else {
            LOG.info(" Do not produce a CSV report of errors and warnings at the end (-csv not set)");
        }
        if (onlyVEOs) {
            LOG.info(" Only process files ending in '.veo.zip' (VEO files)");
        } else {
            LOG.info(" Process all files found irrespective of their file type");
        }
        if (vpa) {
            LOG.info(" Run in VPA mode. Do not carry out testing for valid LTSF.");
        }

        LOG.info("Configuration:");
        LOG.log(Level.INFO, " Output directory: {0}", outputDir.toString());
        if (supportDir != null) {
            LOG.log(Level.INFO, " Support directory: {0}", supportDir.toString());
        } else {
            LOG.info(" Support directory is not set");
        }

        if (debug) {
            LOG.info(" Debug mode is selected");
        }
        if (verbose) {
            LOG.info(" Verbose output is selected");
        }
        LOG.info("");
    }
}
