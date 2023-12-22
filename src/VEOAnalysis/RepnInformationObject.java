/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOAnalysis;

import VERSCommon.AnalysisBase;
import VERSCommon.LTSF;
import VERSCommon.ResultSummary;
import VERSCommon.VEOError;
import VERSCommon.VEOFailure;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This class encapsulates an Information Object in a VEO Content file.
 *
 * @author Andrew Waugh
 */
class RepnInformationObject extends AnalysisBase {

    private static final String CLASSNAME = "RepnInformationObject";
    private int IOid;       // integer used to distinguish this IO from all other IOs in the VEO
    private RepnItem type;  // information object type
    private RepnItem depth;   // depth of the information object
    private boolean firstIO;    // true if this the first information object in VEO
    private ArrayList<RepnMetadataPackage> metadata;  // list of metadata packages
    private ArrayList<RepnInformationPiece> infoPieces; // list of information pieces
    private ArrayList<RepnInformationObject> children;  // list of children of this information object
    private RepnInformationObject parent;   // parent of this information object

    /**
     * Construct an Information Object from the XML document VEOContent.xml.
     *
     * @param document the representation of the XML document
     * @param parentId the parent object identifier
     * @param seq the sequence number of this IO in the VEOContent file
     * @param rdf true if we have seen the RDF namespace declaration
     * @param results the results summary to build
     * @throws VEOError if the XML document has not been properly parsed
     */
    public RepnInformationObject(RepnXML document, String parentId, int seq, boolean rdf, ResultSummary results) throws VEOError {
        super(parentId + ":IO-" + seq, results);

        int i;
        String rdfNameSpace;

        assert (document != null);
        assert (parentId != null);
        assert (seq > -1);

        metadata = new ArrayList<>();
        infoPieces = new ArrayList<>();
        children = new ArrayList<>();
        parent = null;

        // remember if this is the first information object in VEO
        firstIO = (seq == 1);

        // remember the sequence number as the identifier of this IO
        IOid = seq;

        // vers:InformationObjectType
        type = new RepnItem(id, "Information Object type", results);
        type.setValue(document.getTextValue());
        document.gotoNextElement();
        // vers:InformationObjectDepth
        depth = new RepnItem(id, "Information Object depth", results);
        depth.setValue(document.getTextValue());
        document.gotoNextElement();
        i = 0;
        while (document.checkElement("vers:MetadataPackage")) {
            rdfNameSpace = document.getAttribute("xmlns:rdf");
            if (rdfNameSpace != null && !rdfNameSpace.equals("")) {
                switch (rdfNameSpace) {
                    case "http://www.w3.org/1999/02/22-rdf-syntax-ns#":
                    // case "http://www.w3.org/1999/02/22-rdf-syntax-ns": without the hash causes the RDF parser to barf
                        rdf = true;
                        break;
                    default:
                        addError(new VEOFailure(CLASSNAME, 1, id, "vers:MetadataPackage element has an invalid xmlns:rdf attribute. Was '" + rdfNameSpace + "', should be 'http://www.w3.org/1999/02/22-rdf-syntax-ns#'"));
                        break;
                }
            }

            document.gotoNextElement();
            i++;
            metadata.add(new RepnMetadataPackage(document, id, i, rdf, results));
        }
        i = 0;
        while (!document.atEnd() && document.checkElement("vers:InformationPiece")) {
            document.gotoNextElement();
            i++;
            infoPieces.add(new RepnInformationPiece(document, id, i, results));
        }
        objectValid = true;
    }

    /**
     * Free resources associated with this information object
     */
    @Override
    public void abandon() {
        int i;

        super.abandon();
        type.abandon();
        type = null;
        depth.abandon();
        depth = null;
        for (i = 0; i < metadata.size(); i++) {
            metadata.get(i).abandon();
        }
        metadata.clear();
        metadata = null;
        for (i = 0; i < infoPieces.size(); i++) {
            infoPieces.get(i).abandon();
        }
        infoPieces.clear();
        infoPieces = null;
        for (i = 0; i < children.size(); i++) {
            children.set(i, null); // don't abandon, as the IOs will be freed elsewhere
        }
        parent = null; // don't abandon, as the IO will be free elsewhere
    }

    /**
     * Return the depth of this Information Object.
     *
     * @return an integer representing the depth
     */
    public int getDepth() {
        return Integer.parseInt(depth.getValue());
    }

    /**
     * Return the type of this Information Object.
     *
     * @return a string representing the type
     */
    public String getType() {
        return type.getValue();
    }

    /**
     * Add a child IO to this Information Object
     *
     * @param io the child Information Object
     */
    public void addChild(RepnInformationObject io) {
        assert (io != null);
        children.add(io);
    }

    /**
     * Set the parent IO of this Information Object
     *
     * @param io
     */
    public void setParent(RepnInformationObject io) {
        assert (io != null);
        parent = io;
    }

    /**
     * Validate the data in the Information Object.
     *
     * @param veoDir the directory containing the contents of the VEO
     * @param hashAlgorithm hash algorithm to be used for hashing and signing
     * @param contentFiles the collection of content files in the VEO
     * @param ltsfs List of valid long term sustainable formats
     * @param oneLevel true if the information objects are a flat list
     * @param prevDepth depth of previous information object
     * @param noRec true if not to complain about missing recommended metadata
     * @param vpa true if being called from VPA & limit some tests elements
     * @return the depth of this Information Object
     */
    public int validate(Path veoDir, String hashAlgorithm, HashMap<Path, RepnFile> contentFiles, LTSF ltsfs, boolean oneLevel, int prevDepth, boolean noRec, boolean vpa) {
        int i;
        boolean stdMetadata;

        assert (veoDir != null);
        assert (hashAlgorithm != null);
        assert (contentFiles != null);
        assert (ltsfs != null);

        // check to see if the depth values of the Information Objects are valid
        // the depth values must either be all zero, or they must be a depth
        // first traversal
        if (oneLevel) {
            if (getDepth() != 0) {
                depth.addError(new VEOFailure(CLASSNAME, "validate", 1, id, "First information object had a depth of 0 (indicating a flat list), but this information object has a depth > 0"));
            }
        } else {
            if (getDepth() == 0) {
                depth.addError(new VEOFailure(CLASSNAME, "validate", 2, id, "First information object had a depth > 0 (indicating a tree structure), but this information object has a depth = 0"));
            }
            if (firstIO && getDepth() > 1) {
                depth.addError(new VEOFailure(CLASSNAME, "validate", 3, id, "First information object must have a depth of 0 or 1"));
            } else if (getDepth() - prevDepth > 1) {
                depth.addError(new VEOFailure(CLASSNAME, "validate", 4, id, "Information object has a depth which is more than one greater than the previous depth (" + prevDepth + ")"));
            }
        }

        // do not need to validate depth, as XML schema checks it is non negative integer
        // if this is the first Information Object, must have at least one metadata package
        if (firstIO && metadata.isEmpty()) {
            addError(new VEOFailure(CLASSNAME, "validate", 5, id, "The first information object must have at least one metadata package"));
        }
        stdMetadata = false;
        for (i = 0; i < metadata.size(); i++) {
            stdMetadata |= metadata.get(i).validate(veoDir, noRec);
        }
        if (!stdMetadata) {
            if (firstIO) {
                addError(new VEOFailure(CLASSNAME, "validate", 6, id, "The first information object did not contain an AGLS or AGRKMS metadata package"));
            } else {
                addWarning(new VEOFailure(CLASSNAME, "validate", 7, id, "The information object did not contain an AGLS or AGRKMS metadata package"));
            }
        }
        for (i = 0; i < infoPieces.size(); i++) {
            infoPieces.get(i).validate(veoDir, hashAlgorithm, contentFiles, ltsfs, vpa);
        }
        return getDepth();
    }

    /**
     * Check if this object has any errors?
     *
     * @return true if errors have been detected
     */
    @Override
    public boolean hasErrors() {
        int i;
        boolean hasErrors;

        hasErrors = type.hasErrors() | depth.hasErrors();
        for (i = 0; i < metadata.size(); i++) {
            hasErrors |= metadata.get(i).hasErrors();
        }
        for (i = 0; i < infoPieces.size(); i++) {
            hasErrors |= infoPieces.get(i).hasErrors();
        }
        return hasErrors;
    }

    /**
     * Build a list of all of the errors generated by this RepnInformationObject
     *
     * @param returnErrors if true return errors, otherwise return warnings
     * @param l list in which to place the errors/warnings
     */
    @Override
    public void getProblems(boolean returnErrors, List<VEOFailure> l
    ) {
        int i;

        assert (l != null);

        super.getProblems(returnErrors, l);
        type.getProblems(returnErrors, l);
        depth.getProblems(returnErrors, l);
        for (i = 0; i < metadata.size(); i++) {
            metadata.get(i).getProblems(returnErrors, l);
        }
        for (i = 0; i < infoPieces.size(); i++) {
            infoPieces.get(i).getProblems(returnErrors, l);
        }
    }

    /**
     * Has this object (or its children) any warnings?
     *
     * @return true if errors have been detected
     */
    @Override
    public boolean hasWarnings() {
        int i;
        boolean hasWarnings;

        hasWarnings = type.hasWarnings() | depth.hasWarnings();
        for (i = 0; i < metadata.size(); i++) {
            hasWarnings |= metadata.get(i).hasWarnings();
        }
        for (i = 0; i < infoPieces.size(); i++) {
            hasWarnings |= infoPieces.get(i).hasWarnings();
        }
        return hasWarnings;
    }

    /**
     * Produce a string representation of the Information Object
     *
     * @return The string representation
     */
    @Override
    public String toString() {
        StringBuffer sb;
        int i;

        sb = new StringBuffer();
        sb.append("  Information Object - Type:'");
        sb.append(type);
        sb.append("' Depth:");
        sb.append(depth);
        sb.append("\n");
        for (i = 0; i < metadata.size(); i++) {
            sb.append(metadata.get(i).toString());
        }
        for (i = 0; i < infoPieces.size(); i++) {
            sb.append(infoPieces.get(i).toString());
        }
        return sb.toString();
    }

    /**
     * Generate an XML representation of the information object
     *
     * @param verbose true if additional information is to be generated
     * @param pVersion The version of VEOAnalysis
     * @param copyright The copyright string
     * @throws VERSCommon.VEOError if prevented from continuing processing this
     * VEO
     */
    public String genLink() {
        return "Report-IO" + IOid + ".html";
    }

    public void genReport(boolean verbose, Path veoDir, String pVersion, String copyright) throws VEOError {
        int i;
        RepnInformationObject rio;

        assert (veoDir != null);
        assert (pVersion != null);
        assert (copyright != null);

        createReport(veoDir, genLink(), "Report for Information Object " + IOid, pVersion, copyright);
        startDiv("InfoObj", null);
        addLabel("Information Object");
        addString(" (Type = '" + type.getValue());
        addString("', Depth=");
        addString(depth.getValue());
        addString(")");
        if (parent != null) {
            addTag("<br>");
            addString("  Parent Information Object: ");
            addTag("<a href=\"" + parent.genLink() + "\">");
            addString(parent.getId());
            addTag("</a>");
        }
        addTag("<br>");
        addTag("  <a href=\"./index.html\">");
        addString("VEO root");
        addTag("</a>");

        if (hasErrors() || hasWarnings()) {
            addTag("<ul>\n");
            listIssues();
            addTag("</ul>\n");
        }

        type.genReport(verbose, w);
        depth.genReport(verbose, w);

        if (metadata.size() > 0) {
            startDiv(null, "MetaPackages", null);
            addLabel("Metadata Packages:");
            for (i = 0; i < metadata.size(); i++) {
                metadata.get(i).genReport(verbose, w);
            }
            endDiv();
        }
        if (infoPieces.size() > 0) {
            startDiv(null, "InfoPieces", null);
            addLabel("Information Pieces:");
            for (i = 0; i < infoPieces.size(); i++) {
                infoPieces.get(i).genReport(verbose, w);
            }
            endDiv();
        }
        if (children.size() > 0) {
            startDiv("Children", null);
            addLabel("Child Information Objects:");
            for (i = 0; i < children.size(); i++) {
                rio = children.get(i);
                startDiv("InfoObj", null);
                addLabel("Information Object: ");
                addString("Type: '");
                addString(rio.getType());
                addString("' Depth:");
                addString(Integer.toString(rio.getDepth()));
                addString(" ");
                addTag("<a href=\"" + rio.genLink() + "\">");
                addString(rio.getId());
                addTag("</a>");
                endDiv();
            }
            endDiv();
        }
        endDiv();
        finishReport();
    }
}
