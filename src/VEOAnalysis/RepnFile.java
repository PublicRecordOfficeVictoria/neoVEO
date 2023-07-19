/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOAnalysis;

import VERSCommon.ResultSummary;
import VERSCommon.VEOError;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class represents the content of a content file or directory.
 *
 * @author Andrew Waugh
 */
class RepnFile extends Repn {

    String classname = "RepnFile";
    int id;     // unique identifier of this RepnFile
    RepnContentFile rcf; // representation of this file as a content file
    Path file; // path of this file/directory relative to the VEO directory
    FileTime lastModifiedTime; // date/time the file was last modified
    FileTime lastAccessTime; // date/time the file was last accessed
    FileTime creationTime; // date/time the file was created
    Long size;  // size of the file in bytes
    boolean isDirectory;    // true if file is a directory
    boolean isSymbolicLink; // true if file is a symbolic link
    String anchor;      // anchor used to reference this object
    ArrayList<RepnFile> children;    // list of child files (if a directory)
    private final static Logger LOG = Logger.getLogger("VEOAnalysis.RepnFile");

    static int idCnt;  // used to generate a unique id for each RepnFile
    // note this means that this file is not thread safe

    /**
     * Builds an internal representation of a file/directory in one of the
     * content directories.
     *
     * @param file the file to represent
     * @param veoDir the VEODirectory (the root)
     * @param contentFiles an index to the content files that have been found
     * @param results the results summary to build
     * @throws VEOError if a fatal error occurred
     */
    public RepnFile(Path file, Path veoDir, HashMap<Path, RepnFile> contentFiles, ResultSummary results) throws VEOError {
        super(veoDir.relativize(file).toString(), results);

        DirectoryStream<Path> ds;

        // allocate a unique id for this RepnFile
        id = idCnt;
        idCnt++;

        this.file = file;
        rcf = null;
        children = new ArrayList<>();

        // make sure file exists...
        if (!Files.exists(file)) {
            throw new VEOError(classname, 1, "Content directory '" + file.toString() + "' does not exist");
        }

        // get the basic attributes
        lastModifiedTime = null;
        lastAccessTime = null;
        creationTime = null;
        size = null;
        try {
            lastModifiedTime = (FileTime) Files.getAttribute(file, "lastModifiedTime");
            lastAccessTime = (FileTime) Files.getAttribute(file, "lastAccessTime");
            creationTime = (FileTime) Files.getAttribute(file, "creationTime");
            size = (Long) Files.getAttribute(file, "size");
        } catch (IOException e) {
            throw new VEOError(classname, 2, "Failed reading attributes on '" + file.toString() + "'", e);
        }
        isDirectory = Files.isDirectory(file);
        isSymbolicLink = Files.isSymbolicLink(file);

        // go through children if this is a directory
        if (Files.isDirectory(file)) {
            ds = null;
            try {
                ds = Files.newDirectoryStream(file);
                for (Path entry : ds) {
                    children.add(new RepnFile(entry, veoDir, contentFiles, results));
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, VEOError.errMesg(classname, null, 3, "Failed stepping through directory of content", e));
            } finally {
                try {
                    if (ds != null) {
                        ds.close();
                    }
                } catch (IOException e) {
                    LOG.log(Level.WARNING, VEOError.errMesg(classname, null, 4, "Failed closing directory of content", e));
                }
            }

            // add this file to the list of files...
        } else {
            contentFiles.put(veoDir.relativize(file), this);
        }
        anchor = "Report-" + veoDir.relativize(file).getName(0).toString() + ".html#rf" + id;
    }

    /**
     * Free resources associated with this File
     */
    @Override
    public void abandon() {
        int i;

        super.abandon();
        rcf = null;
        file = null;
        lastModifiedTime = null;
        lastAccessTime = null;
        creationTime = null;
        size = null;
        for (i = 0; i < children.size(); i++) {
            children.get(i).abandon();
        }
        children.clear();
    }

    /**
     * Validate the data in the File. The only check required is that this file
     * is listed in the VEOContent.xml file.
     *
     * @throws VEOError if a fatal error occurred
     */
    public final void validate() throws VEOError {
        int i;

        // if a plain file, check that there is a linked RepnContentFile (i.e
        // referenced in the VEOContent.xml file...
        if (!isDirectory) {
            if (rcf == null) {
                addWarning(new VEOError(classname, "validate", 1, "This file is not referenced in the VEOContent.xml file"));
            }

            // otherwise, process children
        } else {
            for (i = 0; i < children.size(); i++) {
                children.get(i).validate();
            }
        }

        // set error and warning flags
        hasErrors();
        hasWarnings();
    }

    /**
     * Return the file name of this object
     *
     * @return String containing the file name
     */
    public String getFileName() {
        return file.getFileName().toString();
    }

    /**
     * Return the anchor used to refer to this RepnFile
     *
     * @return String representing the anchor
     */
    public String getAnchor() {
        return anchor;
    }

    /**
     * Set a link to the reference in the VEOContent.xml file
     *
     * @param rcf the RepnContentFile that references this file
     */
    public void setContentFile(RepnContentFile rcf) {
        this.rcf = rcf;
    }

    /**
     * Check if this File (or its children) has any errors?
     *
     * @return true if errors have been detected
     */
    @Override
    public boolean hasErrors() {
        int i;

        for (i = 0; i < children.size(); i++) {
            hasErrors |= children.get(i).hasErrors();
        }
        return hasErrors;
    }

    /**
     * Build a list of all of the errors generated by this File
     * 
     * @param returnErrors if true return errors, otherwise return warnings
     * @param l list in which to place the errors/warnings
     */
    @Override
    public void getProblems(boolean returnErrors, List<VEOError> l) {
        int i;

        super.getProblems(returnErrors, l);
        for (i = 0; i < children.size(); i++) {
            children.get(i).getProblems(returnErrors, l);
        }
    }

    /**
     * Build a list of all of the errors generated by this File
     *
     * @return String containing the concatenated error list
     */
    @Override
    public void getMesgs(boolean returnErrors, List<String> l) {
        int i;

        super.getMesgs(returnErrors, l);
        for (i = 0; i < children.size(); i++) {
            children.get(i).getMesgs(returnErrors, l);
        }
    }

    /**
     * Has this File (or its children) any warnings?
     *
     * @return true if errors have been detected
     */
    @Override
    public boolean hasWarnings() {
        int i;

        for (i = 0; i < children.size(); i++) {
            hasWarnings |= children.get(i).hasWarnings();
        }
        return hasWarnings;
    }

    /**
     * Generate a String representation of the signature
     *
     * @return the String representation
     */
    @Override
    public String toString() {
        StringBuffer sb;
        int i;

        sb = new StringBuffer();
        sb.append(" File ");
        sb.append(getFileName());
        sb.append("\n");
        for (i = 0; i < children.size(); i++) {
            sb.append(children.get(i).toString());
        }
        return sb.toString();
    }

    /**
     * Generate an HTML representation of the content file
     *
     * @param verbose true if additional information is to be generated
     * @param veoDir the directory in which to create the report
     * @param directory the name of the content directory
     * @param pVersion The version of VEOAnalysis
     * @param copyright The copyright string
     * @throws VERSCommon.VEOError if prevented from continuing processing this
     * VEO
     */
    public void genReport(boolean verbose, Path veoDir, String directory, String pVersion, String copyright) throws VEOError {
        createReport(veoDir, "Report-" + directory + ".html", "Report for content directory '" + directory + "'", pVersion, copyright);
        genReport(verbose, veoDir, w);
        finishReport();
    }

    /**
     * Generate an HTML representation of the content file
     *
     * @param verbose true if additional information is to be generated
     * @param veoDir the VEO directory
     * @param writer where to write the output
     * @throws VERSCommon.VEOError if prevented from continuing processing this
     * VEO
     */
    public void genReport(boolean verbose, Path veoDir, Writer w) throws VEOError {
        int i;

        this.w = w;
        startDiv("File", anchor);
        addLabel(getFileName());
        addString(" (");
        addTag("<a href=\"" + veoDir.relativize(file).toString() + "\">");
        addString(veoDir.relativize(file).toString());
        addTag("</a>) ");
        if (rcf != null) {
            addTag("<a href=\"" + rcf.getAnchor() + "\">");
            addString("Link to reference in VEOContent.xml");
            addTag("</a>");
        }
        addTag("<br>");
        if (isDirectory) {
            addString("This is directory; ");
        } else {
            addString("This is an ordinary file ");
            if (size != null) {
                addString(" with size: " + size.toString() + " ");
            }
            addString(";");
        }
        if (isSymbolicLink) {
            addString("It is a symbolic link.");
        }
        addTag("<br>");
        if (lastModifiedTime != null) {
            addString("last modified: " + lastModifiedTime.toString() + "; ");
        }
        if (lastAccessTime != null) {
            addString("last access: " + lastAccessTime.toString() + "; ");
        }
        if (creationTime != null) {
            addString("created on: " + creationTime.toString());
        }
        if (hasErrors || hasWarnings) {
            addTag("<ul>\n");
            listIssues();
            addTag("</ul>\n");
        }
        for (i = 0; i < children.size(); i++) {
            children.get(i).genReport(verbose, veoDir, w);
        }
        endDiv();
    }
}
