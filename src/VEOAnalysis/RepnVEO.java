/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOAnalysis;

import VERSCommon.LTSF;
import VERSCommon.ResultSummary;
import VERSCommon.ResultSummary.Type;
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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.log4j.PropertyConfigurator;
// import org.apache.log4j.PropertyConfigurator;

/**
 * This class encapsulates testing the VEO as a whole.
 *
 * @author Andrew Waugh
 */
class RepnVEO extends Repn {

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
    private final static Logger LOG = Logger.getLogger("VEOAnalysis.RepnVEO");

    /**
     * Construct a VEO from a zip file.
     *
     * @param veo the zip file containing the VEO
     * @param debug true if more detail is to be generated
     * @param output the directory in which to extract the VEO
     * @param results the results summary to build
     * @throws VEOError if the XML document has not been properly parsed
     */
    public RepnVEO(Path veo, boolean debug, Path output, ResultSummary results) throws VEOError {
        super("", results);

        int i;
        String s, safe;

        // initialise
        schemaDir = null;
        readme = new RepnItem(getId() + "Readme.txt", "", results);
        veoContent = null;
        veoHistory = null;
        veoContentSignatures = new ArrayList<>();
        veoHistorySignatures = new ArrayList<>();
        contentDirs = new ArrayList<>();
        contentFiles = new HashMap<>();
        templateDir = null;
        veoReadmePresent = false;

        // check if VEO exists
        safe = veo.toString().replaceAll("\\\\", "/");
        if (!Files.exists(veo)) {
            throw new VEOError(classname, 2, "VEO file name '" + safe + "' does not exist");
        }

        // get VEO directory name
        s = veo.getFileName().toString();
        if ((i = s.lastIndexOf(".zip")) == -1) {
            throw new VEOError(classname, 3, "VEO file name '" + safe + "' does not end in '.zip'");
        }
        s = s.substring(0, i);
        safe = s.replaceAll("\\\\", "/");
        try {
            veoDir = output.resolve(safe).normalize();
        } catch (InvalidPathException ipe) {
            throw new VEOError(classname, 4, "VEO directory name '" + safe + "' is invalid: " + ipe.getMessage());
        }

        // delete the VEO directory (if it exists)
        deleteVEO();

        // unzip veo into VEO directory
        unzip(veo);
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
    // this array contains the valid lengths of the VEOReadMe.txt over time
    static int expVEOSize[] = {4746, 4840, 5061, 5062};

    public void constructRepn(Path schemaDir) throws VEOError {
        String fileName;
        RepnSignature rs;
        DirectoryStream<Path> ds;
        int i;
        StringBuilder sb;

        // Configure the logging used in the RDF validator. See the discussion
        // RepnMetadataPackage for which version you should use. Uncomment the
        // line for the version of log4j that you wish to use
        // This is for log4j2 used with Jena 4
        // System.setProperty("log4j2.configurationFile", schemaDir.resolve("log4j2.properties").toAbsolutePath().toString());
        // This is for log4j used with Jena 2
        PropertyConfigurator.configure(schemaDir.resolve("log4j.properties").toAbsolutePath().toString());

        // check that the VEO directory has the correct files (and no others)
        // System.out.println("validating " + veoDir.toString());
        this.schemaDir = schemaDir;
        ds = null;
        try {
            ds = Files.newDirectoryStream(veoDir);
            for (Path entry : ds) {
                if (Files.isDirectory(entry)) {
                    contentDirs.add(new RepnFile(entry, veoDir, contentFiles, results));
                    continue;
                }
                fileName = entry.getFileName().toString();
                switch (fileName) {
                    case ".":
                    case "..":
                        break;
                    case "VEOContent.xml":
                        veoContent = new RepnContent(veoDir, schemaDir, contentFiles, results);
                        break;
                    case "VEOHistory.xml":
                        veoHistory = new RepnHistory(veoDir, schemaDir, results);
                        break;
                    case "VEOReadme.txt":

                        // check that the length of the VEOReadme.txt file is
                        // one of the valid lengths, if not complain.
                        for (i = 0; i < expVEOSize.length; i++) {
                            if (Files.size(entry) == expVEOSize[i]) {
                                break;
                            }
                        }
                        if (i == expVEOSize.length) {
                            sb = new StringBuilder();
                            for (i = 0; i < expVEOSize.length; i++) {
                                sb.append(expVEOSize[i]);
                                if (i == expVEOSize.length - 2) {
                                    sb.append(", or ");
                                } else if (i < expVEOSize.length - 1) {
                                    sb.append(", ");
                                }
                            }
                            readme.addWarning(new VEOError(classname, "constRepn", 1, "VEOReadme.txt has an unexpected size (" + Files.size(entry) + ") instead of the valid values of " + sb.toString()));
                        }
                        veoReadmePresent = true;
                        break;
                    default:
                        if (fileName.startsWith("VEOContentSignature") && fileName.endsWith(".xml")) {
                            rs = new RepnSignature(veoDir, fileName, schemaDir, results);
                            veoContentSignatures.add(rs);
                        } else if (fileName.startsWith("VEOHistorySignature") && fileName.endsWith(".xml")) {
                            rs = new RepnSignature(veoDir, fileName, schemaDir, results);
                            veoHistorySignatures.add(rs);
                        } else if (fileName.startsWith("Report") && fileName.endsWith(".html")) {
                            /* ignore */
                        } else if (fileName.equals("index.html")) {
                            /* ignore */
                        } else if (fileName.equals("Report.css")) {
                            /* ignore */
                        } else {
                            addWarning(new VEOError(classname, "constRepn", 2, "Unexpected file in VEO directory: " + fileName));
                        }
                }
            }
        } catch (DirectoryIteratorException e) {
            throw new VEOError(classname, "constRepn", 2, "Directory iterator failed", e);
        } catch (IOException e) {
            throw new VEOError(classname, "constRepn", 3, "Failed to open the VEO directory for reading files", e);
        } finally {
            if (ds != null) {
                try {
                    ds.close();
                } catch (IOException e) {
                    LOG.log(Level.WARNING, VEOError.errMesg(classname, "constRepn", 4, "Closing directory stream failed", e));
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
                throw new VEOError(classname, "deleteVEO", 1, "IOException deleting VEO directory", e);
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
     * @param ltsfs List of valid long term sustainable formats
     * @param noRec true if not to complain about missing recommended metadata
     * elements
     * @param vpa true if being called from VPA & limit some tests
     * @throws VERSCommon.VEOError if prevented from continuing processing this
     * VEO
     */
    public final void validate(LTSF ltsfs, boolean noRec, boolean vpa) throws VEOError {
        int i;

        // check to see that the required files are present
        if (veoContent == null) {
            addError(new VEOError(classname, "validate", 1, "VEOContents.xml file is not present"));
        } else {
            veoContent.validate(veoDir, contentFiles, ltsfs, noRec, vpa);
        }
        if (veoHistory == null) {
            addError(new VEOError(classname, "validate", 2, "VEOHistory.xml file is not present"));
        } else {
            veoHistory.validate();
        }
        if (!veoReadmePresent) {
            addError(new VEOError(classname, "validate", 3, "VEOReadme.txt file is not present"));
        }
        if (veoContentSignatures.isEmpty()) {
            addError(new VEOError(classname, "validate", 4, "No VEOContentSignature?.xml files are present"));
        } else {
            for (i = 0; i < veoContentSignatures.size(); i++) {
                veoContentSignatures.get(i).validate();
            }
        }
        if (veoHistorySignatures.isEmpty()) {
            addError(new VEOError(classname, "validate", 5, "No VEOHistorySignature?.xml files are present"));
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
     * Private function to unzip a VEO file. When unzipping, we follow the
     * advice in the PKWARE application developer notes
     * https://support.pkware.com/home/pkzip/developer-tools/appnote/application-developer-considerations
     * These recommend checking that the file name in the ZIP entries doesn't
     * point to arbitrary locations in the file system (especially containing
     * '..') and that the file sizes are reasonable.
     *
     * @param zipFilePath the path to the VEO file
     * @throws VEOError
     * @throws IOException
     */
    private void unzip(Path zipFilePath) throws VEOError {
        ZipFile zipFile;
        Enumeration entries;
        ZipArchiveEntry entry;
        Path vze, zipEntryPath, p;
        InputStream is;
        BufferedInputStream bis;
        FileOutputStream fos;
        BufferedOutputStream bos;
        byte[] b = new byte[1024];
        int len, i;
        String veoName;
        long modTime;
        boolean complainedOnceAlready;
        String s;

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
            i = veoName.lastIndexOf(".");
            if (i != -1) {
                veoName = veoName.substring(0, i);
            }

            // open the zip file and get the entries in it
            zipFile = new ZipFile(zipFilePath.toFile());

            // be paranoid, just check that the supposed length of the
            // ZIP entry against the length of the ZIP file itself
            entries = zipFile.getEntries();
            long zipFileLength = zipFilePath.toFile().length();
            long claimedLength = 0;
            while (entries.hasMoreElements()) {
                entry = (ZipArchiveEntry) entries.nextElement();
                claimedLength += entry.getCompressedSize();
            }
            if (zipFileLength < claimedLength) {
                s = "ZIP file length (" + zipFileLength + ") is less than the sum of the compressed sizes of the ZIP entrys (" + claimedLength + ")";
                if (results != null) {
                    results.recordResult(Type.ERROR, s, null, null);
                }
                throw new VEOError(classname, "unzip", 1, s);
            }

            // go through each entry
            entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                entry = (ZipArchiveEntry) entries.nextElement();
                LOG.log(Level.FINE, "Extracting: {0}({1}) {2} {3}", new Object[]{entry.getName(), entry.getSize(), entry.getTime(), entry.isDirectory()});

                // get the local path to extract the ZIP entry into
                // this is so horrible because Paths.resolve won't process
                // windows file separators in a string on Unix boxes
                String safe = entry.getName().replaceAll("\\\\", "/");
                try {
                    zipEntryPath = Paths.get(safe);
                } catch (InvalidPathException ipe) {
                    throw new VEOError(classname, "unzip", 2, "ZIP path entry '" + safe + "' is invalid", ipe);
                }

                // complain (once!) if filename of the VEO is different to the
                // base of the filenames in the ZIP file (e.g. the VEO file has
                // been renamed)
                if (!veoName.equals(zipEntryPath.getName(0).toString())) {
                    if (!complainedOnceAlready) {
                        s = "The filename of the VEO (" + veoName + ") is different to that contained in the entries in the ZIP file (" + entry.getName() + ")";
                        addWarning(new VEOError(classname, "unzip", 3, s));
                        if (results != null) {
                            results.recordResult(Type.WARNING, s, null, null);
                        }
                    }
                    complainedOnceAlready = true;
                }

                // doesn't matter what the ZIP file says, force the extract to
                // be in a directory with the same name as the VEO filename
                // (even if we have complained about this)
                try {
                    if (zipEntryPath.getNameCount() == 1) {
                        p = veoDir.getParent().resolve(veoName);
                    } else {
                        zipEntryPath = zipEntryPath.subpath(1, zipEntryPath.getNameCount());
                        p = veoDir.getParent().resolve(veoName).resolve(zipEntryPath);
                    }
                } catch (InvalidPathException ipe) {
                    throw new VEOError(classname, "unzip", 4, "File name '" + veoName + "' is invalid", ipe);
                }

                // where does the file name in the ZIP entry really point to?
                vze = p.normalize();

                // be really, really, paranoid - the file we are creating
                // shouldn't have any 'parent' ('..') elements in the file path
                for (i = 0; i < vze.getNameCount(); i++) {
                    if (vze.getName(i).toString().equals("..")) {
                        s = "ZIP file contains a pathname that includes '..' elements: '" + zipEntryPath + "'";
                        if (results != null) {
                            results.recordResult(Type.ERROR, s, null, null);
                        }
                        throw new VEOError(classname, "unzip", 5, s);
                    }
                }

                // just be cynical and check that the file name to be extracted
                // from the ZIP file is actually in the VEO directory...
                if (!vze.startsWith(veoDir)) {
                    s = "ZIP entry in VEO '" + veoName + "' is attempting to create a file outside the VEO directory '" + vze.toString();
                    if (results != null) {
                        results.recordResult(Type.ERROR, s, null, null);
                    }
                    throw new VEOError(classname, "unzip", 6, s);
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
            s = "ZIP format error in opening Zip file"+ e.getMessage();
            if (results != null) {
                results.recordResult(Type.ERROR, s, null, null);
            }
            throw new VEOError(classname, "unzip", 7, s);
        } catch (IOException e) {
            throw new VEOError(classname,"unzip", 8, "IO error reading Zip file", e);
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
                LOG.log(Level.WARNING, VEOError.errMesg(classname, "unzip", 9, "IOException in closing Zip files", e));
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
     * @param returnErrors if true return errors, otherwise return warnings
     * @param l list in which to place the errors/warnings
     */
    @Override
    public void getProblems(boolean returnErrors, List<VEOError> l) {
        int i;
        
        super.getProblems(returnErrors, l);
        if (veoContent != null) {
            veoContent.getProblems(returnErrors, l);
        }
        if (veoHistory != null) {
            veoHistory.getProblems(returnErrors, l);
        }
        if (readme != null) {
            readme.getProblems(returnErrors, l);
        }
        for (i = 0; i < veoContentSignatures.size(); i++) {
            veoContentSignatures.get(i).getProblems(returnErrors, l);
        }
        for (i = 0; i < veoHistorySignatures.size(); i++) {
            veoHistorySignatures.get(i).getProblems(returnErrors, l);
        }
        for (i = 0; i < contentDirs.size(); i++) {
            contentDirs.get(i).getProblems(returnErrors, l);
        }
    }

    /**
     * Build a list of all of the errors generated by this RepnVEO and its
     * children.
     *
     * @return String containing the concatenated error list
     */
    @Override
    public void getMesgs(boolean returnErrors, List<String> l) {
        int i;
        
        super.getMesgs(returnErrors, l);
        if (veoContent != null) {
            veoContent.getMesgs(returnErrors, l);
        }
        if (veoHistory != null) {
            veoHistory.getMesgs(returnErrors, l);
        }
        if (readme != null) {
            readme.getMesgs(returnErrors, l);
        }
        for (i = 0; i < veoContentSignatures.size(); i++) {
            veoContentSignatures.get(i).getMesgs(returnErrors, l);
        }
        for (i = 0; i < veoHistorySignatures.size(); i++) {
            veoHistorySignatures.get(i).getMesgs(returnErrors, l);
        }
        for (i = 0; i < contentDirs.size(); i++) {
            contentDirs.get(i).getMesgs(returnErrors, l);
        }
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
     * Return a summary of the errors and warnings that occurred in the VEO.
     *
     * @return a String containing the errors and warnings
     */
    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        List<VEOError> l = new ArrayList<>();
        int i;

        // check for errors
        if (!hasErrors()) {
            sb.append("No errors detected\n");
        } else {
            sb.append("Errors detected:\n");
            getProblems(true, l);
            for (i=0; i<l.size(); i++) {
                sb.append("   Error: ");
                sb.append(l.get(i).getMessage());
                sb.append("\n");
            }
        }

        // check for warnings
        l.clear();
        sb.append("\n");
        if (!hasWarnings()) {
            sb.append("No warnings detected\n");
        } else {
            sb.append("Warnings detected:\n");
            getProblems(false, l);
            for (i=0; i<l.size(); i++) {
                sb.append("   Warning: ");
                sb.append(l.get(i).getMessage());
                sb.append("\n");
            }
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
     * @param pVersion The version of VEOAnalysis
     * @param copyright The copyright string
     * @throws VERSCommon.VEOError if prevented from continuing processing this
     * VEO
     */
    public void genReport(boolean verbose, String pVersion, String copyright) throws VEOError {
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
                LOG.log(Level.WARNING, VEOError.errMesg(classname, "genReport", 1, "Copying ReportStyle.css file to VEO directory failed", e));
            }
        } else {
            LOG.log(Level.WARNING, VEOError.errMesg(classname, "genReport", 2, "File: '" + cssSource.toAbsolutePath().toString() + "' doesn't exist"));
        }

        // create index file
        createReport(veoDir, "index.html", "Report for " + veoDir.getFileName(), pVersion, copyright);

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
            veoContent.genReport(verbose, veoDir, pVersion, copyright);
            startDiv(veoContent, "VEOContent", null);
            addString("Report for ");
            addTag("<a href=\"./Report-VEOContent.html\">");
            addString("VEOContent.xml");
            addTag("</a>");
            endDiv();
        }

        if (veoHistory != null) {
            veoHistory.genReport(verbose, veoDir, pVersion, copyright);
            startDiv(veoHistory, "VEOHistory", null);
            addString("Report for ");
            addTag("<a href=\"./Report-VEOHistory.html\">");
            addString("VEOHistory.xml");
            addTag("</a>");
            endDiv();
        }

        for (i = 0; i < veoContentSignatures.size(); i++) {
            r = "VEOContentSignature" + Integer.toString(i + 1);
            veoContentSignatures.get(i).genReport(verbose, veoDir, r + ".xml", pVersion, copyright);
            startDiv(veoContentSignatures.get(i), "VEOContentSig", null);
            addString("Report for ");
            addTag("<a href=\"./Report-" + r + ".html\">");
            addString(r + ".xml");
            addTag("</a>");
            endDiv();
        }

        for (i = 0; i < veoHistorySignatures.size(); i++) {
            r = "VEOHistorySignature" + Integer.toString(i + 1);
            veoHistorySignatures.get(i).genReport(verbose, veoDir, r + ".xml", pVersion, copyright);
            startDiv(veoHistorySignatures.get(i), "VEOHistorySig", null);
            addString("Report for ");
            addTag("<a href=\"./Report-" + r + ".html\">");
            addString(r + ".xml");
            addTag("</a>");
            endDiv();
        }

        for (i = 0; i < contentDirs.size(); i++) {
            r = contentDirs.get(i).getFileName();
            contentDirs.get(i).genReport(verbose, veoDir, r, pVersion, copyright);
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
                readme.genReport(verbose, w);
                addTag("</ul>\n");
            }
            endDiv();
        }

        endDiv();
        finishReport();
    }
}
