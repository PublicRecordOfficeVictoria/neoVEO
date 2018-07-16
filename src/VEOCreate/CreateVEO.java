/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 * Version 1.0.1 Feb 2018 fixed a bug in LinkOrCopy()
 * Version 1.1 25 June 2018 Content files are now zipped directly from the
 * original file, instead of being copied, moved, or linked into the VEO. THe
 * options to copy, move, or link the files were removed
 */
package VEOCreate;

import VERSCommon.PFXUser;
import VERSCommon.VEOError;
import VERSCommon.VEOFatal;
import java.nio.file.Path;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * This class creates a single VEO. Normally the methods in this class are
 * driven by a control file from createVEOs, but it is possible to call these
 * methods directly as an API to construct VEOs.
 */
public class CreateVEO {

    private final static Logger log = Logger.getLogger("veocreate.CreateVEO");
    String classname = "CreateVEO"; // for reporting
    Path veoDir;            // VEO directory to create
    boolean debug;          // if true, we are operating in debug mode
    CreateVEOContent cvc;   // the VEOContent.xml file being created
    CreateVEOHistory cvhf;  // the VEOHistory.xml file being created
    CreateSignatureFile csf;// used to generate signture files
    HashMap<String, Path> contentPrefixes; // content directories to create in VEO
    ArrayList<FileToInclude> filesToInclude; // list of files to include

    // state of the VEO being built
    private enum VEOState {

        VEO_STARTED, // VEO started, but no Information Object has been added
        IO_STARTED, // Information Object has been started, but an Information Piece has not been added
        ADDING_MP, // Metadata Packages are being added
        ADDING_IP, // Information Piece is being added
        FINISHED_FILES, // VEOContent and VEOHistory files have been finalised
        SIGNED, // VEO has been signed
        FINISHED     // VEO has been zipped and finished

    }
    VEOState state;      // the state of creation of the VEO

    /**
     * Main constructor. This sets up the environment in which a VEO is
     * constructed. A VEO directory named veoName is created in the specified
     * directory. The specified hash algorithm is used to generate fixity
     * information for the content files and to generate the digital signatures.
     *
     * @param directory directory in which to create the VEO directory
     * @param veoName name of the VEO to be created
     * @param hashAlg the name of the hash algorithm to be used to protect
     * content files
     * @param debug true if operating in debug mode
     * @throws VEOError if the directory doesn't exist, or the veoName does
     */
    public CreateVEO(Path directory, String veoName, String hashAlg, boolean debug) throws VEOError {
        String name;    // string representation of a file name

        this.debug = debug;

        // check that the directory exists & is writable
        if (directory == null) {
            throw new VEOError(classname, 1, "directory is null");
        }
        name = directory.toString();
        if (Files.notExists(directory)) {
            throw new VEOError(classname, 2, "directory '" + name + "' does not exist");
        }
        if (!Files.isDirectory(directory)) {
            throw new VEOError(classname, 3, "directory '" + name + "' is not a directory");
        }
        if (!Files.isWritable(directory)) {
            throw new VEOError(classname, 4, "directory '" + name + "' is not writable");
        }

        // create VEO directory
        if (veoName == null) {
            throw new VEOError(classname, 5, "veoName is null");
        }
        if (!veoName.endsWith(".veo")) {
            veoName = veoName + ".veo";
        }
        veoDir = Paths.get(directory.toString(), veoName);
        if (Files.exists(veoDir)) {
            try {
                deleteFile(veoDir);
            } catch (IOException e) {
                throw new VEOError(classname, 8, "VEO directory '" + name + "' already exists, and failed when deleting it: " + e.toString());
            }
        }
        name = veoDir.toString();
        try {
            Files.createDirectory(veoDir);
        } catch (FileAlreadyExistsException e) {
            throw new VEOError(classname, 6, "VEO directory '" + name + "' already exists");
        } catch (IOException e) {
            throw new VEOError(classname, 7, "failed to create VEO directory '" + name + "' :" + e.toString());
        }

        contentPrefixes = new HashMap<>();
        contentPrefixes.put("DefaultContent", Paths.get("DefaultContent")); // put in the default content directory
        filesToInclude = new ArrayList<>();

        // create VEO Content and VEO History files
        cvc = new CreateVEOContent(veoDir, "3.0", hashAlg);
        cvhf = new CreateVEOHistory(veoDir, "3.0");
        cvhf.start();
        state = VEOState.VEO_STARTED;

        // create signer
        csf = new CreateSignatureFile(veoDir, "3.0");
    }

    /**
     * Auxiliary constructor used when the VEO directory has already been
     * created and it is only necessary to sign and zip it. The specified hash
     * algorithm is used to generate the digital signatures.
     *
     * @param veoDir directory containing the VEO (including the '.veo')
     * @param hashAlg the name of the hash algorithm to be used to protect
     * content files
     * @param debug true if operating in debug mode
     * @throws VEOError if the directory doesn't exist, or the veoName does
     */
    public CreateVEO(Path veoDir, String hashAlg, boolean debug) throws VEOError {
        String name;    // string representation of a file name

        this.debug = debug;

        // check that the veoDirectory exists
        if (veoDir == null) {
            throw new VEOError(classname, 1, "VEO directory is null");
        }
        name = veoDir.toString();
        if (Files.notExists(veoDir)) {
            throw new VEOError(classname, 2, "VEO directory '" + name + "' does not exist");
        }
        if (!Files.isDirectory(veoDir)) {
            throw new VEOError(classname, 3, "VEO directory '" + name + "' is not a directory");
        }
        this.veoDir = veoDir;

        // create signer
        cvc = null;
        cvhf = null;
        csf = new CreateSignatureFile(veoDir, "3.0");
        state = VEOState.FINISHED_FILES;
    }

    /**
     * Get a Path to the VEO directory.
     *
     * @return Path pointing to the VEO directory
     */
    public Path getVEODir() {
        return veoDir;
    }

    /**
     * Copy the VEOReadme.txt file to the VEO directory being created. The
     * master VEOReadme.txt file is found in the template directory.
     *
     * @param templateDir the template directory
     * @throws VEOError if the error affects this VEO only
     * @throws VEOFatal if the error means no VEOs can be generated
     */
    public void addVEOReadme(Path templateDir) throws VEOError, VEOFatal {
        String method = "AddVEOReadMe";
        String name;
        Path master, dest;

        // check that templateDir is not null & exists
        if (templateDir == null) {
            throw new VEOFatal(classname, method, 1, "template directory is null");
        }
        if (Files.notExists(templateDir)) {
            throw new VEOError(classname, method, 2, "template directory '" + templateDir.toString() + "' does not exist");
        }

        // master VEOReadme.txt file is in the template directory
        master = Paths.get(templateDir.toString(), "VEOReadme.txt");

        // check that the master file exists
        if (master == null) {
            throw new VEOFatal(classname, method, 3, "couldn't generate pathname to master of VEOReadme.txt");
        }
        name = master.toString();
        if (Files.notExists(master)) {
            throw new VEOError(classname, method, 4, "file '" + name + "' does not exist");
        }
        if (!Files.isRegularFile(master)) {
            throw new VEOError(classname, method, 5, "file '" + name + "' is a directory");
        }

        // copy the master to the VEO directory
        dest = Paths.get(veoDir.toString(), "VEOReadme.txt");
        try {
            Files.copy(master, dest, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            throw new VEOError(classname, method, 6, "error when copying VEOReadMe.txt:" + e.toString());
        }
    }

    /**
     * Add content files to the VEO being created. Content files can be copied,
     * moved, or linked into the VEO directory. Linking the files is the
     * fastest, but is not supported across file systems
     *
     * @param directories a list of directories to be added
     * @throws VEOError if the error affects this VEO only
     * @throws VEOFatal if the error means no VEOs can be generated
     */
    public void addContent(Path... directories) throws VEOError {
        String method = "addContent";
        int i;

        // check there is at least one directory to add...
        if (directories.length == 0) {
            throw new VEOError(classname, method, 1, "must be passed at least one directory");
        }

        // can only add content until the VEO has been signed
        switch (state) {
            case VEO_STARTED:
            case IO_STARTED:
            case ADDING_MP:
            case ADDING_IP:
                break;
            case FINISHED_FILES:
                throw new VEOError(classname, method, 2, "Content cannot be added after finishFiles() has been called");
            case SIGNED:
                throw new VEOError(classname, method, 3, "Content cannot be added after sign() has been called");
            case FINISHED:
                throw new VEOError(classname, method, 4, "Content cannot be added after finalise() has been called");
        }
        // add directories...
        for (i = 0; i < directories.length; i++) {

            // check that the source content directory exists
            if (directories[i] == null) {
                throw new VEOError(classname, method, 5, "a content directory is null");
            }
            if (!Files.exists(directories[i])) {
                throw new VEOError(classname, method, 6, "content directory '" + directories[i].toString() + "' does not exist");
            }

            // remember content directory prefix
            contentPrefixes.put(directories[i].getFileName().toString(), directories[i]);
        }
    }

    /**
     * Add a new Information Object with a specific label and depth. A label is
     * an arbitrary string identifying this Information Object.
     *
     * @param label the label of this information object
     * @param depth the depth of this information object
     * @throws VEOError if a fatal error occurred
     */
    public void addInformationObject(String label, int depth) throws VEOError {
        String method = "addInformationObject";

        // sanity checks
        if (label == null) {
            throw new VEOError(classname, method, 1, "label parameter is null");
        }
        if (depth < 0) {
            throw new VEOError(classname, method, 2, "depth parameter is a negative number");
        }

        // if we are already creating an Information Object, Metadata Package, or Information Piece, finish it up...
        switch (state) {
            case VEO_STARTED:
                break;
            case IO_STARTED:
                cvc.finishInfoObject();
                break;
            case ADDING_MP:
                cvc.finishMetadataPackage();
                cvc.finishInfoObject();
                break;
            case ADDING_IP:
                cvc.finishInfoPiece();
                cvc.finishInfoObject();
                break;
            case FINISHED_FILES:
                throw new VEOError(classname, method, 3, "Information Object cannot be added after finishFiles() has been called");
            case SIGNED:
                throw new VEOError(classname, method, 4, "Information Object cannot be added after sign() has been called");
            case FINISHED:
                throw new VEOError(classname, method, 5, "Information Object cannot be added after finalise() has been called");
        }
        cvc.startInfoObject(label, depth);

        // now ready to add metadata packages or information pieces
        state = VEOState.IO_STARTED;
    }

    /**
     * Add a new Metadata Package using the specified template and data.
     *
     * @param template the template to use
     * @param data an array of data to populate the template
     * @throws VEOError if a fatal error occurred
     */
    public void addMetadataPackage(Fragment template, String[] data) throws VEOError {
        String method = "addMetadataPackage";

        // sanity checks
        if (template == null) {
            throw new VEOError(classname, method, 1, "template parameter is null");
        }
        if (data == null) {
            throw new VEOError(classname, method, 2, "data parameter is null");
        }

        // finish off any previous metadata package being added...
        switch (state) {
            case VEO_STARTED:
                throw new VEOError(classname, method, 3, "A Metadata Package cannot be added until an Information Object has been started");
            case IO_STARTED:
                break;
            case ADDING_MP:
                cvc.finishMetadataPackage();
                break;
            case ADDING_IP:
                throw new VEOError(classname, method, 4, "A Metadata Package cannot be added after an Information Piece has been added to an Information Object");
            case FINISHED_FILES:
                throw new VEOError(classname, method, 5, "A Metadata Package cannot be added after finishFiles() has been called");
            case SIGNED:
                throw new VEOError(classname, method, 6, "A Metadata Package cannot be added after sign() has been called");
            case FINISHED:
                throw new VEOError(classname, method, 7, "A Metadata Package cannot be added after finalise() has been called");
        }

        // start metadata package and apply parameters to first template
        cvc.startMetadataPackage(template.getSchemaId(), template.getSyntaxId());
        cvc.addFromTemplate(template, data);

        // now ready to add further metadata packages
        state = VEOState.ADDING_MP;
    }

    /**
     * Add a new Metadata Package using a prebuilt XML fragment
     *
     * @param schemaId a string containing the URI identifying the schema for
     * the XML fragment
     * @param syntaxId a string containing the URI identifying the syntax for
     * the XML fragment
     * @param data an array of data to populate the template
     * @throws VEOError if a fatal error occurred
     */
    public void addMetadataPackage(String schemaId, String syntaxId, StringBuilder data) throws VEOError {
        String method = "addMetadataPackage";

        // sanity checks
        if (schemaId == null) {
            throw new VEOError(classname, method, 2, "schema identifier parameter is null");
        }
        if (syntaxId == null) {
            throw new VEOError(classname, method, 2, "syntax identifier parameter is null");
        }

        // finish off any previous metadata package being added...
        switch (state) {
            case VEO_STARTED:
                throw new VEOError(classname, method, 3, "A Metadata Package cannot be added until an Information Object has been started");
            case IO_STARTED:
                break;
            case ADDING_MP:
                cvc.finishMetadataPackage();
                break;
            case ADDING_IP:
                throw new VEOError(classname, method, 4, "A Metadata Package cannot be added after an Information Piece has been added to an Information Object");
            case FINISHED_FILES:
                throw new VEOError(classname, method, 5, "A Metadata Package cannot be added after finishFiles() has been called");
            case SIGNED:
                throw new VEOError(classname, method, 6, "A Metadata Package cannot be added after sign() has been called");
            case FINISHED:
                throw new VEOError(classname, method, 7, "A Metadata Package cannot be added after finalise() has been called");
        }

        // start metadata package and apply parameters to first template
        cvc.startMetadataPackage(schemaId, syntaxId);
        if (data != null) {
            cvc.addPrebuiltMP(data);
        }

        // now ready to add further metadata packages
        state = VEOState.ADDING_MP;
    }

    /**
     * Continue a metadata package, applying new data to a new template.
     *
     * @param template the template to use
     * @param data an array of data to populate the template
     * @throws VEOError if a fatal error occurred
     */
    public void continueMetadataPackage(Fragment template, String[] data) throws VEOError {
        String method = "continueMetadataPackage";

        // sanity checks
        if (template == null) {
            throw new VEOError(classname, method, 1, "template parameter is null");
        }
        if (data == null) {
            throw new VEOError(classname, method, 2, "data parameter is null");
        }

        // we must be already creating a Metadata Package
        if (state != VEOState.ADDING_MP) {
            throw new VEOError(classname, method, 3, "Can only continue a Metadata Package immediately after adding a Metadata Package or continuing a Metadata Package");
        }

        // apply parameters to template
        cvc.addFromTemplate(template, data);
    }

    /**
     * Continue a metadata package by adding static text
     *
     * @param data static text to be added to metadata package
     * @throws VEOError if a fatal error occurred
     */
    public void continueMetadataPackage(String data) throws VEOError {
        String method = "continueMetadataPackage";

        // sanity checks
        if (data == null) {
            throw new VEOError(classname, method, 2, "data parameter is null");
        }

        // we must be already creating a Metadata Package
        if (state != VEOState.ADDING_MP) {
            throw new VEOError(classname, method, 3, "Can only continue a Metadata Package immediately after adding a Metadata Package or continuing a Metadata Package");
        }

        // apply parameters to template
        cvc.addPrebuiltMP(data);
    }

    /**
     * Add a new Information Piece with a particular label.
     *
     * @param label the label to apply (can be null if no label is to be
     * included)
     * @throws VEOError if a fatal error occurred
     */
    public void addInformationPiece(String label) throws VEOError {
        String method = "startInformationPiece";

        // if we are already creating Metadata Package, or Information Piece, finish it up...
        switch (state) {
            case VEO_STARTED:
                throw new VEOError(classname, method, 1, "Information Piece cannot be added until Information Object has been started");
            case IO_STARTED:
                break;
            case ADDING_MP:
                cvc.finishMetadataPackage();
                break;
            case ADDING_IP:
                cvc.finishInfoPiece();
                break;
            case FINISHED_FILES:
                throw new VEOError(classname, method, 2, "Information Piece cannot be added after finishFiles() has been called");
            case SIGNED:
                throw new VEOError(classname, method, 3, "Information Piece cannot be added after sign() has been called");
            case FINISHED:
                throw new VEOError(classname, method, 4, "Information Piece cannot be added after finalise() has been called");
        }

        // start InformationPiece
        cvc.startInfoPiece(label);

        state = VEOState.ADDING_IP;
    }

    /**
     * Add a reference to a content file to an Information Piece.
     *
     * @param file content file to add
     * @throws VEOError if an error occurs
     */
    public void addContentFile(String file) throws VEOError {
        String method = "addContentFile";
        Path source;

        // sanity checks
        if (file == null) {
            throw new VEOError(classname, method, 1, "file parameter is null");
        }

        // can only add Content Files when adding an Information Piece
        if (state != VEOState.ADDING_IP) {
            throw new VEOError(classname, method, 2, "Can only add a Content File when adding an Information Piece");
        }

        // remember file to be zipped later
        source = getActualSourcePath(file);
        if (!Files.exists(source)) {
            throw new VEOError(classname, method, 3, "content file '" + source.toString() + "' does not exist");
        }
        filesToInclude.add(new FileToInclude(source, file));
        
        cvc.addContentFile(file, source);
    }

    /**
     * Add a reference to a content file to an Information Piece.
     *
     * @param file content file to add
     * @throws VEOError if an error occurs
     */
    public void addContentFile(String file, Path source) throws VEOError {
        String method = "addContentFile";

        // sanity checks
        if (file == null) {
            throw new VEOError(classname, method, 1, "file parameter is null");
        }

        // can only add Content Files when adding an Information Piece
        if (state != VEOState.ADDING_IP) {
            throw new VEOError(classname, method, 2, "Can only add a Content File when adding an Information Piece");
        }

        // if ZIPping files, remember it...
        cvc.addContentFile(file, source);
    }

    /**
     * Get the path to the real source file. THere are two cases. If we are
     * directly ZIPping
     * the source file, calculate the actual file from the previously added
     * content directory (if one exists). Otherwise, file directly points to the
     * source file.
     *
     * @param file the path name to be used in the VEO
     * @return the real file
     * @throws VEOError if the content directory hasn't been loaded
     */
    public Path getActualSourcePath(String file) throws VEOError {
        String method = "getSourcePath";
        Path rootPath, source, destination;
        String rootName;

        destination = Paths.get(file);
        rootName = destination.getName(0).toString();
        rootPath = contentPrefixes.get(rootName);
        if (rootPath == null) {
            source = Paths.get(file);
        } else {
            source = rootPath.resolve(destination.subpath(1, destination.getNameCount()));
        }
        return source;
    }

    /**
     * Add individual content file to the default directory in the VEO being
     * created. Content files can be copied, moved, or linked into the VEO
     * directory. Linking the files is the fastest, but is not supported across
     * file systems
     *
     * @param files a list of directories to be added
     * @throws VEOError if the error affects this VEO only
     * @throws VEOFatal if the error means no VEOs can be generated
     */
    public void addIndividualContentFiles(Path... files) throws VEOError {
        String method = "addIndividualContentFiles";
        int i;
        boolean nofiles;
        String s;

        // check there is at least one directory to add...
        nofiles = true;

        // can only add content until the VEO has been signed
        switch (state) {
            case VEO_STARTED:
            case IO_STARTED:
            case ADDING_MP:
            case ADDING_IP:
                break;
            case FINISHED_FILES:
                throw new VEOError(classname, method, 2, "Content cannot be added after finishFiles() has been called");
            case SIGNED:
                throw new VEOError(classname, method, 3, "Content cannot be added after sign() has been called");
            case FINISHED:
                throw new VEOError(classname, method, 4, "Content cannot be added after finalise() has been called");
        }
        // add directories...
        for (i = 0; i < files.length; i++) {

            // check that the source content file exists
            if (files[i] == null) {
                throw new VEOError(classname, method, 5, "a content file is null");
            }
            if (files[i].toString().equals("") || files[i].toString().trim().equals(" ")) {
                continue;
            }
            nofiles = false;
            if (!Files.exists(files[i])) {
                throw new VEOError(classname, method, 6, "content file '" + files[i].toString() + "' does not exist");
            }

            // log.log(Level.WARNING, "veoDir:''{0}''{1}''{2}", new Object[]{veoDir, directories[i], toDir});
            // link the content directory into the VEO directory
            s = veoDir.resolve(Paths.get("DefaultContent")).resolve(files[i].getFileName()).toString();
            filesToInclude.add(new FileToInclude(files[i], s));
        }

        // check that at least one file has been added...
        if (nofiles) {
            throw new VEOError(classname, method, 1, "must be passed at least one (non blank) file");
        }
    }

    /**
     * Add an event to the VEOHistory.xml file. Events may be added at any time
     * until the finishFiles() method has been called.
     *
     * @param date the date/time of the event in standard VEO format
     * @param event a string labelling the type of event
     * @param initiator a string labelling the initiator of the event
     * @param descriptions an array of descriptions of the event
     * @param errors an array of errors resulting
     * @throws VEOError if a fatal error occurred
     */
    public void addEvent(String date, String event, String initiator, String[] descriptions, String[] errors) throws VEOError {
        String method = "addEvent";

        // sanity checks
        if (date == null) {
            throw new VEOError(classname, method, 1, "date parameter is null");
        }
        if (event == null) {
            throw new VEOError(classname, method, 2, "event parameter is null");
        }
        if (initiator == null) {
            throw new VEOError(classname, method, 3, "initiator parameter is null");
        }
        if (descriptions == null) {
            throw new VEOError(classname, method, 4, "descriptions parameter is null");
        }
        if (errors == null) {
            throw new VEOError(classname, method, 5, "errors parameter is null");
        }

        // can only add an Event before the VEOHistory file has been finalised
        switch (state) {
            case VEO_STARTED:
            case IO_STARTED:
            case ADDING_MP:
            case ADDING_IP:
                break;
            case FINISHED_FILES:
                throw new VEOError(classname, method, 6, "Event cannot be added after finishFiles() has been called");
            case SIGNED:
                throw new VEOError(classname, method, 7, "Event cannot be added after sign() has been called");
            case FINISHED:
                throw new VEOError(classname, method, 8, "Event cannot be added after finalise() has been called");
        }

        // add event
        cvhf.addEvent(date, event, initiator, descriptions, errors);
    }

    /**
     * Finalise the VEOContent and VEOHistory files. This method commences the
     * finishing of the VEO construction. It completes the VEOContent.xml and
     * VEOHistory.xml files. It must be called before the sign() method. When
     * completing the VEOContent.xml file, any uncompleted Information Object,
     * Metadata Package, or Information Piece is completed. This method may only
     * be called once.
     *
     * @throws VEOError if a fatal error occurred
     */
    public void finishFiles() throws VEOError {
        String method = "finishFiles";

        // finish off the VEOContent file...
        switch (state) {
            case VEO_STARTED:
                throw new VEOError(classname, method, 1, "VEOContent file cannot be finished until at least one Information Object has been added");
            case IO_STARTED:
                cvc.finishInfoObject();
                break;
            case ADDING_MP:
                cvc.finishMetadataPackage();
                cvc.finishInfoObject();
                break;
            case ADDING_IP:
                cvc.finishInfoPiece();
                cvc.finishInfoObject();
                break;
            case FINISHED_FILES:
                throw new VEOError(classname, method, 2, "VEOContent and VEOHistory files have already been finished");
            case SIGNED:
                throw new VEOError(classname, method, 3, "VEOContent and VEOHistory files have already been finished");
            case FINISHED:
                throw new VEOError(classname, method, 4, "VEO has been finished");
        }
        // log.log(Level.INFO, "Finalising VEOContent.xml and VEOHistory.xml");

        // finish the VEOContent file
        if (cvc != null) {
            cvc.finalise();
        }
        cvc = null;

        // finalise the VEOHistory.xml file
        if (cvhf != null) {
            cvhf.finalise();
        }
        cvhf = null;

        state = VEOState.FINISHED_FILES;
    }

    /**
     * Sign the VEOContent.xml and VEOHistory.xml files. This method generates a
     * pair of VEOContentSignature and VEOHistorySignature files using the
     * specified signer and hash algorithm. (Note the private key in the signer
     * controls the signature algorithm that is used.) This method must be
     * called after calling the finaliseFiles() method. This method may be
     * called repeatedly to generate new pairs of signature files for different
     * signers.
     *
     * @param signer PFX file representing the signer
     * @param hashAlg algorithm to use to hash file
     * @throws VEOError if a fatal error occurred
     */
    public void sign(PFXUser signer, String hashAlg) throws VEOError {
        String method = "sign";

        // sanity checks
        if (signer == null) {
            throw new VEOError(classname, method, 1, "signer parameter is null");
        }
        if (hashAlg == null) {
            throw new VEOError(classname, method, 2, "signature algorithm parameter is null");
        }

        // can we sign?
        switch (state) {
            case VEO_STARTED:
            case IO_STARTED:
            case ADDING_MP:
            case ADDING_IP:
                throw new VEOError(classname, method, 3, "Files cannot be signed until they are finished");
            case FINISHED_FILES:
                break;
            case SIGNED:
                break;
            case FINISHED:
                throw new VEOError(classname, method, 5, "VEO has been finished");
        }

        // sign the files
        csf.sign("VEOContent.xml", signer, hashAlg);
        csf.sign("VEOHistory.xml", signer, hashAlg);
        state = VEOState.SIGNED;
    }

    /**
     * Produce the actual VEO (a Zip file) and clean up. This method turns the
     * VEO directory and its contents into a ZIP file. The ZIP file has the same
     * name as the VEO directory with the suffix '.veo.zip'. The method must be
     * called after the last sign() method call. In normal operation, this
     * method will delete the VEO directory after the ZIP file has been created,
     * unless the debug flag was specified when the VEO was created or the
     * keepVEODir flag is set). Once this method has been called none of the
     * other methods of this class can be called.
     *
     * @param keepVEODir if true the VEO directory is kept
     * @throws VEOError if a fatal error occurred
     */
    public void finalise(boolean keepVEODir) throws VEOError {
        String method = "finalise";
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;
        ZipOutputStream zos = null;
        String zipName;

        // VEOContent and VEOHistory files must have been finished and signed
        switch (state) {
            case VEO_STARTED:
            case IO_STARTED:
            case ADDING_MP:
            case ADDING_IP:
            case FINISHED_FILES:
                throw new VEOError(classname, method, 1, "VEOContent and VEOHistory have to be signed before finalising VEO");
            case SIGNED:
                break;
            case FINISHED:
                throw new VEOError(classname, method, 2, "VEO has been finished");
        }

        // log.log(Level.INFO, "Finished control file. Zipping");
        // generate the ZIP file
        try {
            // VEO name is the VEO directory name with the suffix '.veo.zip'
            if (veoDir.getFileName().toString().endsWith(".veo")) {
                zipName = veoDir.getFileName().toString() + ".zip";
            } else {
                zipName = veoDir.getFileName().toString() + ".veo.zip";
            }

            // create Zip Output Stream
            fos = new FileOutputStream(Paths.get(veoDir.getParent().toString(), zipName).toString());
            bos = new BufferedOutputStream(fos);
            zos = new ZipOutputStream(bos);

            // recursively process VEO file
            zip(zos, veoDir.getParent(), veoDir);

            // include the content files
            zipContentFiles(zos, veoDir);

        } catch (IOException e) {
            throw new VEOError(classname, method, 1, "Error creating ZIP file: " + e.toString());
        } finally {
            try {
                if (zos != null) {
                    zos.close();
                }
                if (bos != null) {
                    bos.close();
                }
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                /* ignore */ }
        }

        state = VEOState.FINISHED;

        // cleanup...
        abandon(debug || keepVEODir);
    }

    /**
     * ZIP a directory (recursively call to ZIP subdirectories).
     *
     * @param zos the ZIP output stream
     * @param veoDir the root directory of the ZIP
     * @param dir the directory to ZIP
     * @throws IOException
     */
    private void zip(ZipOutputStream zos, Path veoDir, Path dir) throws IOException {
        String method = "zip";
        FileInputStream fis;
        BufferedInputStream bis;
        byte[] b = new byte[1024];
        DirectoryStream<Path> ds = null;
        int l;
        Path relPath;
        ZipEntry zs;

        try {
            // get a list of files in the VEO directory
            ds = Files.newDirectoryStream(dir);

            // go through list and for each file
            for (Path p : ds) {
                // log.log(Level.WARNING, "zipping:" + p.toString());

                // construct the Path between the veoDir and the file being linked
                relPath = veoDir.relativize(p);

                // copy a regular file into the ZIP file
                if (relPath.getNameCount() != 0 && Files.isRegularFile(p)) {
                    // System.err.println("Adding '" + s + "'");
                    zs = new ZipEntry(relPath.toString());
                    zs.setTime(Files.getLastModifiedTime(p).toMillis());
                    zos.putNextEntry(zs);

                    // copy the content
                    fis = new FileInputStream(p.toString());
                    bis = new BufferedInputStream(fis);
                    while ((l = fis.read(b)) > 0) {
                        zos.write(b, 0, l);
                    }
                    bis.close();
                    fis.close();

                    // close this ZIP entry
                    zos.closeEntry();
                }

                // recursively process directories
                if (Files.isDirectory(p)) {
                    zip(zos, veoDir, p);
                }

            }
        } finally {
            if (ds != null) {
                ds.close();
            }
        }
    }

    /**
     * ZIP the content files specified to be included. We keep track of what
     * files have been ZIPped, so that we only include them once.
     *
     * @param zos the ZIP output stream
     * @param veoDir the root directory of the ZIP
     * @throws IOException
     */
    private void zipContentFiles(ZipOutputStream zos, Path veoDir) throws IOException {
        String method = "zipContentFiles";
        FileInputStream fis;
        BufferedInputStream bis;
        byte[] b = new byte[1024];
        FileToInclude fi;
        int i, l;
        Path relPath;
        ZipEntry zs;
        HashMap<String, Path> seen;

        seen = new HashMap<>();

        // go through list and for each file
        for (i = 0; i < filesToInclude.size(); i++) {
            fi = filesToInclude.get(i);
            // log.log(Level.WARNING, "zipping:" + p.toString());

            // have we already zipped this file?
            if (seen.containsKey(fi.destination)) {
                continue;
            } else {
                seen.put(fi.destination, fi.source);
            }

            // construct the Path between the veoDir and the file being linked
            relPath = veoDir.getFileName().resolve(fi.destination);

            // copy a regular file into the ZIP file
            if (relPath.getNameCount() != 0 && Files.isRegularFile(fi.source)) {
                zs = new ZipEntry(relPath.toString());
                zs.setTime(Files.getLastModifiedTime(fi.source).toMillis());
                zos.putNextEntry(zs);

                // copy the content
                fis = new FileInputStream(fi.source.toString());
                bis = new BufferedInputStream(fis);
                while ((l = fis.read(b)) > 0) {
                    zos.write(b, 0, l);
                }
                bis.close();
                fis.close();

                // close this ZIP entry
                zos.closeEntry();
            }
        }
        seen.clear();
        seen = null;
    }

    /**
     * Abandon construction of this VEO and free any resources associated with
     * it. This method is called automatically by the finalise() method. It may
     * also be called at any other time to abandon construction of the VEO (e.g.
     * after an error) and free all the resources associated with the VEO. If
     * the debug flag is set to true, some information is preserved (e.g. the
     * VEO directory)
     *
     * @param debug true if in debugging mode
     */
    public void abandon(boolean debug) {

        // abandon the VEOContent, VEOHistory, and Signature...
        if (cvc != null) {
            cvc.abandon(debug);
        }
        cvc = null;
        if (cvhf != null) {
            cvhf.abandon(debug);
        }
        cvhf = null;
        if (csf != null) {
            csf.abandon(debug);
        }
        csf = null;
        contentPrefixes.clear();
        contentPrefixes = null;
        filesToInclude.clear();
        filesToInclude = null;

        // delete VEO directory if it exists
        try {
            if (!debug && veoDir != null && Files.exists(veoDir)) {
                deleteFile(veoDir);
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Deleting {0} failed: {1}", new Object[]{veoDir.toString(), e.toString()});
        }
        veoDir = null;
    }

    /**
     * Private function to delete a directory. Needed because you cannot delete
     * a non empty directory
     *
     * @param file
     * @throws IOException
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
            System.out.println(e.toString());
        }
    }

    /**
     * This class simply records a content file that needs to be included in the
     * VEO. It contains two file names: the actual location of the file to be
     * included in the real file system; and the eventual location of the file
     * in the VEO.
     */
    private class FileToInclude {

        Path source;
        String destination;

        public FileToInclude(Path source, String destination) {
            this.source = source;
            this.destination = destination;
        }
    }

    /**
     * Test program...
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        CreateVEO cv;
        CreateVEOContent cvc;
        Path veoDir;
        DirectoryStream<Path> ds;
        CreateVEOHistory cvhf;
        String[] descriptions, errors;
        CreateSignatureFile csf;
        PFXUser signer;

        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", true);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test"));

            // add content files
            cv.addContent(Paths.get("Test", "testDir"));

            // create VEO Content file
            cvc = new CreateVEOContent(veoDir, "3.0", "SHA-1");
            cvc.startInfoObject("Record", 1);
            //cvc.StartMetadataPackage();
            // cvc.addFromTemplate();
            cvc.finishMetadataPackage();
            //cvc.StartMetadataPackage();
            cvc.finishMetadataPackage();
            cvc.startInfoPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "testDir"));
            for (Path p : ds) {
                cvc.addContentFile((veoDir.relativize(p)).toString(), null);
            }
            cvc.finishInfoPiece();
            cvc.finishInfoObject();
            cvc.finalise();

            // create VEO History file
            cvhf = new CreateVEOHistory(veoDir, "3.0");
            cvhf.start();
            descriptions = new String[]{"One"};
            errors = new String[]{"Two"};
            cvhf.addEvent("20140101", "Created", "Andrew", descriptions, errors);
            cvhf.finalise();

            // sign VEO Content file
            signer = new PFXUser(new File("Test", "signer.pfx").toString(), "Ag0nc1eS");
            csf = new CreateSignatureFile(veoDir, "3.0");
            csf.sign("VEOContent.xml", signer, "SHA1withRSA");

            // sign VEO History file
            signer = new PFXUser(new File("Test", "signer.pfx").toString(), "Ag0nc1eS");
            csf = new CreateSignatureFile(veoDir, "3.0");
            csf.sign("VEOHistory.xml", signer, "SHA1withRSA");

            // zip
            cv.finalise(true);
        } catch (VEOError e) {
            System.out.println(e.toString());
        } catch (IOException e) {
            System.out.println("IOException: " + e.toString());
        }
    }
}
