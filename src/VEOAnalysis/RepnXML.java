/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOAnalysis;

import VERSCommon.ResultSummary;
import VERSCommon.VEOError;
import VERSCommon.VEOFatal;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * This class represents an object that is an XML document. When an instance of
 * the class is created, the XML document is opened and validated against the
 * DTD or schema.
 *
 * @author Andrew Waugh
 */
abstract class RepnXML extends Repn implements ErrorHandler {

    private final String classname = "RepnXML";
    private Document doc;    // internal DOM representation of XML document
    private DocumentBuilder parser; // parser
    private DocumentBuilderFactory dbf; // DOM factory
    private boolean contentsAvailable; // true if contents are available
    private NodeList elements;  // list of elements in document order
    private int currentElement; // current element
    private final static Logger LOG = Logger.getLogger("VEOAnalysis.RepnXML");

    /**
     * Create a RepnXML instance. This creates the parser for the document, but
     * does not parse the document. The method parse() must be called to parse
     * the document.
     *
     * @param id the identifier to use in describing this
     * @throws VERSCommon.VEOFatal if prevented from continuing processing at all
     */
    protected RepnXML(String id, ResultSummary results) throws VEOFatal {
        super(id, results);
        // create parser
        dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setValidating(false);   // will specify a schema to validate the XML
        try {
            parser = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new VEOFatal(errMesg(classname, "Failed to construct an XML parser. Error was: ", e));
        }
        contentsAvailable = false;
    }

    /**
     * Free resources associated with this object. This instance should not be
     * called again.
     */
    @Override
    public void abandon() {
        super.abandon();
        doc = null;
        dbf = null;
        parser = null;
        elements = null;
        contentsAvailable = false;
    }

    /**
     * Create a representation from an XML file. This parses the XML file into
     * an internal DOM structure and generates a list of elements that can be
     * stepped through. Errors will occur if the file is not valid XML, or if
     * the file was valid XML, but not valid according to the schema.
     *
     * @param file XML file to be parsed and validated.
     * @param schemaFile a file containing the XML schema
     * @return false if the representation could not be constructed
     * @throws VEOSupport.VEOFatal if a system error occurred would prevent
     * processing any VEO
     * @throws VEOSupport.VEOError if an error occurred processing this VEO
     */
    final boolean parse(Path file, Path schemaFile) throws VEOError {
        String method = "parse";
        SchemaFactory sf;
        Schema schema;
        Validator validator;

        // sanity check...
        if (contentsAvailable) {
            LOG.log(Level.WARNING, errMesg(classname, method, "Calling parse() twice"));
            return false;
        }

        // check that the file exists and is an ordinary file
        if (file == null) {
            throw new VEOError(2, errMesg(classname, "file parameter is null"));
        }
        if (!Files.exists(file)) {
            throw new VEOError(3, errMesg(classname, "file '" + file.toString() + "' does not exist"));
        }
        if (!Files.isRegularFile(file)) {
            throw new VEOError(4, errMesg(classname, "file '" + file.toString() + "' is not a regular file"));
        }

        // parse the schema and construct a validator
        sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            schema = sf.newSchema(schemaFile.toFile());
        } catch (SAXException e) {
            throw new VEOFatal(5, errMesg(classname, "Failed to parse schema '" + schemaFile.toString() + "'. Error was", e));
        }
        validator = schema.newValidator();

        // parse the file
        doc = null;
        parser.setErrorHandler(this);
        try {
            InputSource is = new InputSource(new FileInputStream(file.toFile()));
            // is.setEncoding("UTF-8");
            doc = parser.parse(is);
        } catch (SAXParseException e) {
            addError(errMesg(classname, "Parse error when parsing file" + e.getSystemId() + " (line " + e.getLineNumber() + " column " + e.getColumnNumber() + ")", e));
            return false;
        } catch (SAXException e) {
            addError(errMesg(classname, "Problem when parsing file", e));
            return false;
        } catch (IOException e) {
            throw new VEOFatal(errMesg(classname, "System error when parsing file '" + file.toString() + "'. Error was", e));
        }

        // validate the DOM tree against the schema
        try {
            validator.validate(new DOMSource(doc));
        } catch (SAXException e) {
            addError("Error when validating " + file.getFileName().toString() + " against schema '" + schemaFile.toString() + "'. Error was" + e.getMessage());
            return false;
        } catch (IOException e) {
            throw new VEOFatal(errMesg(classname, "System error when validating XML ", e));
        }

        // get list of elements
        elements = doc.getElementsByTagNameNS("*", "*");
        currentElement = 0;

        contentsAvailable = true;
        return true;
    }

    /**
     * callback for warnings generated by SAX parsing of XML
     *
     * @param e the Parse Error
     * @throws SAXException if a SAX parsing problem was detected
     */
    @Override
    public void warning(SAXParseException e) throws SAXException {
        String method = "warning";
        
        addWarning(errMesg(classname, method, "Warning when parsing file", e));
    }

    /**
     * callback for errors generated by SAX parsing of XML
     *
     * @param e the Parse Error
     * @throws SAXException if a SAX parsing problem was detected
     */
    @Override
    public void error(SAXParseException e) throws SAXException {
        String method = "error";
        
        addError(errMesg(classname, method, "Error when parsing file", e));
    }

    /**
     * callback for fatal errors generated by SAX parsing of XML
     *
     * @param e the Parse Error
     * @throws SAXException if a SAX parsing problem was detected
     */
    @Override
    public void fatalError(SAXParseException e) throws SAXException {
        throw new SAXException(e.getMessage());
    }

    /**
     * Test if valid content is available.
     *
     * @return true if data can be examined
     */
    final public boolean contentsAvailable() {
        return contentsAvailable;
    }

    /**
     * Goto the root (first) element of the XML document.
     */
    final public void gotoRootElement() {
        currentElement = 0;
    }

    /**
     * Step to the next element of the XML document in document order.
     */
    final public void gotoNextElement() {
        currentElement++;
    }

    /**
     * Get the index of the current element. This may be beyond the list of
     * elements
     *
     * @return the index
     */
    final public int getCurrentElementIndex() {
        return currentElement;
    }

    /**
     * Check if at end of list of elements
     *
     * @return true if at or beyond current element
     */
    final public boolean atEnd() {
        if (elements == null || !contentsAvailable) {
            return true;
        }
        return currentElement >= elements.getLength();
    }

    /**
     * Scan forward through the list of elements for the next sibling element of
     * the current element. Will return true if a sibling element was found at
     * this level, otherwise false
     *
     * @return true if the skip succeeded
     * @throws IndexOutOfBoundsException if we are already beyond the end of the
     * list of elements
     * @throws VEOError if a sibling was supposed to exist, but we didn't find
     * it in the list of elements
     */
    final public boolean gotoSibling() throws IndexOutOfBoundsException, VEOError {
        String method = "gotoSibling";
        Node next;

        // sanity check...
        if (!contentsAvailable) {
            throw new VEOError(errMesg(classname, method, "Parse failed and no contents are available"));
        }

        // get the next element at this level (or parent level if none at this level)
        next = getCurrentElement();
        do {
            next = next.getNextSibling();
        } while (next != null && next.getNodeType() != Node.ELEMENT_NODE);

        // return if no next sibling element at this level
        if (next == null) {
            return false;
        }

        // otherwise, find the sibling element in our list of elements
        do {
            currentElement++;
        } while (currentElement < elements.getLength() && elements.item(currentElement) != next);

        // if it was not found, complain
        if (atEnd()) {
            throw new VEOError(errMesg(classname, method, "Failed to find next sibling: " + next.getNodeName()));
        }
        return true;
    }

    /**
     * Scan forward through the list of elements for the next sibling element of
     * the parent element. Will return true if a sibling element was found at
     * this level, otherwise false
     *
     * @return true if the skip succeeded
     * @throws IndexOutOfBoundsException if we are already beyond the end of the
     * list of elements
     * @throws VEOError if a sibling was supposed to exist, but we didn't find
     * it in the list of elements
     */
    final public boolean gotoParentSibling() throws IndexOutOfBoundsException, VEOError {
        String method = "gotoParentSibling";
        Node next;

        // sanity check...
        if (!contentsAvailable) {
            throw new VEOError(errMesg(classname, method, "Parse failed and no contents are available"));
        }

        // get the parent node
        next = getCurrentElement().getParentNode();
        do {
            while (next.getNextSibling() == null) {
                next = next.getParentNode();
                if (next == null) {
                    currentElement = elements.getLength();
                    return false;
                    // throw new IndexOutOfBoundsException("Ran out of elements when consuming content");
                }
            }
            next = next.getNextSibling();
        } while (next.getNodeType() != Node.ELEMENT_NODE);

        // otherwise, find the sibling element in our list of elements
        do {
            currentElement++;
        } while (currentElement < elements.getLength() && elements.item(currentElement) != next);

        // if it was not found, complain
        if (atEnd()) {
            throw new VEOError(errMesg(classname, method, "Failed to find next sibling: " + next.getNodeName()));
        }
        return true;
    }

    /**
     * Get the current element.
     *
     * @return the current element
     * @throws IndexOutOfBoundsException if past the end of list of elements
     * @throws VEOError if there are no elements because the parse failed
     */
    final public Element getCurrentElement() throws IndexOutOfBoundsException, VEOError {
        String method = "getCurrentElement";

        if (!contentsAvailable || elements == null) {
            throw new VEOError(errMesg(classname, method, "Valid content not available from XML document"));
        }
        if (currentElement > elements.getLength()) {
            throw new IndexOutOfBoundsException("Stepped past end of document");
        }
        return (Element) elements.item(currentElement);
    }

    /**
     * Check if the current element has the specified tag name.
     *
     * @param tagName the name of the tag
     * @return true if the current element has the tag name.
     * @throws IndexOutOfBoundsException if there are no elements, or all have
     * been processed
     * @throws VEOError if the parse failed and no elements are available
     */
    final public boolean checkElement(String tagName) throws IndexOutOfBoundsException, VEOError {
        Element e;

        e = getCurrentElement();
        if (e == null) {
            return false;
        }
        return e.getTagName().equals(tagName);
    }
    
    /**
     * Get an attribute from the current element.
     * 
     * @param attributeName the attribute to return
     * @return a string containing the attribute 
     * @throws IndexOutOfBoundsException if there are no elements, or all have
     * been processed
     * @throws VEOError if the parse failed and no elements are available 
     */
    final public String getAttribute(String attributeName) throws IndexOutOfBoundsException, VEOError {
        Element e;
        
        e = getCurrentElement();
        if (e == null) {
            return null;
        }
        return e.getAttribute(attributeName);
    }
    
    /**
     * Get the value associated with the current element. This may be null if
     * the element has no text associated with it. The text returned is trimmed
     * of whitespace at the start and end.
     *
     * @return a string containing the complete value
     * @throws VEOError if the parse failed or no elements are available
     */
    final public String getTextValue() throws VEOError {
        String method = "getTextValue";
        Node n;

        if (!contentsAvailable || elements == null) {
            throw new VEOError(errMesg(classname, method, "Valid content not available from XML document"));
        }

        // get first child of this element
        n = elements.item(currentElement).getFirstChild();

        // get text, including text from any adjacent nodes
        // ASSUMES that the element only contains Text nodes
        if (n != null && n.getNodeType() == Node.TEXT_NODE) {
            return ((Text) n).getWholeText().trim();
        }

        // no text, so return null
        return null;
    }

    /**
     * Generate a representation of this object as a string
     *
     * @return the representation
     */
    @Override
    public String toString() {
        return "";
    }

    /**
     * Print the content of a DOM node (and its children).
     *
     * @param n the node
     * @param depth indent
     * @return String containing the node (and its children, if any)
     */
    final public static String prettyPrintNode(Node n, int depth) {
        StringBuffer sb;
        NodeList nl;
        int i;
        String v;

        if (n == null) {
            return "";
        }
        sb = new StringBuffer();
        switch (n.getNodeType()) {
            case 1:
                for (i = 0; i < depth; i++) {
                    sb.append(' ');
                }
                sb.append("Element: ");
                sb.append(n.getNodeName());
                sb.append("\n");
                if (n.hasChildNodes()) {
                    nl = n.getChildNodes();
                    for (i = 0; i < nl.getLength(); i++) {
                        sb.append(prettyPrintNode(nl.item(i), depth + 1));
                    }
                }
                break;
            case 3:
                v = n.getNodeValue();
                if (v == null || v.trim().equals("")) {
                    break;
                }
                for (i = 0; i < depth; i++) {
                    sb.append(' ');
                }
                sb.append("Text: '");
                sb.append(v.trim());
                sb.append("'\n");
                break;
            default:
                sb.append(n.getNodeType());
                sb.append(" ");
                sb.append(n.getNodeValue());
                sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Diagnostic routine to dump the internal DOM representation of the
     * document.
     *
     * @return String containing the DOM representation
     */
    final public String dumpDOM() {
        return dumpNode(elements.item(0), 0);
    }

    /**
     * Print the content of a DOM node (and its children).
     *
     * @param n the node
     * @param depth indent
     * @return String containing the node (and its children, if any)
     */
    final public static String dumpNode(Node n, int depth) {
        StringBuffer sb;
        NodeList nl;
        int i;
        String v;

        if (n == null) {
            return "";
        }
        sb = new StringBuffer();
        for (i = 0; i < depth; i++) {
            sb.append(' ');
        }
        sb.append("Node: ");
        sb.append(n.getNodeName());
        sb.append("(Type: ");
        sb.append(n.getNodeType());
        sb.append(") '");
        v = n.getNodeValue();
        if (v != null) {
            sb.append(v.trim());
        } else {
            sb.append("<null>");
        }
        sb.append("'\n");
        if (n.hasChildNodes()) {
            nl = n.getChildNodes();
            for (i = 0; i < nl.getLength(); i++) {
                sb.append(dumpNode(nl.item(i), depth + 1));
            }
        }
        return sb.toString();
    }
}
