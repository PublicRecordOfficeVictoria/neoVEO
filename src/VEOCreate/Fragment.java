/*
 * Copyright Public Record Office Victoria 2006 & 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOCreate;

import VERSCommon.VEOFatal;
import VERSCommon.VEOError;
import java.io.*;

/**
 * This class represents a fragment of a VEO. Warning: Do not directly call this
 * class. Use the Templates class.
 * <p>
 * A fragment might be a piece of static
 * text that is identical for all constructed VEOs, or it might be a piece of
 * dynamic content that will change, such as the current date and time.
 * <p>
 * A sequence of such fragments is called a template. Templates represent
 * metadata packages.
 * <p>
 * There are two steps in generating a VEO using fragments.
 * <p>
 * The first step is to construct a template (a list of fragments). This
 * contains holes into which dynamic content will be added and externally
 * referenced content (e.g. files) are inserted. The second step is to
 * 'finalise' the template. This resolves all the dynamic content (e.g. the
 * fragment saying 'insert current date/time' is replaced by the actual
 * date/time). This is then written to the actual VEO.
 * <p>
 * Templates may be constructed in two ways. Instances of the Fragment classes
 * may be constructed and manually linked together to form a template.
 * Alternatively, a text file containing a representation of the template can be
 * parsed to produce the appropriate list of Fragments.
 */
abstract public class Fragment {

    static String classname;  // for error reporting
    public Fragment next;     // link to the next fragment in the list
    protected String location;// location where this fragment was found
    final private String schemaId;  // the URI referencing the schema of this metadata package
    final private String syntaxId;  // the URI referencing the syntax of this metadata package

    /**
     * Construct an uninstantiated fragment.
     *
     * @param location location (file/line) where this fragment was generated.
     * @param uri URIs identifying syntax and semantics of fragment
     */
    public Fragment(String location, String[] uri) {
        classname = "Fragment";
        next = (Fragment) null;
        this.location = location;
        this.schemaId = uri[0];
        this.syntaxId = uri[1];
    }

    /**
     * This method is a factory that parses a file containing the template and
     * builds a list of Fragments.
     * <p>
     * A template is a text file that contains static text (typically XML) and
     * substitutions. The static text is copied explicitly into each VEO
     * generated. The substitutions represent dynamic content (e.g. the current
     * date and time). Each substitution is replaced by actual text when the VEO
     * is generated.
     * <p>
     * Valid substitutions are:<br>
     * <ul>
     * <li>
     * $$ date $$ - substitute the current date and time in VERS format</li>
     * <li>
     * $$ [column] &lt;&gt;x&gt; $$ - substitute the contents of column
     * &lt;x&gt; encoding XML characters</li>
     * </ul>
     * <p>
     * The parse will not stop if an error is encountered in parsing the
     * template. If a syntax error is encountered, a message is printed on
     * standard out and the parse continues.
     * <p>
     * This method will generate a VEOError in the following situations:
     * <ul>
     * <li>the template file cannot be opened, is a directory, or the cannonical
     * path cannot be generated.</li>
     * <li>an IO error occurred when reading a template file.</li>
     * </ul>
     *
     * @param template file containing the template
     * @return Fragment the head of the internal representation
     * @throws VEOFatal if an error occurs parsing the template
     *
     */
    static public Fragment parseTemplate(File template) throws VEOFatal {
        FileReader fr;
        LineNumberReader lnr;
        Fragment fs, fe, fn;
        int c, j;
        StringBuffer sb;
        String s, filename, location, tag = "$$";
        String[] uri;
        boolean stringFrag;
        String method = "parseTemplate";

        // check template file is not a directory
        if (template.isDirectory()) {
            try {
                s = template.getCanonicalPath();
            } catch (IOException ioe) {
                throw new VEOFatal(classname, method, 1,
                        "cannot get the canonical path of template file: "
                        + ioe.getMessage());
            }
            throw new VEOFatal(classname, method, 1,
                    "Template file '" + s + "' is a directory not a .txt file");
        }
        // open file & instantiate a line number reader with it
        try {
            fr = new FileReader(template);
        } catch (FileNotFoundException fnfe) {
            try {
                s = template.getCanonicalPath();
            } catch (IOException ioe) {
                throw new VEOFatal(classname, method, 1,
                        "cannot get the canonical path of template file: "
                        + ioe.getMessage());
            }
            throw new VEOFatal(classname, method, 1,
                    "Template file '" + s + "' does not exist");
        }
        lnr = new LineNumberReader(fr);
        filename = template.getName();

        // read template
        // the first line of the template contains two URIs separated by a tab.
        // The first is the schema identifier, the second is the syntax identifier
        try {
            uri = lnr.readLine().split("\t");
        } catch (IOException ioe) {
            throw new VEOFatal(classname, method, 1,
                    "IO Error reading template file " + filename
                    + " Line " + lnr.getLineNumber() + ". Error: "
                    + ioe.getMessage());
        }
        if (uri.length != 2) {
            throw new VEOFatal(classname, method, 1,
                    "IO Error reading template file " + filename
                    + " Line " + lnr.getLineNumber() + ". Error: First line must contain two URIs separated by a tab");
        }

        // go through each character in file. Put the characters in a string
        // buffer until a substitution flag is found ('$$'). If this is the
        // first substitution flag, the found characters are literal text.
        // If it is the second, the found characters are a substitution command
        // which is parsed. This continues (odd flags indicate literal
        // text, even substitutions.
        fs = null;
        fe = null;
        j = 0;
        stringFrag = true;
        sb = new StringBuffer();
        c = 0;
        while (c != -1) {
            try {
                c = lnr.read();
            } catch (IOException ioe) {
                throw new VEOFatal(classname, method, 1,
                        "IO Error reading template file " + filename
                        + " Line " + lnr.getLineNumber() + ". Error: "
                        + ioe.getMessage());
            }

            // if not at end of file, append char to buffer and check if
            // part of substitution tag
            if (c != -1) {
                sb.append((char) c);
                if (c == tag.charAt(j)) {
                    j++;
                } else {
                    j = 0;
                }
            }

            // found a start or end substitution tag or at end of file...
            if (j == tag.length() || c == -1) {

                // delete tag from end of string buffer
                if (j == tag.length()) {
                    sb.setLength(sb.length() - tag.length());
                }
                j = 0;

                location = "template " + filename + " (around line " + (lnr.getLineNumber() + 1) + ") ";

                // if found a start tag put characters out as
                // string fragment and next fragment is a substitution
                if (stringFrag) {
                    fn = new StringFragment(location, sb.toString(), uri);
                    sb.setLength(0);
                    stringFrag = false;

                    // found an end tag so work out what
                    // substitution we got, and next fragment is a string
                } else {
                    fn = parseSubstitution(location, sb, uri);
                    sb.setLength(0);
                    stringFrag = true;
                }

                // append new fragement (if any) to list...
                if (fn != null) {
                    if (fs == null) {
                        fe = fn;
                        fs = fe;
                    } else {
                        fe.next = fn;
                        fe = fe.next;
                    }
                }
            }
        }

        // close template
        try {
            fr.close();
        } catch (IOException ioe) { /* ignore */ }

        // return list of fragments from template
        return fs;
    }

    /**
     * Parse substitution for commands.
     *
     * This method parses a substitution to identify what type it is. The method
     * returns a Fragment containing the substitution. If no substitution is
     * found (or an error occurs) a null Fragment is returned.
     *
     * Valid substitutions are described in parseTemplate().
     *
     * @param errorLoc - the location (file/line) in which the substitution is
     * found
     * @param sb - the StringBuffer containing the substitution
     * @param args - an array of Strings used to construct the argument
     * @param schemaId - a URI identifying the schema of this metadata package
     * @param syntaxId - a URI identifying the syntax of this metadata package
     * substitution
     * @returns a Fragment, or null if an error occurred
     *
     */
    static private Fragment parseSubstitution(String errorLoc, StringBuffer sb, String[] uri) {
        String synerror;
        String[] t, tokens;
        int i, j, column;

        synerror = "Syntax error in " + errorLoc + "\n  ";
        // split substitution into tokens around spaces
        t = sb.toString().split(" ");

        // remove empty tokens
        j = 0;
        for (i = 0; i < t.length; i++) {
            if (t[i] != null && !t[i].equals("")) {
                j++;
            }
        }
        if (j == 0) {
            System.out.println(synerror + "Empty substitution (e.g. '$$ $$') '" + sb.toString() + "'");
            return null;
        }
        tokens = new String[j];
        j = 0;
        for (i = 0; i < t.length; i++) {
            if (t[i] != null && !t[i].equals("")) {
                tokens[j] = t[i];
                j++;
            }
        }

        /*
         for (i=0; i<tokens.length; i++)
         System.out.print("Token '"+tokens[i]+"', ");
         System.out.println("");
         */
        // process tokens
        i = 0;

        // substitute the current date/time
        switch (tokens[i].toLowerCase()) {
            case "date":
                return new DateFragment(errorLoc, uri);
            case "column":
                i++;
                if (i == tokens.length) {
                    System.out.println(synerror + "missing column reference in column substitution '" + sb.toString() + "'");
                    return null;
                }
                try {
                    column = Integer.parseInt(tokens[i]);
                } catch (NumberFormatException nfe) {
                    System.out.println(synerror + "column reference in column substitution '" + sb.toString() + "' is invalid (must be a positive integer)");
                    return null;
                }
                if (column < 1) {
                    System.out.println(synerror + "column reference in column substitution '" + sb.toString() + "' is invalid (must be a positive integer)");
                    return null;
                }
                return new ColumnFragment(errorLoc, column, false, uri);
            case "column-xml":
                i++;
                if (i == tokens.length) {
                    System.out.println(synerror + "missing column reference in column substitution '" + sb.toString() + "'");
                    return null;
                }
                try {
                    column = Integer.parseInt(tokens[i]);
                } catch (NumberFormatException nfe) {
                    System.out.println(synerror + "column reference in column substitution '" + sb.toString() + "' is invalid (must be a positive integer)");
                    return null;
                }
                if (column < 1) {
                    System.out.println(synerror + "column reference in column substitution '" + sb.toString() + "' is invalid (must be a positive integer)");
                    return null;
                }
                return new ColumnFragment(errorLoc, column, true, uri);
        }

        // by default assume simple column
        try {
            column = Integer.parseInt(tokens[i]);
        } catch (NumberFormatException nfe) {
            System.out.println(synerror + "column reference in column substitution '" + sb.toString() + "' is invalid (must be a positive integer)");
            return null;
        }
        if (column < 1) {
            System.out.println(synerror + "column reference in column substitution '" + sb.toString() + "' is invalid (must be a positive integer)");
            return null;
        }
        return new ColumnFragment(errorLoc, column, false, uri);
    }

    /**
     * Append a fragment to the end of this fragment.
     *
     * @param f	fragment to append
     */
    public void appendToEnd(Fragment f) {
        if (next == null) {
            next = f;
        } else {
            next.appendToEnd(f);
        }
    }
    
    /**
     * Get the schemaId for this template
     * @return schemaId
     */
    public String getSchemaId() {
        return schemaId;
    }

    /**
     * Get the syntaxId for this template
     * @return syntaxId
     */
    public String getSyntaxId() {
        return syntaxId;
    }

    /**
     * Resolve any dynamic content and output the contents to the VEO.
     *
     * @param data the source of data to resolve any dynamic content
     * @param document where to put the result of processing the template
     * @throws VEOError  if a fatal error occurred
     */
    abstract public void finalise(String[] data, CreateXMLDoc document) throws VEOError;

    /**
     * Output a list of fragments as a string.
     *
     * @return a string representation of a list of fragments
     */
    @Override
    abstract public String toString();
}
