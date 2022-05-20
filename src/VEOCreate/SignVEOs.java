/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOCreate;

import VERSCommon.PFXUser;
import VERSCommon.VEOError;
import VERSCommon.VEOFatal;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.PatternSyntaxException;

/**
 * This class creates multiple VEOs by signing VEO directories. This class also
 * processes the command line arguments and reads the metadata templates.
 * <h3>Command Line arguments</h3>
 * The following command line arguments must be supplied:
 * <ul>
 * <li><b>-c &lt;file&gt;</b> the control file which controls the production of
 * VEOs (see the next section for details about the control file). Note that the
 * control file can have no content.
 * </ul>
 * <p>
 * The following command line arguments are optional:
 * <ul>
 * <li><b>-v</b> verbose output. By default off.</li>
 * <li><b>-d</b> debug mode. In this mode more logging will be generated, and
 * the VEO directories will not be deleted after the ZIP file is created. By
 * default off.</li>
 * <li><b>-ha &lt;algorithm&gt;</b> The hash algorithm used to protect the
 * content files and create signatures. Valid values are: . The default is
 * 'SHA-1'. The hash algorithm can also be set in the control file.
 * <li><b>-s &lt;PFXfile&gt; &lt;password&gt;</b> a PFX file containing details
 * about the signer (particularly the private key) and the password. The PFX
 * file can also be specified in the control file. If no -s command line
 * argument is present, the PFX file must be specified in the control file.
 * <li><b>-o &lt;outputDir&gt;</b> the directory in which the VEOs are to be
 * created. If not present, the VEOs will be created in the current
 * directory.</li>
 * </ul>
 * <p>
 * A minimal example of usage is<br>
 * <pre>
 *     signVEOs -c data.txt -t templates veoToSign.veo
 * </pre>
 * <h3>Control File</h3>
 * A control file is a text file with multiple lines. Each line contains tab
 * separate text. The first entry on each line is the command, subsequent
 * entries on the line are arguments to the command. The commands are:
 * <ul>
 * <li><b>'!'</b> A comment line. The remainder of the line is ignored.</li>
 * <li><b>'HASH' &lt;algorithm&gt;</b> Specifies the hash algorithm to use. If
 * present, this overrides the -ha command line argument.</li>
 * <li><b>'PFX' &lt;pfxFile&gt; &lt;password&gt;</b> Specifies a PFX file and
 * associated password. Multiple PFX lines may be present, this results in
 * multiple signatures being generated.</li>
 * </ul>
 * <p>
 * A simple example of a control file is:<br>
 * <pre>
 * hash	SHA-1
 * pfx	Test/signer.pfx	Password
 * </pre>
 *
 * @author Andrew Waugh (andrew.waugh@prov.vic.gov.au) Copyright 2014 PROV
 *
 * Versions
 */
public class SignVEOs {

    static String classname = "SignVEOs"; // for reporting
    FileOutputStream fos;   // underlying file stream for file channel
    Path controlFile;       // control file to generate the VEOs
    Path outputDir;         // directory in which to place the VEOs
    boolean forceDel;       // if true, force deletion of VEOContent.xml files
    List<PFXUser> signers;  // list of signers
    boolean verbose;        // true if generate lots of detail
    boolean printComments;  // true if printing comments from control file
    boolean debug;          // true if debugging
    boolean help;           // true if printing a cheat list of command line options
    String hashAlg;         // hash algorithm to use
    ArrayList<String> veoDirectories; // list of directories to sign and zip

    static String USAGE = "SignVEOs [-v] [-d] [-f] [-c <controlFile>] [-s <pfxFile> <password>] [-o <outputDir>] [-ha <hashAlgorithm] fileName*";

    // state of the VEOs being built
    private enum State {

        PREAMBLE, // No VEO has been generated
        VEO_STARTED, // VEO started, but Information Object has not
        VEO_FAILED // Construction of this VEO has failed, scan forward until new VEO is started
    }
    State state;      // the state of creation of the VEO

    // private final static Logger rootLog = Logger.getLogger("VEOCreate");
    private final static Logger LOG = Logger.getLogger("VEOCreate.SignVEOs");

    /**
     * Report on version...
     *
     * <pre>
     * 201502   1.0 Initial release
     * 20180601 1.1 Placed under GIT
     * 20191024 1.2 Improved logging
     * 20200615 1.3 Improved reporting of run
     * 20210407 1.4 Standardised reporting of run, added versions
     * 20210409 1.5 Uses new PFXUser function to report on file name
     * 20210709 1.6 Change Base64 handling routines & provided support for PISA
     * </pre>
     */
    static String version() {
        return ("1.6");
    }

    /**
     * Constructor. Processes the command line arguments to set program up, and
     * parses the metadata templates from the template directory.
     * <p>
     * The defaults are as follows. The templates are found in "./Templates".
     * Output is created in the current directory. The hash algorithm is "SHA1",
     * and the signature algorithm is "SHA1+DSA". Content files are linked to
     * the VEO directory.
     *
     * @param args command line arguments
     * @throws VEOFatal when cannot continue to generate any VEOs
     */
    public SignVEOs(String[] args) throws VEOFatal {
        SimpleDateFormat sdf;
        TimeZone tz;
        int i;
        PFXUser pfxu;

        // Set up logging
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s%n");
        LOG.setLevel(Level.WARNING);

        // sanity check
        if (args == null) {
            throw new VEOFatal(classname, 1, "Null command line argument");
        }

        // defaults...
        outputDir = Paths.get("."); // default is the current working directory
        controlFile = null;
        signers = new LinkedList<>();
        veoDirectories = new ArrayList<>();
        forceDel = false;
        verbose = false;
        printComments = false;
        debug = false;
        hashAlg = "SHA-512";
        state = State.PREAMBLE;

        // process command line arguments
        configure(args);

        // tell what is happening
        System.out.println("******************************************************************************");
        System.out.println("*                                                                            *");
        System.out.println("*                V E O ( V 3 )   R E S I G N I N G   T O O L                 *");
        System.out.println("*                                                                            *");
        System.out.println("*                                Version " + version() + "                                *");
        System.out.println("*               Copyright 2015 Public Record Office Victoria                 *");
        System.out.println("*                                                                            *");
        System.out.println("******************************************************************************");
        System.out.println("");
        System.out.print("Run at ");
        tz = TimeZone.getTimeZone("GMT+10:00");
        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss+10:00");
        sdf.setTimeZone(tz);
        System.out.println(sdf.format(new Date()));
        System.out.println("");
        if (help) {
            // SignVEOs [-v] [-d] [-f] [-c <controlFile>] [-s <pfxFile> <password>] [-o <outputDir>] [-ha <hashAlgorithm] fileName*
            System.out.println("Command line arguments:");
            System.out.println(" Mandatory:");
            System.out.println("  one or more VEOs");
            System.out.println("");
            System.out.println(" Optional:");
            System.out.println("  -c <file>: file path to a control file");
            System.out.println("  -s <pfxFile> <password>: path to a PFX file and its password for signing a VEO (can be repeated)");
            System.out.println("  -f: delete the old VEOContentSignature*.xml files");
            System.out.println("  -ha <hashAlgorithm>: specifies the hash algorithm (default SHA-256)");
            System.out.println("  -o <directory>: the directory in which the VEOs are created (default is current working directory)");
            System.out.println("");
            System.out.println("  -v: verbose mode: give more details about processing");
            System.out.println("  -d: debug mode: give a lot of details about processing");
            System.out.println("  -help: print this listing");
            System.out.println("");
        }
        System.out.println("Configuration:");
        if (controlFile != null) {
            System.out.println(" Control file: '" + controlFile.toString() + "'");
            if (printComments) {
                System.out.println("  (and echo the comments in the output)");
            } else {
                System.out.println("  (and don't echo the comments in the output)");
            }
        }
        if (outputDir != null) {
            System.out.println(" Output directory: '" + outputDir.toString() + "'");
        }
        System.out.println(" Hash algorithm (specified on command line or default): " + hashAlg);
        if (forceDel) {
            System.out.println(" Replace the old VEOContentSignature*.xml files in the VEO");
        } else {
            System.out.println(" Retain the old VEOContentSignature*.xml files in the VEO");
        }
        if (signers.size() > 0) {
            System.out.println(" Signers specified on command line:");
            for (i = 0; i < signers.size(); i++) {
                pfxu = signers.get(i);
                System.out.println("  PFX user: '" + pfxu.getFileName() + "'");
            }
        } else {
            System.out.println(" No PFX files specified on command line for signing");
        }
        if (verbose) {
            System.out.println(" Verbose output is selected");
        }
        System.out.println("");

        // at least one VEO must be specified
        if (veoDirectories.isEmpty()) {
            throw new VEOFatal(classname, 4, "No VEOs specified to resign. Usage: " + USAGE);
        }
    }

    /**
     * This method configures the VEO creator from the arguments on the command
     * line. See the comment at the start of this file for the command line
     * arguments.
     *
     * @param args[] the command line arguments
     * @throws VEOFatal if any errors are found in the command line arguments
     */
    private void configure(String args[]) throws VEOFatal {
        int i;
        PFXUser user;   // details about user
        Path pfxFile;   // path of a PFX file
        String password;// password to PFX file

        // process command line arguments
        i = 0;
        try {
            while (i < args.length) {
                switch (args[i].toLowerCase()) {

                    // get control file
                    case "-c":
                        i++;
                        controlFile = checkFile("control file", args[i], false);
                        i++;
                        break;

                    // force deletion of VEOContent.xml files
                    case "-f":
                        i++;
                        forceDel = true;
                        break;

                    // get pfx file
                    case "-s":
                        i++;
                        pfxFile = checkFile("PFX file", args[i], false);
                        i++;
                        password = args[i];
                        i++;
                        user = new PFXUser(pfxFile.toString(), password);
                        signers.add(user);
                        break;

                    // get output directory
                    case "-o":
                        i++;
                        outputDir = checkFile("output directory", args[i], true);
                        i++;
                        break;

                    // get hash algorithm
                    case "-ha":
                        i++;
                        hashAlg = args[i];
                        i++;
                        break;

                    // if verbose...
                    case "-v":
                        verbose = true;
                        i++;
                        LOG.setLevel(Level.INFO);
                        break;

                    // print comments from control file
                    case "-i":
                        printComments = true;
                        i++;
                        break;

                    // if debugging...
                    case "-d":
                        debug = true;
                        i++;
                        LOG.setLevel(Level.FINE);
                        break;

                    // write a summary of the command line options to the std out
                    case "-help":
                        help = true;
                        i++;
                        break;

                    // if unrecognised arguement, print help string and exit
                    default:
                        if (args[i].charAt(0) == '-') {
                            throw new VEOFatal(classname, 2, "Unrecognised argument '" + args[i] + "'. Usage: " + USAGE);
                        }
                        veoDirectories.add(args[i]);
                        i++;
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

        try {
            p = Paths.get(name).normalize();
        } catch (InvalidPathException ipe) {
            throw new VEOFatal(classname, 9, type + " '" + name + "' is not a valid file name: "+ipe.getMessage());
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
     * Process the control file. See the start of this file for a description of
     * the control file and the various commands that can appear in it.
     *
     * @throws VEOFatal if an error occurs that prevents any further VEOs from
     * being constructed
     */
    public void processControlFile() throws VEOFatal {
        String method = "processControlFile";
        FileInputStream fis;// source of control file to build VEOs
        InputStreamReader isr;
        BufferedReader br;  //
        String s;           // current line read from control file
        String[] tokens;    // tokens extracted from line
        int line;           // which line in control file (for errors)

        // sanity check (redundant, but just in case)...
        if (controlFile == null) {
            return;
        }

        // open control file for reading
        try {
            fis = new FileInputStream(controlFile.toString());
        } catch (FileNotFoundException e) {
            throw new VEOFatal(classname, method, 2, "Failed to open control file '" + controlFile.toString() + "'" + e.toString());
        }
        isr = new InputStreamReader(fis, Charset.forName("UTF-8"));
        br = new BufferedReader(isr);

        // go through command file line by line
        line = 0;
        try {
            while ((s = br.readLine()) != null) {
                // log.log(Level.INFO, "Processing: ''{0}''", new Object[]{s});
                line++;

                // split into tokens and check for blank line
                tokens = s.split("\t");
                if (s.equals("") || tokens.length == 0) {
                    continue;
                }
                switch (tokens[0].toLowerCase().trim()) {

                    // comment - ignore line
                    case "!":
                        if (!debug && !verbose) {
                            break;
                        }
                        if (tokens.length < 2) {
                            break;
                        }
                        if (printComments) {
                            System.out.println("COMMENT: " + tokens[1]);
                        }
                        LOG.log(Level.INFO, "COMMENT: {0}", new Object[]{tokens[1]});
                        break;

                    // set the hash algoritm. Can only do this before the first VEO is started
                    case "hash":
                        if (state != State.PREAMBLE) {
                            throw createVEOFatal(1, line, "HASH command must be specified before first VEO generated");
                        }
                        if (tokens.length < 2) {
                            throw createVEOFatal(1, line, "HASH command doesn't specify algorithm (format: 'HASH' <algorithm>");
                        }
                        hashAlg = tokens[1];
                        LOG.log(Level.INFO, "Using hash algorithm: ''{0}''", new Object[]{hashAlg});
                        break;

                    // set a user to sign the VEO. Can only do this before the first VEO
                    case "pfx":
                        PFXUser pfx;
                        if (state != State.PREAMBLE) {
                            throw createVEOFatal(1, line, "PFX command must be specified before first VEO generated");
                        }
                        if (tokens.length < 3) {
                            throw createVEOFatal(1, line, "PFX command doesn't specify pfx file and/or password (format: 'PFX' <pfxFile> <password>)");
                        }
                        pfx = new PFXUser(tokens[1], tokens[2]);
                        signers.add(pfx);
                        if (signers.size() == 1) {
                            System.out.println("Signing using PFX file: " + pfx.getFileName() + " (set from control file)");
                        } else {
                            System.out.println("Also signing using PFX file: " + pfx.getFileName() + " (set from control file)");
                        }
                        LOG.log(Level.INFO, "Using signer {0} with password ''{1}''", new Object[]{pfx.getUserDesc(), tokens[2]});
                        break;

                    // add an event to the VEO history file
                    /*
                     case "e":
                     boolean error;  // true if processing error part of event
                     List<String> descriptions = new ArrayList<>(); // description strings in command
                     List<String> errors = new ArrayList<>(); // error strings in comman

                     // check the right number of arguments
                     if (tokens.length < 5) {
                     veoFailed(line, "Missing mandatory argument in E command (format: 'E' <date> <event> <initiator> <description> [<description>...] ['$$' <error>...])");
                     veo.abandon(debug);
                     veo = null;
                     break;
                     }
                     log.log(Level.INFO, "Adding an event ''{1}'' ''{2}'' ''{3}'' ''{4}''... (State: {0})", new Object[]{state, tokens[1], tokens[2], tokens[3], tokens[4]});
                        
                     error = false;
                     for (i = 4; i < tokens.length; i++) {
                     if (tokens[i].trim().equals("$$")) {
                     error = true;
                     } else if (!error) {
                     descriptions.add(tokens[i]);
                     } else {
                     errors.add(tokens[i]);
                     }
                     }

                     // must have at least one description...
                     if (descriptions.isEmpty()) {
                     veoFailed(line, "Missing mandatory argument in E command - 4th argument is a $$ (format: 'E' <date> <event> <initiator> <description> [<description>...] ['$$' <error>...])");
                     veo.abandon(debug);
                     veo = null;
                     break;
                     }

                     // Add event
                     try {
                     veo.addEvent(tokens[1], tokens[2], tokens[3], descriptions.toArray(new String[descriptions.size()]), errors.toArray(new String[errors.size()]));
                     } catch (VEOError e) {
                     veoFailed(line, "Adding event in E command failed", e);
                     veo.abandon(debug);
                     veo = null;
                     if (e instanceof VEOFatal) {
                     return;
                     }
                     break;
                     }
                     break;
                     */
                    default:
                        LOG.log(Level.SEVERE, "Error in control file around line {0}: unknown command: ''{1}''", new Object[]{line, tokens[0]});
                }
            }
        } catch (PatternSyntaxException | IOException ex) {
            throw new VEOFatal(classname, method, 1, "unexpected error: " + ex.toString());
        }

        try {
            br.close();
        } catch (IOException ioe){
            /* ignore */ }
        try {
            isr.close();
        } catch (IOException ioe){
            /* ignore */ }
        try {
            fis.close();
        } catch (IOException ioe){
            /* ignore */ }
    }

    /**
     * Build VEOs from the VEO directories specified in the command line.
     *
     * @throws VEOFatal if an error occurs that prevents any further VEOs from
     * being constructed
     */
    public void buildVEOs() throws VEOFatal {
        String method = "buildVEOs";
        int i;
        String s;
        Path veoDir;
        CreateVEO veo;      // current VEO being created

        // cannot resign if no signers specified in command args or control file
        if (signers.isEmpty()) {
            throw new VEOFatal(classname, method, 1, "No signers specified on command line or in control file");
        }

        // go through list of VEO directories
        for (i = 0; i < veoDirectories.size(); i++) {

            // get directory name and sanity check it...
            s = veoDirectories.get(i);
            if (!s.endsWith(".veo")) {
                throw new VEOFatal(classname, method, 2, "VEO directory '" + s + "' must end with '.veo'");
            }
            veoDir = checkFile("VEO directory", s, true);

            // create a VEO from the VEO directory...
            if (verbose) {
                System.out.println(System.currentTimeMillis() / 1000 + " Resigning: " + veoDir.toString());
            }
            veo = null;
            try {
                veo = new CreateVEO(veoDir, hashAlg, debug);
                addEvent();
                deleteSignatures(veoDir);
                sign(veo);
                veo.finalise(true);
            } catch (VEOError e) {
                LOG.log(Level.WARNING, "Failed building VEO ''{0}''. Cause: {1}", new Object[]{veoDir.toAbsolutePath().toString(), e.getMessage()});
            } finally {
                if (veo != null) {
                    veo.abandon(true);
                }
            }
        }
        if (verbose) {
            System.out.println(System.currentTimeMillis() / 1000 + " Finished!");
        }
    }

    /**
     * Add an event to the VEOHistory.xml file documenting the resigning.
     */
    public void addEvent() {
        // open temporary file for writing in VEO directory
        // open VEOHistory.xml for reading
        // copy VEOHistory.xml to temporary file except for last </vers:VEOHistory>
        // add new event documenting the resigning
        // add </vers:VEOHistory>
        // move temporary file to VEOHistory.xml
    }

    /**
     * Delete the existing signatures. The VEOContentSignatures are only deleted
     * if the VEOContent.xml has been modified since the signatures have been
     * created.
     *
     * @param veoDir the VEO directory we are resigning
     * @throws VEOError if the signatures couldn't be deleted
     */
    public void deleteSignatures(Path veoDir) throws VEOError {
        String method = "deleteSignatures";
        String fileName;
        DirectoryStream<Path> ds;
        Path contentFile;
        FileTime lastModTime;

        // get the last modified time of the VEOContent.xml file
        contentFile = Paths.get(veoDir.toString(), "VEOContent.xml");
        if (!Files.exists(contentFile)) {
            // log couldn't find VEOContent.xml
            return;
        }
        try {
            lastModTime = Files.getLastModifiedTime(contentFile);
        } catch (IOException e) {
            // log couldn't get last modified time
            return;
        }

        // go through files in VEO Directory, looking for signature files
        ds = null;
        try {
            ds = Files.newDirectoryStream(veoDir);
            for (Path entry : ds) {
                fileName = entry.getFileName().toString();

                // delete content signature files older than the VEOContent.xml file
                if (fileName.startsWith("VEOContentSignature") && fileName.endsWith(".xml")) {
                    if (forceDel || Files.getLastModifiedTime(entry).compareTo(lastModTime) < 0) {
                        Files.delete(entry);
                    }
                }

                // delete all the history signature files
                if (fileName.startsWith("VEOHistorySignature") && fileName.endsWith(".xml")) {
                    Files.delete(entry);
                }

                // delete all the report files
                if (fileName.startsWith("Report") && fileName.endsWith(".html")) {
                    Files.delete(entry);
                }
                if (fileName.equals("index.html")) {
                    Files.delete(entry);
                }
                if (fileName.equals("ReportStyle.css")) {
                    Files.delete(entry);
                }
            }
        } catch (DirectoryIteratorException e) {
            throw new VEOError(classname, method, 1, "Directory iterator failed: " + e.getMessage());
        } catch (IOException e) {
            throw new VEOError(classname, method, 2, "Failed to open the VEO directory for reading files: " + e.getMessage());
        } finally {
            if (ds != null) {
                try {
                    ds.close();
                } catch (IOException ioe) {
                    /* ignore */ }
            }
        }
    }

    /**
     * Generate a VEOContentSignature.xml or VEOHistorySignature.xml file. If
     * multiple PFX files have been specified, multiple pairs of signature files
     * are generated.
     *
     * @param file name of file to be signed
     * @param veo the VEO that is being constructed
     * @param signer the information about the signer
     * @param password the password
     * @throws VEOError if the signing failed for any reason
     */
    private void sign(CreateVEO veo) throws VEOError {

        for (PFXUser user : signers) {
            LOG.log(Level.FINE, "Signing {0} with ''{1}''", new Object[]{user.toString(), hashAlg});
            veo.sign(user, hashAlg);
        }
    }

    /**
     * Utility method to throw a fatal error.
     *
     * @param errno unique error number
     * @param line line number in control file
     * @param s string description of error
     * @return a VEOFatal exception to throw
     */
    private VEOFatal createVEOFatal(int errno, int line, String s) {
        return new VEOFatal(classname, "buildVEOs", errno, "Error in control file around line " + line + ": " + s);
    }

    /**
     * Abandon construction of these VEO and free any resources associated with
     * it.
     *
     * @param debug true if information is to be left for debugging
     */
    public void abandon(boolean debug) {

    }

    /**
     * Main program.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        SignVEOs sv;

        try {
            if (args.length == 0) {
                args = new String[]{"-f", "-c", "Test/Demo/signControl.txt", "../neoVEOOutput/TestAnalysis/TestVEO2.veo"};
            }
            sv = new SignVEOs(args);
            sv.processControlFile();
            sv.buildVEOs();
        } catch (VEOFatal e) {
            System.err.println(e.getMessage());
        }
    }
}
