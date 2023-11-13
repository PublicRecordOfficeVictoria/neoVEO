/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 */
package VEOAnalysis;

import VERSCommon.ResultSummary;
import VERSCommon.VEOError;
import VERSCommon.VEOFailure;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;

// Use the org.apache.jena imports if using Jena 4
/*
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.ResourceRequiredException;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdfxml.xmlinput.DOM2Model;
import org.apache.jena.shared.BadURIException;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
 */
// end Jena 4 imports
// Use the com.hp.hpl.jena imports if using Jena 2
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.ResourceRequiredException;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.rdfxml.xmlinput.DOM2Model;
import com.hp.hpl.jena.shared.BadURIException;
import java.util.List;
import java.util.NoSuchElementException;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.w3c.dom.Attr;
// end Jena 2 imports

/**
 * This class encapsulates a Metadata Package in a Information Object.
 *
 * @author Andrew Waugh
 */
class RepnMetadataPackage extends Repn {

    private final static String CLASSNAME = "RepnMetadataPackage";
    private RepnItem schemaId;  // schema identifier
    private RepnItem syntaxId;  // syntax identifier
    private ArrayList<Element> metadata; // list of metadata roots
    private boolean rdf;        // true if the metadata package is RDF
    private Model rdfModel;     // RDF model
    private String resourceURI; // URL identifying RDF resource
    private Namespace dcNameSpace; // URL for dublin core namespace actually used
    private Namespace aglsNameSpace; // URL for AGLS namespace actually used
    private Namespace versNameSpace; // URL for VERS namespace actually used
    private Namespace anzs5478NameSpace; // URL for ANZS5478 namespace actually used
    private final static java.util.logging.Logger LOG = java.util.logging.Logger.getLogger("VEOAnalysis.RepnMetadatPackage");

    /**
     * What metadata have we found?
     */
    private enum MetadataPackage {
        Unknown, // we don't know
        AGLS, // AGLS metadata
        ANZS5478    // ASNZS5478
    }
    
    /**
     * Namespace information
     */
    private class Namespace {
        String handle;  // the short identifier used in the XML
        String value;   // the actual URL of this namespace used in the document
        
        Namespace(String handle, String value) {
            this.handle = handle;
            this.value = value;
        }
    }

    // These are the accepted URLs for the namespaces used in the standard VERS
    // metadata packages. If the VEO uses other URLs for these namespaces, an
    // Error will be generated, but the actual namespace used in the VEO will
    // be used to check the VEO (this prevents cascading errors).
    private static final String[] DC_TERMS_NS
            = {"http://purl.org/dc/terms/"};
    private static final String[] AGLS_TERMS_NS
            = {"http://www.agls.gov.au/agls/terms/",
                "https://www.agls.gov.au/agls/terms/"};
    private static final String[] VERS_TERMS_NS = {
        "http://www.prov.vic.gov.au/vers/terms/"
    };
    private static final String[] ANZS5478_TERMS_NS = {
        "http://prov.vic.gov.au/ANZS5478",
        "http://www.prov.vic.gov.au/ANZS5478",
        "http://www.prov.vic.gov.au/vers/schema/ANZS5478",
        "http://www.prov.vic.gov.au/ANSZ5478/terms/",
        "http://www.prov.vic.gov.au/VERS-as5478"};

    private static final String DC_TERMS = "dcterms";
    private static final String AGLS_TERMS = "aglsterms";
    private static final String VERS_TERMS = "versterms";
    private static final String ANZS5478_TERMS = "anzs5478";

    // AGRkMS RDF properties
    /**
     * Construct a Metadata Package from the XML document VEOContent.xml.
     *
     * @param document the representation of the XML document
     * @param parentId the parent object identifier
     * @param seq the sequence number of this MP in the Information Object
     * @param rdf true if metadata is expressed in RDF
     * @param results the results summary to build
     * @throws VEOError if the XML document has not been properly parsed
     */
    public RepnMetadataPackage(RepnXML document, String parentId, int seq, boolean rdf, ResultSummary results) throws VEOError {
        super(parentId + ":MP-" + seq, results);

        assert (document != null);
        assert (parentId != null);
        assert (seq > -1);

        metadata = new ArrayList<>();
        this.rdf = rdf;
        rdfModel = null;
        resourceURI = null;
        dcNameSpace = null;
        aglsNameSpace = null;
        versNameSpace = null;
        anzs5478NameSpace = null;

        // VERS:MetadataSchemaIdentifier
        schemaId = new RepnItem(id, "Metadata schema id:", results);
        schemaId.setValue(document.getTextValue());
        document.gotoNextElement();
        // VERS:MetadataSyntaxIdentifier
        syntaxId = new RepnItem(id, "Metadata syntax id:", results);
        syntaxId.setValue(document.getTextValue());
        if (syntaxId.getValue().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns")) {
            if (!rdf) {
                addError(new VEOFailure(CLASSNAME, 1, id, "Error. Metadata Package has vers:MetadataSyntaxIdentifier of http://www.w3.org/1999/02/22-rdf-syntax-ns, but xmlns:rdf namespace attribute is not defined"));
            }
        } else {
            this.rdf = false; // force rdf false, even if we have seen RDF attributes defined
        }
        document.gotoNextElement();

        // remember the roots of the metadata subtrees
        do {
            metadata.add(document.getCurrentElement());
        } while (document.gotoSibling());
        document.gotoParentSibling();
        objectValid = true;
    }

    /**
     * Validate the data in the Metadata Package.
     *
     * @param veoDir the directory containing the contents of the VEO.
     * @param noRec true if not to complain about missing recommended metadata
     * elements
     * @return true if the metadata package is AGLS or AGRKMS (even if the contents are not valid)
     */
    public boolean validate(Path veoDir, boolean noRec) {

        assert (veoDir != null);

        // confirm that there is a non empty vers:MetadataSchemaIdentifier element
        if (schemaId.getValue() == null) {
            addError(new VEOFailure(CLASSNAME, "validate", 1, id, "vers:MetadataSchemaIdentifier element is missing or has a null value"));
            return false;
        }
        if (schemaId.getValue().trim().equals("") || schemaId.getValue().trim().equals(" ")) {
            addError(new VEOFailure(CLASSNAME, "validate", 2, id, "vers:MetadataSchemaIdentifier element is empty"));
            return false;
        }

        // confirm that there is a non empty vers:MetadataSyntaxIdentifier element
        if (syntaxId.getValue() == null) {
            addError(new VEOFailure(CLASSNAME, "validate", 3, id, "vers:MetadataSyntaxIdentifier element is missing or has a null value"));
            return false;
        }
        if (syntaxId.getValue().trim().equals("") || schemaId.getValue().trim().equals(" ")) {
            addError(new VEOFailure(CLASSNAME, "validate", 4, id, "vers:MetadataSyntaxIdentifier element is empty"));
            return false;
        }

        // if ANZS5478 check to see if the required properties are present and valid
        if (schemaId.getValue().endsWith("ANZS5478")
                || schemaId.getValue().equals("http://www.prov.vic.gov.au/VERS-as5478")) {
            
            // correct syntax?
            if (!syntaxId.getValue().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns")) {
                addError(new VEOFailure(CLASSNAME, "validate", 5, id, "ANZS-5478 metadata must be represented as RDF with the syntax id 'http://www.w3.org/1999/02/22-rdf-syntax-ns'"));
                return true;
            }

            // we've seen the rdf:RDF definition, and the RDF parsing succeeded..
            if (rdf && parseRDF(MetadataPackage.ANZS5478)) {
                checkANZSProperties(noRec);
                return true;
            }
            return true;
        }

        // if AGLS check to see if the required properties are present and valid
        if (schemaId.getValue().endsWith("AGLS")
                || schemaId.getValue().equals("http://www.vic.gov.au/blog/wp-content/uploads/2013/11/AGLS-Victoria-2011-V4-Final-2011.pdf")) {
            
            // correct syntax?
            if (!syntaxId.getValue().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns")) {
                addError(new VEOFailure(CLASSNAME, "validate", 6, id, "AGLS metadata must be represented as RDF with the syntax id 'http://www.w3.org/1999/02/22-rdf-syntax-ns'"));
                return true;
            }

            // we've seen the rdf:RDF definition, and the RDF parsing succeeded..
            if (rdf && parseRDF(MetadataPackage.AGLS)) {
                checkAGLSProperties(noRec);
                return true;
            }
            return true;
        }
        return false;
    }

    /**
     * Validate metadata expressed in RDF as XML. The first step is to parse the
     * RDF into a Model that can be subsequently queried for properties.
     *
     * Apache Jena is used to parse, validate, and extract RDF metadata.
     *
     * The version of Jena used depends on the JDK available. Jena uses Log4j;
     * Jena 3 and Jena 4 use Log4j2 which had serious security bugs until early
     * 2022. Using the patched (latest) version of Log4j2 required JDK11.
     *
     * If you can use JDK 11 or better, use the included libraries for Jena 4.
     * If you prefer to use JDK 8, use the included libraries for Jena 2. This
     * uses Log4j (version 1) which does not have the reported security bugs.
     *
     * This class is the only place that Log4j/Log4j2 is used; the rest of
     * VEOAnalysis uses the standard Java logging library. Consequently, this
     * class constructs a simple WriteAppender, and Jena logging is captured
     * into it and then added as Errors or Warnings.
     *
     * Code is provided for both Jena 2/Log4j and Jena4/Log4j2. The code
     * required should be uncommented, and that not required commented out. 1.
     * Include the appropriate Jar files for Jena 2/Log4j and Jena4/Log4j2.
     * These are found in the srclib/Jena2 or srclib/Jena4 directories. 2.
     * Uncomment the required import statements at the start of this class &
     * comment out the unrequired import statements. 3. Uncomment the required
     * code in this method that attaches the Log4j logging to a string capture
     * mechanism & comment out the unrequired code. The Log4j code must be used
     * with Jena 2, and Log4j2 with Jena 4. 4. Select the appropriate
     * appender.close (Log4j) or appender.stop (Log4j2) call at the end of this
     * method. 5. In RepnVEO, read the appropriate configuration file for Log4j
     * or Log4j2.
     *
     * If it is necessary to update Jena 3 (or Log4j2!), get the Jena
     * distribution. This should contain the necessary Log4j2 and slf4j files -
     * there is no need to download them separately. Note that most of the jar
     * files included in the Jena distribution are not necessary. Currently the
     * only ones required are jena-core, jena-base, jena-iri, jena-shaded-guava,
     * log4j-api, log4j-core, log4j-slf4j-impl, slf-api, and commons-lang3.
     *
     * @return false if the validation failed.
     */
    private boolean parseRDF(MetadataPackage mp) {
        DOM2Model d2m; // translator from DOM nodes to RDF model
        Model m;
        StringWriter parseErrs;     // place where parse errors can be captured
        int i;
        Element e;

        // create a place to put the RDF metadata (if any)
        rdfModel = ModelFactory.createDefaultModel();
        assert (rdfModel != null);

        // add String Logger to Jena logging facility to capture parse errors
        parseErrs = new StringWriter();

        // This code is used with Jena 4 and Log4j2
        // This is pure magical incantation taken from https://logging.apache.org/log4j/2.x/manual/customconfig.html
        /* Uncomment out this section if you wish to use Jena 4/Log4j2
        final LoggerContext context = LoggerContext.getContext(false);
        final Configuration config = context.getConfiguration();
        final PatternLayout layout = PatternLayout.createDefaultLayout(config);
        final Appender appender = WriterAppender.createAppender(layout, null, parseErrs, "STRING_APPENDER", false, true);
        appender.start();
        config.addAppender(appender);
        final org.apache.logging.log4j.Level level = null;
        final Filter filter = null;
        for (final LoggerConfig loggerConfig : config.getLoggers().values()) {
            loggerConfig.addAppender(appender, level, filter);
        }
        config.getRootLogger().addAppender(appender, level, filter);
         */
        // end code for Jena 4/Log4j2
        // This code is used with Jena 2 and Log4j. The code is taken from
        // http://logging.apache.org/log4j/1.2/manual.html
        WriterAppender appender = new WriterAppender(new PatternLayout("%p - %m%n"), parseErrs);
        appender.setName("STRING_APPENDER");
        appender.setThreshold(org.apache.log4j.Level.WARN);
        Logger.getRootLogger().removeAllAppenders();
        Logger.getRootLogger().addAppender(appender);
        // end code for Jena 2/Log4j

        for (i = 0; i < metadata.size(); i++) {
            e = metadata.get(i);

            // if this is RDF metadata, attempt to parse it...
            if (e.getTagName().equals("rdf:RDF")) {

                // check the namespace declarations
                switch (mp) {
                    case AGLS:
                        dcNameSpace = setNameSpace(e, DC_TERMS, DC_TERMS_NS, false);
                        aglsNameSpace = setNameSpace(e, AGLS_TERMS, AGLS_TERMS_NS, false);
                        versNameSpace = setNameSpace(e, VERS_TERMS, VERS_TERMS_NS, false);
                        break;
                    case ANZS5478:
                        anzs5478NameSpace = setNameSpace(e, ANZS5478_TERMS, ANZS5478_TERMS_NS, false);
                        versNameSpace = setNameSpace(e, VERS_TERMS, VERS_TERMS_NS, true);
                        break;
                    default:
                        break;
                }

                // clear any previous errors in the RDF logging facility
                parseErrs.getBuffer().setLength(0);

                // create a model to contain the parsed RDF
                // Note: Jena doesn't seem to be able to parse multiple sets of
                // DOM nodes into one model, hence we have to parse them
                // separately and amalgamate them
                m = ModelFactory.createDefaultModel();
                try {
                    d2m = DOM2Model.createD2M(null, m);
                } catch (SAXParseException spe) {
                    LOG.log(java.util.logging.Level.WARNING, VEOFailure.getMessage(CLASSNAME, "parseRDF", 1, id, "Failed to initialise Jena to parse RDF", spe));
                    return false;
                }
                // d2m.setErrorHandler(errHandler);
                d2m.load(e);
                d2m.close();
                d2m = null;

                // merge the newly passed model into the bigger one
                rdfModel = rdfModel.union(m);
                m.removeAll();

                // rdfModel.write(System.out);
                // if errors occurred, remember them
                if (parseErrs.getBuffer().length() > 0) {
                    addError(new VEOFailure(CLASSNAME, "parseRDF", 2, id, parseErrs.toString().trim()));
                }
                parseErrs.getBuffer().setLength(0);
            }
        }

        // clean up
        // appender.stop(); // use for Jena4/Log4j2
        appender.close(); // use for Jena2/Log4j
        try {
            parseErrs.close();
        } catch (IOException ioe) {
            LOG.log(java.util.logging.Level.WARNING, VEOFailure.getMessage(CLASSNAME, "parseRDF", 3, id, "Failed to close StringWriter used to capture parse errors", ioe));
        }
        return true;
    }

    /**
     * Process the metadata package namespace declarations in the rdf:RDF
     * element. The namespace declaration must exist and must be set to the
     * correct value. However, when parsing the RDF we use the namespace value
     * set in the VEO - this prevents errors about missing metadata if all that
     * is wrong is that the namespace value is incorrect.
     *
     * @param e the RDF element
     * @param namespace the namespace we are looking for
     * @param properValues the proper values of the namespace
     * @param optional this namespace might not appear
     * @return the values of the namespace attribute actually used in the VEO
     */
    private Namespace setNameSpace(Element e, String namespace, String[] properValues, boolean optional) {
        Attr a;
        String v;
        int i;

        if ((a = e.getAttributeNode("xmlns:" + namespace)) == null && !optional) {
            addWarning(new VEOFailure(CLASSNAME, "parseRDF", 1, id, "Namespace declaration for xmlns:" + namespace + " missing from rdf:RDF element"));
            return null;
        }
        v = a.getValue().trim();
        rdfModel.setNsPrefix(namespace, v);

        // check that the namespace definition is the correct one
        for (i = 0; i < properValues.length; i++) {
            if (v.equals(properValues[i])) {
                break;
            }
        }
        if (i == properValues.length) {
            addError(new VEOFailure(CLASSNAME, "parseRDF", 2, id, "Namespace declaration for xmlns:" + namespace + " is '" + v + "' instead of '" + properValues[0] + "'"));
        }
        return new Namespace(namespace, v);
    }

    /**
     * Check the properties in an ANZS5478 instance. Validation is performed by
     * parsing the XML into an RDF model and querying this. An RDF schema is not
     * used to improve and customise the error reporting.
     * <p>
     * Missing mandatory properties are flagged as errors. Missing conditional
     * properties are flagged as warnings where the condition can be tested. The
     * following is NOT checked:
     * <ul>
     * <li>In general, the value of a property for conformance to a scheme</li>
     * <li>The existence of multiple instances of a property that cannot be
     * repeated</li>
     * <li>The existence of mandatory (or conditional) subproperties of optional
     * or conditional properties that are present</li>
     * <li>The presence of properties that are not defined in the standard
     * </ul>
     * <p>
     * The checks support two ANZS5478 models: a multi-entity and a single
     * entity model. In a multi-entity model, agents, businesses, and mandates
     * are separate identified RDF resources that are linked by relationship
     * entities (which are also separate resources). In a single entity model,
     * the only identified RDF resource is a record entity. Relationships are
     * represented as anonymous RDF resources within a record, with the related
     * entity being represented as anonymous RDF resources within the
     * relationship entity.
     */
    private void checkANZSProperties(boolean noRec) {
        ResIterator ri;
        Resource r1;
        int entitiesFound;
        String lid;

        System.out.println(rdfModel2String());
        // get all resources
        ri = rdfModel.listSubjects();
        if (!ri.hasNext()) {
            addError(new VEOFailure(CLASSNAME, "checkANZSProperties", 1, id, "ANZS5478 metadata package contained no rdf:Description, or only an empty rdf:Description"));
            return;
        }

        // step through the resources that have subjects. We are only interested
        // in the named resources (that have an rdf:about attribute)
        entitiesFound = 0;
        while (ri.hasNext()) {
            r1 = ri.nextResource();
            resourceURI = r1.getURI();
            if (resourceURI == null) { // a blank resource
                continue;
            }
            lid = resourceURI;

            // what entity is it?
            if (checkRecord(lid, r1)) {
                entitiesFound += 16;
            }
            if (checkAgent(lid, r1)) {
                entitiesFound += 8;
            }
            if (checkBusiness(lid, r1)) {
                entitiesFound += 4;
            }
            if (checkMandate(lid, r1)) {
                entitiesFound += 2;
            }
            if (checkRelationship(lid, r1, true)) {
                entitiesFound += 1;
            }

            // found a named object that is not one of the ANZS5478 entities...
            if (entitiesFound == 0) {
                addWarning(new VEOFailure(CLASSNAME, "checkANZSProperties", 3, id, lid + ": has no named resource that is an anzs5478:Record, anzs5478:Agent, anzs5478:Business, anzs5478:Mandate, or anzs5478:Relationship entity"));
            }
        }
        // found a named object that is not one of the ANZS5478 entities...
        if (entitiesFound < 16) {
            addError(new VEOFailure(CLASSNAME, "checkANZSProperties", 4, id, "Metadata package does not contain an anzs5478:Record"));
        }

        ri.close();
    }

    /**
     * Check if an anzs5478:Record element exists exists in a resource
     *
     * @param lid the local identifier of the resource (used for error mesgs)
     * @param r1 the resource
     */
    private boolean checkRecord(String lid, Resource r1) {
        StmtIterator si;
        Resource r2;
        int count;
        String nlid;

        nlid = lid + "/anzs5478:Record";
        si = r1.listProperties(ResourceFactory.createProperty(anzs5478NameSpace.value, "Record"));
        count = 0;
        while (si.hasNext()) {
            if ((r2 = getResource(si)) == null) {
                addError("checkRecord", 1, nlid + " is empty or blank");
                continue;
            }
            count++;
            checkEntityType(nlid, r2, "Record");
            checkCategory(nlid, r2);
            checkIdentifier(nlid, r2);
            checkName(nlid, r2);
            checkDateRange(nlid, r2);
            checkDisposal(nlid, r2);
            // format is not checked as it has no use in VERS V3 VEOs
            checkExtent(nlid, r2);
            checkRelationship(nlid, r2, false);
            checkContextPath(nlid, r2);
        }
        if (count > 1) {
            addError("checkRecord", 3, lid + "/rdf:Description contains multiple anzs5478:Record elements");
        }
        return count > 0;
    }

    /**
     * Check if anzs5478:Agent element exists exists in a resource
     *
     * @param lid the local identifier of the resource (used for error mesgs)
     * @param r1 the resource
     */
    private boolean checkAgent(String lid, Resource r1) {
        StmtIterator si;
        Resource r2;
        int count;
        String nlid;

        nlid = lid + "/anzs5478:Agent";
        si = r1.listProperties(ResourceFactory.createProperty(anzs5478NameSpace.value, "Agent"));
        count = 0;
        while (si.hasNext()) {
            if ((r2 = getResource(si)) == null) {
                addError("checkAgent", 1, nlid + " is empty or blank");
                continue;
            }
            count++;
            checkEntityType(nlid, r2, "Agent");
            checkCategory(nlid, r2);
            checkIdentifier(nlid, r2);
            checkName(nlid, r2);
            checkDateRange(nlid, r2);
        }
        if (count > 1) {
            addError("checkAgent", 2, lid + " contains multiple anzs5478:Agent elements");
        }
        return count > 0;
    }

    /**
     * Check if anzs5478:Business element exists exists in a resource
     *
     * @param lid the local identifier of the resource (used for error mesgs)
     * @param r1 the resource
     */
    private boolean checkBusiness(String lid, Resource r1) {
        StmtIterator si;
        Resource r2;
        int count;
        String nlid;

        nlid = lid + "/anzs5478:Business";
        si = r1.listProperties(ResourceFactory.createProperty(anzs5478NameSpace.value, "Business"));
        count = 0;
        while (si.hasNext()) {
            if ((r2 = getResource(si)) == null) {
                addError("checkBusiness", 1, nlid + " is empty or blank");
                continue;
            }
            count++;
            checkEntityType(nlid, r2, "Business");
            checkCategory(nlid, r2);
            checkIdentifier(nlid, r2);
            checkName(nlid, r2);
            checkDateRange(nlid, r2);
        }
        if (count > 1) {
            addError("checkBusiness", 2, lid + " contains multiple anzs5478:Business elements");
        }
        return count > 0;
    }

    /**
     * Check if anzs5478:Mandate element exists exists in a resource
     *
     * @param lid the local identifier of the resource (used for error mesgs)
     * @param r1 the resource
     */
    private boolean checkMandate(String lid, Resource r1) {
        StmtIterator si;
        Resource r2;
        int count;
        String nlid;

        nlid = lid + "/anzs5478:Mandate";
        si = r1.listProperties(ResourceFactory.createProperty(anzs5478NameSpace.value, "Mandate"));
        count = 0;
        while (si.hasNext()) {
            if ((r2 = getResource(si)) == null) {
                addError("checkMandate", 1, nlid + " is empty or blank");
                continue;
            }
            count++;
            checkEntityType(nlid, r2, "Mandate");
            checkCategory(nlid, r2);
            checkIdentifier(nlid, r2);
            checkName(nlid, r2);
            checkDateRange(nlid, r2);
        }
        if (count > 1) {
            addError("checkMandate", 2, lid + " contains multiple anzs5478:Mandate elements");
        }
        return count > 0;
    }

    /**
     * Check if anzs5478:Relationship element exists in a resource
     *
     * @param lid the local identifier of the resource (used for error mesgs)
     * @param r1 the resource
     */
    private boolean checkRelationship(String lid, Resource r1, boolean multientity) {
        StmtIterator si;
        Resource r2;
        int count;
        String nlid;

        nlid = lid + "/anzs5478:Relationship";
        si = r1.listProperties(ResourceFactory.createProperty(anzs5478NameSpace.value, "Relationship"));
        count = 0;
        while (si.hasNext()) {
            if ((r2 = getResource(si)) == null) {
                addError("checkRelationship", 1, nlid + " is empty or blank");
                continue;
            }
            count++;
            checkEntityType(nlid, r2, "Relationship");
            checkCategory(nlid, r2);
            checkIdentifier(nlid, r2);
            checkName(nlid, r2);
            checkDateRange(nlid, r2);
            if (multientity) {
                checkRelatedEntityMultiEntity(nlid, r2);
            } else {
                checkRelatedEntityOneEntity(nlid, r2);
            }
        }
        return count > 0;
    }

    /**
     * Check that exactly one anzs5478:EntityType element exists and its value
     * matches the parent entity.
     *
     * @param r resource
     * @param entityType entityType being looked for
     */
    private void checkEntityType(String lid, Resource r, String entityType) {
        if (checkLeafProperty(r, anzs5478NameSpace, "EntityType", 1, 1, "checkEntityType", lid) == null) {
            return;
        }
        if (!checkExactValue(r, anzs5478NameSpace, "EntityType", entityType)) {
            addError("checkEntityType", 1, lid + "/anzs5478:EntityType must have value '" + entityType + "'");
        }
    }

    /**
     * Check that exactly one anzs5478:Category element exists
     *
     * @param r resource
     * @param entityType entityType being looked for
     */
    private void checkCategory(String lid, Resource r) {
        checkLeafProperty(r, anzs5478NameSpace, "Category", 1, 1, "checkCategory", lid);
    }

    /**
     * Check that one or more anzs5478:Identifier elements exists, each with at
     * exactly one anzs5478:IdentifierString element
     *
     * @param r1 resource
     * @param entityType entityType being looked for
     */
    private void checkIdentifier(String lid, Resource r1) {
        StmtIterator si;
        Resource r2;
        boolean found;
        String nlid;

        nlid = lid + "/anzs5478:Identifier";
        si = r1.listProperties(ResourceFactory.createProperty(anzs5478NameSpace.value, "Identifier"));
        found = false;
        while (si.hasNext()) {
            if ((r2 = getResource(si)) == null) {
                addError("checkIdentifier", 2, nlid + " was empty or blank");
                continue;
            }
            found = true;
            checkLeafProperty(r2, anzs5478NameSpace, "IdentifierString", 1, 1, "checkIdentifier", nlid);
        }
        if (!found) {
            addError("checkIdentifier", 3, lid + " must contain an anzs5478:Identifier");
        }
    }

    /**
     * Check that one or more anzs5478:Name elements exists, each with at exctly
     * one anzs5478:NameWords element
     *
     * @param r1 resource
     * @param entityType entityType being looked for
     */
    private void checkName(String lid, Resource r1) {
        StmtIterator si;
        Resource r2;
        boolean found;
        String nlid;

        nlid = lid + "/anzs5478:Name";
        si = r1.listProperties(ResourceFactory.createProperty(anzs5478NameSpace.value, "Name"));
        found = false;
        while (si.hasNext()) {
            if ((r2 = getResource(si)) == null) {
                addError("checkName", 2, nlid + " is empty or blank");
                continue;
            }
            found = true;
            checkLeafProperty(r2, anzs5478NameSpace, "NameWords", 1, 1, "checkName", nlid);
        }
        if (!found) {
            addError("checkName", 3, lid + " must contain an anzs5478:Name");
        }
    }

    /**
     * Check that exactly one anzs5478:DateRange element exists, with exctly one
     * anzs5478:StartDate element
     *
     * @param r1 resource
     * @param entityType entityType being looked for
     */
    private void checkDateRange(String lid, Resource r1) {
        StmtIterator si;
        Resource r2;
        boolean found;
        String nlid;

        nlid = lid + "/anzs5478:DateRange";
        si = r1.listProperties(ResourceFactory.createProperty(anzs5478NameSpace.value, "DateRange"));
        found = false;
        while (si.hasNext()) {
            if (found) {
                addError("checkDateRange", 1, lid + " contains multiple anzs5478:DateRanges");
            }
            if ((r2 = getResource(si)) == null) {
                addError("checkDateRange", 2, nlid + " is empty or blank");
                continue;
            }
            found = true;
            checkLeafProperty(r2, anzs5478NameSpace, "StartDate", 1, 1, "checkDateRange", nlid);
        }
        if (!found) {
            addError("checkDateRange", 3, lid + " must contain an anzs5478:DateRange");
        }
    }

    /**
     * Check that exactly one anzs5478:Disposal element exists, with exctly one
     * anzs5478:RecordsAuthority element. If the records authority is anything
     * other than 'No Disposal Coverage', check if exactly one anzs5478:
     *
     * @param r1 resource
     * @param entityType entityType being looked for
     */
    private void checkDisposal(String lid, Resource r1) {
        StmtIterator si;
        Resource r2;
        boolean found;
        String s, nlid;

        nlid = lid + "/anzs5478:Disposal";
        si = r1.listProperties(ResourceFactory.createProperty(anzs5478NameSpace.value, "Disposal"));
        found = false;
        while (si.hasNext()) {
            if (found) {
                addError("checkDisposal", 1, lid + " contains multiple anzs5478:Disposal");
            }
            if ((r2 = getResource(si)) == null) {
                addError("checkDisposal", 2, nlid + " is empty or blank");
                continue;
            }
            found = true;
            // for some reason, V3S5 lists 'RetentionAndDisposalAuthority'
            // instead of 'RecordsAuthority'. Accept both.
            if ((s = checkLeafProperty(r2, anzs5478NameSpace, "RecordsAuthority", 0, 1, "checkDisposal", nlid)) == null) {
                if ((s = checkLeafProperty(r2, anzs5478NameSpace, "RetentionAndDisposalAuthority", 0, 1, "checkDisposal", nlid)) == null) {
                    addError("checkDisposal", 3, lid + " must contain an anzs5478:RecordsAuthority (or anzs5478:RetentionAndDisposalAuthority)");
                }
            }
            if (s != null && !s.equals("No Disposal Coverage")) {
                checkLeafProperty(r2, anzs5478NameSpace, "DisposalClassID", 1, 1, "checkDisposal", nlid);
                checkLeafProperty(r2, anzs5478NameSpace, "DisposalAction", 1, 1, "checkDisposal", nlid);
                // technically, should check DisposalTriggerDate, and DisposalActionDue, but these are messy and have little value
            }
        }
        if (!found) {
            addError("checkDisposal", 4, lid + " must contain one anzs5478:Disposal");
        }
    }

    /**
     * Check that one or more anzs5478:Extent elements exists, each with at
     * exactly one anzs5478:LogicalSize and anzs5478:Units elements, but no
     * anzs5478:PhysicalDimensions or anzs5478:Quantity elements
     *
     * @param r1 resource
     * @param entityType entityType being looked for
     */
    private void checkExtent(String lid, Resource r1) {
        StmtIterator si;
        Resource r2;
        boolean found;
        String nlid;

        nlid = lid + "/anzs5478:Extent";
        si = r1.listProperties(ResourceFactory.createProperty(anzs5478NameSpace.value, "Extent"));
        found = false;
        while (si.hasNext()) {
            if ((r2 = getResource(si)) == null) {
                addError("checkExtent", 2, nlid + " is empty or blank");
                continue;
            }
            found = true;
            checkLeafProperty(r2, anzs5478NameSpace, "PhysicalDimensions", 0, 0, "checkExtent", nlid);
            checkLeafProperty(r2, anzs5478NameSpace, "LogicalSize", 1, 1, "checkExtent", nlid);
            checkLeafProperty(r2, anzs5478NameSpace, "Quantity", 0, 0, "checkExtent", nlid);
            checkLeafProperty(r2, anzs5478NameSpace, "Units", 1, 1, "checkExtent", nlid);
        }
        if (!found) {
            addError("checkExtent", 3, lid + " must contain at least one anzs5478:Extent");
        }
    }

    /**
     * Check anzs5478:RelatedEntity elements in a one entity model. In this
     * model the related entity is included within the Related Entity.
     *
     * @param r1 resource
     * @param entityType entityType being looked for
     */
    private void checkRelatedEntityOneEntity(String lid, Resource r1) {
        StmtIterator si;
        Resource r2;
        boolean found;
        int count;
        String nlid;

        nlid = lid + "/anzs5478:RelatedEntity";
        si = r1.listProperties(ResourceFactory.createProperty(anzs5478NameSpace.value, "RelatedEntity"));
        found = false;
        while (si.hasNext()) {
            if (found) {
                addError("checkRelatedEntity", 1, lid + ": contains multiple anzs5478:RelatedEntity elements");
            }
            if ((r2 = getResource(si)) == null) {
                addError("checkRelatedEntity", 2, nlid + " is empty or blank");
                continue;
            }
            found = true;
            count = 0;
            if (checkPropertyExists(r2, anzs5478NameSpace, "AssignedEntityID")) {
                count++;
                checkLeafProperty(r2, anzs5478NameSpace, "AssignedEntityID", 1, 1, "checkRelatedEntityOneEntity", nlid);
            }
            if (checkAgent(nlid, r2)) {
                count++;
            }
            if (checkBusiness(nlid, r2)) {
                count++;
            }
            if (checkMandate(nlid, r2)) {
                count++;
            }
            if (count == 0) {
                addError("checkRelatedEntity", 3, nlid + " must contain an anzs5478:AssignedEntityID, anzs5478:Agent, anzs5478:Business, or anzs5478:Mandate (or the content of the element was empty)");
            }
            if (count > 1) {
                addError("checkRelatedEntity", 4, nlid + " contains multiple anzs5478:AssignedEntityID, anzs5478:Agent, anzs5478:Business, or anzs5478:Mandate");
            }
            if (!checkExactValue(r2, anzs5478NameSpace, "RelationshipRole", "2")) {
                addError("checkRelatedEntity", 5, nlid + "/anzs5478:RelationshipRole must have value '2'");
            }
        }
        if (!found) {
            addError("checkRelatedEntity", 6, "ANZS5478 metadata package " + lid + " must contain one anzs5478:RelatedEntity");
        }
    }

    /**
     * Check anzs5478:RelatedEntity elements in a multi entity model. In this
     * case the Related Entity contains the ids of both related entities
     *
     * @param r1 resource
     * @param entityType entityType being looked for
     */
    private void checkRelatedEntityMultiEntity(String lid, Resource r1) {
        StmtIterator si;
        Resource r2;
        boolean fromFound, toFound;
        String s;
        String nlid;

        nlid = lid + "/anzs5478:RelatedEntity";
        si = r1.listProperties(ResourceFactory.createProperty(anzs5478NameSpace.value, "RelatedEntity"));
        fromFound = false;
        toFound = false;
        while (si.hasNext()) {
            if ((r2 = getResource(si)) == null) {
                addError("checkRelatedEntityMultiEntity", 1, lid + " is empty or blank");
                continue;
            }
            s = checkLeafProperty(r2, anzs5478NameSpace, "RelationshipRole", 1, 1, "checkRelatedEntityMultiEntity", nlid);
            switch (s) {
                case "1":
                    if (toFound) {
                        addError("checkRelatedEntityMultiEntity", 2, nlid + " has multiple RelationshipRole with the value '1'");
                    }
                    toFound = true;
                    break;
                case "2":
                    if (fromFound) {
                        addError("checkRelatedEntityMultiEntity", 3, nlid + " has multiple RelationshipRole with the value '2'");
                    }
                    fromFound = true;
                    break;
                default:
                    addError("checkRelatedEntityMultiEntity", 4, nlid + "/anzs5478:RelationshipRole has an invalid value '" + s + "'");
                    break;
            }
        }
        if (!toFound) {
            addError("checkRelatedEntityMultiEntity", 5, lid + " does not contain an anzs5478:RelatedEntity with a anzs5478:RelationshipRole with a value '2'");
        }
        if (!fromFound) {
            addError("checkRelatedEntityMultiEntity", 5, lid + " does not contain an anzs5478:RelatedEntity with a anzs5478:RelationshipRole with a value '1'");
        }
    }

    /**
     * If a vers:contextPath exists, check that it has...
     *
     * @param r1 resource
     * @param entityType entityType being looked for
     */
    private void checkContextPath(String lid, Resource r1) {
        StmtIterator si;
        Resource r2;
        String nlid;

        nlid = lid + "/versterms:contextPath";
        si = r1.listProperties(ResourceFactory.createProperty(versNameSpace.value, "contextPath"));
        while (si.hasNext()) {
            if ((r2 = getResource(si)) == null) {
                addError("checkContextPath", 2, nlid + " is empty or blank");
                continue;
            }
            checkLeafProperty(r2, versNameSpace, "ContextPathDomain", 1, 1, "checkContextPath", nlid);
            checkLeafProperty(r2, versNameSpace, "ContextPathValue", 1, 1, "checkContextPath", nlid);
        }
    }

    /**
     * Check the mandatory properties in an AGLS instance. Missing mandatory
     * properties are flagged as errors. Missing conditional properties are
     * flagged as warnings. The value of a property is not checked for
     * conformance.
     *
     * Five properties (aglsterms:dateLicensed, aglsterms:aggregationLevel,
     * aglsterms:category, aglsterms:documentType, and aglsterms:serviceType)
     * originally had the wrong namespace prefix (dcterms) in the specification.
     * The validation has been altered to *warn* if the incorrect properties are
     * present, rather than flag an error.
     */
    private void checkAGLSProperties(boolean noRec) {
        ResIterator ri;
        boolean foundRecord;
        Resource r;

        // System.out.println(rdfModel2String());
        // get all resources
        // get all resources
        ri = rdfModel.listSubjects();
        if (!ri.hasNext()) {
            addError(new VEOFailure(CLASSNAME, "checkAGLSProperties", 1, id, "metadata contained no rdf:Description, or only empty rdf:Description"));
            return;
        }

        // step through the resources that have subjects. We are only interested
        // in the named resources (that have an rdf:about attribute)
        foundRecord = false;
        while (ri.hasNext()) {
            r = ri.nextResource();
            resourceURI = r.getURI();
            if (resourceURI == null) { // a blank resource
                continue;
            }

            // were there two named resources in the metadata package?
            if (foundRecord) {
                addError(new VEOFailure(CLASSNAME, "checkAGLSProperties", 16, id, "AGLS metadata package has two (or more) rdf:Description containing different rdf:about attributes"));
            }
            foundRecord = true;

            /* for debugging horrible RDF
            System.out.println(r.toString());
            StmtIterator si = r.listProperties();
            while (si.hasNext()) {
                Statement s = si.nextStatement();
                System.out.println(s.getPredicate()+"->"+s.getString());
            }
             */
            // DC_TERMS:creator
            testLeafProperty(r, "rdf:Description", dcNameSpace, "creator", "checkAGLSProperties", 2, WhatToDo.errorIfMissing);

            // DC_TERMS:date m format YYYY-MM-DD (available, created, dateCopyrighted, dateLicensed, issued, modified, valid) see AGLS Usage Guide for valid schemas and formats.
            if (!containsLeafProperty(r, dcNameSpace, "date", true)
                    && !containsLeafProperty(r, dcNameSpace, "available", true)
                    && !containsLeafProperty(r, dcNameSpace, "created", true)
                    && !containsLeafProperty(r, dcNameSpace, "dateCopyrighted", true)
                    && !containsLeafProperty(r, aglsNameSpace, "dateLicensed", true)
                    && !containsLeafProperty(r, dcNameSpace, "dateLicensed", true) // error in VERSV3 spec, see below
                    && !containsLeafProperty(r, dcNameSpace, "issued", true)
                    && !containsLeafProperty(r, dcNameSpace, "modified", true)
                    && !containsLeafProperty(r, dcNameSpace, "valid", true)) {
                addError(new VEOFailure(CLASSNAME, "checkAGLSProperties", 3, id, "AGLS metadata package does not contain the mandatory dcterms:date or its subelements (dcterms:available, dcterms:created, dcterms:dateCopyrighted, aglsterms:dateLicensed, dcterms:issued, dcterms:modified, or dcterms:valid) or the element was empty"));
            }

            // This was an error in the VERSV3 spec, DateLicensed has the wrong namespace. Warn about it, but not mark it as an error
            if (containsLeafProperty(r, dcNameSpace, "dateLicensed", false)) {
                addWarning(new VEOFailure(CLASSNAME, "checkAGLSProperties", 4, id, "AGLS metadata package contains 'dcterms:dateLicensed' not 'aglsterms:dateLicensed'. This was an error in the specification. The VEO should be fixed."));
            }

            // DC_TERMS:title m
            testLeafProperty(r, "rdf:Description", dcNameSpace, "title", "checkAGLSProperties", 5, WhatToDo.errorIfMissing);

            // DC_TERMS:availability m for offline resources (can't test conditional)
            // DC_TERMS:identifier m for online resources (can't test conditional)
            testLeafProperty(r, "rdf:Description", dcNameSpace, "identifier", "checkAGLSProperties", 2, WhatToDo.errorIfMissing);

            // DC_TERMS:publisher m for information resources  (can't test conditional)
            testLeafProperty(r, "rdf:Description", dcNameSpace, "publisher", "checkAGLSProperties", 2, WhatToDo.errorIfMissing);

            // DC_TERMS:description r
            testLeafProperty(r, "rdf:Description", dcNameSpace, "description", "checkAGLSProperties", 6, WhatToDo.warningIfMissing);

            // DC_TERMS:function r
            // DC_TERMS:subject r if function not present
            if (!noRec && !containsLeafProperty(r, aglsNameSpace, "function", true) && !containsLeafProperty(r, dcNameSpace, "subject", true)) {
                addWarning(new VEOFailure(CLASSNAME, "checkAGLSProperties", 7, id, "AGLS metadata package does not contain the recommended function element (aglsterms:function or dcterms:subject)"));
            }

            // DC_TERMS:language r if not in English (can't test conditional)
            // DC_TERMS:type r (aggregationLevel, category, documentType, serviceType)
            if (!noRec && !containsLeafProperty(r, dcNameSpace, "type", true)
                    && !containsLeafProperty(r, aglsNameSpace, "aggregationLevel", true)
                    && !containsLeafProperty(r, dcNameSpace, "aggregationLevel", true) // mistake in the standard - invalid namespace
                    && !containsLeafProperty(r, aglsNameSpace, "category", true)
                    && !containsLeafProperty(r, dcNameSpace, "category", true) // mistake in the standard - invalid namespace
                    && !containsLeafProperty(r, aglsNameSpace, "documentType", true)
                    && !containsLeafProperty(r, dcNameSpace, "documentType", true) // mistake in the standard - invalid namespace
                    && !containsLeafProperty(r, aglsNameSpace, "serviceType", true)
                    && !containsLeafProperty(r, dcNameSpace, "serviceType", true)) { // mistake in the standard - invalid namespace
                addWarning(new VEOFailure(CLASSNAME, "checkAGLSProperties", 8, id, "AGLS metadata package does not contain the recommended type property (dcterms:type) or one of the subproperties (aglsterms:aggregationLevel, aglsterms:category, aglsterms:documentType, or aglsterms:serviceType) or the element was emtpy"));
            }
            // This was an error in the VERSV3 spec, the subtypes have the wrong namespace. Warn about it, but not mark it as an error
            if (containsLeafProperty(r, dcNameSpace, "aggregationLevel", false)) {
                addWarning(new VEOFailure(CLASSNAME, "checkAGLSProperties", 9, id, "AGLS metadata package contains 'dcterms:aggregationLevel' not 'aglsterms:aggregationLevel'. This was an error in the specification. The VEO should be fixed."));
            }
            if (containsLeafProperty(r, dcNameSpace, "category", false)) {
                addWarning(new VEOFailure(CLASSNAME, "checkAGLSProperties", 10, id, "AGLS metadata package contains 'dcterms:category' not 'aglsterms:category'. This was an error in the specification. The VEO should be fixed."));
            }
            if (containsLeafProperty(r, dcNameSpace, "documentType", false)) {
                addWarning(new VEOFailure(CLASSNAME, "checkAGLSProperties", 11, id, "AGLS metadata package contains 'dcterms:documentType' not 'aglsterms:documentType'. This was an error in the specification. The VEO should be fixed."));
            }
            if (containsLeafProperty(r, dcNameSpace, "serviceType", false)) {
                addWarning(new VEOFailure(CLASSNAME, "checkAGLSProperties", 12, id, "AGLS metadata package contains 'dcterms:serviceType' not 'aglsterms:serviceType'. This was an error in the specification. The VEO should be fixed."));
            }
            // warn if disposal metadata is not present...
            if (!noRec && !containsLeafProperty(r, versNameSpace, "disposalReviewDate", true) && !containsLeafProperty(r, versNameSpace, "disposalCondition", true)) {
                addWarning(new VEOFailure(CLASSNAME, "checkAGLSProperties", 13, id, "AGLS metadata package does not contain either the disposal review date or disposal condition properties (versterms:disposalReviewDate or versterms:disposalCondition)"));
            }
            if (!noRec && !containsLeafProperty(r, versNameSpace, "disposalAction", true)) {
                addWarning(new VEOFailure(CLASSNAME, "checkAGLSProperties", 14, id, "AGLS metadata package does not contain the disposal action property (vers:disposalAction)"));
            }
            if (!noRec && !containsLeafProperty(r, versNameSpace, "disposalReference", true)) {
                addWarning(new VEOFailure(CLASSNAME, "checkAGLSProperties", 15, id, "AGLS metadata package does not contain the disposal reference property (vers:disposalReference)"));
            }
        }
    }

    /**
     * Test for a leaf property in the metadata. If test value is true, the
     * value of the property has to be non empty as well
     *
     * @param r the RDF resource that should contain the property
     * @param nameSpace the namespace the property exists within
     * @param element the property (element) name within the namespace
     * @param notEmpty true if the value has to be non-empty
     * @return whether the property exists
     */
    private boolean containsLeafProperty(Resource r, Namespace nameSpace, String element, boolean notEmpty) {
        Statement stmt;

        stmt = r.getProperty(ResourceFactory.createProperty(nameSpace.value, element));
        if (stmt == null) {
            return false;
        }
        if (notEmpty) {
            if (stmt.getString().trim().equals("")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Test for a leaf property in the metadata, and return its value. A
     * VEOFailure is created if the property doesn't exist, or is empty. If the
     * property is mandatory, this VEOFailure is recorded as an error, otherwise
     * it is recorded as a warning. If the property doesn't exist or is empty, a
     * null is returned.
     *
     * @param r the RDF resource that should contain the property
     * @parem parent the element name of the parent
     * @param nameSpace the namespace the property exists within
     * @param element the property (element) name within the namespace
     * @param method the method testing the property (used to make a unique id)
     * @param errno the error number assigned by the method (must be less than
     * 50)
     * @param entity the type of metadata entity (may be null)
     * @param wtd what to do if property is missing or empty
     * @return the string value of the property (null if not present or empty)
     */
    private enum WhatToDo {
        errorIfMissing, // report an error if property is missing or empty
        warningIfMissing, // report a warning if property is missing or empty
        justReturnNull      // just return null if property is missing or empty
    }

    private String testLeafProperty(Resource r, String parent, Namespace nameSpace, String element, String method, int errno, WhatToDo wtd) {
        Statement stmt;
        String s;

        assert errno < 50;
        // System.out.print("Testing for "+p.toString());
        stmt = r.getProperty(ResourceFactory.createProperty(nameSpace.value, element));
        if (stmt == null) {
            // System.out.println("- Didn't find it");
            createMesg(method, errno, parent, false, nameSpace.handle + ":" + element, wtd);
            return null;
        }
        s = stmt.getString();
        if (s.trim().equals("")) {
            createMesg(method, errno + 50, parent, true, nameSpace.handle + ":" + element, wtd);
            return null;
        }
        return s.trim();
    }

    /**
     * Check whether the required number of specified elements exist in a
     * resource. The value of the last instance is returned, or null if the last
     * element was empty or blank.
     *
     * @param method the calling method (for error messages)
     * @param r1 resource
     * @param lid the id of the parent element (for error messages)
     * @param element the element being looked for
     * @param min the minimum number of elements that must be present
     * @param max the maximum number of elements that is allowed
     * @return the value actually found
     */
    private String checkLeafProperty(Resource r1, Namespace namespace, String element, int min, int max, String method, String lid) {
        StmtIterator si;
        String s;
        int numFound;

        si = r1.listProperties(ResourceFactory.createProperty(namespace.value, element));
        numFound = 0;
        s = null;
        while (si.hasNext()) {
            numFound++;
            if ((s = getValue(si)) == null) {
                addError(method, 11, lid + "/" + namespace.handle + ":" + element + " is empty or blank");
            }
        }
        if (numFound > 0 && min == 0 && max == 0) {
            addError(method, 12, lid + " contains an " + namespace.handle + ":" + element);
        }
        if (numFound == 0 && min == 1) {
            if (max == 1) {
                addError(method, 13, lid + " must contain an " + namespace.handle + ":" + element);
            } else {
                addError(method, 13, lid + " must contain at least one " + namespace.handle + ":" + element);
            }
        }
        if (numFound > 1 && max == 1) {
            addError(method, 14, lid + ":  contains more than one " + namespace.handle + ":" + element);
        }
        return s;
    }

    /**
     * Check whether the required number of specified anzs5478 elements exist in
     * the resource.
     *
     * @param r1 resource
     * @param element the element being looked for
     * @return true if the property exists in the resource
     */
    private boolean checkPropertyExists(Resource r1, Namespace namespace, String element) {
        StmtIterator si;

        si = r1.listProperties(ResourceFactory.createProperty(namespace.value, element));
        return si.hasNext();
    }

    /**
     * Check that exactly one anzs5478:element of the given element type and
     * value exists in the parent element
     *
     * @param r1 resource
     * @param element the element being looked for
     * @param value the value being looked for
     * @return true if the element with the specified value exists
     */
    private boolean checkExactValue(Resource r1, Namespace namespace, String element, String value) {
        StmtIterator si;
        String s;

        si = r1.listProperties(ResourceFactory.createProperty(namespace.value, element));
        while (si.hasNext()) {
            s = getValue(si);
            if (s != null && s.equals(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the current value of the statement iterator
     *
     * @param si
     * @return string value
     */
    private String getValue(StmtIterator si) {
        Statement stmt;
        String s;

        try {
            stmt = si.nextStatement();
            s = stmt.getString();
            if (s == null || s.trim().equals("")) {
                return null;
            }
        } catch (NoSuchElementException nsee) {
            return null;
        } catch (Exception e) { // grrr. getString() just says 'exception' if not literal
            return null;
        }
        return s;
    }

    /**
     * Get the current resource of the statement iterator
     *
     * @param si
     * @return string value
     */
    private Resource getResource(StmtIterator si) {
        Statement stmt;
        Resource r;

        try {
            stmt = si.nextStatement();
            r = stmt.getResource();
        } catch (NoSuchElementException nsee) {
            return null;
        } catch (Exception e) { // grrr. getString() just says 'exception' if not literal
            return null;
        }
        return r;
    }

    private void addError(String method, int errorNo, String message) {
        addError(new VEOFailure(CLASSNAME, method, errorNo, id, message));
    }

    /**
     * Create a standard error message for complaining about an ANZS5478 problem
     *
     * @param method method calling
     * @param errno unique error identifier in the method
     * @param parent the XML tag of the parent
     * @param child the XML tag of the property
     * @param wtd what to do if property is missing or empty
     */
    private void createMesg(String method, int errno, String parent, boolean isEmpty, String child, WhatToDo wtd) {
        VEOFailure vf;

        if (wtd == WhatToDo.justReturnNull) {
            return;
        }
        if (isEmpty) {
            vf = new VEOFailure(CLASSNAME, method, errno, id, child + " is empty (metadata package '" + resourceURI + "')");
        } else {
            vf = new VEOFailure(CLASSNAME, method, errno, id, child + " is not present in " + parent + " (metadata package '" + resourceURI + "')");
        }
        if (wtd == WhatToDo.errorIfMissing) {
            addError(vf);
        } else if (wtd == WhatToDo.warningIfMissing) {
            addWarning(vf);
        }
    }

    /**
     * Free resources associated with this metadata package.
     */
    @Override
    public void abandon() {
        super.abandon();
        schemaId.abandon();
        schemaId = null;
        syntaxId.abandon();
        syntaxId = null;
        metadata.clear();
        metadata = null;
        if (rdfModel != null) {
            rdfModel.removeAll();
        }
        rdfModel = null;
    }

    /**
     * Check if this object has any errors?
     *
     * @return true if errors have been detected
     */
    @Override
    public boolean hasErrors() {
        hasErrors |= schemaId.hasErrors() | syntaxId.hasErrors();
        return hasErrors;
    }

    /**
     * Build a list of all of the errors generated by this RepnMetadataPackage
     *
     * @param returnErrors if true return errors, otherwise return warnings
     * @param l list in which to place the errors/warnings
     */
    @Override
    public void getProblems(boolean returnErrors, List<VEOFailure> l) {
        super.getProblems(returnErrors, l);
        schemaId.getProblems(returnErrors, l);
        syntaxId.getProblems(returnErrors, l);
    }

    /**
     * Has this object (or its children) any warnings?
     *
     * @return true if errors have been detected
     */
    @Override
    public boolean hasWarnings() {
        hasWarnings |= schemaId.hasWarnings() | syntaxId.hasWarnings();
        return hasWarnings;
    }

    /**
     * Produce a string representation of the Metadata Package
     *
     * @return The string representation
     */
    @Override
    public String toString() {
        StringBuffer sb;
        int i;

        sb = new StringBuffer();
        sb.append("   Metadata Package\n   Schema:");
        sb.append(schemaId);
        sb.append(" Syntax:");
        sb.append(syntaxId);
        sb.append("\n");
        for (i = 0; i < metadata.size(); i++) {
            sb.append(RepnXML.prettyPrintNode(metadata.get(i), 4));
        }
        return sb.toString();
    }

    /**
     * Generate a HTML representation of the metadata package.
     *
     * @param verbose true if additional information is to be generated
     * @param writer where to write the output
     */
    public void genReport(boolean verbose, Writer w) {
        Node n;
        int i;

        this.w = w;
        startDiv("MetaPackage", null);
        addLabel("Metadata Package");
        addString(" (Schema: '" + schemaId.getValue() + "',");
        addString(" Syntax: '" + syntaxId.getValue() + "')");
        addString("\n");
        if (hasErrors || hasWarnings) {
            addTag("<ul>\n");
            listIssues();
            schemaId.genReport(verbose, w);
            syntaxId.genReport(verbose, w);
            addTag("</ul>\n");
        }

        // if metadata was RDF...
        addTag("<br>");
        if (rdf) {
            //startDiv("RDF", null);
            addRDF();
            //endDiv();

            // otherwise treat it as normal XML
        } else {
            //startDiv("XML", null);
            for (i = 0; i < metadata.size(); i++) {
                n = metadata.get(i);
                n.normalize(); // make sure adjacent text nodes are coallesced.
                addTag("<ul>\n");
                addXML(n, 2);
                addTag("</ul>\n");
            }
            //endDiv();
        }
        endDiv();
    }

    /**
     * Generate a HTML representation of a (XML) DOM node (and its children).
     *
     * @param n the node
     * @param depth indent
     */
    public void addXML(Node n, int depth) {
        NodeList nl;
        NamedNodeMap at;
        Node node;
        int i;
        String v;
        boolean hasSubElements;

        // sanity check
        if (n == null) {
            return;
        }

        switch (n.getNodeType()) {
            // element node
            case Node.ELEMENT_NODE:
                addTag("<li>");
                addLabel("Element: ");
                addString(n.getNodeName());

                // process attributes
                if (n.hasAttributes()) {
                    at = n.getAttributes();
                    for (i = 0; i < at.getLength(); i++) {
                        node = at.item(i);
                        addString(node.getNodeName());
                        addString("=\"");
                        addString(node.getNodeValue());
                        addString("\" ");
                    }
                }

                // process subnodes
                if (n.hasChildNodes()) {
                    nl = n.getChildNodes();

                    // if sub-elements occur, start a sublist...
                    hasSubElements = false;
                    for (i = 0; i < nl.getLength() && !hasSubElements; i++) {
                        if (nl.item(i).getNodeType() == Node.ELEMENT_NODE) {
                            hasSubElements = true;
                        }
                    }
                    if (hasSubElements) {
                        addTag("<br>\n<ul>\n");
                    }
                    for (i = 0; i < nl.getLength(); i++) {
                        addXML(nl.item(i), depth + 1);
                    }
                    if (hasSubElements) {
                        addTag("</ul>\n");
                    }
                } else {
                    addString(" (Empty element)");
                }
                addTag("</li>\n");
                break;

            // text node...
            case Node.TEXT_NODE:
                v = n.getNodeValue();

                // ignore text nodes that are just white space
                if (v == null || v.trim().equals("")) {
                    break;
                }
                addLabel(" Value:");
                addString("'" + v.trim() + "'");
                break;
            default:
                addLabel(Short.toString(n.getNodeType()));
                addString(" ");
                addString(n.getNodeValue());
                addTag("<br>\n");
        }
    }

    /**
     * Generate a HTML representation of RDF. Actually, we just generate a
     * RDF/XML representation of the RDF and output that.
     */
    public String rdfModel2String() {
        // String syntax = "TURTLE";
        String syntax = "RDF/XML";
        // String syntax = "N-TRIPLE";
        StringWriter sw = new StringWriter();

        if (rdfModel == null) {
            return "Model was Null";
        }
        try {
            rdfModel.write(sw, syntax);
        } catch (BadURIException bue) {
            sw.append("Failed to generate RDF: ");
            sw.append(bue.getMessage());
            sw.append(" RepnMetadataPackage.addRDF()");
        }
        return sw.toString();
    }

    public void addRDF() {
        addTag("<pre>");
        addString(rdfModel2String());
        addTag("</pre>");
    }
}
