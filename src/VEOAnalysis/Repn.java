/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOAnalysis;

import VERSCommon.ResultSummary;
import VERSCommon.ResultSummary.Type;
import VERSCommon.VEOError;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This abstract class is the parent of all representations of all components of
 * VEO objects. It records whether the object is valid (and if not the error
 * messages), and whether there are warnings (things that are not errors, but
 * should not happen).
 *
 * @author Andrew Waugh
 */
abstract class Repn {

    String classname = "Repn";  // for logging
    private final String id;          // identifier of this object for messages
    protected boolean hasErrors; // true if this object (or its children) have errors
    protected ArrayList<String> errors;   // list of errors that occurred
    protected boolean hasWarnings; // true if this object (or its children) have warnings
    protected ArrayList<String> warnings; // list of warnings that occurred
    private FileWriter fw;
    private BufferedWriter htmlOutput; // if not null, generate a HTML version of this representation
    private boolean infoAvailable; // true if information can be retrieved from this object
    private final static Logger log = Logger.getLogger("VEOAnalysis.Repn");
    protected ResultSummary results;  // summary of results

    /**
     * Construct a representation with default reporting parameters.
     *
     * @param id the identifier used to identify this object and not debugging
     * information.
     */
    public Repn(String id, ResultSummary results) {
        fw = null;
        htmlOutput = null;
        errors = new ArrayList<>();
        hasErrors = false;
        warnings = new ArrayList<>();
        hasWarnings = false;
        this.id = id;
        infoAvailable = true;
        this.results = results;
    }

    /**
     * Free resources associated with this object.
     */
    public void abandon() {
        infoAvailable = false;
        errors.clear();
        errors = null;
        warnings.clear();
        warnings = null;
        try {
            if (htmlOutput != null) {
                htmlOutput.close();
            }
            htmlOutput = null;
            if (fw != null) {
                fw.close();
            }
            fw = null;
        } catch (IOException e) {
            log.log(Level.WARNING, errMesg(classname, "abandon", "Failed to close HTML output file", e));
        }
    }

    /**
     * Get the identifier used to label this object
     *
     * @return a String containing the id
     */
    final public String getId() {
        if (!infoAvailable) {
            log.log(Level.WARNING, errMesg(classname, "getId", "Called function after abandon() was called"));
            return("");
        }
        return id;
    }

    /**
     * Report if information is available from this object
     *
     * @return true if component is valid
     */
    final public boolean isInfoAvailable() {
        return infoAvailable;
    }

    /**
     * Add an error to this message. Will ignore requests to add a null or blank
     * message
     *
     * @param s The error to add
     */
    final public void addError(String s) {
        if (s == null || s.equals("") || s.trim().equals(" ")) {
            return;
        }
        if (!infoAvailable) {
            log.log(Level.WARNING, errMesg(classname, "addError", "Called function after abandon() was called"));
            return;
        }
        hasErrors = true;
        errors.add(s);
        if (results != null) {
            results.recordResult(Type.ERROR, s, null, id);
        }
    }

    /**
     * Has this object any errors associated with it?
     *
     * @return true if there are error messages
     */
    protected boolean hasErrors() {
        if (!infoAvailable) {
            log.log(Level.WARNING, errMesg(classname, "hasErrors", "Called function after abandon() was called"));
            return false;
        }
        return hasErrors;
    }

    /**
     * Return a formated list of errors
     *
     * @return a String containing the errors
     */
    protected String getErrors() {
        StringBuffer sb;
        int i;

        if (!infoAvailable) {
            log.log(Level.WARNING, errMesg(classname, "getErrors", "Called function after abandon() was called"));
            return("");
        }
        sb = new StringBuffer();
        for (i = 0; i < errors.size(); i++) {
            sb.append("   Error");
            if (id != null && !id.equals("")) {
                sb.append(" (");
                sb.append(id);
                sb.append(")");
            }
            sb.append(": ");
            sb.append(errors.get(i));
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Has this object any warnings associated with it?
     *
     * @return true if there are warning messages
     */
    protected boolean hasWarnings() {
        if (!infoAvailable) {
            log.log(Level.WARNING, errMesg(classname, "hasWarnings", "Called function after abandon() was called"));
            return(false);
        }
        return hasWarnings;
    }

    /**
     * Add a warning to this message. Will ignore requests to add a null or
     * blank message
     *
     * @param s The warning to add
     */
    protected void addWarning(String s) {
        if (!infoAvailable) {
            log.log(Level.WARNING, errMesg(classname, "addWarning", "Called function after abandon() was called"));
            return;
        }
        if (s == null || s.equals("") || s.trim().equals(" ")) {
            return;
        }
        hasWarnings = true;
        warnings.add(s);
        if (results != null) {
            results.recordResult(Type.WARNING, s, null, id);
        }
    }

    /**
     * Return a formatted list of warnings
     *
     * @return a string
     */
    protected String getWarnings() {
        StringBuffer sb;
        int i;

        if (!infoAvailable) {
            log.log(Level.WARNING, errMesg(classname, "getWarnings", "Called function after abandon() was called"));
            return("");
        }
        sb = new StringBuffer();
        for (i = 0; i < warnings.size(); i++) {
            sb.append("   Warning");
            if (id != null && !id.equals("")) {
                sb.append(" (");
                sb.append(id);
                sb.append(")");
            }
            sb.append(": ");
            sb.append(warnings.get(i));
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Create a report file to capture a view of this Representation
     *
     * @param veoDir The VEO directory in which to create the XML file
     * @param htmlFileName The XML file to create
     * @param title The title of the XML file
     * @throws VEOError if something happened (e.g. it couldn't be created)
     */
    final protected void createReport(Path veoDir, String htmlFileName, String title) throws VEOError {
        String method = "createReport";
        Path htmlFile;
        TimeZone tz;
        SimpleDateFormat sdf;

        if (!infoAvailable) {
            log.log(Level.WARNING, errMesg(classname, method, "Called function after abandon() was called"));
            return;
        }
        htmlFile = veoDir.resolve(htmlFileName);
        try {
            fw = new FileWriter(htmlFile.toFile());
            htmlOutput = new BufferedWriter(fw);
        } catch (IOException e) {
            throw new VEOError(errMesg(classname, method, "IOException when attempting to open HTML output file '" + htmlFile.toString() + "'. Error was", e));
        }
        try {
            htmlOutput.write("<!DOCTYPE html>\n<html>\n<head>\n");
            htmlOutput.write("<link rel=\"stylesheet\" href=\"ReportStyle.css\">");
            htmlOutput.write("</head>\n</body>\n");
            htmlOutput.write("  <h1>");
            htmlOutput.write(title);
            htmlOutput.write("</h1>\n");
            htmlOutput.write("  <p class=\"preamble\">");
            htmlOutput.write("VEO Analysis v1.0");
            htmlOutput.write("<br>\n");
            htmlOutput.write("Copyright Public Record Office Victoria 2015");
            htmlOutput.write("<br>\n");
            htmlOutput.write("VEO analysed: '" + veoDir.toAbsolutePath().normalize().toString() + "' at ");
            tz = TimeZone.getTimeZone("GMT+10:00");
            sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss+10:00");
            sdf.setTimeZone(tz);
            htmlOutput.write(sdf.format(new Date()));
            htmlOutput.write("<br>\n");
            htmlOutput.write("</p>\n");
        } catch (IOException e) {
            log.log(Level.WARNING, errMesg(classname, method, "IOException when writing to HTML output file. Error was", e));
        }
    }

    /**
     * Finish a report file.
     *
     * @throws VEOError if a fatal error occurred
     */
    final protected void finishReport() throws VEOError {
        String method = "finishReport";

        // sanity check
        if (!infoAvailable) {
            log.log(Level.WARNING, errMesg(classname, method, "Called function after abandon() was called"));
            return;
        }
        if (fw == null || htmlOutput == null) {
            log.log(Level.WARNING, errMesg(classname, method, "Attempt to write to HTML output file while it was not open"));
            return;
        }

        // finish and close HTML report
        try {
            htmlOutput.write("</body>\n");
            htmlOutput.close();
            htmlOutput = null;
            fw.close();
            fw = null;
        } catch (IOException e) {
            log.log(Level.WARNING, errMesg(classname, method, "IOException when writing to HTML output file. Error was", e));
        }
    }

    /**
     * Get the writer on which the report is being generated
     *
     * @return the Writer
     */
    final protected BufferedWriter getReportWriter() {
        if (!infoAvailable) {
            log.log(Level.WARNING, errMesg(classname, "getReportWriter", "Called function after abandon() was called"));
            return null;
        }
        return htmlOutput;
    }

    /**
     * Set the Writer to capture HTML output. It is only necessary to call this
     * function if the output is to go into a XML file opened by another
     * Representation. This function has no effect an XML output file has
     * already been set, or if the passed Writer is null.
     *
     * @param htmlOutput a Writer on which to write the output.
     */
    protected void setReportWriter(BufferedWriter htmlOutput) {
        String method = "setReportWriter";
        if (!infoAvailable) {
            log.log(Level.WARNING, errMesg(classname, method, "Called function after abandon() was called"));
            return;
        }
        if (this.htmlOutput != null || htmlOutput == null) {
            log.log(Level.WARNING, errMesg(classname, method, "Attempting to reset HTML output"));
            return;
        }
        this.htmlOutput = htmlOutput;
    }

    /**
     * Start a division (HTML DIV element) in the report. If an anchor is
     * specified, the DIV will contain an ID attribute to that the HTML DIV
     * element can be linked to.
     *
     * @param type list of class names (separated by spaces) to put in the HTML
     * DIV element
     * @param anchor anchor to put in the div to allow linking
     */
    final protected void startDiv(String type, String anchor) {
        startDiv(this, type, anchor);
    }

    /**
     * Start a division (HTML DIV element) in the report. A list of classes may
     * be associated with the DIV to control formatting in the CSS file. If an
     * anchor is specified, the DIV will contain an ID attribute to that the
     * HTML DIV element can be linked to.
     *
     * @param r the Repn to test for errors and warnings
     * @param type list of class names (separated by spaces) to put in the DIV
     * element
     * @param anchor anchor to put in the div to allow linking
     */
    final protected void startDiv(Repn r, String type, String anchor) {
        String method = "startDiv";

        // sanity check...
        if (!infoAvailable) {
            log.log(Level.WARNING, errMesg(classname, method, "Called function after abandon() was called"));
            return;
        }
        if (htmlOutput == null) {
            log.log(Level.WARNING, errMesg(classname, method, "Attempt to write to HTML output file while it was not open"));
            return;
        }

        try {
            htmlOutput.write("<div");
            if (r.hasErrors) {
                htmlOutput.write(" class=\"box error " + type + "\"");
            } else if (r.hasWarnings) {
                htmlOutput.write(" class=\"box warning " + type + "\"");
            } else {
                htmlOutput.write(" class=\"box correct " + type + "\"");
            }
            if (anchor != null) {
                htmlOutput.write(" id=\"" + anchor + "\"");
            }
            htmlOutput.write(">\n");
        } catch (IOException e) {
            log.log(Level.WARNING, errMesg(classname, method, "IOException when writing to HTML output file. Error was", e));
        }
    }

    /**
     * End a DIV in the report.
     */
    final protected void endDiv() {
        String method = "endDiv";

        // sanity check...
        if (!infoAvailable) {
            log.log(Level.WARNING, errMesg(classname, method, "Called function after abandon() was called"));
            return;
        }
        if (htmlOutput == null) {
            log.log(Level.WARNING, errMesg(classname, method, "Attempt to write to HTML output file while it was not open"));
            return;
        }
        try {
            htmlOutput.write("</div>\n");
        } catch (IOException e) {
            log.log(Level.WARNING, errMesg(classname, method, "IOException when writing to HTML output file. Error was", e));
        }
    }

    /**
     * Add the error and warning messages to the report.
     */
    final protected void listIssues() {
        String method = "listIssues";
        int i;

        // sanity check...
        if (!infoAvailable) {
            log.log(Level.WARNING, errMesg(classname, method, "Called function after abandon() was called"));
            return;
        }
        if (htmlOutput == null) {
            log.log(Level.WARNING, errMesg(classname, method, "Attempt to write to HTML output file while it was not open"));
            return;
        }

        try {
            for (i = 0; i < errors.size(); i++) {
                htmlOutput.write(" <li class=\"error\">");
                htmlOutput.write("Error: ");
                htmlOutput.write(safeXML(errors.get(i)));
                htmlOutput.write("</li>\n");
            }
            for (i = 0; i < warnings.size(); i++) {
                htmlOutput.write(" <li class=\"warning\">");
                htmlOutput.write("Warning: ");
                htmlOutput.write(safeXML(warnings.get(i)));
                htmlOutput.write("</li>\n");
            }
        } catch (IOException e) {
            log.log(Level.WARNING, errMesg(classname, method, "IOException when writing to HTML output file. Error was", e));
        }
    }

    /**
     * Add a simple value to the report. The punctuation around the tag must be
     * included
     *
     * @param s String to add to the HTML.
     */
    final protected void addTag(String s) {
        String method = "addTag";

        // sanity checks
        if (!infoAvailable) {
            log.log(Level.WARNING, errMesg(classname, method, "Called function after abandon() was called"));
            return;
        }
        if (htmlOutput == null) {
            log.log(Level.WARNING, errMesg(classname, method, "Attempt to write to HTML output file while it was not open"));
            return;
        }
        try {
            htmlOutput.write(s);
        } catch (IOException e) {
            log.log(Level.WARNING, errMesg(classname, method, "IOException when writing to HTML output file. Error was", e));
        }
    }

    /**
     * Add a label to the report file. Any less than or greater than characters
     * will be escaped.
     *
     * @param s String to add to the HTML.
     */
    final protected void addLabel(String s) {
        String method = "addLabel";

        // sanity check...
        if (!infoAvailable) {
            log.log(Level.WARNING, errMesg(classname, method, "Called function after abandon() was called"));
            return;
        }
        if (htmlOutput == null) {
            log.log(Level.WARNING, errMesg(classname, method, "Attempt to write to HTML output file while it was not open"));
            return;
        }
        try {
            htmlOutput.write("<strong>");
            htmlOutput.write(safeXML(s));
            htmlOutput.write("</strong>");
        } catch (IOException e) {
            log.log(Level.WARNING, errMesg(classname, method, "IOException when writing to HTML output file. Error was", e));
        }
    }

    /**
     * Add a string to the report file. Any less than or greater than characters
     * will be escaped.
     *
     * @param s String to add to the HTML.
     */
    final protected void addString(String s) {
        String method = "addString";

        // sanity check...
        if (!infoAvailable) {
            log.log(Level.WARNING, errMesg(classname, method, "Called function after abandon() was called"));
            return;
        }
        if (htmlOutput == null) {
            log.log(Level.WARNING, errMesg(classname, method, "Attempt to write to HTML output file while it was not open"));
            return;
        }
        try {
            htmlOutput.write(safeXML(s));
        } catch (IOException e) {
            log.log(Level.WARNING, errMesg(classname, method, "IOException when writing to HTML output file. Error was", e));
        }
    }

    /**
     * Low level routine to encode an XML value. The special charactrs
     * ampersand, less than, greater than, double quote and single quote are
     * escaped
     *
     * @param s string to write to XML document
     * @return the XML safe string
     */
    final protected String safeXML(String s) {
        String method = "safeXML";
        StringBuilder sb = new StringBuilder();
        int i;
        char c;

        // sanity check
        if (s == null || s.length() == 0) {
            return "";
        }

        // quote the special characters in the string
        for (i = 0; i < s.length(); i++) {
            c = s.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&apos;");
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
        /*
         try {
         xml.write(cs.encode(sb.toString()));
         } catch (IOException ioe) {
         log.log(Level.WARNING, "write(){0} {1}", new Object[]{ioe.toString(), ioe.getMessage()});
         throw new VEOError(classname, module, 2, "Failed writing to XML document");
         }
         */
    }

    /**
     * Produce a standard error message from a constructor
     *
     * @param classname Class the error occurred in
     * @param problem Description of the problem
     * @return String containing standard error
     */
    final protected String errMesg(String classname, String problem) {
        return problem + " (" + classname + "())";
    }

    /**
     * Produce a standard error message
     *
     * @param classname Class the error occurred in
     * @param method Method the error occurred in
     * @param problem Description of the problem
     * @return String containing standard error
     */
    final protected String errMesg(String classname, String method, String problem) {
        return problem + " (" + classname + "." + method + "())";
    }

    /**
     * Produce a standard error message from a constructor about a thrown
     * exception.
     *
     * @param classname Class the error occurred in
     * @param problem Description of the problem
     * @param e exception
     * @return String containing standard error
     */
    final protected String errMesg(String classname, String problem, Exception e) {
        return problem + ": " + e.getMessage() + " (" + classname + "())";
    }

    /**
     * Produce a standard error message about a thrown exception.
     *
     * @param classname Class the error occurred in
     * @param method Method the error occurred in
     * @param problem Description of the problem
     * @param e the thrown exception
     * @return String containing standard error
     */
    final protected String errMesg(String classname, String method, String problem, Exception e) {
        return problem + ": " + e.getMessage() + " (" + classname + "." + method + "())";
    }

    /**
     * Get a description of the status of this object and all child objects,
     * including any errors or warnings. This is an abstract method that is
     * overriden by subclasses.
     *
     * @return A String containing the status
     */
    @Override
    abstract public String toString();
}
