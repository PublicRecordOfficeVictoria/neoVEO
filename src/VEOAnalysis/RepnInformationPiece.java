/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOAnalysis;

import VERSCommon.VEOError;
import java.io.BufferedWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class encapsulates an Information Piece in a VEO Content file.
 *
 * @author Andrew Waugh
 */
public class RepnInformationPiece extends Repn {

    private RepnItem label;  // label of the piece
    private ArrayList<RepnContentFile> contents;  // list of metadata packages
    
    /**
     * Construct an Information Object from the XML document VEOContent.xml.
     *
     * @param document the representation of the XML document
     * @param parentId the parent object identifier
     * @param seq the sequence number of this IP within the Information Object
     * @throws VEOError if the XML document has not been properly parsed
     */
    public RepnInformationPiece(RepnXML document, String parentId, int seq) throws VEOError {
        super(parentId + ":IP-" + seq);

        int i;

        // vers:Label
        if (document.checkElement("vers:Label")) {
            label = new RepnItem(getId(), "Information piece label");
            label.setValue(document.getTextValue());
            document.gotoNextElement();
        } else {
            label = null;
        }

        contents = new ArrayList<>();
        i = 0;
        while (!document.atEnd() && document.checkElement("vers:ContentFile")) {
            document.gotoNextElement();
            i++;
            contents.add(new RepnContentFile(document, getId(), i));
        }
    }

    /**
     * Free resources associated with this object
     */
    @Override
    public void abandon() {
        int i;

        super.abandon();
        if (label != null) {
            label.abandon();
            label = null;
        }
        for (i = 0; i < contents.size(); i++) {
            contents.get(i).abandon();
        }
        contents.clear();
        contents = null;
    }

    /**
     * Validate the data in the Information Piece.
     *
     * @param veoDir the directory containing the contents of the VEO
     * @param hashAlgorithm the hash algorithm used to check integrity of the
     * @param contentFiles the collection of content files in the VEO files
     * @param ltpfs HashMap of valid long term preservation formats
     * @throws VEOError if an error occurred that won't preclude processing
     * another VEO
     */
    public void validate(Path veoDir, String hashAlgorithm, HashMap<Path, RepnFile> contentFiles, HashMap<String, String> ltpfs) throws VEOError {
        int i;
        RepnContentFile rcf;
        boolean validLTPF;

        // validate Content Files within Information Piece
        validLTPF = false;
        for (i = 0; i < contents.size(); i++) {
            rcf = contents.get(i);
            validLTPF |= rcf.validate(veoDir, hashAlgorithm, contentFiles, ltpfs);
        }
        
        // must have at least one valid long term preservation format
        if (!validLTPF) {
            addError("Information piece did not have a valid long term preservation format");
        }
    }

    /**
     * Check if this object has any errors?
     *
     * @return true if errors have been detected
     */
    @Override
    public boolean hasErrors() {
        int i;

        for (i = 0; i < contents.size(); i++) {
            hasErrors |= contents.get(i).hasErrors();
        }
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
        int i;

        sb = new StringBuffer();
        sb.append(super.getErrors());
        for (i = 0; i < contents.size(); i++) {
            sb.append(contents.get(i).getErrors());
        }
        return sb.toString();
    }

    /**
     * Has this object (or its children) any warnings?
     *
     * @return true if errors have been detected
     */
    @Override
    public boolean hasWarnings() {
        int i;

        for (i = 0; i < contents.size(); i++) {
            hasWarnings |= contents.get(i).hasWarnings();
        }
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
        int i;

        sb = new StringBuffer();
        sb.append(super.getWarnings());
        for (i = 0; i < contents.size() & !hasWarnings; i++) {
            sb.append(contents.get(i).getWarnings());
        }
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
        int i;

        sb = new StringBuffer();
        sb.append("   Information Piece (Label:");
        if (label != null) {
            sb.append(label);
        } else {
            sb.append("<no label>");
        }
        sb.append(")\n");
        for (i = 0; i < contents.size(); i++) {
            sb.append(contents.get(i).toString());
        }
        return sb.toString();
    }

    /**
     * Generate an XML representation of the information piece
     * @param verbose true if additional information is to be generated
     * @throws VEOSupport.VEOError  if a fatal error occurred
     */
    
    public void genReport(boolean verbose) throws VEOError {
        int i;

        startDiv("InfoPiece", null);
        addLabel("Information Piece");
        if (label != null) {
            addString(" (Label: "+label.getValue()+")");
        } else {
            addString(" <no label>");
        }
        addString("\n");
        if (hasErrors || hasWarnings) {
            addTag("<ul>\n");
            listIssues();
            addTag("</ul>\n");
        }
        for (i = 0; i < contents.size(); i++) {
            contents.get(i).genReport(verbose);
        }
        endDiv();
    }

    /**
     * Tell all the Representations where to write the HTML
     *
     * @param bw buffered writer for output
     */
    @Override
    public void setReportWriter(BufferedWriter bw) {
        int i;

        super.setReportWriter(bw);
        if (label != null) {
            label.setReportWriter(bw);
        }
        for (i = 0; i < contents.size(); i++) {
            contents.get(i).setReportWriter(bw);
        }
    }
}