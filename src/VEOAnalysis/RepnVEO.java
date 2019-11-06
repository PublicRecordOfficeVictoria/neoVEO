/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOAnalysis;

import VERSCommon.VEOError;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * This class encapsulates an Information Object in a VEO Content file.
 *
 * @author Andrew Waugh
 */
public class RepnVEO extends Repn {

    String classname = "RepnVEO";
    Path schemaDir; // directory in which XML schemas are to be found
    Path veoDir;        // directory in which the VEO components can be examined
    RepnItem readme; // issues with the VEOReadme.txt file
    RepnContent veoContent; // The representation of the VEOContent.xml file
    RepnHistory veoHistory; // The representation of the VEOHistory.xml file
    ArrayList<RepnSignature> veoContentSignatures; // The representation of the signature files
    ArrayList<RepnSignature> veoHistorySignatures; // The representation of the signature files
    ArrayList<RepnFile> contentDirs; // list of content directories
    HashMap<Path, RepnFile> contentFiles;   // Collection of content files in the VEO
    Path templateDir;   // template directory
    boolean veoReadmePresent; // true if VEOReadme.txt file is present
    private final static Logger log = Logger.getLogger("VEOAnalysis.RepnVEO");

    /**
     * Construct a VEO from a zip file.
     *
     * @param veo the zip file containing the VEO
     * @param debug true if more detail is to be generated
     * @param output the directory in which to extract the VEO
     * @throws VEOError if the XML document has not been properly parsed
     */
    public RepnVEO(String veo, boolean debug, Path output) throws VEOError {
        super("");

        int i;
        String s, safe;
        Path zipFile, p;

        // initialise
        schemaDir = null;
        readme = new RepnItem(getId() + "Readme.txt", "");
        veoContent = null;
        veoHistory = null;
        veoContentSignatures = new ArrayList<>();
        veoHistorySignatures = new ArrayList<>();
        contentDirs = new ArrayList<>();
        contentFiles = new HashMap<>();
        templateDir = null;
        veoReadmePresent = false;

        // check if VEO exists
        safe = veo.replaceAll("\\\\", "/");
        zipFile = Paths.get(safe);
        if (!Files.exists(zipFile)) {
            throw new VEOError(1, errMesg(classname, "VEO file name '" + safe + "' does not exist"));
        }

        // get VEO directory name
        s = zipFile.getFileName().toString();
        if ((i = s.lastIndexOf(".zip")) == -1) {
            throw new VEOError(2, errMesg(classname, "VEO file name '" + safe + "' does not end in '.zip'"));
        }
        s = s.substring(0, i);
        safe = s.replaceAll("\\\\", "/");
        veoDir = output.resolve(safe);
        veoDir = veoDir.normalize();

        // delete the VEO directory (if it exists)
        deleteVEO();

        // unzip veo into VEO directory
        unzip(zipFile);
    }

    /**
     * Return the VEO directory
     *
     * @return the VEO directory
     */
    public Path getVEODir() {
        return veoDir;
    }

    /**
     * Construct an internal representation of the VEO ready for validation
     *
     * @param schemaDir the directory in which the VERS schema information is
     * found
     * @throws VEOError If an error occurred in processing this VEO
     */
    public void constructRepn(Path schemaDir) throws VEOError {
        String fileName;
        RepnSignature rs;
        DirectoryStream<Path> ds;

        // configure the logging used in the RDF validator
        org.apache.log4j.PropertyConfigurator.configure(schemaDir.resolve("log4j.properties").toAbsolutePath().toString());

        // check that the VEO directory has the correct files (and no others)
        // System.out.println("validating " + veoDir.toString());
        this.schemaDir = schemaDir;
        ds = null;
        try {
            ds = Files.newDirectoryStream(veoDir);
            for (Path entry : ds) {
                if (Files.isDirectory(entry)) {
                    contentDirs.add(new RepnFile(entry, veoDir, contentFiles));
                    continue;
                }
                fileName = entry.getFileName().toString();
                switch (fileName) {
                    case ".":
                    case "..":
                        break;
                    case "VEOContent.xml":
                        veoContent = new RepnContent(veoDir, schemaDir, contentFiles);
                        break;
                    case "VEOHistory.xml":
                        veoHistory = new RepnHistory(veoDir, schemaDir);
                        break;
                    case "VEOReadme.txt":
                        int expVEOSize = 4840;
                        if (Files.size(entry) != expVEOSize) {
                            readme.addWarning("VEOReadme.txt has an unexpected size(" + Files.size(entry) + ") instead of " + expVEOSize);
                        }
                        veoReadmePresent = true;
                        break;
                    default:
                        if (fileName.startsWith("VEOContentSignature") && fileName.endsWith(".xml")) {
                            rs = new RepnSignature(veoDir, fileName, schemaDir);
                            veoContentSignatures.add(rs);
                        } else if (fileName.startsWith("VEOHistorySignature") && fileName.endsWith(".xml")) {
                            rs = new RepnSignature(veoDir, fileName, schemaDir);
                            veoHistorySignatures.add(rs);
                        } else if (fileName.startsWith("Report") && fileName.endsWith(".html")) {
                            /* ignore */
                        } else if (fileName.equals("index.html")) {
                            /* ignore */
                        } else if (fileName.equals("Report.css")) {
                            /* ignore */
                        } else {
                            addWarning("Unexpected file in VEO directory: " + fileName);
                        }
                }
            }
        } catch (DirectoryIteratorException e) {
            throw new VEOError(errMesg(classname, "Directory iterator failed", e));
        } catch (IOException e) {
            throw new VEOError(errMesg(classname, "Failed to open the VEO directory for reading files", e));
        } finally {
            if (ds != null) {
                try {
                    ds.close();
                } catch (IOException e) {
                    log.log(Level.WARNING, errMesg(classname, "Closing directory stream failed", e));
                }
            }
        }
    }

    /**
     * Delete the VEO directory. Typically called after the VEO has been
     * processed.
     *
     * @throws VEOError if an error occurred when deleting the VEO.
     */
    public final void deleteVEO() throws VEOError {
        if (veoDir != null && Files.exists(veoDir)) {
            try {
                deleteFile(veoDir);
            } catch (IOException e) {
                throw new VEOError(errMesg(classname, "IOException deleting VEO directory", e));
            }
        }
    }

    /**
     * Private function to recursively delete a directory or file. Needed
     * because you cannot delete a non empty directory
     *
     * @param file Path of directory or file to delete
     * @throws IOException if deleting file failed
     */
    private void deleteFile(Path file) throws IOException {
        DirectoryStream<Path> ds;

        // if a directory, list all the files and delete them
        if (Files.isDirectory(file)) {
            ds = Files.newDirectoryStream(file);
            for (Path p : ds) {
                deleteFile(p);
            }
            ds.close();
        }

        // finally, delete the file
        try {
            Files.delete(file);
        } catch (FileSystemException e) {
            System.out.println(e.toString());
        }
    }

    /**
     * Free resources associated with this RepnVEO.
     */
    @Override
    public void abandon() {
        int i;

        super.abandon();
        schemaDir = null;
        veoDir = null;
        if (veoContent != null) {
            veoContent.abandon();
        }
        veoContent = null;
        if (veoHistory != null) {
            veoHistory.abandon();
        }
        veoHistory = null;
        readme.abandon();
        readme = null;
        for (i = 0; i < veoContentSignatures.size(); i++) {
            veoContentSignatures.get(i).abandon();
        }
        veoContentSignatures.clear();
        for (i = 0; i < veoHistorySignatures.size(); i++) {
            veoHistorySignatures.get(i).abandon();
        }
        veoHistorySignatures.clear();
        for (i = 0; i < contentDirs.size(); i++) {
            contentDirs.get(i).abandon();
        }
        contentDirs.clear();
        contentDirs = null;
        contentFiles.clear();
        contentFiles = null;
    }

    /**
     * Validate the data in the RepnVEO.
     *
     * @param ltpfs HashMap of valid long term preservation formats
     * @param noRec true if not to complain about missing recommended metadata
     * elements
     * @throws VEOSupport.VEOError if validation couldn't be completed
     */
    public final void validate(HashMap<String, String> ltpfs, boolean noRec) throws VEOError {
        int i;

        // check to see that the required files are present
        if (veoContent == null) {
            addError("VEOContents.xml file is not present");
        } else {
            veoContent.validate(veoDir, contentFiles, ltpfs, noRec);
        }
        if (veoHistory == null) {
            addError("VEOHistory.xml file is not present");
        } else {
            veoHistory.validate();
        }
        if (!veoReadmePresent) {
            addError("VEOReadme.txt file is not present");
        }
        if (veoContentSignatures.isEmpty()) {
            addError("No VEOContentSignature?.xml files are present");
        } else {
            for (i = 0; i < veoContentSignatures.size(); i++) {
                veoContentSignatures.get(i).validate();
            }
        }
        if (veoHistorySignatures.isEmpty()) {
            addError("No VEOHistorySignature?.xml files are present");
        } else {
            for (i = 0; i < veoHistorySignatures.size(); i++) {
                veoHistorySignatures.get(i).validate();
            }
        }
        for (i = 0; i < contentDirs.size(); i++) {
            contentDirs.get(i).validate();
        }
    }

    /**
     * Private function to unzip a VEO file.
     *
     * @param zipFilePath the path to the VEO file
     * @throws VEOError
     * @throws IOException
     */
    private void unzip(Path zipFilePath) throws VEOError {
        String method = "unzip";
        ZipFile zipFile;
        Enumeration entries;
        ZipEntry entry;
        Path vze, zipEntryPath;
        InputStream is;
        BufferedInputStream bis;
        FileOutputStream fos;
        BufferedOutputStream bos;
        byte[] b = new byte[1024];
        int len;
        String veoName, s;
        long modTime;
        boolean complainedOnceAlready;

        // unzip the VEO file
        bos = null;
        fos = null;
        bis = null;
        is = null;
        zipFile = null;
        complainedOnceAlready = false;
        try {
            // get the name of this VEO, stripping off the final '.zip'
            veoName = zipFilePath.getFileName().toString();
            int i = veoName.lastIndexOf(".");
            if (i != -1) {
                veoName = veoName.substring(0, i);
            }

            // open the zip file and get the entries in it
            zipFile = new ZipFile(zipFilePath.toFile());
            entries = zipFile.entries();

            // go through each entry
            while (entries.hasMoreElements()) {
                entry = (ZipEntry) entries.nextElement();
                log.log(Level.FINE, "Extracting: {0}({1}) {2} {3}", new Object[]{entry.getName(), entry.getSize(), entry.getTime(), entry.isDirectory()});

                // get the local path to extract the ZIP entry into
                // this is so horrible because Paths.resolve won't process
                // windows file separators in a string on Unix boxes
                String safe = entry.getName().replaceAll("\\\\", "/");
                zipEntryPath = Paths.get(safe);

                // complain (once!) if filename of the VEO is different to the
                // base of the filenames in the ZIP file (e.g. the VEO file has
                // been renamed)
                if (!veoName.equals(zipEntryPath.getName(0).toString())) {
                    if (!complainedOnceAlready) {
                        log.log(Level.WARNING, "The filename of the VEO ({0}) is different to that contained in the entries in the ZIP file ({1})", new Object[]{veoName, entry.getName()});
                    }
                    complainedOnceAlready = true;
                }

                // doesn't matter what the ZIP file says, force the extract to
                // be in a directory with the same name as the VEO filename
                // (even if we have complained about this)
                if (zipEntryPath.getNameCount() == 1) {
                    vze = veoDir.getParent().resolve(Paths.get(veoName));
                } else {
                    zipEntryPath = zipEntryPath.subpath(1, zipEntryPath.getNameCount());
                    vze = veoDir.getParent().resolve(Paths.get(veoName)).resolve(zipEntryPath);
                }
                vze = vze.normalize();

                // just be cynical and check that the file name to be extracted
                // from the ZIP file is actually in the VEO directory...
                if (!vze.startsWith(veoDir)) {
                    throw new VEOError(errMesg(classname, method, "ZIP entry in VEO '" + veoName + "' is attempting to create a file outside the VEO directory '" + vze.toString()));
                }

                // make any directories...
                if (entry.isDirectory()) {
                    Files.createDirectories(vze);
                } else {
                    // make any missing directories parent
                    Files.createDirectories(vze.getParent());

                    // extract file
                    is = zipFile.getInputStream(entry);
                    bis = new BufferedInputStream(is);
                    fos = new FileOutputStream(vze.toFile());
                    bos = new BufferedOutputStream(fos, 1024);
                    while ((len = bis.read(b, 0, 1024)) != -1) {
                        bos.write(b, 0, len);
                    }
                    bos.flush();
                    bos.close();
                    bos = null;
                    fos.flush();
                    fos.close();
                    fos = null;
                    bis.close();
                    bis = null;
                    is.close();
                    is = null;
                }

                // set the time of the file
                if ((modTime = entry.getTime()) != -1) {
                    Files.setLastModifiedTime(vze, FileTime.fromMillis(modTime));
                }
            }
            zipFile.close();
        } catch (ZipException e) {
            throw new VEOError(errMesg(classname, method, "ZIP format error in opening Zip file", e));
        } catch (IOException e) {
            throw new VEOError(errMesg(classname, method, "IO error reading Zip file", e));
        } finally {
            try {
                if (bos != null) {
                    bos.close();
                }
                if (fos != null) {
                    fos.close();
                }
                if (bis != null) {
                    bis.close();
                }
                if (is != null) {
                    is.close();
                }
                if (zipFile != null) {
                    zipFile.close();
                }
            } catch (IOException e) {
                log.log(Level.WARNING, errMesg(classname, method, "IOException in closing Zip files", e));
            }
        }
    }

    /**
     * Has this RepnVEO (or its children) any errors?
     *
     * @return true if errors have been detected
     */
    @Override
    public boolean hasErrors() {
        int i;

        hasErrors |= readme.hasErrors();
        if (veoContent != null) {
            hasErrors |= veoContent.hasErrors();
        }
        if (veoHistory != null) {
            hasErrors |= veoHistory.hasErrors();
        }
        for (i = 0; i < veoContentSignatures.size(); i++) {
            hasErrors |= veoContentSignatures.get(i).hasErrors();
        }
        for (i = 0; i < veoHistorySignatures.size(); i++) {
            hasErrors |= veoHistorySignatures.get(i).hasErrors();
        }
        for (i = 0; i < contentDirs.size(); i++) {
            hasErrors |= contentDirs.get(i).hasErrors();
        }
        return hasErrors;
    }

    /**
     * Build a list of all of the errors generated by this RepnVEO and its
     * children.
     *
     * @return String containing the concatenated error list
     */
    @Override
    public String getErrors() {
        int i;
        StringBuffer sb;

        sb = new StringBuffer();
        sb.append(super.getErrors());
        if (veoContent != null) {
            sb.append(veoContent.getErrors());
        }
        if (veoHistory != null) {
            sb.append(veoHistory.getErrors());
        }
        if (readme != null) {
            sb.append(readme.getErrors());
        }
        for (i = 0; i < veoContentSignatures.size(); i++) {
            sb.append(veoContentSignatures.get(i).getErrors());
        }
        for (i = 0; i < veoHistorySignatures.size(); i++) {
            sb.append(veoHistorySignatures.get(i).getErrors());
        }
        for (i = 0; i < contentDirs.size(); i++) {
            sb.append(contentDirs.get(i).getErrors());
        }
        return sb.toString();
    }

    /**
     * Has this RepnVEO (or its children) any warnings?
     *
     * @return true if errors have been detected
     */
    @Override
    public boolean hasWarnings() {
        int i;

        hasWarnings |= readme.hasWarnings();
        if (veoContent != null) {
            hasWarnings |= veoContent.hasWarnings();
        }
        if (veoHistory != null) {
            hasWarnings |= veoHistory.hasWarnings();
        }
        for (i = 0; i < veoContentSignatures.size(); i++) {
            hasWarnings |= veoContentSignatures.get(i).hasWarnings();
        }
        for (i = 0; i < veoHistorySignatures.size(); i++) {
            hasWarnings |= veoHistorySignatures.get(i).hasWarnings();
        }
        for (i = 0; i < contentDirs.size(); i++) {
            hasWarnings |= contentDirs.get(i).hasWarnings();
        }
        return hasWarnings;
    }

    /**
     * Build a list of all of the warnings generated by this RepnVEO and its
     * children.
     *
     * @return String containing the concatenated error list
     */
    @Override
    public String getWarnings() {
        int i;
        StringBuffer sb;

        sb = new StringBuffer();
        sb.append(super.getWarnings());
        if (veoContent != null) {
            sb.append(veoContent.getWarnings());
        }
        if (veoHistory != null) {
            sb.append(veoHistory.getWarnings());
        }
        if (readme != null) {
            sb.append(readme.getWarnings());
        }
        for (i = 0; i < veoContentSignatures.size(); i++) {
            sb.append(veoContentSignatures.get(i).getWarnings());
        }
        for (i = 0; i < veoHistorySignatures.size(); i++) {
            sb.append(veoHistorySignatures.get(i).getWarnings());
        }
        for (i = 0; i < contentDirs.size(); i++) {
            sb.append(contentDirs.get(i).getWarnings());
        }
        return sb.toString();
    }

    /**
     * Return a summary of the errors and warnings that occurred in the VEO.
     *
     * @return a String containing the errors and warnings
     */
    public String getStatus() {
        StringBuffer sb;

        // check for errors
        sb = new StringBuffer();
        if (!hasErrors()) {
            sb.append("No errors detected\n");
        } else {
            sb.append("Errors detected:\n");
            sb.append(getErrors());
        }

        // check for warnings
        if (!hasWarnings()) {
            sb.append("No warnings detected\n");
        } else {
            sb.append("Warnings detected:\n");
            sb.append(getWarnings());
        }
        return sb.toString();
    }

    /**
     * Produce a string representation of the VEO
     *
     * @return The string representation
     */
    @Override
    public String toString() {
        StringBuffer sb;
        int i;

        sb = new StringBuffer();
        sb.append("VEO (");
        sb.append(veoDir.toString());
        sb.append(")\n");
        if (veoContent != null) {
            sb.append(veoContent.toString());
        } else {
            sb.append("<No VEOContent>\n");
        }
        if (veoHistory != null) {
            sb.append(veoHistory.toString());
        } else {
            sb.append("<No VEOHistory>\n");
        }
        for (i = 0; i < veoContentSignatures.size(); i++) {
            sb.append(veoContentSignatures.get(i).toString());
        }
        for (i = 0; i < veoHistorySignatures.size(); i++) {
            sb.append(veoHistorySignatures.get(i).toString());
        }
        for (i = 0; i < contentDirs.size(); i++) {
            sb.append(contentDirs.get(i).toString());
        }
        return sb.toString();
    }

    /**
     * Generate a HTML representation of the VEO. Must be called after
     * validate().
     *
     * @param verbose true if additional information is to be generated
     * @throws VEOSupport.VEOError if a fatal error occurred
     */
    public void genReport(boolean verbose) throws VEOError {
        String method = "genReport";
        int i;
        String r;
        Path cssSource, cssDest;

        // copy in CSS file from schema directory
        cssSource = schemaDir.resolve("ReportStyle.css");
        cssDest = veoDir.resolve("ReportStyle.css");
        if (Files.exists(cssSource)) {
            try {
                Files.copy(cssSource, cssDest, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.log(Level.WARNING, errMesg(classname, method, "Copying ReportStyle.css file to VEO directory failed", e));
            }
        } else {
            log.log(Level.WARNING, errMesg(classname, method, "File: '" + cssSource.toAbsolutePath().toString() + "' doesn't exist"));
        }

        // create index file
        createReport(veoDir, "index.html", "Report for " + veoDir.getFileName());

        // check for errors and warnings
        hasErrors();
        hasWarnings();

        // generate the report
        startDiv("VEO", null);
        addLabel("VEO");
        if (hasErrors || hasWarnings) {
            addTag("<ul>\n");
            listIssues();
            addTag("</ul>\n");
        }

        if (veoContent != null) {
            veoContent.genReport(verbose, veoDir);
            startDiv(veoContent, "VEOContent", null);
            addString("Report for ");
            addTag("<a href=\"./Report-VEOContent.html\">");
            addString("VEOContent.xml");
            addTag("</a>");
            endDiv();
        }

        if (veoHistory != null) {
            veoHistory.genReport(verbose, veoDir);
            startDiv(veoHistory, "VEOHistory", null);
            addString("Report for ");
            addTag("<a href=\"./Report-VEOHistory.html\">");
            addString("VEOHistory.xml");
            addTag("</a>");
            endDiv();
        }

        for (i = 0; i < veoContentSignatures.size(); i++) {
            r = "VEOContentSignature" + Integer.toString(i + 1);
            veoContentSignatures.get(i).genReport(verbose, veoDir, r + ".xml");
            startDiv(veoContentSignatures.get(i), "VEOContentSig", null);
            addString("Report for ");
            addTag("<a href=\"./Report-" + r + ".html\">");
            addString(r + ".xml");
            addTag("</a>");
            endDiv();
        }

        for (i = 0; i < veoHistorySignatures.size(); i++) {
            r = "VEOHistorySignature" + Integer.toString(i + 1);
            veoHistorySignatures.get(i).genReport(verbose, veoDir, r + ".xml");
            startDiv(veoHistorySignatures.get(i), "VEOHistorySig", null);
            addString("Report for ");
            addTag("<a href=\"./Report-" + r + ".html\">");
            addString(r + ".xml");
            addTag("</a>");
            endDiv();
        }

        for (i = 0; i < contentDirs.size(); i++) {
            r = contentDirs.get(i).getFileName();
            contentDirs.get(i).genReport(verbose, veoDir, r);
            startDiv(contentDirs.get(i), "ContentDir", null);
            addString("Report for ");
            addTag("<a href=\"./Report-" + r + ".html\">");
            addString(r);
            addTag("</a> content directory");
            endDiv();
        }

        if (veoReadmePresent) {
            startDiv(readme, "VEOReadme", null);
            addString("View the ");
            addTag("<a href=\"./VEOReadme.txt\">");
            addString("VEOReadme.txt");
            addTag("</a>");
            addString(" file\n");
            if (readme.hasErrors || readme.hasWarnings) {
                addTag("<ul>\n");
                readme.setReportWriter(getReportWriter());
                readme.listIssues();
                addTag("</ul>\n");
            }
            endDiv();
        }

        endDiv();
        finishReport();
    }
}
