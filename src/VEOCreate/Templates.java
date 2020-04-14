/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOCreate;

import VERSCommon.VEOFatal;
import VERSCommon.VEOError;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manage a database of Metadata Package templates. Each templates is physically
 * represented as a text (.txt) file. The name of the template is the filename
 * of the file without the final '.txt'.
 * <p>
 * A template file contains the <i>contents</i> of a metadata package.
 * <p>
 * The first line of a template file contains the schema identifier (a URI)
 * and the syntax identifier (another URI) separated by a tab character. See
 * the specification for valid identifiers. <i>Even if the template continues
 * another template, put the two URIs in. They will be ignored.</i>
 * <p>
 * The remaining lines of a template file contains XML text that will be
 * included explicitly in each
 * VEO, and substitutions. The start of each substitution is marked by '$$' and
 * the end by '$$'. Possible substitutions are:
 * <ul>
 * <li>
 * $$ date $$ - substitute the current date and time in VERS format</li>
 * <li>
 * $$ [column] &gt;x&gt; $$ - substitute the contents of column (array element)
 * &lt;x&gt;. Note that keyword 'column' is optional.</li>
 * <li>
 * $$ file utf8|xml [column] &lt;x&gt; $$ - include the contents of the file
 * specified in column (array element) &lt;x&gt;. The file is encoded
 * depending on the second keyword: a 'binary' file is encoded in Base64; a
 * 'utf8' file has the
 * characters &lt;, &gt;, and &amp; encoded; and an 'xml' file is included as
 * is. Note that keyword 'column' is optional.</li>
 * </ul>
 * A metadata package may be composed of several template files strung together.
 */
public class Templates {

    private final static Logger log = Logger.getLogger("veocreate.Templates");
    private final static String classname = "Templates"; // for reporting
    HashMap<String, Fragment> templates; //collection of templates

    /**
     * This constructor just initialises an empty template database. Templates
     * are added to the database by subsequent calls to the
     * readTemplateDirectory() and readTemplateFile() methods.
     */
    public Templates() {
        initTemplates();
    }

    /**
     * This constructor reads a set of template files from a template directory.
     * Additional template files can be added by subsequent calls to the 
     * readTemplateDirectory() and readTemplateFile() methods.
     *
     * @param templateDir the directory in which the templates are to be found
     * @throws VEOFatal if the template directory doesn't exist, or if an
     * IOException occurs
     */
    public Templates(Path templateDir) throws VEOFatal {
        initTemplates();
        readTemplateDirectory(templateDir);
    }

    /**
     * Initialise the templates database.
     */
    private void initTemplates() {
        log.setLevel(null);
        templates = new HashMap<>();
    }

    /**
     * Read a directory of templates. A template directory consists of a set of
     * .txt files. Each txt file is one template, which is referenced by the
     * file name (without the trailing '.txt'). An error will be thrown if a
     * template is already defined.
     * <p>
     * This method can be called multiple times to build up a large database.
     * <p>
     * For convenience, the 'Readme.txt' can be in the template directory. This
     * file is ignored.
     *
     * @param templateDir the directory in which the templates are to be found
     * @throws VEOFatal if the template directory doesn't exist, or if an
     * IOException occurs
     */
    final protected void readTemplateDirectory(Path templateDir) throws VEOFatal {
        DirectoryStream<Path> ds; // iterator for template directory
        Path template;  // template file to read
        Fragment f;     // generated fragment
        String key;     // key (i.e. name of file with stripped '.txt'
        int j;

        if (!Files.exists(templateDir)) {
            throw new VEOFatal(classname, 1,
                    "Encoding template directory '" + templateDir.toAbsolutePath().toString() + "' does not exist");
        }

        // go through encoding template directory, parsing encoding templates
        ds = null;
        try {
            // get a list of files in the content directory
            ds = Files.newDirectoryStream(templateDir);

            // go through list and for each file
            for (Path p : ds) {
                // log.log(Level.INFO, "reading template: ''{0}''", p.toString());
                template = p.getFileName();

                // ignore VERSReadMe.txt
                if (template.getFileName().toString().equals("VEOReadme.txt")) {
                    continue;
                }

                // ignore anything that doesn't end in .txt
                if (!template.getFileName().toString().endsWith(".txt")) {
                    continue;
                }

                readTemplateFile(p);
            }
        } catch (IOException e) {
            throw new VEOFatal(classname, 2, e.toString());
        } finally {
            try {
                if (ds != null) {
                    ds.close();
                }
            } catch (IOException e) {
                /* ignore */
            }
        }
    }

    /**
     * Read a template from a file. The template name is the part of the
     * filename before the '.'. If there is no '.' in the filename, the entire
     * filename is used. If there are multiple '.' in the filename, the portion
     * upto the first is used. If there is no portion before the '.', the
     * template is silently ignored.
     * 
     * @param file the file containing the template
     * @throws VERSCommon.VEOFatal if parsing the template fails, or the
     * template name already exists in the database.
     */
    final protected void readTemplateFile(Path file) throws VEOFatal {
        Path template;
        Fragment f;     // generated fragment
        String key;     // key (i.e. name of file with stripped '.txt'
        int j;
        // log.log(Level.INFO, "reading template: ''{0}''", p.toString());
        template = file.getFileName();

        // Hashtable key is the part of the filename in
        // advance of the '.' (if any)
        // get name of template (leading part of filename)
        // if no '.' in filename, use the entire file name
        // if nothing before '.', ignore this template
        j = template.getFileName().toString().indexOf('.');
        switch (j) {
            case -1:
                key = template.getFileName().toString();
                break;
            case 0: // file name is just '.ext'
                return;
            default:
                try {
                    key = template.getFileName().toString().substring(0, j);
                } catch (IndexOutOfBoundsException ie) {
                    return; /* ignore, cannot happen */
                }
                break;
        }

        // see if the template already exists
        if (templates.get(key) != null) {
            throw new VEOFatal(classname, 1, "Template '" + key + "' already exists in database");
        }

        // parse template
        f = Fragment.parseTemplate(file.toFile());
        if (f == null) {
            return;
        }

        // put in database
        // log.log(Level.INFO, "adding template: ''{0}''", key);
        templates.put(key, f);
    }

    /**
     * Return the template with a given identifier. The template is returned as
     * as a Fragment. Null is returned if no template is found.
     *
     * @param id identifier to find
     * @return the starting fragment of the template
     * @throws VEOError if the id parameter is null
     * @throws VEOFatal if the templates database has not been initialised
     */
    public Fragment findTemplate(String id) throws VEOError {
        String method = "findTemplate";
        Fragment f;

        // sanity check
        if (id == null) {
            throw new VEOError(classname, method, 1, "id parameter is null");
        }
        if (templates == null) {
            throw new VEOFatal(classname, method, 1, "templates database has not been initialised.");
        }

        // try to retrieve Fragment identified by this id
        f = templates.get(id);
        if (f == null) {
            throw new VEOError(classname, method, 1, "template '" + id + "' not found");
        }
        log.log(Level.FINE, "Found template. Schema ''{0}'' Syntax ''{1}''", new Object[]{f.getSchemaId(), f.getSyntaxId()});
        return f;
    }
}
