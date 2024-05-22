/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOResign;

import VERSCommon.PFXUser;
import VERSCommon.VEOError;
import VERSCommon.VEOFatal;
import VEOAnalysis.RepnSignature;
import VEOCreate.CreateVEO;
import VEOCreate.CreateVEO.SignType;
import VERSCommon.AppError;
import VERSCommon.AppFatal;
import VERSCommon.ResultSummary;
import VERSCommon.VERSDate;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class creates VEOs by signing VEO directories. The class can operate in
 * three modes:
 * <ul>
 * <li><b>Verify</b> In this mode, all of the digital signatures in the VEO are
 * verified (but not the certificates). The result of this verification is
 * documented as an event in the VEOHistory.xml file. Since this invalidates the
 * VEOHistory signatures, these are removed and the VEOHistory.xml file.
 * resigned</li>
 * <li><b>Renew</b> In this mode, the VEOContent.xml is resigned and any invalid
 * VEOContent signatures are deleted. The result of the renewal is documented as
 * an event in the VEOHistory.xml file. The existing VEOHistory signatures are
 * removed and the VEOHistory.xml file resigned.</li>
 * <li><b>Create</b> In this mode, all the existing signatures are removed, and
 * the VEOContent.xml and VEOHistory.xml files are resigned. Note that no entry
 * is made in the VEOHistory.xml file - this mode is intended to be used to
 * create test VEOs from a template VEO.</li>
 * </ul>
 * <h3>Command Line arguments</h3>
 * The following command line arguments must be supplied:
 * <ul>
 * <li>One of <b>-verify</b>, <b>-renew</b>, or <b>-create</b> to indicate the
 * mode.</li>
 * <li><b>-s &lt;PFXfile&gt; &lt;password&gt;</b> a PFX file containing details
 * about the signer (particularly the private key) and the password. This
 * command line argument may be repeated to sign the VEO multiple times.</li>
 * <li><b>-support &lt;directory&gt;</b> the VERS support directory containing
 * the XML schema files used to validate VEOs.</li>
 * </ul>
 * <p>
 * The following command line arguments are optional:
 * <ul>
 * <li><b>-u &lt;string&gt;</b> A string containing the identity of the user
 * running this program. If the string contains multiple words, it should be
 * enclosed in double quote marks. If not specified, the login id will be
 * used</li>
 * <li><b>-e &lt;string&gt;</b> A string containing a description of the event
 * causing the VEO to be resigned (e.g. a description of the changes made to the
 * VEO). If the string contains multiple words, it should be enclosed in double
 * quote marks. If not specified a simple description of the changes to the
 * signatures are added.</li>
 * <li><b>-ha &lt;algorithm&gt;</b> The hash algorithm used to protect the
 * content files and create signatures. The default is 'SHA-512'.</li>
 * <li><b>-o &lt;outputDir&gt;</b> the directory in which the VEOs are to be
 * created. If not present, the VEOs will be created in the current
 * directory.</li>
 * <li><b>-v</b> verbose output. By default off.</li>
 * <li><b>-d</b> debug mode. In this mode more logging will be generated, and
 * the VEO directories will not be deleted after the ZIP file is created. By
 * default off.</li>
 * </ul>
 *
 * @author Andrew Waugh (andrew.waugh@prov.vic.gov.au) Copyright 2014, 2024 PROV
 *
 * Versions
 */
public class SignVEOs {

    static String classname = "SignVEOs"; // for reporting
    public Path supportDir; // directory in which XML schemas are to be found
    Path outputDir;         // directory in which to place the VEOs
    Task task;              // task to be performed
    ArrayList<String> veoDirectories; // list of directories to sign and zip
    List<PFXUser> signers;  // list of signers
    boolean noZIP;          // true if leaving VEO unzipped at end
    boolean verbose;        // true if generate lots of detail
    boolean printComments;  // true if printing comments from control file
    boolean debug;          // true if debugging
    boolean help;           // true if printing a cheat list of command line options
    String hashAlg;         // hash algorithm to use
    String userDesc;        // user resigning VEO
    String eventDesc;       // user supplied description of cause of resigning

    // what task are we asking SignVEOs to do?
    static enum Task {
        NOTSPECIFIED, // user hasn't specified task; stop and complain
        VERIFY, // verify the signature (only) and record result in VEOHistory
        RENEW, // resign VEOContent, deleting old signatures if invalid, and record even in VEOHistory
        CREATE      // delete old signatures & resign without updating VEOHistory
    }

    static String USAGE = "SignVEOs -verify|-renew|-create -s <pfxFile> <password> -support <directory> [-u user] [-e eventDesc] [-ha <hashAlgorithm] [-o <outputDir>] [-v] [-d] fileName*";

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
     * 20240313 2.0 Now updates VEOHistory.xml with events
     * 20240417 2.1 Moved from VEOCreate to be its own package
     * 20240508 2.2 Adjusted logging so that std header & help are displayed
     * 20240515 2.3 Selecting output directory now works, and can skip ZIPping file
     * 20240522 2.4 Fixed major bug in renewing signatures, improved logging of what happened, & added -nozip option
     * </pre>
     */
    static String version() {
        return ("2.4");
    }

    /**
     * Constructor. Processes the command line arguments to set program up.
     * <p>
     * The defaults are as follows. Output is created in the current directory.
     * The hash algorithm is "SHA512".
     *
     * @param args command line arguments
     * @throws AppFatal when cannot continue to generate any VEOs
     */
    public SignVEOs(String[] args) throws AppFatal {
        int i;
        PFXUser pfxu;

        // Set up logging
        System.setProperty("java.util.logging.SimpleFormatter.format", "%5$s%n");
        LOG.setLevel(Level.WARNING);

        // sanity check
        if (args == null) {
            throw new AppFatal(classname, 1, "Null command line argument");
        }

        // defaults...
        task = Task.NOTSPECIFIED;
        supportDir = null;
        outputDir = Paths.get("."); // default is the current working directory
        userDesc = null;
        eventDesc = null;
        signers = new LinkedList<>();
        veoDirectories = new ArrayList<>();
        noZIP = false;
        verbose = false;
        printComments = false;
        debug = false;
        help = false;
        hashAlg = "SHA-512";

        // process command line arguments
        configure(args);

        // tell what is happening
        LOG.warning("******************************************************************************");
        LOG.warning("*                                                                            *");
        LOG.warning("*                V E O ( V 3 )   R E S I G N I N G   T O O L                 *");
        LOG.warning("*                                                                            *");
        LOG.log(Level.WARNING, "*                                Version {0}                                *", version());
        LOG.warning("*               Copyright 2015 Public Record Office Victoria                 *");
        LOG.warning("*                                                                            *");
        LOG.warning("******************************************************************************");
        LOG.warning("");
        LOG.log(Level.WARNING, "Run at {0}", VERSDate.versDateTime(0));
        LOG.warning("");
        if (help) {
            LOG.warning("Command line arguments:");
            LOG.warning(" Mandatory:");
            LOG.warning("  -verify or -renew or -create: task to perform");
            LOG.warning("  -s <pfxFile> <password>: path to a PFX file and its password for signing a VEO (can be repeated)");
            LOG.warning("  -support <direct>: path directory where schema files are found");
            LOG.warning("  one or more VEOs");
            LOG.warning("");
            LOG.warning(" Optional:");
            LOG.warning("  -u <userDesc>: a description of the user resigning the file");
            LOG.warning("  -e <eventDesc>: a description of the event causing the resigning");
            LOG.warning("  -ha <hashAlgorithm>: specifies the hash algorithm (default SHA-256)");
            LOG.warning("  -o <directory>: the directory in which the VEOs are created (default is current working directory)");
            LOG.warning("  -nozip: leave the VEO unzipped at end)");
            LOG.warning("");
            LOG.warning("  -v: verbose mode: give more details about processing");
            LOG.warning("  -d: debug mode: give a lot of details about processing");
            LOG.warning("  -help: print this listing");
            LOG.warning("");
        }

        // set and check config
        if (task == Task.NOTSPECIFIED) {
            throw new AppFatal(classname, 2, "No task specified: must use -verify, -renew, or -create. Usage: " + USAGE);
        }
        if (supportDir == null) {
            throw new AppFatal(classname, 3, "Support directory is not specified. Usage: " + USAGE);
        }
        if (signers.isEmpty()) {
            throw new AppFatal(classname, 4, "No PFX files specified to resign. Usage: " + USAGE);
        }
        if (veoDirectories.isEmpty()) {
            throw new AppFatal(classname, 5, "No VEOs specified to resign. Usage: " + USAGE);
        }
        if (userDesc == null) {
            userDesc = System.getProperty("user.name");
        } else {
            userDesc = userDesc + " (" + System.getProperty("user.name") + ")";
        }

        LOG.info("Configuration:");
        switch (task) {
            case VERIFY:
                LOG.info(" Verify all the signatures (but not certificates), record result in VEOHistory, and resign VEOHistory.xml");
                break;
            case RENEW:
                LOG.info(" Delete invalid VEOContent signatures, record renewal in VEOHistory, and resign everything");
                break;
            case CREATE:
                LOG.info(" Delete all signatures & resign. VEOHistory is not changed");
                break;
            default:
                LOG.info(" Task to perform is not specified");
                break;
        }
        LOG.info(" Signers:");
        for (i = 0; i < signers.size(); i++) {
            pfxu = signers.get(i);
            LOG.log(Level.INFO, "  PFX user: ''{0}''", pfxu.getFileName());
        }
        LOG.log(Level.INFO, " User: ''{0}''", userDesc);
        LOG.log(Level.INFO, " Event description: ''{0}''", eventDesc);
        LOG.log(Level.INFO, " Hash algorithm: {0}", hashAlg);
        LOG.log(Level.INFO, " Output directory: ''{0}''", outputDir.toString());
        LOG.log(Level.INFO, " Produce ZIP file: ''{0}''", !noZIP);
        if (verbose) {
            LOG.info(" Verbose output is selected");
        }
        LOG.info("");
    }

    /**
     * This method configures the VEO creator from the arguments on the command
     * line. See the comment at the start of this file for the command line
     * arguments.
     *
     * @param args[] the command line arguments
     * @throws VEOFatal if any errors are found in the command line arguments
     */
    private void configure(String args[]) throws AppFatal {
        int i;
        PFXUser user;   // details about user
        Path pfxFile;   // path of a PFX file
        String password;// password to PFX file

        // process command line arguments
        i = 0;
        try {
            while (i < args.length) {
                switch (args[i].toLowerCase()) {
                    case "-verify": // document if signatures are valid
                        i++;
                        task = Task.VERIFY;
                        break;
                    case "-renew": // renew the signatures, documenting that we've done so
                        i++;
                        task = Task.RENEW;
                        break;
                    case "-create": // delete all signatures and resign, don't document
                        i++;
                        task = Task.CREATE;
                        break;
                    case "-s": // specify the PFX file of a signer
                        i++;
                        pfxFile = checkFile("PFX file", args[i], false);
                        i++;
                        password = args[i];
                        i++;
                        try {
                            user = new PFXUser(pfxFile.toString(), password);
                        } catch (VEOFatal vf) {
                            throw new AppFatal(classname, "configure", 1, vf.getMessage());
                        }
                        signers.add(user);
                        break;
                    case "-support": // set support directory
                        i++;
                        supportDir = checkFile("support directory", args[i], true);
                        i++;
                        break;
                    case "-u": // specify a responsible user
                        i++;
                        userDesc = args[i];
                        i++;
                        break;
                    case "-e": // specify an event description
                        i++;
                        eventDesc = args[i];
                        i++;
                        break;
                    case "-o": // specify the output directory
                        i++;
                        outputDir = checkFile("output directory", args[i], true);
                        i++;
                        break;
                    case "-ha": // specify hash algorithm for signers
                        i++;
                        hashAlg = args[i];
                        i++;
                        break;
                    case "-nozip": // do not ZIP VEO at end
                        i++;
                        noZIP = true;
                        break;
                    case "-v": // verbose output
                        verbose = true;
                        i++;
                        LOG.setLevel(Level.INFO);
                        break;
                    case "-d": // debugging
                        debug = true;
                        i++;
                        LOG.setLevel(Level.FINE);
                        break;
                    case "-help": // write a summary of the command line options to the std out
                        help = true;
                        i++;
                        break;

                    // if unrecognised arguement, print help string and exit
                    // otherwise, it's a VEO to resign
                    default:
                        if (args[i].charAt(0) == '-') {
                            throw new AppFatal(classname, 2, "Unrecognised argument '" + args[i] + "'. Usage: " + USAGE);
                        }
                        veoDirectories.add(args[i]);
                        i++;
                }
            }
        } catch (ArrayIndexOutOfBoundsException ae) {
            throw new AppFatal(classname, 3, "Missing argument. Usage: " + USAGE);
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
    private Path checkFile(String type, String name, boolean isDirectory) throws AppFatal {
        Path p;

        try {
            p = Paths.get(name).normalize();
        } catch (InvalidPathException ipe) {
            throw new AppFatal(classname, 9, type + " '" + name + "' is not a valid file name: " + ipe.getMessage());
        }
        if (!Files.exists(p)) {
            throw new AppFatal(classname, 6, type + " '" + p.toAbsolutePath().toString() + "' does not exist");
        }
        if (isDirectory && !Files.isDirectory(p)) {
            throw new AppFatal(classname, 7, type + " '" + p.toAbsolutePath().toString() + "' is a file not a directory");
        }
        if (!isDirectory && Files.isDirectory(p)) {
            throw new AppFatal(classname, 8, type + " '" + p.toAbsolutePath().toString() + "' is a directory not a file");
        }
        return p;
    }

    /**
     * Resign VEOs specified in the command line.
     *
     * @throws AppFatal if an error occurs that prevents any further VEOs from
     * being constructed
     */
    public void resignVEOs() throws AppFatal {
        int i;
        String s;
        Path veoDir;

        // go through list of VEO directories
        for (i = 0; i < veoDirectories.size(); i++) {

            // get directory name and sanity check it...
            s = veoDirectories.get(i);
            if (!s.endsWith(".veo")) {
                LOG.log(Level.INFO, "VEO name ''{0}'' must end with ''.veo''", s);
                continue;
            }
            try {
                veoDir = checkFile("VEO directory", s, true);
            } catch (AppFatal e) {
                LOG.severe(e.getMessage());
                continue;
            }
            switch (task) {
                case VERIFY:
                    LOG.log(Level.INFO, "{0} Verifying signatures: {1}", new Object[]{VERSDate.versDateTime(0), veoDir.toString()});
                    break;
                case RENEW:
                    LOG.log(Level.INFO, "{0} Renewing VEOContent signatures: {1}", new Object[]{VERSDate.versDateTime(0), veoDir.toString()});
                    break;
                case CREATE:
                    LOG.log(Level.INFO, "{0} Resigning from scratch: {1}", new Object[]{VERSDate.versDateTime(0), veoDir.toString()});
                    break;
            }
            doTask(veoDir);
        }
        LOG.log(Level.INFO, "{0} Finished!", new Object[]{VERSDate.versDateTime(0)});
    }

    /**
     * Perform the requested resigning task on a VEO
     *
     * @param veoDir the directory representing the unpacked VEO
     * @throws AppError if something failed in resigning this VEO
     */
    private void doTask(Path veoDir) throws AppFatal {
        CreateVEO veo;      // current VEO being created
        boolean contentSigsPassed; // the existing VEOContentSignatures verified
        boolean historySigsPassed; // the existing VEOHistorySignatures verified
        ArrayList<RepnSignature> contentSigs = new ArrayList<>(); // list of VEOContent signatures in VEO
        ArrayList<RepnSignature> historySigs = new ArrayList<>(); // list of VEOHistory signatures in VEO
        RepnSignature rs;
        int i;
        StringBuilder sb = new StringBuilder();

        // create a VEO from the VEO directory...
        veo = null;
        try {
            // test the existing digital signatures
            contentSigsPassed = checkSignatures(veoDir, contentSigs, "VEOContent");
            historySigsPassed = checkSignatures(veoDir, historySigs, "VEOHistory");

            veo = new CreateVEO(veoDir, hashAlg, debug);

            switch (task) {
                case VERIFY:
                    if (eventDesc != null) {
                        sb.append(eventDesc);
                        sb.append(". ");
                    }
                    if (contentSigsPassed && historySigsPassed) {
                        sb.append("All signatures checked and were valid. ");
                    } else {
                        sb.append("Signatures checked, but some failed:\n");
                        for (i = 0; i < contentSigs.size(); i++) {
                            rs = contentSigs.get(i);
                            sb.append(" ");
                            sb.append(rs.getSigFilename());
                            sb.append(rs.isValid() ? " VALID" : " FAILED (but kept)");
                            sb.append("\n");
                        }
                        for (i = 0; i < historySigs.size(); i++) {
                            rs = historySigs.get(i);
                            sb.append(" ");
                            sb.append(rs.getSigFilename());
                            sb.append(rs.isValid() ? " VALID" : " FAILED (and removed)");
                            sb.append("\n");
                        }
                    }
                    sb.append("VEOHistory.xml updated, old VEOHistory.xml signatures removed, and new VEOHistory.xml signature applied.");
                    addEvent(veoDir, "Signature verification", userDesc, sb.toString());
                    sign(veo, SignType.VEOHistory);
                    deleteOldSignatures(veoDir, historySigs, true);
                    break;
                case RENEW:
                    if (eventDesc != null) {
                        sb.append(eventDesc);
                        sb.append(". ");
                    }
                    if (!contentSigsPassed) {
                        sb.append("The following VEOContent.xml signatures failed and have been removed:\n");
                        for (i = 0; i < contentSigs.size(); i++) {
                            rs = contentSigs.get(i);
                            if (!rs.isValid()) {
                                sb.append(" ");
                                sb.append(rs.getSigFilename());
                                sb.append("\n");
                            }
                        }
                    }
                    if (!historySigsPassed) {
                        sb.append("The following VEOHistory.xml signatures failed:\n");
                        for (i = 0; i < historySigs.size(); i++) {
                            rs = historySigs.get(i);
                            if (!rs.isValid()) {
                                sb.append(" ");
                                sb.append(rs.getSigFilename());
                                sb.append("\n");
                            }
                        }
                    }
                    sb.append("A new VEOContent.xml signature was applied. ");
                    sb.append("VEOHistory.xml updated, old VEOHistory.xml signatures removed, and new VEOHistory.xml signature applied.");
                    addEvent(veoDir, "VEOContent.xml signature renewal", userDesc, sb.toString());
                    sign(veo, SignType.BOTH);
                    deleteOldSignatures(veoDir, historySigs, true);
                    deleteOldSignatures(veoDir, contentSigs, false);
                    break;
                case CREATE:
                    if (eventDesc != null) {
                        sb.append(eventDesc);
                        sb.append(". ");
                    }
                    sign(veo, SignType.BOTH);
                    deleteOldSignatures(veoDir, historySigs, true);
                    deleteOldSignatures(veoDir, contentSigs, true);
                    sb.append("All existing signatures have been removed and new VEOContent.xml and VEOHistory.xml signatures generated.");
                    break;
            }
            veo.finalise(outputDir, !noZIP, true);
            if (noZIP) {
                sb.append(" New ZIP file was NOT created as the -nozip option was set.\n");
            } else {
                sb.append("\n");
            }
            LOG.info(sb.toString());
        } catch (AppError | VEOError e) {
            LOG.log(Level.WARNING, "Failed building VEO ''{0}''. Cause: {1}\n", new Object[]{veoDir.toAbsolutePath().toString(), e.getMessage()});
            if (veo != null) {
                veo.abandon(true);
            }
        }
    }

    /**
     * Check to see if any of the VEOContentSignatures or VEOHistorySignatures
     * fail. If so, we remember this to replace them. We do not check if the
     * certificate chain validates the public key; this is not the problem we
     * are resolving in SignVEOs. Note that if *one* signature fails, we replace
     * all of them.
     *
     * @param veoDir The directory containing the contents of the unpacked VEO
     * @param schema The schema directory
     * @param type The type of signatures to be checked
     * @return true if all the signatures of the type passed
     * @throws VEOError
     */
    private boolean checkSignatures(Path veoDir, ArrayList<RepnSignature> sigs, String type) throws AppError {
        RepnSignature rs;
        String fileName;
        boolean passed;
        DirectoryStream<Path> ds;
        ResultSummary results = new ResultSummary();

        // go through files in VEO Directory, checking signature files 
        ds = null;
        passed = true;
        try {
            ds = Files.newDirectoryStream(veoDir);
            for (Path entry : ds) {
                fileName = entry.getFileName().toString();
                if (fileName.startsWith(type + "Signature") && fileName.endsWith(".xml")) {
                    rs = new RepnSignature(veoDir, fileName, supportDir, results);
                    passed &= rs.verifySignature(veoDir.resolve(type + ".xml"));
                    sigs.add(rs);
                }
            }
        } catch (VEOError e) {
            throw new AppError(classname, "checkSignatures", 1, "Processing signature file failed: " + e.getMessage());
        } catch (DirectoryIteratorException e) {
            throw new AppError(classname, "checkSignatures", 2, "Directory iterator failed: " + e.getMessage());
        } catch (IOException e) {
            throw new AppError(classname, "checkSignatures", 3, "Failed to open the VEO directory for reading files: " + e.getMessage());
        } finally {
            if (ds != null) {
                try {
                    ds.close();
                } catch (IOException ioe) {
                    /* ignore */ }
            }
        }
        return passed;
    }

    /**
     * Add an event to the VEOHistory.xml file documenting the resigning.If any
     * failure occurs, the original VEOHistory.xml file is unaltered.
     *
     * @param veoDir the directory containing the contents of the unpacked VEO
     * @param eventType a string describing the event being documented
     * @param user the user responsible for the event (can be null)
     * @param desc a description of the event
     * @throws VERSCommon.AppError Something went wrong
     */
    private void addEvent(Path veoDir, String eventType, String user, String desc) throws AppError {
        Path tmpVEOHistory, veoHistory;
        FileInputStream fis;
        InputStreamReader isr;
        BufferedReader br;
        FileOutputStream fos;
        OutputStreamWriter osw;
        BufferedWriter bw;
        String line, s;
        int i;

        assert veoDir != null;
        assert eventType != null;
        assert user != null;
        assert desc != null;

        tmpVEOHistory = null;

        try {
            // open temporary file for writing in VEO directory
            tmpVEOHistory = Files.createTempFile(veoDir, null, null);
            fos = new FileOutputStream(tmpVEOHistory.toFile());
            osw = new OutputStreamWriter(fos);
            bw = new BufferedWriter(osw);

            // open VEOHistory.xml for reading
            veoHistory = veoDir.resolve("VEOHistory.xml");
            fis = new FileInputStream(veoHistory.toFile());
            isr = new InputStreamReader(fis, "UTF-8");
            br = new BufferedReader(isr);

            // copy VEOHistory.xml to temporary file adding new event before
            // the end </vers:VEOHistory> text
            while ((line = br.readLine()) != null) {
                if ((i = line.indexOf("</vers:VEOHistory>")) != -1) {
                    s = line.substring(0, i);
                    if (!s.trim().equals(" ")) {
                        bw.write(s);
                    }
                    bw.write(" <vers:Event>\n  <vers:EventDateTime>");
                    bw.write(VERSDate.versDateTime(0));
                    bw.write("</vers:EventDateTime>\n  <vers:EventType>");
                    bw.write(eventType);
                    bw.write("</vers:EventType>\n  <vers:Initiator>");
                    bw.write(user);
                    bw.write("</vers:Initiator>\n  <vers:Description>\n");
                    bw.write(desc);
                    bw.write("\n  </vers:Description>\n </vers:Event>\n");
                    bw.write("</vers:VEOHistory>\n");
                } else {
                    bw.write(line);
                    bw.write("\n");
                }
            }

            bw.close();
            osw.close();
            fos.close();

            br.close();
            isr.close();
            fis.close();

            // move temporary file to VEOHistory.xml
            Files.move(tmpVEOHistory, veoHistory, REPLACE_EXISTING);
        } catch (IOException ioe) {
            throw new AppError(classname, "addEvent", 1, "Adding new event to VEOHistory.xml file failed: " + ioe.getMessage());
        } finally {
            if (tmpVEOHistory != null) {
                File f = tmpVEOHistory.toFile();
                if (f.exists()) {
                    f.delete();
                }
            }
        }
    }

    /**
     * Delete the existing signatures.The VEOContentSignatures are only deleted
     * if the VEOContent.xml has been modified since the signatures have been
     * created.
     *
     * @param veoDir the VEO directory we are resigning
     * @param type
     * @throws VEOError if the signatures couldn't be deleted
     */
    private void deleteSignatures(Path veoDir, String type) throws VEOError {
        String method = "deleteSignatures";
        String fileName;
        DirectoryStream<Path> ds;

        // go through files in VEO Directory, looking for signature files
        ds = null;
        try {
            ds = Files.newDirectoryStream(veoDir);
            for (Path entry : ds) {
                fileName = entry.getFileName().toString();

                // delete content signature files older than the VEOContent.xml file
                if (fileName.startsWith(type + "Signature") && fileName.endsWith(".xml")) {
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
     * Delete old signatures. The signatures deleted are those present at the
     * start of processing (i.e. in the array of RepnSignatures). If allSigs
     * is true, all of the signatures at the start are deleted. If allSigs is
     * false, only those signatures that are invalid are deleted.
     */
    private void deleteOldSignatures(Path veoDir, ArrayList<RepnSignature> sigs, boolean allSigs) throws AppFatal {
        int i;
        RepnSignature rs;
        Path p;

        for (i = 0; i < sigs.size(); i++) {
            rs = sigs.get(i);
            try {
                if (allSigs || !rs.isValid()) {
                    p = veoDir.resolve(rs.getSigFilename());
                    if (p != null) {
                        p.toFile().delete();
                    }
                }
            } catch (VEOError e) {
                throw new AppFatal(classname, "deleteInvalidSignatures", 1, "Internal representation of signature is invalid: " + e.getMessage());
            }
        }
    }

    /**
     * Generate a VEOContentSignature.xml or VEOHistorySignature.xml file. If
     * multiple PFX files have been specified, multiple pairs of signature files
     * are generated.
     *
     * @param veo encapsulation of VEO
     * @param type whether VEOContent or VEOHistory signatures are required
     * @throws VEOError if the signing failed for any reason
     */
    private void sign(CreateVEO veo, SignType type) throws VEOError {
        for (PFXUser user : signers) {
            LOG.log(Level.FINE, "Signing {0} with ''{1}''", new Object[]{user.toString(), hashAlg});
            veo.sign(type, user, hashAlg);
        }
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
            sv = new SignVEOs(args);
            sv.resignVEOs();
        } catch (AppFatal e) {
            LOG.warning(e.getMessage());
        }
    }
}
