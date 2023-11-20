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

    private String USAGE
            = "AnalyseVEOs [-help] [-e] [-sr] [-csv] [-classify] [-r|-u] [-v] [-d] [-c] [-iocnt] [-norec] [-vpa] -s supportDir [-o outputDir] [files*]";

    public Path supportDir;    // directory in which XML schemas are to be found
    public LTSF ltsfs;         // long term sustainable formats (if null, will be read from supportDir/validLTSF.txt)
    public Path outputDir;     // directory in which the output of the analysis is to be placed (default current working directory)
    public boolean genErrorReport; // if true report on output of analysis for each VEO on standard out (default false) 
    public boolean genHTMLReport; // if true produce HTML reports about each VEO (default false)
    public boolean genCSVReport; // if true generate a TSV report about analysis of each VEO (default false)
    public boolean genResultSummary; // if true summarise the results at the end of the run (default false)
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
                    // if chatty mode...
                    case "-c":
                        chatty = true;
                        i++;
                        break;

                    // classify VEOs by error status
                    case "-class":
                        classifyVEOs = true;
                        // classErrDir = outputDir.resolve("Run-" + runDateTime.replaceAll(":", "-"));
                        i++;
                        break;
                        
                    // get output directory
                    case "-csv":
                        genCSVReport = true;
                        i++;
                        break;

                    // if debugging...
                    case "-d":
                        debug = true;
                        i++;
                        LOG.getParent().setLevel(Level.FINE);
                        break;

                    // produce report containing errors and warnings
                    case "-e":
                        genErrorReport = true;
                        i++;
                        break;

                    // write a summary of the command line options to the std out
                    case "-help":
                        help = true;
                        i++;
                        break;

                    // report on number of IOs in VEO
                    case "-iocnt":
                        reportIOcnt = true;
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

                    // produce HMTL report for each VEO
                    case "-r":
                        genHTMLReport = true;
                        i++;
                        break;

                    // get support directory
                    case "-s":
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

                    // run in VPA mode
                    case "-vpa":
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
        LOG.info("  -e: generate a list of errors and warnings as each VEO is processed");
        LOG.info("  -sr: as for -e, but also generate a summary report of all the unique errors and warnings");
        LOG.info("  -tsv <file>: as for -sr, but also generate a TSV file with of all the errors and warnings");
        LOG.info("  -classErr: classify the VEOs according to error status in the output directory");
        LOG.info("  -r: generate a HTML report describing each VEO (implies '-u')");
        LOG.info("  -u: leave the unpacked VEOs in the file system at the end of the run");
        LOG.info("  -norec: do not warn about missing recommended metadata elements");
        LOG.info("  -vpa: back off on some of the tests (being called from VPA)");
        LOG.info("  -o <directory>: the directory in which the VEOs are unpacked");
        LOG.info("  -iocnt: report on the number of IOs in VEO");
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
            LOG.info(" Report on each VEO processed, including any errors or warnings (-e, -sr, or -tsv set)");
            if (norec) {
                LOG.info(" Do not warn about missing recommended metadata elements (-norec set)");
            } else {
                LOG.info(" Warn about missing recommended metadata elements");
            }
        } else {
            LOG.info(" Do not list VEOs as they are processed, nor on any errors and warnings (-e not set)");
        }
        if (genHTMLReport) {
            LOG.info(" Unpack each VEO and produce a HTML report for each VEO processed (-r set)");
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
            LOG.info(" Leave an unpacked copy of each VEO processed (-u set)");
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
        if (classifyVEOs) {
            LOG.info(" Classify the VEOs by error status in the output directory (-classErr set)");
        } else {
            LOG.info(" Do not classify the VEOs by error status (-classErr not set)");
        }
        if (reportIOcnt) {
            LOG.info(" Report on number of IOs in VEO (-iocnt set)");
        }
        if (genResultSummary) {
            LOG.info(" Produce a summary report of errors and warnings at the end (-sr or -tsv set)");
        } else {
            LOG.info(" Do not produce a summary report of errors and warnings at the end (-sr and -tsv not set)");
        }
        if (genCSVReport) {
            LOG.info(" Produce a TSV report of errors and warnings at the end (-tsv set)");
        } else {
            LOG.info(" Do not produce a TSV report of errors and warnings at the end (-tsv not set)");
        }
        if (vpa) {
            LOG.info(" Run in VPA mode. Do not carry out testing for valid LTSF.");
        }

        LOG.info("Configuration:");
        LOG.info(" Output directory: " + outputDir.toString());
        if (supportDir != null) {
            LOG.info(" Support directory: " + supportDir.toString());
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
