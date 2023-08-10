/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOAnalysis;

import VERSCommon.ResultSummary;
import VERSCommon.VEOError;
import VERSCommon.VEOFailure;
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
 * This class represents the content of a physical content file or directory in
 * a content directory in the VEO. The related RepnContentFile structure
 * represents the same file (if it exists) in the VEOContent.xml file.
 *
 * @author Andrew Waugh
 */
class RepnFile extends Repn {
    private static final String CLASSNAME = "RepnFile";
    private RepnContentFile rcf; // representation of this file as a content file
    private Path file; // path of this file/directory relative to the VEO directory
    private FileTime lastModifiedTime; // date/time the file was last modified
    private FileTime lastAccessTime; // date/time the file was last accessed
    private FileTime creationTime; // date/time the file was created
    private Long size;  // size of the file in bytes
    private boolean isDirectory;    // true if file is a directory
    private boolean isSymbolicLink; // true if file is a symbolic link
    private String anchor;      // anchor used to reference this object
    private ArrayList<RepnFile> children;    // list of child files (if a directory)
    private final static Logger LOG = Logger.getLogger("VEOAnalysis.RepnFile");

    static int idCnt;  // used to generate a unique id for each RepnFile
    // note this means that this file is not thread safe

    /**
     * Builds an internal representation of a file/directory in one of the
     * content directories.
     *
     * @param file the file/directory to represent (must not be null)
     * @param veoDir the root directory of the extracted VEO (must not be null)
     * @param contentFiles an index to the content files that have been found (must not be null)
     * @param results the results summary to build
     * @throws VEOError if a fatal error occurred
     */
    public RepnFile(Path file, Path veoDir, HashMap<Path, RepnFile> contentFiles, ResultSummary results) throws VEOError {
        super(veoDir.relativize(file).toString(), results);

        DirectoryStream<Path> ds;
        
        // sanity
        assert (file != null);
        assert (veoDir != null);
        assert (contentFiles != null);

        idCnt++;

        this.file = file;
        rcf = null;
        children = new ArrayList<>();

        // make sure file exists...
        if (!Files.exists(file)) {
            addError(new VEOFailure(CLASSNAME, 1, id, "Content directory '" + file.toString() + "' does not exist"));
            return;
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
            throw new VEOError(CLASSNAME, 2, "Failed reading attributes on '" + file.toString() + "'", e);
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
                LOG.log(Level.WARNING, VEOFailure.getMessage(CLASSNAME, null, 3, id, "Failed stepping through directory of content", e));
            } finally {
                try {
                    if (ds != null) {
                        ds.close();
                    }
                } catch (IOException e) {
                    LOG.log(Level.WARNING, VEOFailure.getMessage(CLASSNAME, null, 4, id, "Failed closing directory of content", e));
                }
            }

            // add this file to the list of files...
        } else {
            contentFiles.put(veoDir.relativize(file), this);
        }
        anchor = "Report-" + veoDir.relativize(file).getName(0).toString() + ".html#rf" + id;
        objectValid = true;
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
     */
    public final void validate() {
        int i;

        // if a plain file, check that there is a linked RepnContentFile (i.e
        // this file is referenced in the VEOContent.xml file...
        if (!isDirectory) {
            if (rcf == null) {
                addWarning(new VEOFailure(CLASSNAME, "validate", 1, id, "This file is not referenced in the VEOContent.xml file"));
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
        assert (rcf != null);
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
    public void getProblems(boolean returnErrors, List<VEOFailure> l) {
        int i;

        assert(l != null);
        
        super.getProblems(returnErrors, l);
        for (i = 0; i < children.size(); i++) {
            children.get(i).getProblems(returnErrors, l);
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
     * @throws VEOError if prevented from continuing processing this VEO
     */
    public void genReport(boolean verbose, Path veoDir, String directory, String pVersion, String copyright) throws VEOError {
        assert(veoDir != null);
        assert(directory != null);
        assert(pVersion != null);
        assert(copyright != null);
        
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
     * VEO
     */
    public void genReport(boolean verbose, Path veoDir, Writer w) {
        int i;
        
        assert(veoDir != null);
        assert(w != null);

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
