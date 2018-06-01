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
 * Manage a database of templates.
 */
public class Templates {

    private final static Logger log = Logger.getLogger("veocreate.Templates");
    private final static String classname = "Templates"; // for reporting
    HashMap<String, Fragment> templates; //collection of templates
    Path templateDir;   // template directory

    /**
     * This constructor reads the template files from the template directory.
     *
     * @param templateDir the directory in which the templates are to be found
     * @throws VEOFatal if the template directory doesn't exist, or if an
     * IOException occurs
     */
    public Templates(Path templateDir) throws VEOFatal {
        DirectoryStream<Path> ds; // iterator for template directory
        Path template;  // template file to read
        Fragment f;     // generated fragment
        String key;     // key (i.e. name of file with stripped '.txt'
        int j;

        log.setLevel(null);
        
        // allocate hash table for encoding templates
        templates = new HashMap<>();

        this.templateDir = templateDir;
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

                // parse template
                f = Fragment.parseTemplate(p.toFile());
                if (f == null) {
                    continue;
                }
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
                    case 0:
                        continue;
                    default:
                        try {
                            key = template.getFileName().toString().substring(0, j);
                        } catch (IndexOutOfBoundsException ie) {
                            continue; /* ignore, cannot happen */
                            
                        }   break;
                }
                // put in hashtable
                // log.log(Level.INFO, "adding template: ''{0}''", key);
                templates.put(key, f);
            }
        } catch (IOException e) {
            throw new VEOFatal(classname, 1, "IOException: " + e.toString());
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
     * Return the template with a given identifier. Null is returned if no
     * template is found.
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
            throw new VEOError(classname, method, 1, "template '" + id + "' not found in template directory "+templateDir.toString());
        }
        log.log(Level.FINE, "Found template. Schema ''{0}'' Syntax ''{1}''", new Object[]{f.getSchemaId(), f.getSyntaxId()});
        return f;
    }
}
