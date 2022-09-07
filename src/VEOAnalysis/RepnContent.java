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
    public int ioCnt;       // count of number of IOs in VERSContent file

    /**
     * Builds an internal representation of the VEOContent.xml file, validating
     * it against the schema in VEOContent.xsd.
     *
     * @param veoDir VEO directory in which the VEOContent.xml file is
     * @param schemaDir schemaDir directory in which the VEOContent.xsd file is
     * @param contentFiles collection of content files in VEO
     * @param results the results summary to build
     * @throws VEOError if a fatal error occurred
     */
    public RepnContent(Path veoDir, Path schemaDir, HashMap<Path, RepnFile> contentFiles, ResultSummary results) throws VEOError {
        super("VEOContent.xml", results);

        RepnInformationObject io, parentIO;
        Path file, schema;
        String rdfNameSpace;
        boolean rdf; // true if we have seen the RDF namespace declaration
        ArrayList<RepnInformationObject> lastIOatDepth = new ArrayList<>();

        version = new RepnItem(getId(), "Version", results);
        hashAlgorithm = new RepnItem(getId(), "Hash algorithm", results);
        infoObjs = new ArrayList<>();
        rdf = false;

        // parse the VEOContent.xml file against the VEOContent scheme
        file = veoDir.resolve("VEOContent.xml");
        schema = schemaDir.resolve("vers3-content.xsd");
        if (!parse(file, schema)) {
            return;
        }

        // extract the information from the DOM representation
        gotoRootElement();
        checkElement("vers:VEOContentFile");
        // check for RDF namespace declaration
        rdfNameSpace = getAttribute("xmlns:rdf");
        if (rdfNameSpace != null && !rdfNameSpace.equals("")) {
            switch (rdfNameSpace) {
                case "http://www.w3.org/1999/02/22-rdf-syntax-ns#":
                case "http://www.w3.org/1999/02/22-rdf-syntax-ns":
                    break;
                default:
                    throw new VEOError("Error detected:\n  Error (VEOContent.xml): vers:VEOContentFile element has an invalid xmlns:rdf attribute. Was '" + rdfNameSpace + "', should be 'http://www.w3.org/1999/02/22-rdf-syntax-ns#");
            }
            rdf = true;
        }
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
        ioCnt = 0;
        while (!atEnd() && checkElement("vers:InformationObject")) {

            // check for RDF namespace declaration
            rdfNameSpace = getAttribute("xmlns:rdf");
            if (rdfNameSpace != null && !rdfNameSpace.equals("")) {
                switch (rdfNameSpace) {
                    case "http://www.w3.org/1999/02/22-rdf-syntax-ns#":
                    case "http://www.w3.org/1999/02/22-rdf-syntax-ns":
                        break;
                    default:
                        throw new VEOError("Error detected:\n  Error (VEOContent.xml): vers:InformationObject element has an invalid xmlns:rdf attribute. Was '" + rdfNameSpace + "', should be 'http://www.w3.org/1999/02/22-rdf-syntax-ns#");
                }
                rdf = true;
            }
            gotoNextElement();
            ioCnt++;
            io = new RepnInformationObject(this, getId(), ioCnt, rdf, results);

            // build the tree of IOs
            if (io.getDepth() > 0) {

                // add this io as a child of the last io we saw at depth-1
                if (io.getDepth() > 1) {
                    parentIO = lastIOatDepth.get(io.getDepth() - 2);
                    if (parentIO != null) {
                        parentIO.addChild(io);
                        io.setParent(parentIO);
                    } else {
                        throw new VEOError("Error detected:\n  Error (VEOContent.xml): IO has depth of " + io.getDepth() + " but last seen IO at depth-1 is null");
                    }
                }

                // record this as the latest io found at this depth
                if (io.getDepth() - 1 == lastIOatDepth.size()) {
                    lastIOatDepth.add(io);
                } else if (io.getDepth() - 1 < lastIOatDepth.size()) {
                    lastIOatDepth.set(io.getDepth() - 1, io);
                } else {
                    throw new VEOError("Error detected:\n  Error (VEOContent.xml): IO has depth of " + io.getDepth() + " which is more than one more than the maximum depth " + lastIOatDepth.size());
                }
            }
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
     * @param noRec true if not to complain about missing recommended metadata
     * elements
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
     * @param pVersion the version of VEOAnalysis for reporting
     * @param copyright the copyright string for reporting
     * @throws VERSCommon.VEOError if prevented from continuing processing this
     * VEO
     */
    public void genReport(boolean verbose, Path veoDir, String pVersion, String copyright) throws VEOError {
        int i;
        RepnInformationObject rio;

        createReport(veoDir, "Report-VEOContent.html", "Report for 'VEOContent.xml'", pVersion, copyright);
        startDiv("xml", null);
        addLabel("XML Document");
        if (hasErrors || hasWarnings) {
            addTag("<ul>\n");
            listIssues();
            addTag("</ul>\n");
        }
        if (contentsAvailable()) {
            version.genReport(verbose, w);
            hashAlgorithm.genReport(verbose, w);
            if (infoObjs.size() > 0) {
                startDiv(null, "InfoObjs", null);
                addLabel("Information Objects: ");
                for (i = 0; i < infoObjs.size(); i++) {
                    rio = infoObjs.get(i);
                    if (rio.getDepth() < 2) {
                        startDiv("InfoObj", null);
                        addLabel("Information Object: ");
                        addString("Type: '");
                        addString(rio.getType());
                        addString("' Depth:");
                        addString(Integer.toString(rio.getDepth()));
                        addString(" ");
                        addTag("<a href=\"./" + rio.genLink() + "\">");
                        addString(rio.getId());
                        addTag("</a>");
                        endDiv();
                    }
                }
                endDiv();
            }
        } else {
            addString(" VEOContent.xml: No valid content available as parse failed\n");
        }
        endDiv();
        finishReport();

        for (i = 0; i < infoObjs.size(); i++) {
            infoObjs.get(i).genReport(verbose, veoDir, pVersion, copyright);
        }
    }

    /**
     * Main program for testing
     *
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
            rc.genReport(false, veoDir, "1.0", "Copyright");
            // System.out.println(rc.toString());
        } catch (VEOError e) {
            System.out.println(e.getMessage());
        }
    }
}
