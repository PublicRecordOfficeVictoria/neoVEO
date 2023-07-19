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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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

    String classname = "Repn";      // for logging
    private final String id;        // identifier of this object for messages
    protected boolean hasErrors;    // true if this object (or its children) have errors
    protected ArrayList<VEOError> errors;   // list of errors that occurred
    protected boolean hasWarnings;  // true if this object (or its children) have warnings
    protected ArrayList<VEOError> warnings; // list of warnings that occurred
    private FileOutputStream fos;
    private OutputStreamWriter osw;
    protected Writer w;             // if not null, generate a HTML version of this representation
    private boolean infoAvailable;  // true if information can be retrieved from this object
    private final static Logger log = Logger.getLogger("VEOAnalysis.Repn");
    protected ResultSummary results;// summary of results

    /**
     * Construct a representation with default reporting parameters.
     *
     * @param id the identifier used to identify this object and not debugging
     * @param results the results summary to build information.
     */
    public Repn(String id, ResultSummary results) {
        fos = null;
        osw = null;
        w = null;
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
            if (w != null) {
                w.close();
            }
            w = null;
            if (osw != null) {
                osw.close();
            }
            osw = null;
            if (fos != null) {
                fos.close();
            }
            fos = null;
        } catch (IOException e) {
            log.log(Level.WARNING, VEOError.errMesg(classname, "abandon", 1, "Failed to close HTML output file", e));
        }
    }

    /**
     * Get the identifier used to label this object
     *
     * @return a String containing the id
     */
    final public String getId() {
        assert(infoAvailable);
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
     * @param v The error to add
     */
    final public void addError(VEOError v) {
        assert(infoAvailable);
        hasErrors = true;
        errors.add(v);
        if (results != null) {
            results.recordResult(Type.ERROR, v.getMessage(), null, id);
        }
    }

    /**
     * Has this object any errors associated with it?
     *
     * @return true if there are error messages
     */
    protected boolean hasErrors() {
        assert(infoAvailable);
        return hasErrors;
    }

    /**
     * Add problems encountered with this object to a list of problems
     *
     * @param returnErrors if true return errors, otherwise return warnings
     * @param l list in which to place the errors/warnings
     */
    protected void getProblems(boolean returnErrors, List<VEOError> l) {
        assert(infoAvailable);
        if (returnErrors) {
            l.addAll(errors);
        } else {
            l.addAll(warnings);
        }
    }

    /**
     * Return a list of messages
     *
     * @param returnErrors if true return error messages, otherwise return warnings
     * @param l list in which to place the messages
     * @return a String containing the errors
     */
    
    protected void getMesgs(boolean returnErrors, List<String> l) {
        int i;

        assert(infoAvailable);
        if (returnErrors) {
            for (i = 0; i < errors.size(); i++) {
                l.add(formatMesg("Error", errors.get(i).getMessage()));
            }
        } else {
            for (i = 0; i < warnings.size(); i++) {
                l.add(formatMesg("Warning", warnings.get(i).getMessage()));
            }
        }
    }
    
    /**
     * Turn a list of messages into a formatted string in a StringBuilder
     * @param l a list of messages
     * @param sb the StringBuilder to receive the messages
     */
    protected void mesgs2String(List<VEOError> l, StringBuilder sb) {
        int i;
        
        for (i = 0; i < l.size(); i++) {
            sb.append("   ");
            sb.append(l.get(i).getMessage());
            sb.append("\n");
        }
    }

    /**
     * Has this object any warnings associated with it?
     *
     * @return true if there are warning messages
     */
    protected boolean hasWarnings() {
        assert(infoAvailable);
        return hasWarnings;
    }

    /**
     * Add a warning to this message. Will ignore requests to add a null or
     * blank message
     *
     * @param v The warning to add
     */
    protected void addWarning(VEOError v) {
        assert(infoAvailable);
        hasWarnings = true;
        warnings.add(v);
        if (results != null) {
            results.recordResult(Type.WARNING, v.getMessage(), null, id);
        }
    }
    
    /**
     * Return the list of warnings associated with this object. Will never be
     * null, but may be empty.
     * 
     * @return 
     */
    protected List<VEOError> getWarnings() {
        return warnings;
    }

    /**
     * Format an error or warning message.
     *
     * @param type type of message
     * @param mesg message to output
     * @return
     */
    private String formatMesg(String type, String mesg) {
        StringBuilder sb = new StringBuilder();

        sb.append(type);
        if (id != null && !id.equals("")) {
            sb.append(" (");
            sb.append(id);
            sb.append(")");
        }
        sb.append(": ");
        sb.append(mesg);
        return sb.toString();
    }

    /**
     * Create a report file to capture a view of this Representation
     *
     * @param veoDir The VEO directory in which to create the XML file
     * @param htmlFileName The XML file to create
     * @param title The title of the XML file
     * @param pVersion The version of VEOAnalysis
     * @param copyright The copyright string
     * @throws VEOError if something happened (e.g. it couldn't be created)
     */
    final protected void createReport(Path veoDir, String htmlFileName, String title, String pVersion, String copyright) throws VEOError {
        Path htmlFile;
        TimeZone tz;
        SimpleDateFormat sdf;

        assert(infoAvailable);
        try {
            htmlFile = veoDir.resolve(htmlFileName);
        } catch (InvalidPathException e) {
            throw new VEOError(classname, "createReport", 1, "Error when attempting to open HTML output file; '" + htmlFileName + "' is not a valid file name", e);
        }
        try {
            fos = new FileOutputStream(htmlFile.toFile());
            osw = new OutputStreamWriter(fos, Charset.forName("UTF-8"));
            w = new BufferedWriter(osw);
        } catch (IOException e) {
            throw new VEOError(classname, "createReport", 2, "Error when attempting to open HTML output file '" + htmlFile.toString() + "'", e);
        }
        try {
            w.write("<!DOCTYPE html>\n<html>\n<head>\n");
            w.write("<link rel=\"stylesheet\" href=\"ReportStyle.css\">");
            w.write("</head>\n</body>\n");
            w.write("  <h1>");
            w.write(title);
            w.write("</h1>\n");
            w.write("  <p class=\"preamble\">");
            w.write("VEO Analysis ");
            w.write(pVersion);
            w.write("<br>\n");
            w.write(copyright);
            w.write("<br>\n");
            w.write("VEO analysed: '" + veoDir.toAbsolutePath().normalize().toString() + "' at ");
            tz = TimeZone.getTimeZone("GMT+10:00");
            sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss+10:00");
            sdf.setTimeZone(tz);
            w.write(sdf.format(new Date()));
            w.write("<br>\n");
            w.write("</p>\n");
        } catch (IOException e) {
            log.log(Level.WARNING, VEOError.errMesg(classname, "createReport", 3, "IOException when writing to HTML output file", e));
        }
    }

    /**
     * Finish a report file.
     *
     * @throws VEOError if a fatal error occurred
     */
    final protected void finishReport() throws VEOError {

        // sanity check
        assert(infoAvailable);
        assert(fos != null && osw != null & w != null);

        // finish and close HTML report
        try {
            w.write("</body>\n");
            w.close();
            w = null;
            if (osw != null) {
                osw.close();
            }
            osw = null;
            if (fos != null) {
                fos.close();
            }
            fos = null;
        } catch (IOException e) {
            log.log(Level.WARNING, VEOError.errMesg(classname, "finishReport", 1, "IOException when writing to HTML output file", e));
        }
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
     * HTML DIV element can be linked to. If r is null, force the division to be
     * displayed as correct.
     *
     * @param r the Repn to test for errors and warnings
     * @param type list of class names (separated by spaces) to put in the DIV
     * element
     * @param anchor anchor to put in the div to allow linking
     */
    final protected void startDiv(Repn r, String type, String anchor) {

        // sanity check...
        assert(infoAvailable);
        assert(w != null);

        try {
            w.write("<div");
            if (r == null) {
                w.write(" class=\"box correct " + type + "\"");
            } else {
                if (r.hasErrors) {
                    w.write(" class=\"box error " + type + "\"");
                } else if (r.hasWarnings) {
                    w.write(" class=\"box warning " + type + "\"");
                } else {
                    w.write(" class=\"box correct " + type + "\"");
                }
            }
            if (anchor != null) {
                w.write(" id=\"" + anchor + "\"");
            }
            w.write(">\n");
        } catch (IOException e) {
            log.log(Level.WARNING, VEOError.errMesg(classname, "startDiv", 1, "IOException when writing to HTML output file", e));
        }
    }

    /**
     * End a DIV in the report.
     */
    final protected void endDiv() {
        String method = "endDiv";

        // sanity check...
        assert(infoAvailable);
        assert(w != null);
        try {
            w.write("</div>\n");
        } catch (IOException e) {
            log.log(Level.WARNING, VEOError.errMesg(classname, method, 1, "IOException when writing to HTML output file", e));
        }
    }

    /**
     * Add the error and warning messages to the report.
     */
    final protected void listIssues() {
        int i;

        // sanity check...
        assert(infoAvailable);
        assert(w != null);

        try {
            for (i = 0; i < errors.size(); i++) {
                w.write(" <li class=\"error\">");
                w.write("Error: ");
                w.write(safeXML(errors.get(i).getMessage()));
                w.write("</li>\n");
            }
            for (i = 0; i < warnings.size(); i++) {
                w.write(" <li class=\"warning\">");
                w.write("Warning: ");
                w.write(safeXML(warnings.get(i).getMessage()));
                w.write("</li>\n");
            }
        } catch (IOException e) {
            log.log(Level.WARNING, VEOError.errMesg(classname, "listIssues", 1, "IOException when writing to HTML output file", e));
        }
    }

    /**
     * Add a simple value to the report. The punctuation around the tag must be
     * included
     *
     * @param s String to add to the HTML.
     */
    final protected void addTag(String s) {

        // sanity checks
        assert(infoAvailable);
        assert(w != null);
        try {
            w.write(s);
        } catch (IOException e) {
            log.log(Level.WARNING, VEOError.errMesg(classname, "addTag", 1, "IOException when writing to HTML output file", e));
        }
    }

    /**
     * Add a label to the report file. Any less than or greater than characters
     * will be escaped.
     *
     * @param s String to add to the HTML.
     */
    final protected void addLabel(String s) {

        // sanity check...
        assert(infoAvailable);
        assert(w != null);
        try {
            w.write("<strong>");
            w.write(safeXML(s));
            w.write("</strong>");
        } catch (IOException e) {
            log.log(Level.WARNING, VEOError.errMesg(classname, "addLabel", 1, "IOException when writing to HTML output file", e));
        }
    }

    /**
     * Add a string to the report file. Any less than or greater than characters
     * will be escaped.
     *
     * @param s String to add to the HTML.
     */
    final protected void addString(String s) {

        // sanity check...
        assert(infoAvailable);
        assert(w != null);
        try {
            w.write(safeXML(s));
        } catch (IOException e) {
            log.log(Level.WARNING, VEOError.errMesg(classname, "addString", 1, "IOException when writing to HTML output file", e));
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
     * Get a description of the status of this object and all child objects,
     * including any errors or warnings. This is an abstract method that is
     * overriden by subclasses.
     *
     * @return A String containing the status
     */
    @Override
    abstract public String toString();
}
