/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 * 25-06-2018 changed addContentFile() to have explicit source and destination
 * parameters
 */
package VEOCreate;

import VERSCommon.VEOError;
import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;
import java.util.Base64;

/**
 * This class creates a VEOContent.xml file. This class should not be directly
 * used, instead use the CreateVEO or CreateVEOs classes.
 */
class CreateVEOContent extends CreateXMLDoc {

    String version; // version to use (default is "3.0")

    private enum State {
        NOT_STARTED, // First Information Object has not been started
        FIRST_STG_INFO_OBJ, // Information Object has been started, but an Information Piece has not been added
        GEN_META_PACK, // Metadata Package is being generated
        FIRST_STG_INFO_PIECE, // Information piece has been started, but no Content File has been added
        SCND_STG_INFO_PIECE, // Content File has been added
        SCND_STG_INFO_OBJ, // Information Pieces have been added
        FINISHED_INFO_OBJ     // Ready to start new Information Object
    }
    State state;      // the state of creation of the content file
    boolean rdfMetadataPackage; // true if creating an RDF MetadataPackage

    String hashAlgorithm; // hash algorithm to use on content files

    private final static Logger LOG = Logger.getLogger("veocreate.createSignatureFile");

    static String contents1
            = " <vers:Version>";
    static String contents2
            = "</vers:Version>\r\n";
    static String contents3
            = " <vers:HashFunctionAlgorithm>";
    static String contents4
            = "</vers:HashFunctionAlgorithm>\r\n";

    /**
     * Create an instance of a VEOContent.xml file. The file is created in the
     * veoDir directory. The file will have the specified version, and will use
     * the specified hashAlgorithm to generate fixity information for the
     * content files.
     * <p>
     * The hash algorithm must be one of: "SHA-1", "SHA-256", "SHA-384", or
     * "SHA-512".
     *
     * @param veoDir the directory in which to create the VEOContent file
     * @param version the version of the standard (currently always "3.0"
     * @param hashAlgorithm the hash algorithm used on content files
     * @throws VEOError if this VEO could not be generated.
     */
    public CreateVEOContent(Path veoDir, String version, String hashAlgorithm) throws VEOError {
        super(veoDir);
        classname = "CreateVEOContent";

        // check version number (should be "3.0" but this code is generic)
        if (version == null) {
            throw new VEOError(classname, 1, "version is null");
        }
        if (version.trim().equals(" ")) {
            throw new VEOError(classname, 1, "version is blank");
        }
        this.version = version;
        rdfMetadataPackage = false;

        // check hash algorithm specification
        if (hashAlgorithm == null) {
            throw new VEOError(classname, 1, "hash algorithm is null");
        }
        if (hashAlgorithm.trim().equals(" ")) {
            throw new VEOError(classname, 1, "hash algorithm is blank");
        }

        // explicitly refuse to use MD2 or MD5 as these are insufficient secure
        if (hashAlgorithm.trim().equals("MD2")
                || hashAlgorithm.trim().equals("MD5")) {
            throw new VEOError(classname, 1, "hash algorithm '" + hashAlgorithm + "' is not supported as they are insecure");
        }
        // check that hash algorithm is one of the SHA varients
        if (!hashAlgorithm.trim().equals("SHA-1")
                && !hashAlgorithm.trim().equals("SHA-256")
                && !hashAlgorithm.trim().equals("SHA-384")
                && !hashAlgorithm.trim().equals("SHA-512")) {
            throw new VEOError(classname, 1, "hash algorithm '" + hashAlgorithm + "' must be one of SHA-1, SHA-256, SHA-384, or SHA-512");
        }
        this.hashAlgorithm = hashAlgorithm;
        startXMLDoc("VEOContent.xml", "vers:VEOContent");

        // VEO Version
        write(contents1);
        writeValue(version);
        write(contents2);

        // Hash function
        write(contents3);
        writeValue(hashAlgorithm);
        write(contents4);

        state = State.NOT_STARTED;
        LOG.exiting("CreateVEOHistoryFile", "start");
    }

    static String contentsIO1
            = " <vers:InformationObject>\r\n  <vers:InformationObjectType>";
    static String contentsIO2
            = "</vers:InformationObjectType>\r\n  <vers:InformationObjectDepth>";
    static String contentsIO3
            = "</vers:InformationObjectDepth>\r\n";
    static String contentsIO4
            = " </vers:InformationObject>\r\n";

    /**
     * Start an Information Object in the VEOContent.xml file
     *
     * @param type the type of the InformationObject
     * @param depth the depth in a inorder traversal
     * @throws VEOError if this VEOContent.xml file cannot be completed.
     */
    public void startInfoObject(String type, int depth) throws VEOError {
        String method = "startInfoObject";

        // are we ready to start an information object?
        if (state != State.NOT_STARTED && state != State.FINISHED_INFO_OBJ) {
            throw new VEOError(classname, method, 1, "Information Object has already started");
        }
        state = State.FIRST_STG_INFO_OBJ;

        // sanity checks...
        if (type == null) {
            throw new VEOError(classname, method, 1, "type is null");
        }
        if (type.trim().equals(" ")) {
            throw new VEOError(classname, method, 1, "type is blank");
        }
        if (depth < 0) {
            throw new VEOError(classname, method, 1, "depth is < 0");
        }

        // start information object
        write(contentsIO1);
        writeValue(type);
        write(contentsIO2);
        writeValue(Integer.toString(depth));
        write(contentsIO3);
    }

    /**
     * End an Information Object in the VEOContent.xml file.
     *
     * @throws VEOError if this VEOContent.xml file cannot be completed.
     */
    public void finishInfoObject() throws VEOError {
        String method = "finishInfoObject";

        // are we ready to start an information object?
        if (state != State.FIRST_STG_INFO_OBJ && state != State.SCND_STG_INFO_OBJ) {
            throw new VEOError(classname, method, 1, "Information Object finished before an Information Piece added");
        }
        state = State.FINISHED_INFO_OBJ;

        // start information object
        write(contentsIO4);
    }

    static String contentsMP1
            = "  <vers:MetadataPackage ";
    static String contentsMP2
            = ">\r\n   <vers:MetadataSchemaIdentifier>";
    static String contentsMP3
            = "</vers:MetadataSchemaIdentifier>\r\n   <vers:MetadataSyntaxIdentifier>";
    static String contentsMP4
            = "</vers:MetadataSyntaxIdentifier>\r\n";
    static String contentsMP5
            = "  </vers:MetadataPackage>\r\n";
    static String contentsRDFMP1
            = "   <rdf:RDF";
    static String contentsRDFMP2
            = ">\r\n    <rdf:Description rdf:about=\"";
    static String contentsRDFMP3
            = "\">\r\n";
    static String contentsRDFMP4
            = "    </rdf:Description>\r\n"
            + "   </rdf:RDF>\r\n";

    /**
     * Start an RDF metadata package (a specific type of metadata package). The
     * namespaceDefns may be NULL, but the resourceId must be present.
     *
     * @param semanticId the string to use to identify the meaning of metadata
     * @param namespaceDefns any namespace definitions to include in rdf:RDF
     * @param entityId a URL identifying the resource the RDF is describing
     * @throws VEOError
     */
    public void startRDFMetadataPackage(String semanticId, String namespaceDefns, URL resourceId) throws VEOError {
        String method = "startRDFMetadataPackage";

        startMetadataPackage(semanticId, "http://www.w3.org/1999/02/22-rdf-syntax-ns");
        write(contentsRDFMP1);
        if (namespaceDefns != null) {
            write(" ");
            write(namespaceDefns);
        }
        write(contentsRDFMP2);
        write(resourceId.toExternalForm());
        write(contentsRDFMP3);
        rdfMetadataPackage = true;
        indentDepth = 6;
    }

    /**
     * Start an an XML metadata package (a specific type of metadata package).
     *
     * @param semanticId the string to use to identify the meaning of metadata
     * @throws VEOError
     */
    public void startXMLMetadataPackage(String semanticId) throws VEOError {
        String method = "startRDFMetadataPackage";

        startMetadataPackage(semanticId, "https://www.w3.org/TR/2008/REC-xml-20081126/");
        indentDepth = 3;
    }

    /**
     * Start a Metadata Package in an Information Object
     *
     * @param semanticId a URI that indicates the meaning of this metadata
     * package
     * @param syntaxId a URI that indicates the syntax of this metadata package
     * @throws VEOError if a fatal error occurred
     */
    public void startMetadataPackage(String semanticId, String syntaxId) throws VEOError {
        String method = "startMetadataPackage";

        // are we ready to start an information object?
        if (state != State.FIRST_STG_INFO_OBJ) {
            throw new VEOError(classname, method, 1, "Not ready to start a metadata package");
        }
        state = State.GEN_META_PACK;

        // start information object
        write(contentsMP1);
        if (syntaxId.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns")) {
            write("xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"");
        }
        write(contentsMP2);
        writeValue(semanticId);
        write(contentsMP3);
        writeValue(syntaxId);
        write(contentsMP4);
        indentDepth = 0;
    }

    /**
     * Completes a template (a metadata package).
     *
     * @param template template to complete
     * @param data data to populate template
     * @throws VEOError if a fatal error occurred
     */
    public void addFromTemplate(Fragment template, String[] data) throws VEOError {
        String method = "addFromTemplate";

        if (state != State.GEN_META_PACK) {
            throw new VEOError(classname, method, 1, "Cannot add content to a metadata package before starting it");
        }
        if (template == null) {
            throw new VEOError(classname, method, 1, "template is null");
        }
        if (data == null) {
            throw new VEOError(classname, method, 1, "data is null");
        }
        template.finalise(data, this);
    }

    /**
     * Adds a prebuilt metadata packages from a StringBuilder
     *
     * @param data stringbuilder containing prebuilt metadata package
     * @throws VEOError if a fatal error occurred
     */
    public void addPrebuiltMP(StringBuilder data) throws VEOError {
        String method = "addPrebuiltMP";
        if (state != State.GEN_META_PACK) {
            throw new VEOError(classname, method, 1, "Cannot add content to a metadata package before starting it");
        }
        if (data == null) {
            throw new VEOError(classname, method, 1, "data is null");
        }
        write(data.toString());
    }

    /**
     * Adds a prebuilt metadata packages from a String
     *
     * @param data string containing prebuilt metadata package
     * @throws VEOError if a fatal error occurred
     */
    public void addPrebuiltMP(String data) throws VEOError {
        String method = "addPrebuiltMP";
        if (state != State.GEN_META_PACK) {
            throw new VEOError(classname, method, 1, "Cannot add content to a metadata package before starting it");
        }
        if (data == null) {
            throw new VEOError(classname, method, 1, "data is null");
        }
        write(data);
    }

    /**
     * Finishes a metadata package
     *
     * @throws VEOError if a fatal error occurred
     */
    public void finishMetadataPackage() throws VEOError {
        String method = "finishMetadataPackage";

        // are we ready to start an information object?
        if (state != State.GEN_META_PACK) {
            throw new VEOError(classname, method, 1, "Cannot finish a metadata package before starting it");
        }
        state = State.FIRST_STG_INFO_OBJ;

        // if generating an RDF metadata package, finish it
        if (rdfMetadataPackage) {
            write(contentsRDFMP4);
        }
        rdfMetadataPackage = false;

        // finish the metadata package
        write(contentsMP5);
    }

    static String contentsIP1
            = "  <vers:InformationPiece>\r\n";
    static String contentsIP2 = "   <vers:Label>";
    static String contentsIP3
            = "</vers:Label>\r\n";
    static String contentsIP4
            = "  </vers:InformationPiece>\r\n";

    /**
     * Start an Information Piece in the Information Object
     *
     * @param label the label of the Information Piece
     * @throws VEOError if a fatal error occurred
     */
    public void startInfoPiece(String label) throws VEOError {
        String method = "startInfoPiece";

        // are we ready to start an information object?
        if (state != State.FIRST_STG_INFO_OBJ && state != State.SCND_STG_INFO_OBJ) {
            throw new VEOError(classname, method, 1, "Information Piece must be started after Metadata Packages have been added");
        }
        state = State.FIRST_STG_INFO_PIECE;

        // start information object
        write(contentsIP1);
        if (label != null) {
            if (label.trim().equals(" ")) {
                throw new VEOError(classname, method, 1, "label is blank");
            }
            write(contentsIP2);
            writeValue(label);
            write(contentsIP3);
        }
    }

    /**
     * End an Information Piece
     *
     * @throws VEOError if a fatal error occurred
     */
    public void finishInfoPiece() throws VEOError {
        String method = "finishInfoPiece";

        // are we ready to start an information object?
        if (state != State.SCND_STG_INFO_PIECE) {
            throw new VEOError(classname, method, 1, "Information Piece finished before any Information Content added");
        }
        state = State.SCND_STG_INFO_OBJ;

        // start information object
        write(contentsIP4);
    }

    static String contentsCF1
            = "   <vers:ContentFile>\r\n    <vers:PathName>";
    static String contentsCF2
            = "</vers:PathName>\r\n    <vers:HashValue>";
    static String contentsCF3
            = "</vers:HashValue>\r\n   </vers:ContentFile>\r\n";

    /**
     * Add a content file to an Information Piece
     *
     * @param nameInVEO the name of the content file being added
     * @param fileToHash the fileToHash of the file at the moment
     * @throws VEOError if a fatal error occurred
     */
    public void addContentFile(String nameInVEO, Path fileToHash) throws VEOError {
        String method = "addContentFile";
        MessageDigest md;
        FileInputStream fis;    // input streams to read file to sign
        BufferedInputStream bis;//
        byte[] hash;            // generated hash
        int i;
        byte[] b = new byte[1000]; // buffer used to read input file

        // are we ready to start an information object?
        if (state != State.FIRST_STG_INFO_PIECE && state != State.SCND_STG_INFO_PIECE) {
            throw new VEOError(classname, method, 1, "Information Object has already started");
        }
        state = State.SCND_STG_INFO_PIECE;

        // sanity checks...
        if (nameInVEO == null) {
            throw new VEOError(classname, method, 1, "nameInVEO is null");
        }
        if (nameInVEO.trim().equals(" ")) {
            throw new VEOError(classname, method, 1, "nameInVEO is blank");
        }

        // check that the file exists
        if (fileToHash == null) {
            try {
                fileToHash = veoDir.resolve(nameInVEO);
            } catch (InvalidPathException ipe) {
                throw new VEOError(classname, method, 2, "Destination file name (" + nameInVEO + ") was invalid: " + ipe.getMessage());
            }
        }
        if (Files.notExists(fileToHash)) {
            throw new VEOError(classname, method, 1, "File to add '" + fileToHash.toString() + "' does not exist");
        }

        // get message digest
        try {
            md = MessageDigest.getInstance(hashAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new VEOError(classname, method, 1, "Hash algorithm '" + hashAlgorithm + "' not supported");
        }
        // open the file to digest
        try {
            fis = new FileInputStream(fileToHash.toString());
        } catch (FileNotFoundException e) {
            throw new VEOError(classname, method, 1, "File to sign ('" + fileToHash.toString() + "') was not found");
        }
        bis = new BufferedInputStream(fis);

        // enter the bytes from the file
        try {
            while ((i = bis.read(b)) != -1) {
                md.update(b, 0, i);
            }
        } catch (IOException e) {
            throw new VEOError(classname, method, 1, "failed reading file to sign: " + e.getMessage());
        }

        // close the input file
        try {
            bis.close();
        } catch (IOException e) {
            throw new VEOError(classname, method, 1, "failed closing file to sign: " + e.getMessage());
        }

        // calculate the digital signature over the input file
        // calculate signature and convert it into a byte buffer
        hash = md.digest();

        // add the Content File
        write(contentsCF1);
        writeValue(nameInVEO);
        write(contentsCF2);

        // output hash value
        writeValue(Base64.getEncoder().encodeToString(hash));
        write(contentsCF3);
    }

    /**
     * Finishes a VEOContent.xml file.
     *
     * @throws VEOError if a fatal error occurred
     */
    public void finalise() throws VEOError {
        String method = "Finalise";
        LOG.entering("CreateVEOContentFile", "finalise");

        // check start is only called once...
        if (state != State.FINISHED_INFO_OBJ) {
            throw new VEOError(classname, method, 1, "finalise() called before start() or after finalise()");
        }

        // close XML document
        endXMLDoc();

        LOG.exiting("CreateVEOContentFile", "finalise");
    }
}
