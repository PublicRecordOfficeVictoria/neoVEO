/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOAnalysis;

import VERSCommon.LTSF;
import VERSCommon.ResultSummary;
import VERSCommon.VEOError;
import java.io.BufferedWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * This class represents the content of a VEOContent.xml file.
 *
 * @author Andrew Waugh
 */
class RepnContent extends RepnXML {

    String classname = "RepnContent";
    RepnItem version; // version identifier of this VEOContent.xml file
    RepnItem hashAlgorithm; // identifier of the hash function used
    ArrayList<RepnInformationObject> infoObjs;    // list of events associated with this history

    /**
     * Builds an internal representation of the VEOContent.xml file, validating
     * it against the schema in VEOContent.xsd.
     *
     * @param veoDir VEO directory in which the VEOContent.xml file is
     * @param schemaDir schemaDir directory in which the VEOContent.xsd file is
     * @param contentFiles collection of content files in VEO
     * @throws VEOError if a fatal error occurred
     */
    public RepnContent(Path veoDir, Path schemaDir, HashMap<Path, RepnFile> contentFiles, ResultSummary results) throws VEOError {
        super("VEOContent.xml", results);

        RepnInformationObject io;
        Path file, schema;
        int i;

        version = new RepnItem(getId(), "Version", results);
        hashAlgorithm = new RepnItem(getId(), "Hash algorithm", results);
        infoObjs = new ArrayList<>();

        // parse the VEOContent.xml file against the VEOContent scheme
        file = veoDir.resolve("VEOContent.xml");
        schema = schemaDir.resolve("vers3-content.xsd");
        if (!parse(file, schema)) {
            return;
        }

        // extract the information from the DOM representation
        gotoRootElement();
        checkElement("vers:VEOContentFile");
        gotoNextElement();
        if (checkElement("vers:Version")) {
            version.setValue(getTextValue());
            gotoNextElement();
        }
        if (checkElement("vers:HashFunctionAlgorithm")) {
            hashAlgorithm.setValue(getTextValue());
            gotoNextElement();
        }

        // step through the information objects
        i = 0;
        while (!atEnd() && checkElement("vers:InformationObject")) {
            gotoNextElement();
            i++;
            io = new RepnInformationObject(this, getId(), i, results);
            infoObjs.add(io);
        }
    }

    /**
     * Free resources associated with this RepnContent object.
     */
    @Override
    public void abandon() {
        int i;

        super.abandon();
        version.abandon();
        version = null;
        hashAlgorithm.abandon();
        hashAlgorithm = null;
        for (i = 0; i < infoObjs.size(); i++) {
            infoObjs.get(i).abandon();
        }
        infoObjs.clear();
    }

    /**
     * Validate the data in the VEOContent.xml file.
     *
     * @param veoDir the directory containing the contents of the VEO
     * @param contentFiles the collection of content files in the VEO
     * @param ltsfs List of valid long term sustainable formats
     * @param noRec true if not to complain about missing recommended metadata elements
     * @throws VEOError if an error occurred that won't preclude processing
     * another VEO
     */
    public final void validate(Path veoDir, HashMap<Path, RepnFile> contentFiles, LTSF ltsfs, boolean noRec) throws VEOError {
        String s;
        Boolean oneLevel;
        int prevDepth, i;
        RepnInformationObject rio;

        // can't validate if parse failed...
        if (!contentsAvailable()) {
            return;
        }

        // validate version...
        if (!version.getValue().equals("3.0")) {
            version.addWarning("VEOVersion has a value of '" + version.getValue() + "' instead of '3.0'");
        }

        // validate hash algorithm...
        s = hashAlgorithm.getValue();
        if (!s.equals("SHA-1") && !s.equals("SHA-256") && !s.equals("SHA-384") && !s.equals("SHA-512")) {
            hashAlgorithm.addError("VEOHashFunctionAlgorithm has a value of '" + hashAlgorithm.getValue() + "' instead of 'SHA-1', 'SHA-256', 'SHA-384', or 'SHA-512'");
        }

        // validate information objects...
        oneLevel = false;
        prevDepth = 0;
        for (i = 0; i < infoObjs.size(); i++) {
            rio = infoObjs.get(i);

            // if the depth of the first IO is 0, assume flat list of IOs.
            if (i == 0) {
                if (rio.getDepth() == 0) {
                    oneLevel = true;
                }
            }
            prevDepth = rio.validate(veoDir, hashAlgorithm.getValue(), contentFiles, ltsfs, oneLevel, prevDepth, noRec);
        }
    }

    /**
     * Check if this VEOContent.xml file has any errors?
     *
     * @return true if errors have been detected
     */
    @Override
    public boolean hasErrors() {
        int i;

        hasErrors |= version.hasErrors() | hashAlgorithm.hasErrors();
        for (i = 0; i < infoObjs.size(); i++) {
            hasErrors |= infoObjs.get(i).hasErrors();
        }
        return hasErrors;
    }

    /**
     * Build a list of all of the errors generated by this VEOContent.xml file.
     *
     * @return The concatenated error list
     */
    @Override
    public String getErrors() {
        StringBuffer sb;
        int i;

        sb = new StringBuffer();
        sb.append(super.getErrors());
        sb.append(version.getErrors());
        sb.append(hashAlgorithm.getErrors());
        for (i = 0; i < infoObjs.size(); i++) {
            sb.append(infoObjs.get(i).getErrors());
        }
        return sb.toString();
    }

    /**
     * Has this VEOContent.xml file any warnings?
     *
     * @return true if errors have been detected
     */
    @Override
    public boolean hasWarnings() {
        int i;

        hasWarnings |= version.hasWarnings() | hashAlgorithm.hasWarnings();
        for (i = 0; i < infoObjs.size(); i++) {
            hasWarnings |= infoObjs.get(i).hasWarnings();
        }
        return hasWarnings;
    }

    /**
     * Build a list of all of the warnings generated by this VEOContent.xml file
     *
     * @return The concatenated error list
     */
    @Override
    public String getWarnings() {
        StringBuffer sb;
        int i;

        sb = new StringBuffer();
        sb.append(super.getWarnings());
        sb.append(version.getWarnings());
        sb.append(hashAlgorithm.getWarnings());
        for (i = 0; i < infoObjs.size(); i++) {
            sb.append(infoObjs.get(i).getWarnings());
        }
        return sb.toString();
    }

    /**
     * Generate a String representation of the signature
     *
     * @return the String representation
     */
    @Override
    public String toString() {
        StringBuffer sb;
        Iterator<RepnInformationObject> it = infoObjs.iterator();
        RepnInformationObject io;

        sb = new StringBuffer();
        sb.append(super.toString());
        if (contentsAvailable()) {
            sb.append(" VEOContent - Version: ");
            sb.append(version);
            sb.append("\n");
            while (it.hasNext()) {
                io = it.next();
                sb.append(io.toString());
            }
        } else {
            sb.append(" VEOContent: No valid content available as parse failed\n");
        }
        return sb.toString();
    }

    /**
     * Generate an XML representation of the content file
     *
     * @param veoDir the directory in which to create the report
     * @param verbose true if additional information is to be generated
     * @throws VERSCommon.VEOError if prevented from continuing processing this VEO
     */
    public void genReport(boolean verbose, Path veoDir) throws VEOError {
        int i;

        createReport(veoDir, "Report-VEOContent.html", "Report for 'VEOContent.xml'");
        setReportWriter(getReportWriter());
        startDiv("xml", null);
        addLabel("XML Document");
        if (hasErrors || hasWarnings) {
            addTag("<ul>\n");
            listIssues();
            addTag("</ul>\n");
        }
        if (contentsAvailable()) {
            version.genReport(verbose);
            hashAlgorithm.genReport(verbose);
            for (i = 0; i < infoObjs.size(); i++) {
                infoObjs.get(i).genReport(verbose);
            }
        } else {
            addString(" VEOContent.xml: No valid content available as parse failed\n");
        }
        endDiv();
        finishReport();
    }

    /**
     * Tell all the Representations where to write the HTML
     *
     * @param bw buffered writer where the report is to be written
     */
    @Override
    public void setReportWriter(BufferedWriter bw) {
        int i;

        // super.setReportWriter(bw); don't need to do this as it is set in createReport()
        version.setReportWriter(bw);
        hashAlgorithm.setReportWriter(bw);
        for (i = 0; i < infoObjs.size(); i++) {
            infoObjs.get(i).setReportWriter(bw);
        }
    }

    /**
     * Main program for testing
     * @param args command line arguments
    */
    public static void main(String args[]) {
        RepnContent rc;
        Path veoDir;
        Path schemaDir;

        veoDir = Paths.get("..", "neoVEOOutput", "Demo", "BadVEO1.veo");
        schemaDir = Paths.get("Test", "Demo", "Schemas");
        try {
            rc = new RepnContent(veoDir, schemaDir, null, null);
            System.out.println(rc.dumpDOM());
            rc.genReport(false, veoDir);
            // System.out.println(rc.toString());
        } catch (VEOError e) {
            System.out.println(e.getMessage());
        }
    }
}
