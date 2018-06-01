/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOCreate;

import VERSCommon.VEOError;
import VERSCommon.VEOFatal;
import java.io.*;
import java.nio.CharBuffer;
import java.nio.channels.*;
import java.nio.charset.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.*;

/**
 * This class creates a generic XML document. It is subclassed to produce the
 * specific XML documents in the VEO
 */
public class CreateXMLDoc {

    String rootElement;     // root element
    Path veoDir;            // directory in which the VEO is being created
    Charset cs;             // converter from String to UTF-8
    FileOutputStream fos;   // underlying file stream for file channel
    FileChannel xml;        // XML document being written
    String classname = "CreateXMLDoc";       // String describing this class for error & logging

    private final static Logger log = Logger.getLogger("veocreate.createXMLDoc");

    /**
     * Creates an XML Document in the specified directory.
     *
     * @param veoDir the directory in which the XML document will be created
     * @throws VEOError if this VEO cannot be completed
     * @throws VEOFatal if no VEO could be completed
     */
    public CreateXMLDoc(Path veoDir) throws VEOError, VEOFatal {

        // utilities
        try {
            cs = Charset.forName("UTF-8");
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            throw new VEOFatal(classname, 1, ": Failed to set UTF-8 charset");
        }

        // check that the veoDirectory exists and is a directory
        if (Files.notExists(veoDir)) {
            throw new VEOError(classname, 2, ": VEO directory '" + veoDir.toString() + "' does not exist");
        }
        if (!Files.isDirectory(veoDir)) {
            throw new VEOError(classname, 3, ": VEO directory '" + veoDir.toString() + "' is not a directory");
        }

        this.veoDir = veoDir;
    }

    /**
     * Start an XML document.
     *
     * This method generates the XML preamble and the root element.
     *
     * @param xmlDoc the XML Document to generate
     * @param rootElement the root element of the XML document
     * @throws VEOError
     */
    static String contentsXML01
            = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\r\n<";
    static String contentsXML02 = "\r\n"
            + "  xmlns:vers=\"http://www.prov.vic.gov.au/VERS\">\r\n";

    public void startXMLDoc(String xmlDoc, String rootElement) throws VEOError {
        String module = "startXMLDoc";
        Path doc;   // document to create

        // remember root element
        this.rootElement = rootElement;

        // open XML document for writing
        doc = Paths.get(veoDir.toString(), xmlDoc);
        try {
            fos = new FileOutputStream(doc.toString());
        } catch (FileNotFoundException fnfe) {
            throw new VEOError(classname, module, 1, "Output VEO file '" + doc.toString() + "' cannot be opened for writing");
        }
        xml = fos.getChannel();

        // generate XML document up to start of content
        write(contentsXML01);
        write(rootElement);
        write(contentsXML02);
    }

    static String contentsXML04 = "</";
    static String contentsXML05 = ">";

    /**
     * Finish and close an XML document.
     *
     * @throws VEOError  if a fatal error occurred
     */
    public void endXMLDoc() throws VEOError {
        String module = "startXMLDoc";

        write(contentsXML04);
        write(rootElement);
        write(contentsXML05);

        try {
            xml.close();
            xml = null;
            fos.close();
            fos = null;
        } catch (IOException ioe) {
            throw new VEOError(classname, module, 1, "Failed to close XML document:"+ioe.getMessage());
        }
    }

    /**
     * Low level routine to encode text to UTF-8 and write to the XML document.
     * Warning: this routine assumes that XML special characters are already
     * encoded.
     *
     * @param s string to write to XML document
     * @throws VEOError  if a fatal error occurred
     */
    public void write(String s) throws VEOError {
        String module = "write";

        try {
            xml.write(cs.encode(s));
        } catch (IOException ioe) {
            throw new VEOError(classname, module, 1, "Failed writing to XML document: "+ioe.getMessage());
        }
    }

    /**
     * Low level routine to encode a character to UTF-8 and write to XML
     * document. Warning: this routine assumes that XML special characters are
     * already encoded.
     *
     * @param c character to write to XML document
     * @throws VEOError  if a fatal error occurred
     */
    public void write(char c) throws VEOError {
        String module = "write";
        CharBuffer cb;
        try {
            cb = CharBuffer.allocate(1);
            cb.append(c);
            xml.write(cs.encode(cb));
        } catch (IOException ioe) {
            throw new VEOError(classname, module, 2, "Failed writing to XML document:"+ioe.getMessage());
        }
    }

    /**
     * Low level routine to encode an XML value to UTF-8 and write to the XML
     * document. The special characters ampersand, less than, greater than,
     * single quote and double quote are quoted.
     *
     * @param s string to write to XML document
     * @throws VEOError  if a fatal error occurred
     */
    public void writeValue(String s) throws VEOError {
        String module = "write";
        StringBuilder sb = new StringBuilder();
        int i;
        char c;
        
        // sanity check
        if (s == null || s.length() == 0)
            return;

        // quote the special characters in the string
        for (i = 0; i < s.length(); i++) {
            c = s.charAt(i);
            if (c == '&') {
                sb.append("&amp;");
            } else if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '"') {
                sb.append("&quot;");
            } else if (c == '\'') {
                sb.append("&apos;");
            } else {
                sb.append(c);
            }
        }
        try {
            xml.write(cs.encode(sb.toString()));
        } catch (IOException ioe) {
            throw new VEOError(classname, module, 2, "Failed writing to XML document"+ioe.getMessage());
        }
    }

    /**
     * Abandon construction of this XML document and free any resources.
     *
     * @param debug true if in debugging mode
     */
    public void abandon(boolean debug) {
        try {
            if (xml != null) {
                xml.close();
            }
            if (fos != null) {
                fos.close();
            }
        } catch (IOException e) { /* ignore */ }
    }

    /**
     * M A I N
     *
     * Test program to tell if CreateXMLDoc is working
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        CreateXMLDoc cxd;
        Path path;

        try {
            path = FileSystems.getDefault().getPath("Test");
            System.out.println((path.toAbsolutePath()).toString());
            cxd = new CreateXMLDoc(Paths.get("Test"));
            cxd.startXMLDoc("VEOContent.xml", "vers:TestDoc");
            cxd.write("Testing123");
            cxd.endXMLDoc();
        } catch (VEOError e) {
            System.out.println(e.getMessage());
        }
        System.out.println("Complete!");
    }
}
