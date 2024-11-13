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
import VERSCommon.VEOFailure;
import VERSCommon.VERSDate;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

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
    boolean rezipVEO;         // true if rezipping VEO at end
    boolean overwrite;      // true if ok to overwrite an existing VEO when zipping or unzipping a VEO
    boolean verbose;        // true if generate lots of detail
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
        CREATE, // delete old signatures & resign without updating VEOHistory
        ADDEVENT            // add an event, deleting the old history signatures
    }

    static String USAGE = "SignVEOs -verify|-renew|-create|-addevent -s <pfxFile> <password> -support <directory> [-zip] [-overwrite] [-u user] [-e eventDesc] [-ha <hashAlgorithm] [-o <outputDir>] [-v] [-d] fileName*";

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
     * 20240703 2.5 Added -zip, -overwrite, and -addevent options, further improved logging
     * 20241113 2.6 Minor bug fix
     * 20241113 2.7 Changed error message when source VEO could not be found
     * </pre>
     */
    static String version() {
        return ("2.7");
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
        try {
            outputDir = Paths.get(".").toRealPath(); // default is the current working directory
        } catch (IOException ioe) {
            throw new AppFatal("Failed converting current working directory to a real path: " + ioe.getMessage());
        }
        userDesc = null;
        eventDesc = null;
        signers = new LinkedList<>();
        veoDirectories = new ArrayList<>();
        rezipVEO = false;
        overwrite = false;
        verbose = false;
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
            LOG.warning("  -verify -renew, -create, or -addevent: task to perform");
            LOG.warning("  -support <direct>: path directory where schema files are found");
            LOG.warning("  one or more VEOs");
            LOG.warning("");
            LOG.warning(" Optional:");
            LOG.warning("  -s <pfxFile> <password>: path to a PFX file and its password for signing a VEO (can be repeated)");
            LOG.warning("  -u <userDesc>: a description of the user resigning the file");
            LOG.warning("  -e <eventDesc>: a description of the event causing the resigning");
            LOG.warning("  -ha <hashAlgorithm>: specifies the hash algorithm (default SHA-256)");
            LOG.warning("  -o <directory>: the directory in which the VEOs are created (default is current working directory)");
            LOG.warning("  -zip: zip the resulting VEO at end");
            LOG.warning("  -overwrite: overwrite an existing unzipped or zipped VEO when unpacking or packing a .veo.zip' VEO");
            LOG.warning("");
            LOG.warning("  -v: verbose mode: give more details about processing");
            LOG.warning("  -d: debug mode: give a lot of details about processing");
            LOG.warning("  -help: print this listing");
            LOG.warning("");
        }

        // set and check config
        if (task == Task.NOTSPECIFIED) {
            throw new AppFatal(classname, 2, "No task specified: must use -verify, -renew, -addevent, or -create. Usage: " + USAGE);
        }
        if (supportDir == null) {
            throw new AppFatal(classname, 3, "Support directory is not specified. Usage: " + USAGE);
        }
        if (signers.isEmpty()) {
            throw new AppFatal(classname, 4, "No PFX files were supplied to resign (-s option). Usage: " + USAGE);
        }
        if (veoDirectories.isEmpty()) {
            throw new AppFatal(classname, 5, "No VEOs specified to resign. Usage: " + USAGE);
        }
        if (task == Task.ADDEVENT && (eventDesc == null || eventDesc.trim().isEmpty())) {
            throw new AppFatal(classname, 6, "No event description specified (-e option) with -addevent. Usage: " + USAGE);
        }
        if (task == Task.CREATE && (eventDesc != null || userDesc != null)) {
            throw new AppFatal(classname, 7, "Cannot use -e or -u with -create. Use -renew. Usage: " + USAGE);
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
            case ADDEVENT:
                LOG.info(" Add an event to VEOHistory.xml & replace VEOHistory signatures");
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
        if (overwrite) {
            LOG.log(Level.INFO, " Unpack any '.veo.zip' VEOs into the output directory. OVERWRITE any existing unpacked copy of the VEO in the output directory");
        } else {
            LOG.log(Level.INFO, " Unpack any '.veo.zip' VEOs into the output directory but only if no unpacked output exists");
        }
        if (rezipVEO) {
            if (overwrite) {
                LOG.log(Level.INFO, " Rezip VEO after resigning. OVERWRITE any existing zipped copy of the VEO in the output directory");
            } else {
                LOG.log(Level.INFO, " Rezip VEO after resigning - if a zipped copy of the VEO does not exist in the output directory");
            }
        } else {
            LOG.log(Level.INFO, " Do NOT rezip VEO after resigning");
        }
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
                    case "-addevent": // add an event to the history, and delete the history signatures
                        i++;
                        task = Task.ADDEVENT;
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
                    case "-zip": // reZIP VEO at end
                        i++;
                        rezipVEO = true;
                        break;
                    case "-overwrite": // overwrite existing VEOs when zipping or unzipping
                        i++;
                        overwrite = true;
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
            p = Paths.get(name).toAbsolutePath().normalize();
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
        int i, j;
        Path givenVEOPath;  // whatever path from command line argument
        String veoName;     // name from command line argument
        Path veoDir;        // actual path containing the unpacked VEO

        // go through list of VEO directories
        for (i = 0; i < veoDirectories.size(); i++) {

            // get directory name and sanity check it. Unpack into the output
            // directory if still zipped
            try {
                givenVEOPath = Paths.get(veoDirectories.get(i)).toRealPath();
            } catch (IOException ioe) {
                LOG.log(Level.SEVERE, "Could not find ''{0}''", new Object[]{veoDirectories.get(i)});
                continue;
            }
            if (!givenVEOPath.toFile().exists()) {
                LOG.log(Level.WARNING, "File ''{0}'' does not exist", new Object[]{givenVEOPath});
                continue;
            }
            veoName = givenVEOPath.getFileName().toString();

            // if being asked to resign a packed VEO, unzip it first
            if (veoName.toLowerCase().endsWith(".veo.zip")) {
                j = veoName.toLowerCase().lastIndexOf(".zip");
                String s = veoName.substring(0, j);
                veoDir = outputDir.resolve(Paths.get(s));
                if (veoDir.toFile().exists()) {
                    if (overwrite) {
                        try {
                            deleteFile(veoDir);
                        } catch (IOException ioe) {
                            LOG.log(Level.WARNING, "{0}: Could not delete unpacked VEO.", new Object[]{veoDir.toString()});
                        }
                    } else {
                        log(Level.WARNING, givenVEOPath, "FAILED. VEO unchanged. Cause: Unpacked VEO already exists & -overwrite not set.");
                        continue;
                    }
                }
                try {
                    unzip(givenVEOPath, outputDir);
                } catch (VEOError e) {
                    log(Level.WARNING, givenVEOPath, "FAILED unzipping. VEO unchanged. Cause: " + e.getMessage());
                    continue;
                }
            } else if (veoName.toLowerCase().endsWith(".veo")) {
                veoDir = outputDir.resolve(Paths.get(veoName));
            } else {
                LOG.log(Level.WARNING, "VEO name ''{0}'' must end with ''.veo'' or ''.veo.zip''", givenVEOPath);
                continue;
            }

            doTask(givenVEOPath, veoDir);
        }
    }

    /**
     * Perform the requested resigning task on a VEO
     *
     * @param givenVEOPath the file containing the original VEO
     * @param veoDir the file containing the unpacked VEO
     * @throws AppError if something failed in resigning this VEO
     */
    private void doTask(Path givenVEOPath, Path veoDir) throws AppFatal {
        CreateVEO veo;      // current VEO being created
        boolean contentSigsPassed; // the existing VEOContentSignatures verified
        boolean historySigsPassed; // the existing VEOHistorySignatures verified
        ArrayList<RepnSignature> contentSigs = new ArrayList<>(); // list of VEOContent signatures in VEO
        ArrayList<RepnSignature> historySigs = new ArrayList<>(); // list of VEOHistory signatures in VEO
        RepnSignature rs;
        int i;
        StringBuilder eventMesg = new StringBuilder();
        StringBuilder logMesg = new StringBuilder();
        Level logMesgLevel;
        String s;

        // create a VEO from the VEO directory...
        veo = null;
        logMesgLevel = Level.INFO;
        try {
            // test the existing digital signatures
            contentSigsPassed = checkSignatures(veoDir, contentSigs, "VEOContent");
            historySigsPassed = checkSignatures(veoDir, historySigs, "VEOHistory");

            veo = new CreateVEO(veoDir, hashAlg, debug);

            switch (task) {
                case VERIFY:
                    if (eventDesc != null) {
                        eventMesg.append(eventDesc);
                        eventMesg.append(". ");
                    }
                    if (contentSigsPassed && historySigsPassed) {
                        eventMesg.append("All signatures checked and were valid. ");
                    } else {
                        eventMesg.append("Signatures checked, but some failed:\n");
                        for (i = 0; i < contentSigs.size(); i++) {
                            rs = contentSigs.get(i);
                            eventMesg.append(" ");
                            eventMesg.append(rs.getSigFilename());
                            eventMesg.append(rs.isValid() ? " VALID" : " FAILED (but kept)");
                            eventMesg.append("\n");
                        }
                        for (i = 0; i < historySigs.size(); i++) {
                            rs = historySigs.get(i);
                            eventMesg.append(" ");
                            eventMesg.append(rs.getSigFilename());
                            eventMesg.append(rs.isValid() ? " VALID" : " FAILED (and removed)");
                            eventMesg.append("\n");
                        }
                    }
                    eventMesg.append("VEOHistory.xml updated, old VEOHistory.xml signatures removed, and new VEOHistory.xml signature(s) applied.");
                    addEvent(veoDir, "Signature verification", userDesc, eventMesg.toString());
                    sign(veo, SignType.VEOHistory);
                    deleteOldSignatures(veoDir, historySigs, true);
                    if (contentSigsPassed && historySigsPassed) {
                        logMesg.append("Signatures verified. VEO history updated. ");
                    } else {
                        logMesg.append("Some signatures FAILED. VEO history updated. ");
                        logMesgLevel = Level.SEVERE;
                    }
                    break;
                case RENEW:
                    if (eventDesc != null) {
                        eventMesg.append(eventDesc);
                        eventMesg.append(". ");
                    }
                    if (!contentSigsPassed) {
                        eventMesg.append("The following VEOContent.xml signatures failed and have been removed:\n");
                        for (i = 0; i < contentSigs.size(); i++) {
                            rs = contentSigs.get(i);
                            if (!rs.isValid()) {
                                eventMesg.append(" ");
                                eventMesg.append(rs.getSigFilename());
                                eventMesg.append("\n");
                            }
                        }
                    }
                    if (!historySigsPassed) {
                        eventMesg.append("The following VEOHistory.xml signatures failed:\n");
                        for (i = 0; i < historySigs.size(); i++) {
                            rs = historySigs.get(i);
                            if (!rs.isValid()) {
                                eventMesg.append(" ");
                                eventMesg.append(rs.getSigFilename());
                                eventMesg.append("\n");
                            }
                        }
                    }
                    eventMesg.append("New VEOContent.xml signature(s) were applied. ");
                    eventMesg.append("VEOHistory.xml updated, old VEOHistory.xml signatures removed, and new VEOHistory.xml signature(s) applied.");
                    addEvent(veoDir, "VEOContent.xml signature renewal", userDesc, eventMesg.toString());
                    sign(veo, SignType.BOTH);
                    deleteOldSignatures(veoDir, historySigs, true);
                    deleteOldSignatures(veoDir, contentSigs, false);
                    logMesg.append("Signatures renewed. VEO history updated. ");
                    break;
                case CREATE:
                    sign(veo, SignType.BOTH);
                    deleteOldSignatures(veoDir, historySigs, true);
                    deleteOldSignatures(veoDir, contentSigs, true);
                    logMesg.append("Signatures created. ");
                    break;
                case ADDEVENT:
                    if (eventDesc != null) {
                        eventMesg.append(eventDesc);
                        eventMesg.append(". ");
                    }
                    if (!historySigsPassed) {
                        eventMesg.append("The following VEOHistory.xml signatures were already invalid:\n");
                        for (i = 0; i < historySigs.size(); i++) {
                            rs = historySigs.get(i);
                            if (!rs.isValid()) {
                                eventMesg.append(" ");
                                eventMesg.append(rs.getSigFilename());
                                eventMesg.append("\n");
                            }
                        }
                    }
                    eventMesg.append("VEOHistory.xml updated, old VEOHistory.xml signatures removed, and new VEOHistory.xml signature(s) applied.");
                    addEvent(veoDir, "VEOHistory.xml event added", userDesc, eventMesg.toString());
                    sign(veo, SignType.VEOHistory);
                    deleteOldSignatures(veoDir, historySigs, true);
                    logMesg.append("Event added to history. VEOHistory resigned. ");
                    break;
            }

            // finalise VEO. Rezip the VEO if asked to and either the zipped
            // VEO doesn't exist, or it does and overwrite has been specified.
            // For all other cases, finalise the VEO without rezipping
            if (rezipVEO) {
                Path p;
                s = veoDir.getFileName().toString();
                p = outputDir.resolve(s + ".zip");
                if (!p.toFile().exists()) {
                    veo.finalise(outputDir, true, true);
                    logMesg.append("Rezipped");
                } else {
                    if (overwrite) {
                        try {
                            deleteFile(p);
                        } catch (IOException ioe) {
                            LOG.log(Level.WARNING, "{0}: Could not delete zipped VEO in output directory.", new Object[]{veoDir.toString()});
                        }
                        veo.finalise(outputDir, true, true);
                        logMesg.append("Rezipped");
                    } else {
                        veo.finalise(outputDir, false, true);
                        logMesg.append("Not rezipped (-overwrite not set)");
                    }
                }
            } else {
                veo.finalise(outputDir, false, true);
            }
        } catch (AppError | VEOError e) {
            logMesg.append("FAILED. VEO not updated. Cause: ");
            logMesg.append(e.getMessage());
            logMesgLevel = Level.SEVERE;
            if (veo != null) {
                veo.abandon(true);
            }
        }
        log(logMesgLevel, givenVEOPath, logMesg.toString());
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
     * Delete old signatures. The signatures deleted are those present at the
     * start of processing (i.e. in the array of RepnSignatures). If allSigs is
     * true, all of the signatures at the start are deleted. If allSigs is
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
     * Private function to recursively delete a directory or file. Needed
     * because you cannot delete a non empty directory
     *
     * @param file Path of directory or file to delete
     * @throws IOException if deleting file failed
     */
    private void deleteFile(Path file) throws IOException {
        DirectoryStream<Path> ds;

        // if a directory, list all the files and delete them
        if (Files.isDirectory(file)) {
            ds = Files.newDirectoryStream(file);
            for (Path p : ds) {
                deleteFile(p);
            }
            ds.close();
        }

        // finally, delete the file
        try {
            Files.delete(file);
        } catch (FileSystemException e) {
            LOG.log(Level.WARNING, "{0} Failed to delete temporary file: ", e.toString());
        }
    }

    /**
     * Private function to unzip a VEO file. This is almost identical to the
     * code in VEOAnalysis.RepnVEO - it was replicated because the calling
     * environment is completely different. In the original code, we tried to
     * find and report on all errors. In this code we give up when we find the
     * first.
     *
     * When unzipping, we follow the advice in the PKWARE application developer
     * notes
     * https://support.pkware.com/home/pkzip/developer-tools/appnote/application-developer-considerations
     * These recommend checking that the file name in the ZIP entries doesn't
     * point to arbitrary locations in the file system (especially containing
     * '..') and that the file sizes are reasonable.
     *
     * @param zipFilePath the path to the VEO file
     * @param outputDir the directory in which the VEO is to be unpacked
     */
    private void unzip(Path zipFilePath, Path outputDir) throws VEOError, AppFatal {
        String CLASSNAME = "unzip";
        ZipFile zipFile;
        Enumeration entries;
        ZipArchiveEntry entry;
        Path vze, zipEntryPath;
        InputStream is;
        BufferedInputStream bis;
        FileOutputStream fos;
        BufferedOutputStream bos;
        byte[] b = new byte[1024];
        int len, i;
        String veoName;
        long modTime;
        File f;

        // unzip the VEO file
        bos = null;
        fos = null;
        bis = null;
        is = null;
        zipFile = null;
        try {
            // get the name of this VEO, stripping off the final '.zip'
            veoName = zipFilePath.getFileName().toString();
            i = veoName.lastIndexOf(".");
            if (i != -1) {
                veoName = veoName.substring(0, i);
            }

            // open the zip file and get the entries in it
            f = zipFilePath.toFile();
            if (!f.exists()) {
                throw new VEOError("ZIP file doesn't exist: '" + zipFilePath.toString() + "'");
            }
            zipFile = new ZipFile(f);

            // be paranoid, just check that the supposed length of the
            // ZIP entry against the length of the ZIP file itself
            entries = zipFile.getEntries();
            long zipFileLength = f.length();
            long claimedLength = 0;
            while (entries.hasMoreElements()) {
                entry = (ZipArchiveEntry) entries.nextElement();
                claimedLength += entry.getCompressedSize();
            }
            if (zipFileLength < claimedLength) {
                throw new VEOError("ZIP file length (" + zipFileLength + ") is less than the sum of the compressed sizes of the ZIP entrys (" + claimedLength + ")");
            }

            // go through each entry
            entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                entry = (ZipArchiveEntry) entries.nextElement();
                LOG.log(Level.FINE, "Extracting: {0}({1}) {2} {3}", new Object[]{entry.getName(), entry.getSize(), entry.getTime(), entry.isDirectory()});

                // get the local path to extract the ZIP entry into
                // this is so horrible because Paths.resolve won't process
                // windows file separators in a string on Unix boxes
                String safe = entry.getName().replaceAll("\\\\", "/");
                try {
                    zipEntryPath = Paths.get(safe);
                } catch (InvalidPathException ipe) {
                    throw new VEOError("ZIP path entry '" + safe + "' is invalid: " + ipe.getMessage());
                }

                // complain (once!) if filename of the VEO is different to the
                // base of the filenames in the ZIP file (e.g. the VEO file has
                // been renamed)
                if (!veoName.equals(zipEntryPath.getName(0).toString())) {
                    if (zipEntryPath.getNameCount() == 1) {
                        throw new VEOError("The names of the entries in the ZIP file (e.g. '" + entry.getName() + "') do not start with the name of the veo ('" + veoName + "')");
                    } else {
                        throw new VEOError("The filename of the VEO (" + veoName + ") is different to that contained in the entries in the ZIP file (" + entry.getName() + ")");
                    }
                }

                // doesn't matter what the ZIP file says, force the extract to
                // be in a directory with the same name as the VEO filename
                // (even if we have complained about this)
                try {
                    if (zipEntryPath.getNameCount() > 1) {
                        zipEntryPath = zipEntryPath.subpath(1, zipEntryPath.getNameCount());
                        vze = outputDir.resolve(veoName).resolve(zipEntryPath);
                    } else {
                        vze = outputDir.resolve(veoName);
                    }
                } catch (InvalidPathException ipe) {
                    throw new VEOError("File name '" + veoName + "' is invalid" + ipe.getMessage());
                }

                // where does the file name in the ZIP entry really point to?
                vze = vze.normalize();

                // be really, really, paranoid - the file we are creating
                // shouldn't have any 'parent' ('..') elements in the file path
                for (i = 0; i < vze.getNameCount(); i++) {
                    if (vze.getName(i).toString().equals("..")) {
                        throw new VEOError("ZIP file contains a pathname that includes '..' elements: '" + zipEntryPath + "'");
                    }
                }

                // just be cynical and check that the file name to be extracted
                // from the ZIP file is actually in the VEO directory...
                if (!vze.startsWith(outputDir)) {
                    throw new VEOError("ZIP entry in VEO '" + veoName + "' is attempting to create a file outside the VEO directory '" + vze.toString());
                }

                // make any directories...
                if (entry.isDirectory()) {
                    Files.createDirectories(vze);
                } else {
                    // make any missing directories parent
                    Files.createDirectories(vze.getParent());

                    // extract file
                    is = zipFile.getInputStream(entry);
                    bis = new BufferedInputStream(is);
                    fos = new FileOutputStream(vze.toFile());
                    bos = new BufferedOutputStream(fos, 1024);
                    while ((len = bis.read(b, 0, 1024)) != -1) {
                        bos.write(b, 0, len);
                    }
                    bos.flush();
                    bos.close();
                    bos = null;
                    fos.flush();
                    fos.close();
                    fos = null;
                    bis.close();
                    bis = null;
                    is.close();
                    is = null;
                }

                // set the time of the file
                if ((modTime = entry.getTime()) != -1) {
                    Files.setLastModifiedTime(vze, FileTime.fromMillis(modTime));
                }
            }
            zipFile.close();
        } catch (ZipException e) {
            throw new VEOError("ZIP format error in opening Zip file: " + e.getMessage());
        } catch (IOException e) {
            throw new VEOError("IO error reading Zip file: " + e.getMessage());
        } finally {
            try {
                if (bos != null) {
                    bos.close();
                }
                if (fos != null) {
                    fos.close();
                }
                if (bis != null) {
                    bis.close();
                }
                if (is != null) {
                    is.close();
                }
                if (zipFile != null) {
                    zipFile.close();
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, VEOFailure.getMessage(CLASSNAME, "unzip", 10, "IOException in closing Zip files", e));
            }
        }
    }

    /**
     * Internal logging method to ensure consistent reporting
     */
    private void log(Level logLevel, Path veo, String s) {
        StringBuilder sb = new StringBuilder();

        sb.append(VERSDate.versDateTime(0));
        sb.append(" ");
        sb.append(veo.toString());
        sb.append(" ");
        sb.append(s);
        LOG.log(logLevel, sb.toString());
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
