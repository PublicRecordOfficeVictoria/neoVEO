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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.*;

/**
 * This class creates a generic XML document. It is subclassed to produce the
 * specific XML documents in the VEO
 */
class CreateXMLDoc {

    String rootElement;     // root element
    Path veoDir;            // directory in which the VEO is being created
    Charset cs;             // converter from String to UTF-8
    FileOutputStream fos;   // underlying file stream for file channel
    FileChannel xml;        // XML document being written
    public int indentDepth; // indent depth when outputing elements
    String classname = "CreateXMLDoc";       // String describing this class for error & logging

    private final static Logger LOG = Logger.getLogger("veocreate.createXMLDoc");

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
        
        indentDepth = 0;

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

    public final void startXMLDoc(String xmlDoc, String rootElement) throws VEOError {
        String module = "startXMLDoc";
        Path doc;   // document to create

        // remember root element
        this.rootElement = rootElement;

        // open XML document for writing
        try {
            doc = veoDir.resolve(xmlDoc);
        } catch (InvalidPathException ipe) {
            throw new VEOError(classname, module, 2, "Output XML document name (" + xmlDoc + ") is not a valid file name: "+ipe.getMessage());
        }
        try {
            fos = new FileOutputStream(doc.toString());
        } catch (FileNotFoundException fnfe) {
            throw new VEOError(classname, module, 1, "Output XML file '" + doc.toString() + "' cannot be opened for writing");
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
    public final void endXMLDoc() throws VEOError {
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
     * Add a simple XML element to the Metadata Package. A simple XML element is
     * one that has no subelements. The method is passed the tag name, the
     * attributes (pre-encoded as a string), and the value (again, pre-encoded
     * as a string). The attributes may be null, as may the value. The value is
     * encoded as XML safe.
     *
     * @param tag the tag name
     * @param attributes a string containing the attributes for the element
     * @param value a string containing the element value
     * @throws VEOError
     */
    public void addSimpleElement(String tag, String attributes, String value) throws VEOError {
        String method = "addSimpleElement";
        int i;
        
        // indent
        for (i=0; i<indentDepth; i++) {
            write(" ");
        }
        
        // if value is given, generate start tag, value, end tag, otherwise
        // generate an empty tag
        if (value != null && !value.equals("") && !value.trim().equals(" ")) {
            generateStartTag(tag, attributes);
            writeValue(value);
            generateEndTag(tag);
        } else {
            generateEmptyTag(tag, attributes);
        }
        write("\r\n");
    }
    
    /**
     * Add a complex XML element to the Metadata Package. A complex XML element is
     * one that has subelements. The method is passed the tag name, the
     * attributes (pre-encoded as a string)). The attributes may be null, as may the value.
     *
     * @param tag the tag name
     * @param attributes a string containing the attributes for the element
     * @throws VEOError
     */
    public void startComplexElement(String tag, String attributes) throws VEOError {
        String method = "addComplexElement";
        int i;
        
        for (i=0; i<indentDepth; i++) {
            write(" ");
        }
        generateStartTag(tag, attributes);
        indentDepth++;
        write("\r\n");
    }
    
    /**
     * End a complex XML element in the current Metadata Package. The tag
     * name is passed. No checking is performed that this is the correct en
     * tag (i.e. it is possible to create invalid XML).
     * 
     * @param tag
     * @throws VEOError 
     */
    public void endComplexElement(String tag) throws VEOError {
        String method = "endComplexElement";
        int i;
        
        indentDepth--;
        for (i=0; i<indentDepth; i++) {
            write(" ");
        }
        generateEndTag(tag);
        write("\r\n");
    }
    
    /**
     * Wrapper around generateTag() to generate a start tag.
     * 
     * @param tag tag name
     * @param attributes attributes
     * @throws VEOError 
     */
    private void generateStartTag(String tag, String attributes) throws VEOError {
        generateTag(true, tag, attributes, false);
    }
    
    /**
     * Wrapper around generateTag() to generate an end tag
     * @param tag tag name
     * @throws VEOError 
     */
    private void generateEndTag(String tag) throws VEOError {
        generateTag(false, tag, null, false);
    }
    
    /**
     * Wrapper around generateTag() to generate an empty element
     * @param tag tag name
     * @param attributes attributes
     * @throws VEOError 
     */
    private void generateEmptyTag(String tag, String attributes) throws VEOError {
        generateTag(true, tag, attributes, true);
    }
    
    /**
     * Generate an XML start or end tag. If start is true, a start tag will be
     * generated, otherwise an end tag will be generated. If attributes is not
     * null or empty, the predefined string is included as attributes in the
     * start tag (attributes is ignored if start is false). If empty is true,
     * the start tag is a complete empty element.
     *
     * IMPORTANT - no checking is done that the tag or the attributes are
     * valid according to the XML specification. It is the caller's
     * responsibility to ensure this.
     *
     * @param tag the tag name
     * @param attributes a string containing the attributes for the element
     * @throws VEOError
     */
    private void generateTag(boolean start, String tag, String attributes, boolean empty) throws VEOError {
        String method = "generateTag";
        
        if (tag == null || tag.equals("") || tag.trim().equals(" ")) {
            throw new VEOError(classname, method, 2, "Tag name is null, or blank");
        }
        write("<");
        if (!start) {
            write("/");
        }
        write(tag);
        if (start && attributes != null && !attributes.equals("") && !attributes.trim().equals(" ")) {
            write(" ");
            write(attributes);
        }
        if (start && empty) {
            write("/");
        }
        write(">");
    }

    /**
     * Low level routine to encode text to UTF-8 and write to the XML document.
     * Warning: this routine assumes that XML special characters are already
     * encoded.
     *
     * @param s string to write to XML document
     * @throws VEOError  if a fatal error occurred
     */
    public final void write(String s) throws VEOError {
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
    public final void write(char c) throws VEOError {
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
    public final void writeValue(String s) throws VEOError {
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
