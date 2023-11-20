/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOAnalysis;

import VERSCommon.AnalysisBase;
import VERSCommon.ResultSummary;
import VERSCommon.VEOFailure;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * The object represents a unit of data (typically a string)
 */
final class RepnItem extends AnalysisBase {
    private static final String CLASSNAME = "RepnItem";
    private String label; // label for the unit of data
    private String value; // the value of the data

    /**
     * Constructor. This cannot throw a VEOError, so can be assumed to exist.
     *
     * @param id item identifier
     * @param label label to use to describe this item (must not be null)
     * @param results the results summary to build
     */
    public RepnItem(String id, String label, ResultSummary results) {
        super(id, results);
        
        assert(label != null);
        
        this.label = label;
        value = null;
        objectValid = true;
    }

    /**
     * Free all the resources associated with this message
     */
    @Override
    public void abandon() {
        super.abandon();
        label = null;
        value = null;
    }

    /**
     * Add the value actually read
     *
     * @param s the value
     */
    public void setValue(String s) {
        
        if (s == null || s.equals("") || s.trim().equals(" ")) {
            s = null;
        }
        value = s;
    }

    /**
     * Get the value of the item. Note can never return null
     *
     * @return a String containing the value as read
     */
    public String getValue() {
        return value != null?value:"";
    }

    /**
     * Add a label for the value
     *
     * @param s the label
     */
    public void setLabel(String s) {
        assert(s != null && !s.equals(""));
        
        label = s;
    }

    /**
     * Return a formatted description of this message
     *
     * @return a String
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        List<VEOFailure> l = new ArrayList<>();
        int i;

        sb.append("  ");
        sb.append(label);
        sb.append(": ");
        sb.append(value);
        sb.append("\n");
        sb.append("Errors:\n   ");
        getProblems(true, l);
        for (i=0; i<l.size(); i++) {
            sb.append("  ");
            sb.append(l.get(i).getMessage());
            sb.append("\n");
        }
        l.clear();
        sb.append("Warnings:\n   ");
        getProblems(false, l);
        for (i=0; i<l.size(); i++) {
            sb.append("  ");
            sb.append(l.get(i).getMessage());
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Generate a HTML representation of the item
     *
     * @param verbose true if additional information is to be generated
     * @param writer where to write the output
     */
    public void genReport(boolean verbose, Writer w) {
        assert(w != null);
        genReport(verbose, null, w);
    }

    /**
     * Generate a HTML representation of the item
     *
     * @param verbose true if additional information is to be generated
     * @param mesg a String message to add to report
     * @param writer where to write the output
     */
    public void genReport(boolean verbose, String mesg, Writer w) {
        assert(mesg != null);
        assert(w != null);
        
        this.w = w;
        startDiv("Item", null);
        addLabel(label);
        addString(": " + value);
        if (mesg != null) {
            if (mesg.length() < 40) {
                addString(mesg);
            } else if (verbose) {
                addTag("<pre>");
                addString(mesg);
                addTag("</pre>\n");
            }
        }
        if (hasErrors() || hasWarnings()) {
            addTag("<ul>\n");
            listIssues();
            addTag("</ul>\n");
        }
        endDiv();
    }
}
