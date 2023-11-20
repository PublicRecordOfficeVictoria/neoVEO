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
import VERSCommon.VEOFatal;
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
class RepnVEO extends AnalysisBase {

    private static final String CLASSNAME = "RepnVEO";
    private Path veo;               // VEO to process
    private Path schemaDir;         // directory in which XML schemas are to be found
    private Path veoOutputDir;      // directory in which the VEO components can be examined
    private RepnItem readme;        // issues with the VEOReadme.txt file
    private RepnVEOContent veoContent; // The representation of the VEOContent.xml file
    private RepnHistory veoHistory; // The representation of the VEOHistory.xml file
    private ArrayList<RepnSignature> veoContentSignatures; // The representation of the signature files
    private ArrayList<RepnSignature> veoHistorySignatures; // The representation of the signature files
    private ArrayList<RepnFile> contentDirs; // list of content directories
    private HashMap<Path, RepnFile> contentFiles;   // Collection of content files in the VEO
    private boolean veoReadmePresent; // true if VEOReadme.txt file is present
    private final static Logger LOG = Logger.getLogger("VEOAnalysis.RepnVEO");

    /**
     * Construct a VEO from a zip file.
     *
     * @param schemaDir the directory containing the VERS schema information
     * (must not be null)
     * @param veo the zip file containing the VEO (must not be null)
     * @param debug true if more detail is to be generated
     * @param output the directory in which to extract the VEO (must not be
     * null)
     * @param results the results summary to build (may be null)
     * @throws VEOError if a system error prevents opening the VEO
     */
    public RepnVEO(Path schemaDir, Path veo, boolean debug, Path output, ResultSummary results) throws VEOError, VEOFatal {
        super("", results);

        int i;
        String s, safe;
        Path p;

        assert (schemaDir != null);
        assert (veo != null);
        assert (output != null);

        // initialise
        this.veo = veo;
        this.schemaDir = schemaDir;
        veoOutputDir = null;
        readme = new RepnItem(id + "Readme.txt", "", results);
        veoContent = null;
        veoHistory = null;
        veoContentSignatures = new ArrayList<>();
        veoHistorySignatures = new ArrayList<>();
        contentDirs = new ArrayList<>();
        contentFiles = new HashMap<>();
        veoReadmePresent = false;

        // check if schema directory exists...
        if (!Files.exists(schemaDir)) {
            throw new VEOFatal(CLASSNAME, 1, "Schema directory '" + schemaDir.toString() + "' does not exist");
        }

        // check if VEO exists
        safe = veo.toString().replaceAll("\\\\", "/");
        if (!Files.exists(veo)) {
            throw new VEOError(CLASSNAME, 2, "VEO file name '" + safe + "' does not exist");
        }

        // get VEO directory name
        s = veo.getFileName().toString();
        if ((i = s.lastIndexOf(".zip")) == -1) {
            addWarning(new VEOFailure(CLASSNAME, 3, "VEO file name '" + safe + "' does not end in '.zip'"));
        } else {
            s = s.substring(0, i);
        }
        try {
            veoOutputDir = output.resolve(s).normalize();
        } catch (InvalidPathException ipe) {
            throw new VEOError(CLASSNAME, 4, "VEO name is invalid as a directory'" + s + "' is invalid: " + ipe.getMessage());
        }

        // delete the VEO directory (if it exists)
        deleteVEO();

        // Configure the logging used in the RDF validator. See the discussion
        // RepnMetadataPackage for which version you should use. Uncomment the
        // line for the version of log4j that you wish to use
        // This is for log4j2 used with Jena 4
        // p = schemaDir.resolve("log4j2.properties");
        // if (!Files.exists(p)) {
        //     throw new VEOError(CLASSNAME, 5, "Log4j properties file '" + p.toString() + "' does not exist");
        // }
        // System.setProperty("log4j2.configurationFile", schemaDir.resolve("log4j2.properties").toAbsolutePath().toString());
        // This is for log4j used with Jena 2
        p = schemaDir.resolve("log4j.properties");
        if (!Files.exists(p)) {
            throw new VEOFatal(CLASSNAME, 5, "Log4j properties file '" + p.toString() + "' does not exist");
        }
        PropertyConfigurator.configure(p.toAbsolutePath().toString());

        objectValid = true;
    }

    /**
     * Delete the VEO directory. Typically called after the VEO has been
     * processed.
     *
     * @throws VEOError if an error occurred when deleting the VEO.
     */
    public final void deleteVEO() throws VEOError {
        if (veoOutputDir != null && Files.exists(veoOutputDir)) {
            try {
                deleteFile(veoOutputDir);
            } catch (IOException e) {
                throw new VEOError(CLASSNAME, "deleteVEO", 1, "IOException deleting VEO directory", e);
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
            LOG.log(Level.WARNING, "{0} Failed to delete temporary file: ", e.toString());
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
        veoOutputDir = null;
        if (veoContent != null) {
            veoContent.abandon();
            veoContent = null;
        }
        if (veoHistory != null) {
            veoHistory.abandon();
            veoHistory = null;
        }
        if (readme != null) {
            readme.abandon();
            readme = null;
        }
        for (i = 0; i < veoContentSignatures.size(); i++) {
            veoContentSignatures.get(i).abandon();
        }
        veoContentSignatures.clear();
        veoContentSignatures = null;
        for (i = 0; i < veoHistorySignatures.size(); i++) {
            veoHistorySignatures.get(i).abandon();
        }
        veoHistorySignatures.clear();
        veoHistory = null;
        for (i = 0; i < contentDirs.size(); i++) {
            contentDirs.get(i).abandon();
        }
        contentDirs.clear();
        contentDirs = null;
        contentFiles.clear();
        contentFiles = null;
    }

    /**
     * Construct an internal representation of the VEO ready for validation
     *
     * @throws VEOError If an error occurred in processing this VEO
     */
    // this array contains the valid lengths of the VEOReadMe.txt over time
    static int[] expVEOReadmeSize = {4746, 4840, 5061, 5062};

    public boolean constructRepn() throws VEOError {
        String fileName;
        RepnSignature rs;
        DirectoryStream<Path> ds;
        int i;
        StringBuilder sb;

        // check that the VEO directory has the correct files (and no others)
        // System.out.println("validating " + veoDir.toString());
        // unzip the VEO into the VEO directory
        unzip(veo);

        // did it unpack?
        if (!Files.exists(veoOutputDir)) {
            return false;
        }

        // go through the VEO directory constructing the internal represententation
        ds = null;
        try {
            ds = Files.newDirectoryStream(veoOutputDir);
            for (Path entry : ds) {
                if (Files.isDirectory(entry)) {
                    contentDirs.add(new RepnFile(entry, veoOutputDir, contentFiles, results));
                    continue;
                }
                fileName = entry.getFileName().toString();
                switch (fileName) {
                    case ".":
                    case "..":
                        break;
                    case "VEOContent.xml":
                        veoContent = new RepnVEOContent(veoOutputDir, schemaDir, contentFiles, results);
                        break;
                    case "VEOHistory.xml":
                        veoHistory = new RepnHistory(veoOutputDir, schemaDir, results);
                        break;
                    case "VEOReadme.txt":

                        // check that the length of the VEOReadme.txt file is
                        // one of the valid lengths, if not complain.
                        for (i = 0; i < expVEOReadmeSize.length; i++) {
                            if (Files.size(entry) == expVEOReadmeSize[i]) {
                                break;
                            }
                        }
                        if (i == expVEOReadmeSize.length) {
                            sb = new StringBuilder();
                            for (i = 0; i < expVEOReadmeSize.length; i++) {
                                sb.append(expVEOReadmeSize[i]);
                                if (i == expVEOReadmeSize.length - 2) {
                                    sb.append(", or ");
                                } else if (i < expVEOReadmeSize.length - 1) {
                                    sb.append(", ");
                                }
                            }
                            readme.addWarning(new VEOFailure(CLASSNAME, "constRepn", 3, "VEOReadme.txt has an unexpected size (" + Files.size(entry) + ") instead of the valid values of " + sb.toString()));
                        }
                        veoReadmePresent = true;
                        break;
                    default:
                        if (fileName.startsWith("VEOContentSignature") && fileName.endsWith(".xml")) {
                            rs = new RepnSignature(veoOutputDir, fileName, schemaDir, results);
                            veoContentSignatures.add(rs);
                        } else if (fileName.startsWith("VEOHistorySignature") && fileName.endsWith(".xml")) {
                            rs = new RepnSignature(veoOutputDir, fileName, schemaDir, results);
                            veoHistorySignatures.add(rs);
                        } else if (fileName.startsWith("Report") && fileName.endsWith(".html")) {
                            /* ignore */
                        } else if (fileName.equals("index.html")) {
                            /* ignore */
                        } else if (fileName.equals("Report.css")) {
                            /* ignore */
                        } else {
                            addWarning(new VEOFailure(CLASSNAME, "constRepn", 4, "Unexpected file in VEO directory: " + fileName));
                        }
                }
            }
        } catch (DirectoryIteratorException e) {
            LOG.log(Level.WARNING, VEOFailure.getMessage(CLASSNAME, "constRepn", 5, "Directory iterator failed", e));
        } catch (IOException e) {
            addError(new VEOFailure(CLASSNAME, "constRepn", 6, "Failed to open a file in the VEO for reading", e));
        } finally {
            if (ds != null) {
                try {
                    ds.close();
                } catch (IOException e) {
                    LOG.log(Level.WARNING, VEOFailure.getMessage(CLASSNAME, "constRepn", 7, "Closing directory stream failed", e));
                }
            }
        }
        return true;
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
        boolean secErr = false;

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
                addError(new VEOFailure(CLASSNAME, "unzip", 1, "ZIP file length (" + zipFileLength + ") is less than the sum of the compressed sizes of the ZIP entrys (" + claimedLength + ")"));
                return;
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
                    addError(new VEOFailure(CLASSNAME, "unzip", 2, "ZIP path entry '" + safe + "' is invalid", ipe));
                    continue;
                }

                // complain (once!) if filename of the VEO is different to the
                // base of the filenames in the ZIP file (e.g. the VEO file has
                // been renamed)
                if (!veoName.equals(zipEntryPath.getName(0).toString())) {
                    if (!complainedOnceAlready) {
                        if (zipEntryPath.getNameCount() == 1) {
                            addError(new VEOFailure(CLASSNAME, "unzip", 3, "The names of the entries in the ZIP file (e.g. '" + entry.getName() + "') do not start with the name of the veo ('" + veoName + "')"));
                        } else {
                            addError(new VEOFailure(CLASSNAME, "unzip", 4, "The filename of the VEO (" + veoName + ") is different to that contained in the entries in the ZIP file (" + entry.getName() + ")"));
                        }
                    }
                    complainedOnceAlready = true;
                    zipEntryPath = Paths.get(veoName).resolve(zipEntryPath);
                }

                // doesn't matter what the ZIP file says, force the extract to
                // be in a directory with the same name as the VEO filename
                // (even if we have complained about this)
                try {
                    if (zipEntryPath.getNameCount() > 1) {
                        zipEntryPath = zipEntryPath.subpath(1, zipEntryPath.getNameCount());
                    }
                    p = veoOutputDir.getParent().resolve(veoName).resolve(zipEntryPath);
                } catch (InvalidPathException ipe) {
                    addError(new VEOFailure(CLASSNAME, "unzip", 5, "File name '" + veoName + "' is invalid", ipe));
                    continue;
                }

                // where does the file name in the ZIP entry really point to?
                vze = p.normalize();

                // be really, really, paranoid - the file we are creating
                // shouldn't have any 'parent' ('..') elements in the file path
                for (i = 0; i < vze.getNameCount(); i++) {
                    if (vze.getName(i).toString().equals("..")) {
                        addError(new VEOFailure(CLASSNAME, "unzip", 6, "ZIP file contains a pathname that includes '..' elements: '" + zipEntryPath + "'"));
                        secErr = true;
                    }
                }
                if (secErr) {
                    continue;
                }

                // just be cynical and check that the file name to be extracted
                // from the ZIP file is actually in the VEO directory...
                if (!vze.startsWith(veoOutputDir)) {
                    addError(new VEOFailure(CLASSNAME, "unzip", 7, "ZIP entry in VEO '" + veoName + "' is attempting to create a file outside the VEO directory '" + vze.toString()));
                    continue;
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
            addError(new VEOFailure(CLASSNAME, "unzip", 8, "ZIP format error in opening Zip file" + e.getMessage()));
        } catch (IOException e) {
            addError(new VEOFailure(CLASSNAME, "unzip", 9, "IO error reading Zip file", e));
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
                LOG.log(Level.WARNING, VEOFailure.getMessage(CLASSNAME, "unzip", 10, "IOException in closing Zip files", e));
            }
        }
    }

    /**
     * Validate the data in the RepnVEO.
     *
     * @param ltsfs List of valid long term sustainable formats (must not be
     * null)
     * @param noRec true if not to complain about missing recommended metadata
     * elements
     * @param vpa true if being called from VPA & limit some tests
     * @throws VERSCommon.VEOError if prevented from continuing processing this
     * VEO
     */
    public final void validate(LTSF ltsfs, boolean noRec, boolean vpa) throws VEOError {
        int i;

        if (ltsfs == null) {
            throw new VEOError(CLASSNAME, "validate", 1, "List of valid long term sustainable formats is null");
        }
        if (veoOutputDir == null) {
            throw new VEOError(CLASSNAME, "validate", 2, "veoDir is null");
        }

        // check to see that the required files are present
        // is the VEOContent file present? If so validate it, and then the content files
        if (veoContent == null) {
            addError(new VEOFailure(CLASSNAME, "validate", 3, "VEOContent.xml file is not present"));
        } else {
            veoContent.validate(veoOutputDir, contentFiles, ltsfs, noRec, vpa);
            for (i = 0; i < contentDirs.size(); i++) {
                contentDirs.get(i).validate();
            }
        }
        if (veoHistory == null) {
            addError(new VEOFailure(CLASSNAME, "validate", 4, "VEOHistory.xml file is not present"));
        } else {
            veoHistory.validate();
        }
        if (!veoReadmePresent) {
            addError(new VEOFailure(CLASSNAME, "validate", 5, "VEOReadme.txt file is not present"));
        }
        if (veoContentSignatures.isEmpty()) {
            addError(new VEOFailure(CLASSNAME, "validate", 6, "No VEOContentSignature?.xml files are present"));
        } else if (veoContent != null) {
            for (i = 0; i < veoContentSignatures.size(); i++) {
                veoContentSignatures.get(i).validate();
            }
        }
        if (veoHistorySignatures.isEmpty()) {
            addError(new VEOFailure(CLASSNAME, "validate", 7, "No VEOHistorySignature?.xml files are present"));
        } else if (veoHistory != null) {
            for (i = 0; i < veoHistorySignatures.size(); i++) {
                veoHistorySignatures.get(i).validate();
            }
        }
    }

    /**
     * Return the VEO directory
     *
     * @return the VEO directory
     */
    public Path getVEODir() {
        return veoOutputDir;
    }

    /**
     * Get a unique id for this VEO (we use the first content signature)
     *
     * @return the first content signature, or null if not defined
     */
    public String getUniqueId() {
        if (veoContentSignatures != null && veoContentSignatures.size() >= 1) {
            return veoContentSignatures.get(0).getSignature();
        } else {
            return null;
        }
    }

    /**
     * Return the number of IOs in this VEO
     *
     * @return
     */
    public int getIOCount() {
        return veoContent != null ? veoContent.getIOCount() : 0;
    }

    /**
     * Has this RepnVEO (or its children) any errors?
     *
     * @return true if errors have been detected
     */
    @Override
    public boolean hasErrors() {
        int i;
        boolean hasErrors = false;

        if (readme != null) {
            hasErrors |= readme.hasErrors();
        }
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
     * Has this RepnVEO (or its children) any warnings?
     *
     * @return true if errors have been detected
     */
    @Override
    public boolean hasWarnings() {
        int i;
        boolean hasWarnings = false;

        if (readme != null) {
            hasWarnings |= readme.hasWarnings();
        }
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
     * Build a list of all of the errors generated by this RepnVEO and its
     * children.
     *
     * @param returnErrors if true return errors, otherwise return warnings
     * @param l list in which to place the errors/warnings
     */
    @Override
    public void getProblems(boolean returnErrors, List<VEOFailure> l) {
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
        sb.append(veoOutputDir != null ? veoOutputDir.toString() : "null");
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
     * @param pVersion The version of VEOAnalysis (not null)
     * @param copyright The copyright string (not null)
     * @throws VERSCommon.VEOError if prevented from continuing processing this
     * VEO
     */
    public void genReport(boolean verbose, String pVersion, String copyright) throws VEOError {
        int i;
        String r;
        Path cssSource, cssDest;

        // sanity...
        if (veoOutputDir == null) {
            throw new VEOError(CLASSNAME, "genReport", 1, "veoDir is null");
        }
        if (pVersion == null) {
            throw new VEOError(CLASSNAME, "genReport", 2, "pVersion is null");
        }
        if (copyright == null) {
            throw new VEOError(CLASSNAME, "genReport", 3, "copyright is null");
        }

        // copy in CSS file from schema directory
        cssSource = schemaDir.resolve("ReportStyle.css");
        cssDest = veoOutputDir.resolve("ReportStyle.css");
        if (Files.exists(cssSource)) {
            try {
                Files.copy(cssSource, cssDest, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOG.log(Level.WARNING, VEOFailure.getMessage(CLASSNAME, "genReport", 1, "Copying ReportStyle.css file to VEO directory failed", e));
            }
        } else {
            LOG.log(Level.WARNING, VEOFailure.getMessage(CLASSNAME, "genReport", 2, "File: '" + cssSource.toAbsolutePath().toString() + "' doesn't exist"));
        }

        // create index file
        createReport(veoOutputDir, "index.html", "Report for " + veoOutputDir.getFileName(), pVersion, copyright);

        // check for errors and warnings
        hasErrors();
        hasWarnings();

        // generate the report
        startDiv("VEO", null);
        addLabel("VEO");
        if (hasErrors() || hasWarnings()) {
            addTag("<ul>\n");
            listIssues();
            addTag("</ul>\n");
        }

        if (veoContent != null) {
            veoContent.genReport(verbose, veoOutputDir, pVersion, copyright);
            startDiv(veoContent, "VEOContent", null);
            addString("Report for ");
            addTag("<a href=\"./Report-VEOContent.html\">");
            addString("VEOContent.xml");
            addTag("</a>");
            endDiv();
        }

        if (veoHistory != null) {
            veoHistory.genReport(verbose, veoOutputDir, pVersion, copyright);
            startDiv(veoHistory, "VEOHistory", null);
            addString("Report for ");
            addTag("<a href=\"./Report-VEOHistory.html\">");
            addString("VEOHistory.xml");
            addTag("</a>");
            endDiv();
        }

        for (i = 0; i < veoContentSignatures.size(); i++) {
            r = "VEOContentSignature" + Integer.toString(i + 1);
            veoContentSignatures.get(i).genReport(verbose, veoOutputDir, r + ".xml", pVersion, copyright);
            startDiv(veoContentSignatures.get(i), "VEOContentSig", null);
            addString("Report for ");
            addTag("<a href=\"./Report-" + r + ".html\">");
            addString(r + ".xml");
            addTag("</a>");
            endDiv();
        }

        for (i = 0; i < veoHistorySignatures.size(); i++) {
            r = "VEOHistorySignature" + Integer.toString(i + 1);
            veoHistorySignatures.get(i).genReport(verbose, veoOutputDir, r + ".xml", pVersion, copyright);
            startDiv(veoHistorySignatures.get(i), "VEOHistorySig", null);
            addString("Report for ");
            addTag("<a href=\"./Report-" + r + ".html\">");
            addString(r + ".xml");
            addTag("</a>");
            endDiv();
        }

        for (i = 0; i < contentDirs.size(); i++) {
            r = contentDirs.get(i).getFileName();
            contentDirs.get(i).genReport(verbose, veoOutputDir, r, pVersion, copyright);
            startDiv(contentDirs.get(i), "ContentDir", null);
            addString("Report for ");
            addTag("<a href=\"./Report-" + r + ".html\">");
            addString(r);
            addTag("</a> content directory");
            endDiv();
        }

        if (veoReadmePresent && readme != null) {
            startDiv(readme, "VEOReadme", null);
            addString("View the ");
            addTag("<a href=\"./VEOReadme.txt\">");
            addString("VEOReadme.txt");
            addTag("</a>");
            addString(" file\n");
            if (readme.hasErrors() || readme.hasWarnings()) {
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
