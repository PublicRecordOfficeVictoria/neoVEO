/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 * History 20170608 File references in control file can be absolute, or relative
 * to either control file or current working directory 20170825 Added -e &lt;str&gt;
 * so that non ASCII control files are handled correctly
 */
package VEOCreate;

import VERSCommon.VERSDate;
import VERSCommon.PFXUser;
import VERSCommon.VEOFatal;
import VERSCommon.VEOError;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.PatternSyntaxException;

/**
 * This class creates multiple VEOs from a control file. The control file is a
 * text file containing multiple rows of tab separated commands. Each command
 * builds a part of a VEO (or controls how subsequent VEOs are to be built).
 * This class also processes the command line arguments and reads the metadata
 * templates.
 * <h3>Command Line arguments</h3>
 * The following command line arguments must be supplied:
 * <ul>
 * <li><b>-t &lt;directory&gt;</b> the directory in which the metadata templates
 * and the standard VEOReadme.txt file will be found. See the last section for
 * details about the metadata templates.</li>
 * <li><b>-c &lt;file&gt;</b> the control file which controls the production of
 * VEOs (see the next section for details about the control file).
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
 * <li><b>-copy</b> If present, this argument forces content files to be copied
 * to the VEO directory when creating the VEO. This is the slowest option, but
 * it is the most certain to succeed.</li>
 * <li><b>-move</b> If present, the content files will be moved to the VEO
 * directory. This is faster than -copy, but typically can only be performed on
 * the same file system.</li>
 * <li><b>-link</b> If present, the content files will be linked to the VEO
 * directory. This is the fastest option, but may not work on all computer
 * systems and files. -link is the default</li>
 * <li><b>-e &lt;encoding&gt;</b> If present this specifies the encoding used to
 * convert the control file into characters. The default is 'windows-1252' as
 * PROV runs on Windows boxes. Other useful choices are 'UTF-8' and 'ISO-8859-1"
 * </li>
 * </ul>
 * <p>
 * A minimal example of usage is<br>
 * <pre>
 *     createVEOs -c data.txt -t templates
 * </pre>
 * <h3>Control File</h3>
 * A control file is a text file with multiple lines. Each line contains tab
 * separated text. The first entry on each line is the command, subsequent
 * entries on the line are arguments to the command. The commands are:
 * <ul>
 * <li><b>'!'</b> A comment line. The remainder of the line is ignored.</li>
 * <li><b>'HASH' &lt;algorithm&gt;</b> Specifies the hash algorithm to use. If
 * present, this overrides the -ha command line argument. It must appear at the
 * start of the control file, before the first 'BV' command.</li>
 * <li><b>'PFX' &lt;pfxFile&gt; &lt;password&gt;</b> Specifies a PFX file and
 * associated password. Multiple PFX lines may be present, this results in
 * multiple signatures being generated. PFX commands must occur before the first
 * BV command.</li>
 * <li><b>'BV' &lt;veoName&gt;</b> Begin a new VEO. The single argument is the
 * VEO name (i.e. the file name of the VEO to be generated). If a VEO is already
 * being constructed, a BV command will cause the generation of the previous
 * VEO.</li>
 * <li><b>'IO' &lt;type&gt; [&lt;level&gt;]</b> Begin a new Information Object
 * within a VEO. The Information Object will have the specified type (which may
 * be blank) and level. If the level is not present, it will be set to 0. If an
 * Information Object is already being constructed, a new IO command will finish
 * the previous Information Object.</li>
 * <li><b>'MP' &lt;template&gt; [&lt;subs&gt;...]</b> Begin a new Metadata
 * Package within an Information Object. The first argument is the template
 * name, subsequent arguments are the substitutions. An MP command may be
 * followed by MPC commands to construct a metadata package from several
 * templates. Another MP command will finish this Metadata Package and begin a
 * new one.</li>
 * <li><b>'MPC' &lt;template&gt; [&lt;subs&gt;...]</b> Continue a Metadata
 * Package using another template and substitutions.</li>
 * <li><b>'IP' [&lt;label&gt;] &lt;file&gt; [&lt;files&gt;...]</b> Add an
 * Information Piece to the Information Object. The first (optional) argument is
 * the label for the information piece, subsequent arguments are the content
 * files to include in the Information Piece. An IP command must be after all
 * the MP and MPC commands in this Information Object.</li>
 * <li><b> 'E' &lt;date&gt; &lt;event&gt; &lt;initiator&gt;
 * [&lt;description&gt;...] ['$$' &lt;error&gt;...]</b> Add an event to the VEO
 * History file. The first argument is the date/time of the event, the second a
 * label for the type of event, the third the name of the initiator of the
 * event. Then there are a series of arguments describing the event, and finally
 * an option special argument ('$$') and a series of error messages. Events may
 * occur at any point within the construction of a VEO (i.e. after a BV
 * command).</li>
 * </ul>
 * <p>
 * A simple example of a control file is:<br>
 * <pre>
 * hash	SHA-1
 * pfx	signer.pfx	Password
 *
 * !	VEO with two IOs, one with an MP and one IP, the other with an MP and two IPs, two Es)
 * BV	testVEO5
 * AC	S-37-6
 * IO	Record	1
 * MP	agls	laserfish   data    data    etc
 * MP	agls	fishlaser   data    data    etc
 * IP	Data	S-37-6/S-37-6-Nov.docx
 * IO	Data	2
 * IP	Content	S-37-6/S-37-6-Nov.docx	S-37-6/S-37-6-Nov.docx
 * E	2014-09-09	Opened	Andrew	Description	$$  Error
 * E	2014-09-10	Closed	Andrew	Description
 * </pre>
 * <h3>Metadata Templates</h3>
 * The template files are found in the directory specified by the -t command
 * line argument. Templates are used to generate the metadata packages. Each MP
 * or MPC command in the control file specifies a template name (e.g. 'agls').
 * An associated text template file (e.g. 'agls.txt') must exist in the template
 * directory.
 * <p>
 * The template files contains the <i>contents</i> of the metadata package. The
 * contents composed of XML text, which will be included explicitly in each VEO,
 * and substitutions. The start of each substitution is marked by '$$' and the
 * end by '$$'. Possible substitutions are:
 * <ul>
 * <li>
 * $$ date $$ - substitute the current date and time in VERS format</li>
 * <li>
 * $$ [column] &lt;x&gt; $$ - substitute the contents of column &lt;x&gt;. Note
 * that keyword 'column' is optional.</li>
 * </ul>
 * <p>
 * The MP/MPC commands in the control file contain the information used in the
 * column or file substitutions. Note that the command occupies column 1, and
 * the template name column 2. So real data starts at column 3.
 */
public class CreateVEOs {

    static String classname = "CreateVEOs"; // for reporting
    FileOutputStream fos;   // underlying file stream for file channel
    Path templateDir;       // directory that holds the templates
    Path controlFile;       // control file to generate the VEOs
    Path baseDir;           // directory which to interpret the files in the control file
    Path outputDir;         // directory in which to place the VEOs
    List<PFXUser> signers;  // list of signers
    boolean chatty;         // true if report the start of each VEO
    boolean verbose;        // true if generate lots of detail
    boolean debug;          // true if debugging
    String hashAlg;         // hash algorithm to use
    String inputEncoding;   // how to translate the characters in the control file to UTF-16
    Templates templates;    // database of templates

    // state of the VEOs being built
    private enum State {

        PREAMBLE, // No VEO has been generated
        VEO_STARTED, // VEO started, but Information Object has not
        VEO_FAILED // Construction of this VEO has failed, scan forward until new VEO is started
    }
    State state;      // the state of creation of the VEO

    private final static Logger rootLog = Logger.getLogger("veocreate");
    private final static Logger log = Logger.getLogger("veocreate.CreateVEOs");

    /**
     * Constructor. Processes the command line arguments to set program up, and
     * parses the metadata templates from the template directory.
     * <p>
     * The defaults are as follows. The templates are found in "./Templates".
     * Output is created in the current directory. The hash algorithm is
     * "SHA256".
     *
     * @param args command line arguments
     * @throws VEOFatal when cannot continue to generate any VEOs
     */
    public CreateVEOs(String[] args) throws VEOFatal {

        // sanity check
        if (args == null) {
            throw new VEOFatal(classname, 1, "Null command line argument");
        }

        // defaults...
        log.setLevel(null);
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s%n");
        rootLog.setLevel(Level.WARNING);
        templateDir = Paths.get(".", "Templates");
        outputDir = Paths.get("."); // default is the current working directory
        controlFile = null;
        signers = new LinkedList<>();
        verbose = false;
        chatty = false;
        debug = false;
        hashAlg = "SHA-1";
        inputEncoding = "UTF-8";
        state = State.PREAMBLE;

        // process command line arguments
        configure(args);

        // read templates
        templates = new Templates(templateDir);
    }

    /**
     * This method configures the VEO creator from the arguments on the command
     * line. See the general description of this class for the command line
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
        String usage = "CreateVEOs [-vv] [-v] [-d] -t <templateDir> -c <controlFile> [-s <pfxFile> <password>] [-o <outputDir>] [-ha <hashAlgorithm] [-copy|move|link] [-e <encoding>]";

        // check for no arguments...
        if (args.length == 0) {
            throw new VEOFatal(classname, 10, "No arguments. Usage: " + usage);
        }

        // process command line arguments
        i = 0;
        try {
            while (i < args.length) {
                switch (args[i].toLowerCase()) {

                    // get template directory
                    case "-t":
                        i++;
                        templateDir = checkFile("template directory", args[i], true);
                        log.log(Level.INFO, "Template directory is ''{0}''", templateDir.toString());
                        i++;
                        break;

                    // get data file
                    case "-c":
                        i++;
                        controlFile = checkFile("control file", args[i], false);
                        baseDir = controlFile.getParent();
                        log.log(Level.INFO, "Control file is ''{0}''", controlFile.toString());
                        i++;
                        break;

                    // get pfx file
                    case "-s":
                        i++;
                        pfxFile = checkFile("PFX file", args[i], false);
                        i++;
                        password = args[i];
                        log.log(Level.INFO, "PFX file is ''{0}'' with password ''{1}''", new Object[]{pfxFile.toString(), password});
                        i++;
                        user = new PFXUser(pfxFile.toString(), password);
                        signers.add(user);
                        break;

                    // get output directory
                    case "-o":
                        i++;
                        outputDir = checkFile("output directory", args[i], true);
                        log.log(Level.INFO, "Output directory is ''{0}''", outputDir.toString());
                        i++;
                        break;

                    // get hash algorithm
                    case "-ha":
                        i++;
                        hashAlg = args[i];
                        log.log(Level.INFO, "Hash algorithm is ''{0}''", hashAlg);
                        i++;
                        break;

                    // get input encoding
                    case "-e":
                        i++;
                        inputEncoding = args[i];
                        log.log(Level.INFO, "Input encoding is ''{0}''", inputEncoding);
                        i++;
                        break;

                    // copy content - ignore
                    case "-copy":
                        i++;
                        log.log(Level.INFO, "-copy argument is now redundant");
                        break;

                    // move content
                    case "-move":
                        i++;
                        log.log(Level.INFO, "-move argument is now redundant");
                        break;

                    // link content
                    case "-link":
                        i++;
                        log.log(Level.INFO, "-link argument is now reduntant");
                        break;

                    // if verbose...
                    case "-v":
                        chatty = true;
                        i++;
                        log.log(Level.INFO, "Verbose output is selected");
                        break;

                    // if very verbose...
                    case "-vv":
                        verbose = true;
                        i++;
                        rootLog.setLevel(Level.INFO);
                        log.log(Level.INFO, "Very verbose output is selected");
                        break;

                    // if debugging...
                    case "-d":
                        debug = true;
                        i++;
                        rootLog.setLevel(Level.FINE);
                        // log.log(Level.FINE, "Debug mode is selected");
                        break;

                    // if unrecognised arguement, print help string and exit
                    default:
                        throw new VEOFatal(classname, 2, "Unrecognised argument '" + args[i] + "'. Usage: " + usage);
                }
            }
        } catch (ArrayIndexOutOfBoundsException ae) {
            throw new VEOFatal(classname, 3, "Missing argument. Usage: " + usage);
        }

        // check to see that user specified a template directory and control file
        if (templateDir == null) {
            throw new VEOFatal(classname, 4, "No template directory specified. Usage: " + usage);
        }
        if (controlFile == null) {
            throw new VEOFatal(classname, 5, "No control file specified. Usage: " + usage);
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

        p = Paths.get(name);

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
     * Build VEOs specified by the control file. See the general description of
     * this class for a description of the control file and the various commands
     * that can appear in it.
     *
     * @throws VEOFatal if an error occurs that prevents any further VEOs from
     * being constructed
     */
    public void buildVEOs() throws VEOFatal {
        String method = "buildVEOs";
        FileInputStream fis;     // source of control file to build VEOs
        InputStreamReader isr;
        BufferedReader br;

        // sanity check (redundant, but just in case)...
        if (controlFile == null) {
            throw new VEOFatal(classname, method, 1, "Control file is null");
        }

        // open control file for reading
        try {
            fis = new FileInputStream(controlFile.toString());
            isr = new InputStreamReader(fis, inputEncoding);
            br = new BufferedReader(isr);
        } catch (FileNotFoundException e) {
            throw new VEOFatal(classname, method, 2, "Failed to open control file '" + controlFile.toString() + "'" + e.toString());
        } catch (UnsupportedEncodingException e) {
            throw new VEOFatal(classname, method, 3, "The encoding '" + inputEncoding + "' used when reading the control file is invalid");
        }

        // build VEOs
        buildVEOs(br);

        // close the control file
        try {
            br.close();
        } catch (IOException e) {
            /* ignore */ }
        try {
            isr.close();
            //fr.close();
        } catch (IOException e) {
            /* ignore */ }
        try {
            fis.close();
        } catch (IOException e) {
            /* ignore */ }
    }

    /**
     * Read commands from the Reader to build VEOs. See the general description of
     * this class for a description of the control file and the various commands
     * that can appear in it.
     *
     * @param br file to read the commands from
     * @throws VERSCommon.VEOFatal if prevented from continuing processing at all
     */
    public void buildVEOs(BufferedReader br) throws VEOFatal {
        String method = "buildVEOs";
        String s;           // current line read from control file
        String[] tokens;    // tokens extracted from line
        int line;           // which line in control file (for errors)
        int i;
        CreateVEO veo;      // current VEO being created

        // sanity check (redundant, but just in case)...
        if (controlFile == null) {
            throw new VEOFatal(classname, method, 1, "Control file is null");
        }

        // go through command file line by line
        line = 0;
        veo = null;
        try {
            while ((s = br.readLine()) != null && !s.toLowerCase().trim().equals("end")) {
                // log.log(Level.FINE, "Processing: ''{0}''", new Object[]{s});
                line++;

                // split into tokens and check for blank line
                tokens = s.split("\t");
                if (s.equals("") || tokens.length == 0) {
                    continue;
                }
                switch (tokens[0].toLowerCase().trim()) {

                    // comment - ignore line
                    case "!":
                        if (tokens.length < 2) {
                            break;
                        }
                        if (chatty) {
                            System.out.println("COMMENT: " + tokens[1]);
                        }
                        log.log(Level.FINE, "COMMENT: {0}", new Object[]{tokens[1]});
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
                        log.log(Level.FINE, "Using hash algorithm: ''{0}''", new Object[]{hashAlg});
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

                        try {
                            pfx = new PFXUser(getRealFile(tokens[1]).toString(), tokens[2]);
                        } catch (VEOError e) {
                            veoFailed(line, "In PFX command, failed to process PFX file", e);
                            break;
                        }
                        signers.add(pfx);
                        log.log(Level.FINE, "Using signer {0} with password ''{1}''", new Object[]{pfx.getUserDesc(), tokens[2]});
                        break;

                    // Begin a new VEO. If necessary, finish the old VEO up
                    case "bv":

                        // check that a signer has been defined
                        if (signers.isEmpty()) {
                            throw new VEOFatal(classname, method, 1, "Attempting to begin construction of a VEO without specifying a signer using a PFX command or -s command line argument");
                        }

                        // if we are already constructing a VEO, finalise it before starting a new one
                        if (veo != null) {
                            try {
                                veo.finishFiles();
                                sign(veo);
                                veo.finalise(false);
                            } catch (VEOError e) {
                                veoFailed(line, "When starting new BV command, failed to finalise previous VEO", e);
                                veo.abandon(debug);
                                if (e instanceof VEOFatal) {
                                    return;
                                }
                            }
                            veo = null;
                        }

                        // check command arguments
                        if (tokens.length < 2) {
                            veoFailed(line, "Missing VEO name in BV command (format: 'BV' <veoName>)");
                            if (veo != null) {
                                veo.abandon(debug);
                            }
                            veo = null;
                            break;
                        }

                        // tell the world if verbose...
                        if (chatty) {
                            System.out.println(System.currentTimeMillis() / 1000 + " Starting: " + tokens[1]);
                        }
                        log.log(Level.FINE, "Beginning VEO ''{0}'' (State: {1}) at {3}", new Object[]{tokens[1], state, System.currentTimeMillis() / 1000});

                        // create VEO & add VEOReadme.txt from template directory
                        try {
                            veo = new CreateVEO(outputDir, tokens[1], hashAlg, debug);
                            veo.addVEOReadme(templateDir);
                        } catch (VEOError e) {
                            veoFailed(line, "Failed in starting new VEO in BV command", e);
                            if (veo != null) {
                                veo.abandon(debug);
                            }
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }

                        // we have started...
                        state = State.VEO_STARTED;
                        break;

                    // Add content directories to a VEO
                    case "ac":
                        // ignore line if VEO failed...
                        if (state == State.VEO_FAILED) {
                            break;
                        }
                        if (veo == null) {
                            veoFailed(line, "AC command before first BV");
                            break;
                        }

                        // check the right number of arguments
                        if (tokens.length < 2) {
                            veoFailed(line, "Missing content directory in AC command (format: 'AC' <contentDirectory> [<contentDirectory>...])");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }
                        log.log(Level.FINE, "Adding content directories (State: {0})", new Object[]{state});

                        // go through list of directories adding them
                        try {
                            for (i = 1; i < tokens.length; i++) {
                                veo.addContent(getRealFile(tokens[i]));
                            }
                        } catch (VEOError e) {
                            veoFailed(line, "AC command failed", e);
                            veo.abandon(debug);
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }
                        break;

                    // Start a new information object
                    case "io":
                        String label;
                        int depth;

                        // ignore line if VEO failed...
                        if (state == State.VEO_FAILED) {
                            break;
                        }
                        if (veo == null) {
                            veoFailed(line, "IO command before first BV command");
                            break;
                        }

                        // check the right number of arguments
                        if (tokens.length < 2) {
                            veoFailed(line, "Missing label in IO command (format: 'IO' <label> [<level>])");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }

                        // default is anonymous IO with depth = 1
                        depth = 0;

                        // label is the first argument
                        label = tokens[1].trim();

                        // depth is second argument - if empty then default to 0
                        if (tokens.length > 2) {
                            try {
                                depth = new Integer(tokens[2]);
                            } catch (NumberFormatException e) {
                                veoFailed(line, "Level in IO command is not a valid integer");
                                veo.abandon(debug);
                                veo = null;
                                break;
                            }
                            if (depth < 0) {
                                veoFailed(line, "Level in IO command is not zero or a positive integer");
                                veo.abandon(debug);
                                veo = null;
                                break;
                            }
                        }

                        // add the information object
                        try {
                            veo.addInformationObject(label, depth);
                        } catch (VEOError e) {
                            veoFailed(line, "Error in an IO command", e);
                            veo.abandon(debug);
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }
                        log.log(Level.FINE, "Starting new Information Object ''{0}'' level {1} (State: {2})", new Object[]{label, depth, state});
                        break;

                    // start a new Metadata Package
                    case "mp":
                        // ignore line if VEO failed...
                        if (state == State.VEO_FAILED) {
                            break;
                        }
                        if (veo == null) {
                            veoFailed(line, "MP command before first BV command");
                            break;
                        }

                        // check the right number of arguments
                        if (tokens.length < 2) {
                            veoFailed(line, "Missing template in MP command (format: 'MP' <template> [<subs>...])");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }

                        log.log(Level.FINE, "Starting new Metadata Package ''{1}'' (State: {0})", new Object[]{state, tokens[1]});

                        // get template
                        try {
                            veo.addMetadataPackage(templates.findTemplate(tokens[1]), tokens);
                        } catch (VEOError e) {
                            veoFailed(line, "Applying template '" + tokens[1] + "' in MP command failed", e);
                            veo.abandon(debug);
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }

                        // log.log(Level.FINE, "Found template. Schema ''{0}'' Syntax ''{1}''", new Object[]{template.getSchemaId(), template.getSyntaxId()});
                        break;

                    // continue an existing metadata package...
                    case "mpc":
                        // ignore line if VEO failed...
                        if (state == State.VEO_FAILED) {
                            break;
                        }
                        if (veo == null) {
                            veoFailed(line, "MPC command before first BV command");
                            break;
                        }

                        // check the right number of arguments
                        if (tokens.length < 2) {
                            veoFailed(line, "Missing template in MPC command (format: 'MPC' <template> [<subs>...])");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }

                        log.log(Level.FINE, "Continuing a Metadata Package ''{1}'' (State: {0})", new Object[]{state, tokens[1]});

                        try {
                            veo.continueMetadataPackage(templates.findTemplate(tokens[1]), tokens);
                        } catch (VEOError e) {
                            veoFailed(line, "Applying template in MPC command failed", e);
                            veo.abandon(debug);
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }
                        break;

                    // add an information package to an information object
                    case "ip":
                        // ignore line if VEO failed...
                        if (state == State.VEO_FAILED) {
                            break;
                        }
                        if (veo == null) {
                            veoFailed(line, "IP command before first BV command");
                            break;
                        }

                        // check the right number of arguments
                        if (tokens.length < 2) {
                            veoFailed(line, "Missing file in IP command (format: 'IP' [<label>] <file> [<files>...])");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }

                        // check to see if first argument is a label or a file...
                        Path p;
                        label = null;
                        i = 1;
                        try {
                            p = veo.getActualSourcePath(tokens[1]);
                        } catch (VEOError ve) {
                            p = null;
                        }
                        if (p == null || !Files.exists(p) || !Files.isRegularFile(p)) {
                            i = 2;
                            label = tokens[1];
                            if (tokens.length < 3) {
                                veoFailed(line, "Missing file after a label in IP command (format: 'IP' [<label>] <file> [<files>...]). Possibly referenced file hasn't been added in an AC command");
                                veo.abandon(debug);
                                veo = null;
                                break;
                            }
                        }
                        log.log(Level.FINE, "Starting new Information Piece {1} ''{2}'' (State: {0})", new Object[]{state, i, tokens[1]});

                        // add Information Packages...
                        try {
                            veo.addInformationPiece(label);

                            // go through list of files to add
                            while (i < tokens.length) {
                                log.log(Level.FINE, "Adding ''{0}''", tokens[i]);
                                veo.addContentFile(tokens[i]);
                                i++;
                            }
                        } catch (VEOError e) {
                            veoFailed(line, "IP command failed", e);
                            veo.abandon(debug);
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }
                        break;

                    // add an event to the VEO history file
                    case "e":
                        boolean error;  // true if processing error part of event
                        List<String> descriptions = new ArrayList<>(); // description strings in command
                        List<String> errors = new ArrayList<>(); // error strings in comman

                        // ignore line if VEO failed...
                        if (state == State.VEO_FAILED) {
                            break;
                        }
                        if (veo == null) {
                            veoFailed(line, "E command before first BV command");
                            break;
                        }

                        // check the right number of arguments
                        if (tokens.length < 5) {
                            veoFailed(line, "Missing mandatory argument in E command (format: 'E' <date> <event> <initiator> <description> [<description>...] ['$$' <error>...])");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }
                        if (state != State.VEO_STARTED) {
                            veoFailed(line, "E command must be specified after a BV command");
                            veo.abandon(debug);
                            veo = null;
                            break;
                        }
                        log.log(Level.FINE, "Adding an event ''{1}'' ''{2}'' ''{3}'' ''{4}''... (State: {0})", new Object[]{state, tokens[1], tokens[2], tokens[3], tokens[4]});

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

                    // shorthand for a VEO that has but one MP, one IO, one IP
                    case "veo":
                        // check that a signer has been defined
                        if (signers.isEmpty()) {
                            throw new VEOFatal(classname, method, 1, "Attempting to begin construction of a VEO without specifying a signer using a PFX command or -s command line argument");
                        }

                        // if we are already constructing a VEO, finalise it before starting a new one
                        if (veo != null) {
                            try {
                                veo.finishFiles();
                                sign(veo);
                                veo.finalise(false);
                            } catch (VEOError e) {
                                veoFailed(line, "When starting new BV command, failed to finalise previous VEO:", e);
                                veo.abandon(debug);
                                if (e instanceof VEOFatal) {
                                    return;
                                }
                            }
                            veo = null;
                        }

                        // check command arguments
                        if (tokens.length < 2) {
                            veoFailed(line, "Missing VEO name in VEO command (format: 'VEO' <veoName> <label> <template> [<data>...] '$$' [<files>...]))");
                            if (veo != null) {
                                veo.abandon(debug);
                            }
                            veo = null;
                            break;
                        }
                        // tell the world if verbose...
                        if (chatty) {
                            System.out.println(System.currentTimeMillis() / 1000 + " Starting: " + tokens[1]);
                        }
                        log.log(Level.FINE, "Beginning VEO ''{0}'' (State: {1}) at {3}", new Object[]{tokens[1], state, System.currentTimeMillis() / 1000});

                        // create VEO
                        try {
                            // create VEO & add VEOReadme.txt from template directory
                            veo = new CreateVEO(outputDir, tokens[1], hashAlg, debug);
                            veo.addVEOReadme(templateDir);

                            // which contains one anonymous IO
                            veo.addInformationObject(tokens[2], 0);

                            // which contains one metadata package
                            veo.addMetadataPackage(templates.findTemplate(tokens[3]), tokens);

                            // and multiple anonymous IP (one for each content file)
                            // go through list of files to add
                            boolean foundFiles = false;
                            for (i = 0; i < tokens.length; i++) {
                                if (tokens[i].equals("$$")) {
                                    foundFiles = true;
                                    continue;
                                }
                                if (foundFiles) {
                                    // create information piece
                                    veo.addInformationPiece(null);

                                    // add the content file to the information piece
                                    log.log(Level.FINE, "Adding ''{0}''", tokens[i]);
                                    veo.addContentFile(tokens[i]);
                                }
                            }

                            // and one event documenting creation of the VEO
                            veo.addEvent(VERSDate.versDateTime((long) 0), "VEO Created", "VEOCreate", new String[]{"No Description"}, new String[]{"No Errors"});
                        } catch (VEOError e) {
                            veoFailed(line, "Failed in creating new VEO in VEO command", e);
                            if (veo != null) {
                                veo.abandon(debug);
                            }
                            veo = null;
                            if (e instanceof VEOFatal) {
                                return;
                            }
                            break;
                        }
                        // we have started...
                        state = State.VEO_STARTED;
                        break;

                    default:
                        log.log(Level.SEVERE, "Error in control file around line {0}: unknown command: ''{1}''", new Object[]{line, tokens[0]});
                }
            }
        } catch (PatternSyntaxException | IOException ex) {
            throw new VEOFatal(classname, method, 1, "unexpected error: " + ex.toString());
        }

        // if we are already constructing a VEO, finalise it...
        if (veo != null) {
            try {
                veo.finishFiles();
                sign(veo);
                veo.finalise(false);
            } catch (VEOError e) {
                veoFailed(line, "Failed when finalising last VEO", e);
                veo.abandon(debug);
            }
        }
        if (verbose) {
            System.out.println(System.currentTimeMillis() / 1000 + " Finished");
        }
    }

    /**
     * Generate file reference The control file contains references to other
     * files. These references may be absolute, they may be relative to the
     * directory containing the control file, or they may be relative to the
     * current working directory. If the file starts with the root (typically a
     * slash), the file ref is absolute. If the file ref starts with a '.', it
     * is considered relative to the current working direction.
     *
     * @param fileRef the file reference from the control file
     * @return the real path of the referenced file or directory
     */
    private Path getRealFile(String fileRef) throws VEOError {
        Path f;
        Properties p;
        String cwd;

        f = Paths.get(fileRef);

        // if it is relative to current working directory
        if (f.startsWith(".")) {
            p = System.getProperties();
            cwd = p.getProperty("user.dir");
            f = Paths.get(cwd, fileRef);

            // if it is absolute (starts at the root)
        } else if (!f.isAbsolute()) {
            f = Paths.get(baseDir.toString(), fileRef);
        }
        try {
            f = f.toRealPath();
        } catch (IOException ioe) {
            throw new VEOError("Invalid file reference in control file: '" + ioe.getMessage() + "'; typically this file doesn't exist");
        }
        return f;
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
            log.log(Level.FINE, "Signing {0} with ''{1}''", new Object[]{user.toString(), hashAlg});
            veo.sign(user, hashAlg);
        }
    }

    /**
     * VERSDate method to throw a fatal error.
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
     * VERSDate method to report an error resulting from a VEOFatal or VEOError
     * exception.
     *
     * @param line line in control file in which error occurred
     * @param s a string describing error
     * @param e the error that caused the failure
     */
    private void veoFailed(int line, String s, Throwable e) {
        s = s + ". Error was: " + e.getMessage() + ". ";
        if (e instanceof VEOFatal) {
            s = s + "Creation of VEOs halted.";
        } else {
            s = s + "VEO being abandoned.";
        }
        veoFailed(line, s);
    }

    /**
     * VERSDate method to report an error
     *
     * @param line line in control file in which error occurred
     * @param s a string describing error
     */
    private void veoFailed(int line, String s) {
        log.log(Level.WARNING, "Error in control file around line {0}: {1}.", new Object[]{line, s});
        state = State.VEO_FAILED;
    }

    /**
     * Abandon construction of these VEOs and free any resources associated with
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
        CreateVEOs cv;

        if (args.length == 0) {
            // args = new String[]{"-c", "Test/Demo/createANZStests.txt", "-t", "Test/Demo/templates", "-o", "../neoVEOOutput/TestAnalysis"};
            // args = new String[]{"-c", "Test/Demo/control.txt", "-t", "Test/Demo/templates", "-o", "../neoVEOOutput/TestAnalysis"};
        }
        try {
            cv = new CreateVEOs(args);
            cv.buildVEOs();
        } catch (VEOFatal e) {
            System.err.println(e.getMessage());
        }
    }
}
