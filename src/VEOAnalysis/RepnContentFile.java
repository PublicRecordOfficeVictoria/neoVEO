/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOAnalysis;

import VERSCommon.VEOError;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.DatatypeConverter;

/**
 * This class encapsulates a Content File in a VEO Content file.
 *
 * @author Andrew Waugh
 */
public class RepnContentFile extends Repn {

    private final String classname = "RepnContentFile";
    private final int id; // unique id for this content file
    private RepnFile rf; // representation of this file
    private RepnItem pathName;  // file name of the content file
    private RepnItem hashValue; // genHash value of the content file
    private final static Logger LOG = Logger.getLogger("VEOAnalysis.RepnContent");

    static int idCnt; // counter to generate unique id (not thread safe)

    /**
     * Construct a Content File from the XML document VEOContent.xml.
     *
     * @param document the representation of the XML document
     * @param parentId the parent object identifier
     * @param seq the sequence number of this Content File within the IP
     * @throws VEOError if the XML document has not been properly parsed
     */
    public RepnContentFile(RepnXML document, String parentId, int seq) throws VEOError {
        super(parentId + ":CF-" + seq);

        // allocate unique id
        id = idCnt;
        idCnt++;
        rf = null;

        // vers:PathName
        pathName = new RepnItem(getId() + ":pathName", "Path name of content file");
        pathName.setValue(document.getTextValue());
        document.gotoNextElement();
        // vers:HashValue
        hashValue = new RepnItem(getId() + ":hashValue", "Hash value of content file");
        hashValue.setValue(document.getTextValue());
        document.gotoNextElement();
    }

    /**
     * Free resources associated with this Content File.
     */
    @Override
    public void abandon() {
        super.abandon();
        rf = null;
        pathName.abandon();
        pathName = null;
        hashValue.abandon();
        hashValue = null;
    }

    /**
     * Validate the Content File. This checks that the
     * <ul>
     * <li>named file exists in the VEO</li>
     * <li>the calculated hash value has not been altered</li>
     * <li>whether the file format is one of the valid LTPFs</li>
     * </ul>
     *
     * @param veoDir directory in which to find the content files
     * @param hashAlgorithm algorithm to verify the digital signature
     * @param contentFiles the collection of content files in this VEO
     * @param ltpfs HashMap of valid long term preservation formats
     * @return true if the file was a valid long term preservation format
     * @throws VEOError if a unrecoverable error occurred
     */
    public boolean validate(Path veoDir, String hashAlgorithm, HashMap<Path, RepnFile> contentFiles, HashMap<String, String> ltpfs) throws VEOError {
        String method = "validate";
        Path p;
        Path fileToHash;    // path of content file
        MessageDigest md;
        FileInputStream fis; // file to genHash
        BufferedInputStream bis;
        byte[] genHash;            // generated genHash
        byte[] storedHash;      // genHash read from file
        int i;
        byte[] b = new byte[1000]; // buffer used to read input file
        boolean ltpf;
        String s, fmt;
 
        // test to see if this file is a LTPF (using the file name extension)
        ltpf = false;
        s = pathName.getValue();
        i = s.lastIndexOf(".");
        if (i != -1) {
            fmt = s.substring(i).toLowerCase();
            ltpf = (ltpfs.get(fmt) != null);
        }
        
        // check that the file exists
        fileToHash = veoDir.resolve(pathName.getValue());
        if (Files.notExists(fileToHash)) {
            addError("Referenced file '" + fileToHash.toString() + "' does not exist");
            return ltpf;
        }

        // get the RepnFile associated with this content file, and mark it off the file in the list of files in VEO
        p = Paths.get(pathName.getValue());
        if (contentFiles.containsKey(p)) {
            rf = contentFiles.get(p);
            rf.setContentFile(this);
        } else {
            LOG.log(Level.WARNING, errMesg(classname, method, "VEOContent.xml referenced content file (" + pathName.getValue() + "), but it could not be found in the index"));
        }

        // get message digest
        try {
            md = MessageDigest.getInstance(hashAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            addError("Hash algorithm '" + hashAlgorithm + "' not supported");
            return ltpf;
        }
        
        // open the file to digest
        try {
            fis = new FileInputStream(fileToHash.toString());
        } catch (FileNotFoundException e) {
            LOG.log(Level.WARNING, errMesg(classname, method, "File to hash not found: ", e));
            return ltpf;
        }
        bis = new BufferedInputStream(fis);

        // enter the bytes from the file
        try {
            while ((i = bis.read(b)) != -1) {
                md.update(b, 0, i);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, errMesg(classname, method, "Failed reading file to hash: ", e));
        } finally {
            try {
                bis.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, errMesg(classname, method, "failed closing file to hash: ", e));
            }
        }

        // calculate hash on file
        genHash = md.digest();

        // extract the stored hash
        storedHash = null;
        try {
            storedHash = DatatypeConverter.parseBase64Binary(hashValue.getValue());
        } catch (IllegalArgumentException e) {
            hashValue.addError("Converting Base64 signature failed: " + e.getMessage());
        }

        // System.out.println("Hashing "+pathName);
        // System.out.println("Orig "+DatatypeConverter.printBase64Binary(hashValue));
        // System.out.println("Calc "+DatatypeConverter.printBase64Binary(genHash));
        if (storedHash != null && !MessageDigest.isEqual(genHash, storedHash)) {
            addError("Integrity check of file '" + pathName + "' failed as hash value has changed.");
        }

        return ltpf;
    }

    /**
     * Get the anchor to link to this object
     *
     * @return String representing this object
     */
    public String getAnchor() {
        return "Report-VEOContent.html#rcf" + id;
    }

    /**
     * Check if this object has any errors?
     *
     * @return true if errors have been detected
     */
    @Override
    public boolean hasErrors() {
        hasErrors |= pathName.hasErrors() | hashValue.hasErrors();
        return hasErrors;
    }

    /**
     * Build a list of all of the errors generated by this object
     *
     * @return The concatenated error list
     */
    @Override
    public String getErrors() {
        StringBuffer sb;

        sb = new StringBuffer();
        sb.append(super.getErrors());
        sb.append(pathName.getErrors());
        sb.append(hashValue.getErrors());
        return sb.toString();
    }

    /**
     * Has this object (or its children) any warnings?
     *
     * @return true if errors have been detected
     */
    @Override
    public boolean hasWarnings() {
        hasWarnings |= pathName.hasWarnings() | hashValue.hasWarnings();
        return hasWarnings;
    }

    /**
     * Build a list of all of the warnings generated by this object
     *
     * @return The concatenated error list
     */
    @Override
    public String getWarnings() {
        StringBuffer sb;

        sb = new StringBuffer();
        sb.append(super.getWarnings());
        sb.append(pathName.getWarnings());
        sb.append(hashValue.getWarnings());
        return sb.toString();
    }

    /**
     * Produce a string representation of the Information Piece
     *
     * @return The string representation
     */
    @Override
    public String toString() {
        StringBuffer sb;

        sb = new StringBuffer();
        sb.append("    Content File - Path Name:'");
        sb.append(pathName.getValue());
        sb.append("' Hash Value:");
        sb.append(hashValue.getValue());
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Generate a HTML representation of the content file
     *
     * @param verbose true if additional information is to be generated
     * @throws VEOSupport.VEOError if a fatal error occurred
     */
    public void genReport(boolean verbose) throws VEOError {
        startDiv("ContentFile", getAnchor());
        addLabel("Content file");
        addTag("<a href=\"./" + pathName.getValue() + "\">");
        addString(pathName.getValue());
        addTag("</a> ");
        if (rf != null) {
            addTag("<a href=\"" + rf.getAnchor() + "\">");
            addString("(More) ");
            addTag("</a> ");
        }
        addString(" Hash value: '" + hashValue.getValue() + "'");
        if (hasErrors || hasWarnings) {
            addTag("<ul>\n");
            listIssues();
            pathName.listIssues();
            hashValue.listIssues();
            addTag("</ul>\n");
        }
        endDiv();
    }

    /**
     * Tell all the Representations where to write the HTML
     *
     * @param bw buffered writer where to write the HTML
     */
    @Override
    public void setReportWriter(BufferedWriter bw) {
        super.setReportWriter(bw);
        pathName.setReportWriter(bw);
        hashValue.setReportWriter(bw);
    }

}