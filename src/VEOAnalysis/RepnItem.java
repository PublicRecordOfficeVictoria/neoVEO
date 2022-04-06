/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOAnalysis;

import VERSCommon.ResultSummary;
import VERSCommon.VEOError;
import java.io.Writer;

/**
 * The object represents a unit of data (typically a string)
 */
final class RepnItem extends Repn {
    private String label; // label for the unit of data
    private String value; // the value of the data

    /**
     * Constructor
     *
     * @param id item identifier
     * @param label label to use to describe this item
     * @param results the results summary to build
     */
    public RepnItem(String id, String label, ResultSummary results) {
        super(id, results);
        this.label = label;
        value = null;
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
        value = s;
    }

    /**
     * Get the value of the item
     *
     * @return a String containing the value as read
     */
    public String getValue() {
        return value;
    }

    /**
     * Add a label for the value
     *
     * @param s the label
     */
    public void setLabel(String s) {
        label = s;
    }

    /**
     * Return a formatted description of this message
     *
     * @return a String
     */
    @Override
    public String toString() {
        StringBuffer sb;

        sb = new StringBuffer();
        sb.append("  ");
        sb.append(label);
        sb.append(": ");
        sb.append(value);
        sb.append("\n");
        sb.append(getErrors());
        sb.append(getWarnings());
        return sb.toString();
    }

    /**
     * Generate a HTML representation of the item
     *
     * @param verbose true if additional information is to be generated
     * @param writer where to write the output
     * @throws VERSCommon.VEOError if prevented from continuing processing this VEO
     */
    public void genReport(boolean verbose, Writer w) throws VEOError {
        genReport(verbose, null, w);
    }

    /**
     * Generate a HTML representation of the item
     *
     * @param verbose true if additional information is to be generated
     * @param mesg a String message to add to report
     * @param writer where to write the output
     * @throws VERSCommon.VEOError if prevented from continuing processing this VEO
     */
    public void genReport(boolean verbose, String mesg, Writer w) throws VEOError {
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
        if (hasErrors || hasWarnings) {
            addTag("<ul>\n");
            listIssues();
            addTag("</ul>\n");
        }
        endDiv();
    }
}
