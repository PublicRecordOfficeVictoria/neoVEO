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
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
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
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

/**
 * This class creates a single VEO using API calls. These methods can be called
 * directly as an API to create a single VEO, or indirectly from the
 * {@link VEOCreate.CreateVEOs} class to create multiple VEOs.
 * <p>
 * Two types of errors are thrown by the methods in this class:
 * {@link VERSCommon.VEOError} and {@link VERSCommon.VEOFatal}. VEOError is
 * thrown when an error occurs that requires the construction of this VEO to be
 * abandoned, but construction of further VEOs
 * can be attempted. VEOFatal is thrown when an error occurs that means there is
 * no point attempting to construct further VEOs (typically a system error).
 * 
 * @author Andrew Waugh, Public Record Office Victoria
 */
public class CreateVEO {

    private final static Logger LOG = Logger.getLogger("veocreate.CreateVEO");
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
     * constructed. A VEO directory named veoName is created in the working
     * directory. The specified hash algorithm is used to generate fixity
     * information for the content files and to generate the digital signatures.
     * <p>
     * Valid hashAlg values are 'SHA-1', 'SHA-256', 'SHA-384', and 'SHA-512'.
     * MD2 and MD5 are NOT supported as these are considered insecure today. The
     * signature algorithm is implicitly specified by the PFX file.
     * <p>
     * Note that this CreateVEO instance can only create ONE VEO. A new instance
     * must be created for each VEO.
     *
     * @param workingDir directory in which to create the VEO directory
     * @param veoName name of the VEO to be created
     * @param hashAlg the name of the hash algorithm to be used to protect
     * content files
     * @param debug true if operating in debug mode
     * @throws VERSCommon.VEOError if the instance could not be constructed
     */
    public CreateVEO(Path workingDir, String veoName, String hashAlg, boolean debug) throws VEOError {
        String name;    // string representation of a file name

        this.debug = debug;

        // check that the directory exists & is writable
        if (workingDir == null) {
            throw new VEOError(classname, 1, "directory is null");
        }
        name = workingDir.toString();
        if (Files.notExists(workingDir)) {
            throw new VEOError(classname, 2, "directory '" + name + "' does not exist");
        }
        if (!Files.isDirectory(workingDir)) {
            throw new VEOError(classname, 3, "directory '" + name + "' is not a directory");
        }
        if (!Files.isWritable(workingDir)) {
            throw new VEOError(classname, 4, "directory '" + name + "' is not writable");
        }

        // create VEO directory
        if (veoName == null) {
            throw new VEOError(classname, 5, "veoName is null");
        }
        if (!veoName.endsWith(".veo")) {
            veoName = veoName + ".veo";
        }
        veoDir = Paths.get(workingDir.toString(), veoName).toAbsolutePath().normalize();
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
     * Auxiliary constructor used when the VEO directory and all its contents
     * has already been created and it is only necessary to sign and zip the
     * VEO.
     * <p>
     * The specified hash algorithm is used to generate the digital signatures.
     * Valid hashAlg values are 'SHA-1', 'SHA-256', 'SHA-384', and 'SHA-512'.
     * MD2 and MD5 are NOT supported as these are considered insecure today. The
     * signature algorithm is implicitly specified by the PFX file.
     *
     * @param veoDir directory containing the partially constructed VEO
     * @param hashAlg the name of the hash algorithm to be used to protect
     * content files
     * @param debug true if operating in debug mode
     * @throws VERSCommon.VEOError if an error occurred
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
        this.veoDir = veoDir.toAbsolutePath().normalize();

        // create signer
        cvc = null;
        cvhf = null;
        csf = new CreateSignatureFile(veoDir, "3.0");
        state = VEOState.FINISHED_FILES;
    }

    /**
     * Get the file path to the VEO directory. The VEO directory is the
     * directory containing the contents of the VEO before it is zipped.
     *
     * @return Path pointing to the VEO directory
     */
    public Path getVEODir() {
        return veoDir;
    }

    /**
     * Copy the VEOReadme.txt file to the VEO directory being created. The
     * master VEOReadme.txt file is found in the template directory.
     * <p>
     * This method is normally called immediately after creating a CreateVEO
     * object, but may be called anytime until the call to the finaliseFiles().
     *
     * @param templateDir the template directory
     * @throws VERSCommon.VEOError if the error affects this VEO only
     * @throws VERSCommon.VEOFatal if the error means no more VEOs can be generated
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
        dest = veoDir.resolve("VEOReadme.txt");
        try {
            Files.copy(master, dest, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            throw new VEOError(classname, method, 6, "error when copying VEOReadMe.txt:" + e.toString());
        }
    }

    /**
     * Add a new Information Object with a specific IOType and IODepth. A IOType
     * is an arbitrary string identifying this Information Object. IOType must
     * not be null. IODepth must be a positive integer or zero.
     * <p>
     * After adding a new Information Object you must add all of its contents
     * (Metadata Packages and Information Pieces) before adding a new
     * Information Object. Once an Information Object has been started, all the
     * Metadata Packages must be added before any of the Information Pieces.
     *
     * @param IOType the IOType of this information object
     * @param IODepth the IODepth of this information object
     * @throws VERSCommon.VEOError if an error occurred
     */
    public void addInformationObject(String IOType, int IODepth) throws VEOError {
        String method = "addInformationObject";

        // sanity checks
        if (IOType == null) {
            throw new VEOError(classname, method, 1, "label parameter is null");
        }
        if (IODepth < 0) {
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
        cvc.startInfoObject(IOType, IODepth);

        // now ready to add metadata packages or information pieces
        state = VEOState.IO_STARTED;
    }

    /**
     * Add a new Metadata Package using a template which is populated with data.
     * The template is expressed as a {@link VEOCreate.Fragment}, but use the
     * {@link VEOCreate.Templates} class to obtain the desired Fragment.
     * <p>
     * Read the description for the Templates class to understand Fragments and
     * their use.
     * <p>
     * The data to be substituted into the template (fragment) is contained in a
     * String array. Substitution $$1$$ obtains data from array element 0, and
     * so on. (Yes, having the substitution number be one greater than the array
     * index is not ideal, but this is for historical reasons.)
     * <p>
     * Neither the template or data arguments may be null.
     * <p>
     * If required, the Metadata Package can be created using multiple API
     * calls. Once a Metadata Package has been started, use the
     * continueMetadataPackage(), addSimpleElementToMP(), or
     * startComplexMetadataElementInMP() methods to add more content to this
     * Metadata Package.
     * <p>
     * Metadata Packages are automatically finalised when a new
     * Metadata Package, is started, an Information Piece is added, or a
     * new InformationObject is started.
     * <p>
     * All of the Metadata Packages associated with this Information Object must
     * be added to the Information Object before an Information Piece is added.
     *
     * @param template the template to use
     * @param data an array of data to populate the template
     * @throws VERSCommon.VEOError if an error occurred
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
        startMetadataPackage(method);

        // start metadata package and apply parameters to first template
        cvc.startMetadataPackage(template.getSchemaId(), template.getSyntaxId());
        cvc.addFromTemplate(template, data);

        // now ready to add further metadata packages
        state = VEOState.ADDING_MP;
    }

    /**
     * Add a new Metadata Package containing an arbitrary piece of XML text.
     * The schemaId and syntaxId are URIs and are defined in the VERS V3
     * specifications. None of the arguments can be null.
     * <p>
     * If required, the Metadata Package can be created using multiple API
     * calls. Once a Metadata Package has been started, use the
     * continueMetadataPackage(), addSimpleElementToMP(), or
     * startComplexMetadataElementInMP() methods to add more content to this
     * Metadata Package.
     * <p>
     * Metadata Packages are automatically finalised when a new
     * Metadata Package, is started, an Information Piece is added, or a
     * new InformationObject is started.
     * <p>
     * All of the Metadata Packages associated with this Information Object must
     * be added to the Information Object before an Information Piece is added.
     *
     * @param schemaId a string containing the URI identifying the schema of the
     * Metadata Package being commenced.
     * @param syntaxId a string containing the URI identifying the syntax of the
     * Metadata Package being commenced.
     * @param text text to put in the Metadata Package
     * @throws VERSCommon.VEOError if an error occurred
     */
    public void addMetadataPackage(String schemaId, String syntaxId, StringBuilder text) throws VEOError {
        String method = "addMetadataPackage";

        // sanity checks
        if (schemaId == null) {
            throw new VEOError(classname, method, 1, "schema identifier parameter is null");
        }
        if (syntaxId == null) {
            throw new VEOError(classname, method, 2, "syntax identifier parameter is null");
        }
        startMetadataPackage(method);

        // start metadata package and apply parameters to first template
        cvc.startMetadataPackage(schemaId, syntaxId);
        if (text != null) {
            cvc.addPrebuiltMP(text);
        }

        // now ready to add further metadata packages
        state = VEOState.ADDING_MP;
    }
    
    /**
     * Start an RDF metadata package. This is effectively a wrapper around one
     * of the addMetadataPackage() methods; it automatically creates an RDF
     * metadata package including the rdf:RDF and rdf:Description elements.
     * <p>
     * Any namespace definitions to be added as attributes to the rdf:RDF
     * element can be passed (this may be null if there are none). The URI
     * identifying the resource being described can be passed.
     * <p>
     * The content of the metadata package can be added after this call using
     * the continueMetadataPackage(), addSimpleElementToMP(), or
     * startComplexMetadataElementInMP() methods.
     * <p>
     * Metadata Packages are automatically finalised when a new
     * Metadata Package, is started, an Information Piece is added, or a
     * new InformationObject is started.
     * When this package is finalised, the rdf:RDF and rdf:Description elements
     * are automatically closed.
     * 
     * @param schemaId the URI identifying the schema of the Metadata Package
     * being commenced.
     * @param namespaceDefns any XML namespace definitions to include in the 
     * rdf:RDF element
     * @param resourceId a URI identifying the object being described by the RDF.
     * @throws VEOError if a failure occurred
     */
    public void startRDFMetadataPackage(String schemaId, String namespaceDefns, URL resourceId) throws VEOError {
        String method = "startRDFMetadataPackage";

        // sanity checks
        if (schemaId == null) {
            throw new VEOError(classname, method, 2, "schema identifier parameter is null");
        }
        startMetadataPackage(method);

        // start metadata package and apply parameters to first template
        cvc.startRDFMetadataPackage(schemaId, namespaceDefns, resourceId);

        // now ready to add further metadata packages
        state = VEOState.ADDING_MP;
    }
    
    /**
     * Start an XML metadata package. This is effectively a wrapper around one
     * of the addMetadataPackage() methods; it automatically creates a generic
     * XML metadata package.
     * <p>
     * The content of the metadata package can be added after this call using
     * the continueMetadataPackage(), addSimpleElementToMP(), or
     * startComplexMetadataElementInMP() methods.
     * <p>
     * Metadata Packages are automatically finalised when a new
     * Metadata Package, is started, an Information Piece is added, or a
     * new InformationObject is started.
     * 
     * @param schemaId the URI identifying the schema of the Metadata Package
     * being commenced.
     * @throws VEOError if a failure occurred
     */
    public void startXMLMetadataPackage(String schemaId) throws VEOError {
        String method = "startXMLMetadataPackage";

        // sanity checks
        if (schemaId == null) {
            throw new VEOError(classname, method, 2, "schema identifier parameter is null");
        }
        startMetadataPackage(method);

        // start metadata package and apply parameters to first template
        cvc.startXMLMetadataPackage(schemaId);

        // now ready to add further metadata packages
        state = VEOState.ADDING_MP;
    }
    
    /**
     * Utility method to handle common code for starting a metadata package
     * @param method
     * @throws VEOError 
     */
    private void startMetadataPackage(String method) throws VEOError {
        
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
    }

    /**
     * Continue a metadata package, applying new data into a new template. This
     * method can be called after a call to addMetadataPackage(),
     * start*MetadataPackage(), addSimpleMetadataElementToMP(),
     * startComplexMetadataElementInMP(), startComplexMetadataElementInMP(),
     * endComplexMetadataElementInMP(), or a previous continueMetadataPackage()
     * call.
     * <p>
     * Any number of calls to either continueMetadataPackage() method can be
     * made.
     * <p>
     * Note that the Syntax and Semantic Identifiers specified on the first line
     * of the template are ignored.
     * <p>
     * Neither argument may be null.
     *
     * @param template the template to use
     * @param data an array of data to populate the template
     * @throws VERSCommon.VEOError if an error occurred
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
     * Continue a metadata package by adding text. This method uses additional
     * text to extend a previously commenced Metadata Package. Any number of
     * calls to either continueMetadataPackage() method can be made.
     * <p>
     * Neither argument may be null.
     *
     * @param text static text to be added to metadata package
     * @throws VERSCommon.VEOError if an error occurred
     */
    public void continueMetadataPackage(String text) throws VEOError {
        String method = "continueMetadataPackage";

        // sanity checks
        if (text == null) {
            throw new VEOError(classname, method, 2, "text parameter is null");
        }

        // we must be already creating a Metadata Package
        if (state != VEOState.ADDING_MP) {
            throw new VEOError(classname, method, 3, "Can only continue a Metadata Package immediately after adding a Metadata Package or continuing a Metadata Package");
        }

        // apply parameters to template
        cvc.addPrebuiltMP(text);
    }

    /**
     * Continue a metadata package by adding text. This method uses additional
     * text to extend a previously commenced Metadata Package. Any number of
     * calls to either continueMetadataPackage() method can be made.
     * <p>
     * The data argument can not be null.
     *
     * @param text static text to be added to metadata package
     * @throws VERSCommon.VEOError if an error occurred
     */
    public void continueMetadataPackage(StringBuilder text) throws VEOError {
        continueMetadataPackage(text.toString());
    }
    
    /**
     * Add a simple metadata element to a metadata package. A simple element
     * is one whose value is simply a text string (i.e. doesn't contain
     * subelements).
     * <p>
     * The element tag is represented as a string (e.g. "vers:title"). A string
     * containing one or more attributes can be specified. The value may be
     * null, in which case an empty element is added to the metadata package.
     * <p>
     * IMPORTANT - no checking is done that the tag or the attributes are
     * valid according to the XML specification. It is the caller's
     * responsibility to ensure this. In particular, any &amp; and &lt;
     * characters in the attribute values are not encoded into XML entities.
     * However, the characters &amp;, &gt;, &lt; ', and &quot; are encoded
     * when encountered in the value. This is because attribute values are
     * usually hard coded in the calling code, while values are usually
     * extracted from data in the calling code.
     * 
     * @param tag the element tag (including namespace, if used)
     * @param attributes any attributes to be included (null if none)
     * @param value the value of the element (null if the element is empty
     * @throws VEOError if a failure occurred
     */
    public void addSimpleMetadataElementToMP(String tag, String attributes, String value) throws VEOError {
                String method = "addSimpleMetadataElementToMP";

        // sanity checks
        if (tag == null || tag.equals("") || tag.trim().equals(" ")) {
            throw new VEOError(classname, method, 2, "Tag name is null, or blank");
        }
        if (attributes != null && (attributes.equals("") || attributes.trim().equals(" "))) {
            attributes = null;
        }
        if (value != null && (value.equals("") || value.trim().equals(" "))) {
            value = null;
        }

        // we must be already creating a Metadata Package
        if (state != VEOState.ADDING_MP) {
            throw new VEOError(classname, method, 3, "Can only continue a Metadata Package immediately after adding a Metadata Package or continuing a Metadata Package");
        }
        
        // output the simple element
        cvc.addSimpleElement(tag, attributes, value);
    }
    
    /**
     * Start a complex metadata elements in a metadata package. A complex element
     * is one whose value contains sub-elements. A call to this method should
     * be followed by calls to add the sub-elements by using the
     * addMetadataElement() and startComplexMetadataElement() methods
     * <p>
     * The element tag is represented as a string (e.g. "vers:title"). A string
     * containing one or more attributes can be specified.
     * <p>
     * IMPORTANT - no checking is done that the tag or the attributes are
     * valid according to the XML specification. It is the caller's
     * responsibility to ensure this. In particular, any &amp; and &lt;
     * characters in the attribute values are not encoded into XML entities.
     * 
     * @param tag the element tag (including namespace, if used)
     * @param attributes any attributes to be included (null if none)
     * @throws VEOError if a failure occurred
     */
    public void startComplexMetadataElementInMP(String tag, String attributes) throws VEOError {
        String method = "startComplexMetadataElementInMP";

        // sanity checks
        if (tag == null || tag.equals("") || tag.trim().equals(" ")) {
            throw new VEOError(classname, method, 2, "Tag name is null, or blank");
        }
        if (attributes != null && (attributes.equals("") || attributes.trim().equals(" "))) {
            attributes = null;
        }

        // we must be already creating a Metadata Package
        if (state != VEOState.ADDING_MP) {
            throw new VEOError(classname, method, 3, "Can only continue a Metadata Package immediately after adding a Metadata Package or continuing a Metadata Package");
        }
        
        // output the simple element
        cvc.startComplexElement(tag, attributes);
    }
    
    /**
     * End a complex metadata elements in a metadata package. A complex element
     * is one whose value contains sub-elements. The method does not check
     * whether endComplexMetadataElementInMP() calls match up with
     * startMetadataElementInMP() calls; it is quite possible to generate
     * invalid XML.
     * <p>
     * The element tag is represented as a string (e.g. "vers:title").
     * 
     * @param tag the element tag (including namespace, if used)
     * @throws VEOError if a failure occurred
     */
    public void endComplexMetadataElementInMP(String tag) throws VEOError {
        String method = "endComplexMetadataElement";

        // sanity checks
        if (tag == null || tag.equals("") || tag.trim().equals(" ")) {
            throw new VEOError(classname, method, 2, "Tag name is null, or blank");
        }

        // we must be already creating a Metadata Package
        if (state != VEOState.ADDING_MP) {
            throw new VEOError(classname, method, 3, "Can only continue a Metadata Package immediately after adding a Metadata Package or continuing a Metadata Package");
        }
        
        // output the simple element
        cvc.endComplexElement(tag);
    }

    /**
     * Add a new Information Piece to the Information Object with a particular
     * label.
     * <p>
     * Information Pieces must be added to an Information Object after all of
     * the Metadata Packages have been added to the Information Object.
     *
     * @param label the label to apply (can be null if no label is to be
     * included)
     * @throws VERSCommon.VEOError if an error occurred
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
     * Add a Content File to an Information Piece. A Content File is a reference
     * to a real physical computer file.
     * <p>
     * In order to understand this method, it is important to know that the
     * Content Files are represented in the VEO in an arbitrary directory
     * structure. The arguments to this method are the veoReference (the
     * directory structure in the VEO) and the source (the actual location of
     * the Content File in the file system). For example, if the veoReference is
     * 'c/d/e.txt', and the source 'm:/a/b/c/f.txt', this would incorporate the
     * file m:/a/b/c/f.txt into the VEO with the path in the VEO of c/d/e.txt
     * (note the Content File name has changed from 'f.txt' to 'e.txt' in this
     * case. The veoReference must have at least one directory level.
     * <p>
     * The veoReference argument cannot contain self ('.') or parent ('..')
     * directory references and must not be an absolute file name.
     * <p>
     * The actual veoReference is not physically included in the VEO until it is
     * ZIPped, and so it must exist until the finalise() method is called.
     * <p>
     * All the Content Files contained within an Information Piece must be added
     * to the Information Piece before a new Information Piece or Information
     * Object is added.
     * <p>
     * Neither argument is allowed to be null.
     *
     * @param veoReference path name of content file in the VEO
     * @param source actual real veoReference in the veoReference system
     * @throws VERSCommon.VEOError if an error occurred
     */
    public void addContentFile(String veoReference, Path source) throws VEOError {
        String method = "addContentFile";
        Path p;

        // sanity checks
        if (veoReference == null) {
            throw new VEOError(classname, method, 1, "veoReference parameter is null");
        }
        if (source == null) {
            throw new VEOError(classname, method, 2, "source file parameter is null");
        }

        // can only add Content Files when adding an Information Piece
        if (state != VEOState.ADDING_IP) {
            throw new VEOError(classname, method, 3, "Can only add a Content File when adding an Information Piece");
        }

        // veoReference must have a directory, and cannot be relative.
        if (veoReference.startsWith("./") || veoReference.startsWith("../")
                || veoReference.contains("/./") || veoReference.contains("/../")
                || veoReference.endsWith("/.") || veoReference.endsWith("/..")) {
            throw new VEOError(classname, method, 4, "veoReference parameter (" + veoReference + ") cannot contain file compenents '.' or '..'");
        }
        p = Paths.get(veoReference);
        if (p.isAbsolute()) {
            throw new VEOError(classname, method, 5, "veoReference parameter (" + veoReference + ") must not be absolute");
        }
        if (p.getNameCount() < 2) {
            throw new VEOError(classname, method, 6, "veoReference parameter (" + veoReference + ") must have at least one directory");
        }
        
        // source file must exist
        if (!Files.exists(source)) {
            throw new VEOError(classname, method, 7, "source file '" + source.toString() + "' does not exist");
        }

        // remember file to be zipped later
        filesToInclude.add(new FileToInclude(source, veoReference));

        // if ZIPping files, remember it...
        cvc.addContentFile(veoReference, source);
    }
    
    /**
     * Add a Content File to an Information Piece. A
     * Content File is a reference to a real physical computer file.
     * <p>
     * In this call, the files are referenced by a single file name that is
     * interpreted relative to the current working directory. In the other
     * addContentFile() calls, the file path is interpreted relative to a
     * Content Directory. In this call, the current working directory is
     * equivalent to the Content Directory.
     * <p>
     * The purpose of this division is that the relative part (c/d/e.txt in this
     * case) explicitly appears as a directory structure in the VEO when it is
     * generated.
     * <p>
     * The actual file is not physically included in the VEO until it is ZIPped,
     * and so it must exist until the finalise() method is called.
     * <p>
     * All the Content Files contained within an Information Piece must be added
     * to the Information Piece before a new Information Piece or Information
     * Object is added.
     * <p>
     * The file argument must not be null, and the actual referenced file must
     * exist.
     *
     * @param file the relative portion of the Content File being added
     * @throws VERSCommon.VEOError if an error occurred
     */
    public void addAbsContentFile(String file) throws VEOError {
        String method = "addContentFile";

        // sanity checks
        if (file == null) {
            throw new VEOError(classname, method, 1, "file parameter is null");
        }
        
        // interpret file relative to the current working directory
        addContentFile(file, Paths.get("", file));
    }
    
    /**
     * A synonym for registerContentDirectories(), retained for backwards
     * compatibility.
     *
     * @param directories a list of directories to be registered
     * @throws VERSCommon.VEOError if an error occurred
     */
    @Deprecated
    public void addContent(Path... directories) throws VEOError {
        registerContentDirectories(directories);
    }
    
    /**
     * Register content directories where content files will be found. This has
     * two purposes. First, it allows shorthand references to content files.
     * Second, the shorthand references will be the content directories in the
     * final ZIP file.
     * <p>
     * <b>
     * This method deprecated and is only used with the addContentFile(String)
     * method. Users should use the simpler and more easily understood
     * addContentFile(String, Path) method instead.
     * </b>
     * <p>
     * For example, if the content files are m:/a/b/c/d/e.txt and
     * m:/a/b/c/d/f.txt, you can register 'm:/a/b/c' and subsequently add
     * content files 'c/d/e.txt' and 'c/d/f.txt'. This will eventually create a
     * content directory 'c' in the VEO, which contains the files 'c/d/e.txt'
     * and 'c/d/f.txt'.
     * <p>
     * Note that the final directory name ('c') has to appear in both the
     * registered directory, and the content file; this forms the linkage. You
     * cannot register two directories with the same name (e.g. m:/a/b/c and
     * m:/r/s/c).
     * <p>
     * Multiple directories to be registered can be passed in one call to this
     * method. In addition, this method may be called multiple times to add
     * multiple directories. At least one directory must be listed in each call
     * to this method.
     * <p>
     * A directory must be registered before it is referenced in the
     * addContentFile() methods.
     *
     * @param directories a list of directories to be registered
     * @throws VERSCommon.VEOError if an error occurred
     */
    @Deprecated
    public void registerContentDirectories(Path... directories) throws VEOError {
        String method = "addContent";
        int i;
        String dirName;

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

            // check that the source content directory exists and is a directory
            if (directories[i] == null) {
                throw new VEOError(classname, method, 5, "a content directory is null");
            }
            if (!Files.exists(directories[i])) {
                throw new VEOError(classname, method, 6, "content directory '" + directories[i].toString() + "' does not exist");
            }
            if (!Files.isDirectory(directories[i])) {
                throw new VEOError(classname, method, 7, "content directory '" + directories[i].toString() + "' is not a directory");
            }

            // check that we are not adding a directory name twice
            dirName = directories[i].getFileName().toString();
            if (contentPrefixes.get(dirName) != null) {
                throw new VEOError(classname, method, 8, "content directory '" + dirName + "' (refenced in '" + directories[i].toString() + "') has already been registered");
            }

            // remember content directory prefix
            contentPrefixes.put(dirName, directories[i]);
        }
    }

    /**
     * Add a Content File to an Information Piece. A Content File is a reference
     * to a real physical computer file.
     * <p>
     * <b>
     * This method is deprecated and retained for backwards compatability. Users
     * should use the simpler and more easily understood
     * addContentFile(String, Path) method.
     * </b>
     * <p>
     * The files are referenced by a two part scheme, the parts are the path to
     * a Content Directory and a file reference relative to the Content
     * Directory. For example, to include the file m:/a/b/c/d/e.txt, you might
     * divide this up into a Content Directory m:a/b/c and a relative reference
     * c/d/e.txt. Note that the directory c appears in both parts.
     * <p>
     * The purpose of this division is that the relative part (c/d/e.txt in this
     * case) explicitly appears as a directory structure in the VEO when it is
     * generated.
     * <p>
     * The Content Directories (m:a/b/c in this example) are registered using
     * the registerContentDirectories() method. The portion relative to the
     * Content Directory (c/d/e.txt in this example) is passed as an argument to
     * the addContentFile() method. Note that the directory 'c' is used to link
     * the two portions together.
     * <p>
     * The actual file is not physically included in the VEO until it is ZIPped,
     * and so it must exist until the finalise() method is called.
     * <p>
     * All the Content Files contained within an Information Piece must be added
     * to the Information Piece before a new Information Piece or Information
     * Object is added.
     * <p>
     * The file argument must not be null, and the actual referenced file must
     * exist.
     *
     * @param file the relative portion of the Content File being added
     * @throws VERSCommon.VEOError if an error occurred
     */
    @Deprecated
    public void addContentFile(String file) throws VEOError {
        String method = "addContentFile";

        // sanity checks
        if (file == null) {
            throw new VEOError(classname, method, 1, "file parameter is null");
        }

        // can only add Content Files when adding an Information Piece
        if (state != VEOState.ADDING_IP) {
            throw new VEOError(classname, method, 2, "Can only add a Content File when adding an Information Piece");
        }

        // interpret file relative to a content directory
        addContentFile(file, getActualSourcePath(file));
    }

    /**
     * Get the path to the real source file. This method can only be used if you
     * are using the addContentFile(String) and
     * registerContentDirectories(Path...) methods. It converts the short hand
     * form 'c/d/e.txt' into a fully qualified name. If no directory 'c' has
     * been loaded by a registerContentDirectories() call, a VEOError is thrown.
     *
     * @param file the path name to be used in the VEO
     * @return the real file
     * @throws VERSCommon.VEOError if an error occurred
     */
    @Deprecated
    public Path getActualSourcePath(String file) throws VEOError {
        String method = "getSourcePath";
        Path rootPath, source, destination;
        String rootName;

        destination = Paths.get(file);
        rootName = destination.getName(0).toString();
        rootPath = contentPrefixes.get(rootName);
        if (rootPath == null) {
            throw new VEOError(classname, method, 1, "cannot match veoFile '" + file + "' to a content directory");
        }
        source = rootPath.resolve(destination.subpath(1, destination.getNameCount()));
        return source;
    }
    
    /**
     * Add an event to the VEOHistory.xml file. An event has five parameters:
     * the timestamp (optionally including the time) the event occurred; a label
     * naming the event, the name of the person who initiated the event; an
     * array of descriptions about the event; and an array of errors that the
     * event generated (if any).
     * <p>
     * Only the timestamp is mandatory, but it is expected that if the event is
     * null, at least one description would be present to describe the event.
     * <p>
     * Events may be added at any time until the finishFiles() method has been
     * called.
     *
     * @param timestamp the timestamp/time of the event in standard VEO format
     * @param event a string labelling the type of event
     * @param initiator a string labelling the initiator of the event
     * @param descriptions an array of descriptions of the event
     * @param errors an array of errors resulting
     * @throws VERSCommon.VEOError if an error occurred
     */
    public void addEvent(String timestamp, String event, String initiator, String[] descriptions, String[] errors) throws VEOError {
        String method = "addEvent";

        // sanity checks
        if (timestamp == null) {
            throw new VEOError(classname, method, 1, "date parameter is null");
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
        cvhf.addEvent(timestamp, event, initiator, descriptions, errors);
    }

    /**
     * Finalise the VEOContent and VEOHistory files. This method commences the
     * finishing of the VEO construction. It completes the VEOContent.xml and
     * VEOHistory.xml files.
     * <p>
     * This method must be called before the sign() method. Once this method has
     * been called, no further information can be added to the VEO (i.e.
     * Information Objects, Information Pieces, Content Files, or Events). This
     * method may be called only once.
     *
     * @throws VERSCommon.VEOError if an error occurred
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
     * controls the signature algorithm to be used.)
     * <p>
     * This method can be called repeatedly to create multiple pairs of
     * signature files by different signers.
     * <p>
     * This method must be called after calling the finaliseFiles() method.
     * <p>
     * Valid hashAlg values are 'SHA-1', 'SHA-256', 'SHA-384', and 'SHA-512'.
     * MD2 and MD5 are NOT supported as these are considered insecure today.
     * This hashAlg may be different to that specified when instantiating this
     * CreateVEO instance.
     *
     * @param signer PFX file representing the signer
     * @param hashAlg algorithm to use to hash file
     * @throws VERSCommon.VEOError if an error occurred
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
     * name as the VEO directory with the suffix '.veo.zip'.
     * <p>
     * In normal operation, this method will delete the VEO directory after the
     * ZIP file has been created, unless the debug flag was specified when the
     * VEO was created or the keepVEODir flag is set).
     * <p>
     * The method must be called after the last sign() method call. Once this
     * method has been called the VEO has been completed and none of the other
     * methods of this class can be called.
     *
     * @param keepVEODir if true the VEO directory is kept
     * @throws VERSCommon.VEOError if an error occurred
     */
    public void finalise(boolean keepVEODir) throws VEOError {
        String method = "finalise";
        // FileOutputStream fos = null;
        // BufferedOutputStream bos = null;
        ZipArchiveOutputStream zos = null;
        String zipName;
        Path p;

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
            p = veoDir.getParent();
            // changed to use the Apache ZIP implementation rather than the native Java one
            // fos = new FileOutputStream(Paths.get(p.toString(), zipName).toString());
            // bos = new BufferedOutputStream(fos);
            // zos = new ZipOutputStream(bos, StandardCharsets.UTF_8);
            zos = new ZipArchiveOutputStream(Paths.get(p.toString(), zipName).toFile());
            zos.setFallbackToUTF8(true);
            zos.setUseLanguageEncodingFlag(true);
            zos.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.ALWAYS);

            // recursively process VEO file
            zip(zos, p, veoDir);

            // include the content files
            zipContentFiles(zos, veoDir);

        } catch (IOException e) {
            throw new VEOError(classname, method, 1, "Error creating ZIP file: " + e.toString());
        } finally {
            try {
                if (zos != null) {
                    zos.close();
                }
                /*
                if (bos != null) {
                    bos.close();
                }
                if (fos != null) {
                    fos.close();
                }
                */
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
    private void zip(ZipArchiveOutputStream zos, Path veoDir, Path dir) throws IOException {
        String method = "zip";
        FileInputStream fis;
        BufferedInputStream bis;
        byte[] b = new byte[1024];
        DirectoryStream<Path> ds = null;
        int l;
        Path relPath;
        ZipArchiveEntry zs;

        try {
            // get a list of files in the VEO directory
            ds = Files.newDirectoryStream(dir);

            // go through list and for each file
            for (Path p : ds) {
                // log.log(Level.WARNING, "zipping:" + p.toString());

                // construct the Path between the veoDir and the file being linked
                relPath = veoDir.relativize(p.toAbsolutePath().normalize());

                // copy a regular file into the ZIP file
                if (relPath.getNameCount() != 0 && Files.isRegularFile(p)) {
                    // System.err.println("Adding '" + s + "'");
                    zs = new ZipArchiveEntry(relPath.toString());
                    zs.setTime(Files.getLastModifiedTime(p).toMillis());
                    zos.putArchiveEntry(zs);

                    // copy the content
                    fis = new FileInputStream(p.toString());
                    bis = new BufferedInputStream(fis);
                    while ((l = fis.read(b)) > 0) {
                        zos.write(b, 0, l);
                    }
                    bis.close();
                    fis.close();

                    // close this ZIP entry
                    zos.closeArchiveEntry();
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
    private void zipContentFiles(ZipArchiveOutputStream zos, Path veoDir) throws IOException {
        String method = "zipContentFiles";
        FileInputStream fis;
        BufferedInputStream bis;
        byte[] b = new byte[1024];
        FileToInclude fi;
        int i, l;
        Path relPath;
        ZipArchiveEntry zs;
        HashMap<String, Path> seen;

        // if resigning, the content files will already be in the VEO
        if (filesToInclude == null) {
            return;
        }

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
                zs = new ZipArchiveEntry(relPath.toString());
                zs.setTime(Files.getLastModifiedTime(fi.source).toMillis());
                zos.putArchiveEntry(zs);

                // copy the content
                fis = new FileInputStream(fi.source.toString());
                bis = new BufferedInputStream(fis);
                while ((l = fis.read(b)) > 0) {
                    zos.write(b, 0, l);
                }
                bis.close();
                fis.close();

                // close this ZIP entry
                zos.closeArchiveEntry();
            }
        }
        seen.clear();
        seen = null;
    }

    /**
     * Abandon construction of this VEO and free any resources associated with
     * it (including any files created). It is only necessary to use this method
     * in the event of a VEOError or VEOFatal thrown, or if it is otherwise
     * desired to not finish constructing the VEO. For normal use, this method
     * is automatically called from the finalise() method.
     * <p>
     * If the debug flag is set to true the constructed VEO directory is
     * retained.
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
        if (contentPrefixes != null) {
            contentPrefixes.clear();
        }
        contentPrefixes = null;
        if (filesToInclude != null) {
            filesToInclude.clear();
        }
        filesToInclude = null;

        // delete VEO directory if it exists
        try {
            if (!debug && veoDir != null && Files.exists(veoDir)) {
                deleteFile(veoDir);
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Deleting {0} failed: {1}", new Object[]{veoDir.toString(), e.toString()});
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
     * Test program and example code
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        CreateVEO cv;
        Path templateDir, outputDir, testData;
        Templates templates;
        String[] descriptions, errors;
        PFXUser signer;
        String filename;

        cv = null;
        try {
            templateDir = Paths.get("demo", "templates");
            outputDir = Paths.get("f:", "output");
            testData = Paths.get("f:", "VERS-V3-Package", "neoVEO", "demo");
            templates = new Templates(templateDir);
            System.out.println("Starting construction of VEO");

            // create an empty VEO. You need to create a new VEO for each VEO
            // constructed
            cv = new CreateVEO(outputDir, "testVEO", "SHA-256", true);
            cv.addVEOReadme(templateDir);
            filename = cv.getVEODir().toString();

            // add first information object with a depth of 1. A VEO need only
            // have one information object; if you are only creating one
            // (or a VEO with a simple list of information objects), use a
            // depth of 0.
            // this will have one AGLS metadata package with a minimum of metadata
            // one information piece with two content files
            cv.addInformationObject("Record", 1);
            cv.addMetadataPackage(templates.findTemplate("VEOCreate-min-agls"), new String[]{"http://prov.vic.gov.au/112344", "This is a Test Record", "Thomas Bent", "Test Organisation"});
            cv.addInformationPiece("Label");
            cv.addContentFile("S-37-6/S-37-6-Nov.docx", testData.resolve(Paths.get("S-37-6", "S-37-6-Nov.docx")));
            cv.addContentFile("S-37-6/S-37-6-Nov.pdf", testData.resolve(Paths.get("S-37-6", "S-37-6-Nov.pdf")));

            // add a second information that is a child of the first
            // this has no metadata package or information pieces
            // in all cases when creating a new information object, it is the
            // immediate child of the most recent information object with a
            // depth one less (in this case 1).
            cv.addInformationObject("Structural IO", 2);

            // add another information object, with two metadata packages, but
            // no information pieces
            cv.addInformationObject("Record", 3);
            cv.addMetadataPackage(templates.findTemplate("VEOCreate-min-agls"), new String[]{"http://prov.vic.gov.au/112345", "3rd level IO", "John Cain", "Test Organisation"});
            cv.addMetadataPackage(templates.findTemplate("VEOCreate-min-agls"), new String[]{"http://prov.vic.gov.au/112345", "Another Metadata Package", "Henry Bolte", "Test Organisation"});

            // add a sibling information object to the previous information
            // object with a metadata package, and
            // two information pieces, each with one content file
            cv.addInformationObject("Record", 3);
            cv.addMetadataPackage(templates.findTemplate("VEOCreate-min-agls"), new String[]{"http://prov.vic.gov.au/112346", "Yet another IO", "Jeff Kennett", "Test Organisation"});
            cv.addInformationPiece("Label1");
            cv.addContentFile("S-37-6/S-37-6-Nov.docx", testData.resolve(Paths.get("S-37-6", "S-37-6-Nov.docx")));
            cv.addInformationPiece("Label2");
            cv.addContentFile("S-37-6/S-37-6-Nov.pdf", testData.resolve(Paths.get("S-37-6", "S-37-6-Nov.pdf")));

            // and go all the way back to the root of the tree of IOs and add
            // a second child, just to show how its done by changing the depth.
            // Doesn't contain a metadata package (to show it's not necessary)
            cv.addInformationObject("Record", 2);
            cv.addInformationPiece("Label1");
            cv.addContentFile("S-37-6/S-37-6-Nov.docx", testData.resolve(Paths.get("S-37-6", "S-37-6-Nov.docx")));

            // create VEO history with two events (normally there will be
            // different descriptions and errors!
            descriptions = new String[]{"1st description", "2nd description"};
            errors = new String[]{"1st error report", "2nd error report"};
            cv.addEvent("2020-01-01", "Created", "Creator Name", descriptions, errors);
            cv.addEvent("2020-01-01", "Registered", "Registrar", descriptions, errors);
            cv.finishFiles();

            // sign the VEO
            signer = new PFXUser(testData.resolve("testSigner.pfx").toString(), "password");
            cv.sign(signer, "SHA-256");

            // zip
            cv.finalise(true);
            System.out.println("VEO '"+filename+".zip' constructed");
        } catch (VEOError e) {
            System.out.println(e.toString());
            if (cv != null) {
                cv.abandon(false);
            }
        }
    }
}
