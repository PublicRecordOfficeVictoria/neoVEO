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
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.ResourceRequiredException;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdfxml.xmlinput.DOM2Model;
import com.hp.hpl.jena.shared.BadURIException;
import java.util.List;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
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
    private boolean rdf;    // true if the metadata package is RDF
    private Model rdfModel; // RDF model
    private final static java.util.logging.Logger LOG = java.util.logging.Logger.getLogger("VEOAnalysis.RepnMetadatPackage");

    // AGLS RDF properties
    private static final String DC_TERMS = "http://purl.org/dc/terms/";
    private static final String AGLS_TERMS = "http://www.agls.gov.au/agls/terms/";
    // static final String ANZS5478 = "http://www.prov.vic.gov.au/ANSZ5478/terms/";
    private static final String ANZS5478 = "http://www.prov.vic.gov.au/VERS-as5478";
    private static final String VERS_TERMS = "http://www.prov.vic.gov.au/vers/terms/";

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
        
        assert(document != null);
        assert(parentId != null);
        assert(seq > -1);

        metadata = new ArrayList<>();
        this.rdf = rdf;
        rdfModel = null;

        // VERS:MetadataSchemaIdentifier
        schemaId = new RepnItem(getId(), "Metadata schema id:", results);
        schemaId.setValue(document.getTextValue());
        document.gotoNextElement();
        // VERS:MetadataSyntaxIdentifier
        syntaxId = new RepnItem(getId(), "Metadata syntax id:", results);
        syntaxId.setValue(document.getTextValue());
        if (syntaxId.getValue().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns")) {
            if (!rdf) {
                addError(new VEOFailure(CLASSNAME, 1, "Error. Metadata Package has vers:MetadataSyntaxIdentifier of http://www.w3.org/1999/02/22-rdf-syntax-ns, but xmlns:rdf namespace attribute is not defined"));
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
     * @return true if the metadata package is AGLS or AGRKMS
     */
    public boolean validate(Path veoDir, boolean noRec) {
        
        assert(veoDir != null);

        // confirm that there is a non empty vers:MetadataSchemaIdentifier element
        if (schemaId.getValue() == null) {
            addError(new VEOFailure(CLASSNAME, "validate", 1, "vers:MetadataSchemaIdentifier element is missing or has a null value"));
            return false;
        }
        if (schemaId.getValue().trim().equals("") || schemaId.getValue().trim().equals(" ")) {
            addError(new VEOFailure(CLASSNAME, "validate", 2, "vers:MetadataSchemaIdentifier element is empty"));
            return false;
        }

        // confirm that there is a non empty vers:MetadataSyntaxIdentifier element
        if (syntaxId.getValue() == null) {
            addError(new VEOFailure(CLASSNAME, "validate", 3, "vers:MetadataSyntaxIdentifier element is missing or has a null value"));
            return false;
        }
        if (syntaxId.getValue().trim().equals("") || schemaId.getValue().trim().equals(" ")) {
            addError(new VEOFailure(CLASSNAME, "validate", 4, "vers:MetadataSyntaxIdentifier element is empty"));
            return false;
        }

        // if ANZS5478 check to see if the required properties are present and valid
        if (schemaId.getValue().endsWith("ANZS5478")
                || schemaId.getValue().equals("http://www.prov.vic.gov.au/VERS-as5478")) {
            if (!syntaxId.getValue().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns")) {
                addError(new VEOFailure(CLASSNAME, "validate", 5, "ANZS-5478 metadata must be represented as RDF with the syntax id 'http://www.w3.org/1999/02/22-rdf-syntax-ns'"));
                return false;
            }

            // we've seen the rdf:RDF definition, and the RDF parsing succeeded..
            if (rdf && parseRDF()) {
                rdfModel.setNsPrefix("anzs5478", ANZS5478);
                checkANZSProperties(noRec);
                return true;
            }
            return false;
        }

        // if AGLS check to see if the required properties are present and valid
        if (schemaId.getValue().endsWith("AGLS")
                || schemaId.getValue().equals("http://www.vic.gov.au/blog/wp-content/uploads/2013/11/AGLS-Victoria-2011-V4-Final-2011.pdf")) {
            if (!syntaxId.getValue().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns")) {
                addError(new VEOFailure(CLASSNAME, "validate", 6, "AGLS metadata must be represented as RDF with the syntax id 'http://www.w3.org/1999/02/22-rdf-syntax-ns'"));
                return false;
            }

            // we've seen the rdf:RDF definition, and the RDF parsing succeeded..
            if (rdf && parseRDF()) {
                rdfModel.setNsPrefix("dcterms", DC_TERMS);
                rdfModel.setNsPrefix("aglsterms", AGLS_TERMS);
                rdfModel.setNsPrefix("versterms", VERS_TERMS);
                checkAGLSProperties(noRec);
                return true;
            }
            return false;
        }
        return true;
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
    private boolean parseRDF() {
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
                    LOG.log(java.util.logging.Level.WARNING, VEOFailure.mesg(CLASSNAME, "parseRDF", 1, "Failed to initialise Jena to parse RDF", spe));
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
                    addError(new VEOFailure(CLASSNAME, "parseRDF", 2, parseErrs.toString().trim()));
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
            LOG.log(java.util.logging.Level.WARNING, VEOFailure.mesg(CLASSNAME, "parseRDF", 3, "Failed to close StringWriter used to capture parse errors", ioe));
        }
        return true;
    }

    /**
     * Check the properties in an ANZS5478 instance. Missing mandatory
     * properties are flagged as errors. Missing conditional properties are
     * flagged as warnings where the condition can be tested. The following is
     * NOT checked:
     * <ul>
     * <li>In general, the value of a property for conformance to a scheme</li>
     * <li>The existence of multiple instances of a property that cannot be
     * repeated</li>
     * <li>The existence of mandatory (or conditional) subproperties of optional
     * or conditional properties that are present</li>
     * <li>The presence of properties that are not defined in the standard
     * </ul>
     */
    static final Property ANZS_ENTITYTYPE = ResourceFactory.createProperty(ANZS5478, "EntityType");

    private void checkANZSProperties(boolean noRec) {
        ResIterator ri;
        Resource r;
        Statement stmt;

        // get all resources and step through them one at a time
        // note this is not correct, as it will not return resources that lack an Entity Type property
        ri = rdfModel.listResourcesWithProperty(ANZS_ENTITYTYPE);
        while (ri.hasNext()) {
            r = ri.nextResource();

            // get the value of the EntityType property
            stmt = r.getProperty(ANZS_ENTITYTYPE);
            switch (stmt.getString()) {
                case "Record":
                    checkCategory("Record", r);
                    checkIdentifier("Record", r);
                    checkName("Record", r);
                    checkDateRange("Record", r);
                    checkDisposal("Record", r);
                    checkFormat("Record", r);
                    checkExtent("Record", r);
                    break;
                case "Agent":
                    checkCategory("Agent", r);
                    checkIdentifier("Agent", r);
                    checkName("Agent", r);
                    checkDateRange("Agent", r);
                    break;
                case "Business":
                    checkCategory("Business", r);
                    checkIdentifier("Business", r);
                    checkName("Business", r);
                    checkDateRange("Business", r);
                    break;
                case "Mandate":
                    checkCategory("Mandate", r);
                    checkIdentifier("Mandate", r);
                    checkName("Mandate", r);
                    checkDateRange("Mandate", r);
                    break;
                case "Relationship":
                    checkCategory("Relationship", r);
                    checkIdentifier("Relationship", r);
                    checkName("Relationship", r);
                    checkDateRange("Relationship", r);
                    checkRelatedEntity("Relationship", r);
                    break;
                default:
                    addError(new VEOFailure(CLASSNAME, "checkANZSProperties", 1, "ANZS5478 resource does not have a valid Entity Type. Found: '" + stmt.getString() + "'. Expected 'Record', 'Agent', 'Business', 'Mandate', or 'Relationship'"));
                    break;
            }
        }
        ri.close();
    }

    static final Property ANZS_CATEGORY = ResourceFactory.createProperty(ANZS5478, "Category");

    /**
     * Check if the mandatory Category property is present
     *
     * @param entity type of entity being checked
     * @param r resource
     */
    private void checkCategory(String entity, Resource r) {
        if (r.getProperty(ANZS_CATEGORY) == null) {
            createMesg("checkCategory", 1, "ANZS5478", entity, r.getURI(), true, "Category", "anzs5478:Category");
        }
    }

    static final Property ANZS_IDENTIFIER = ResourceFactory.createProperty(ANZS5478, "Identifier");
    static final Property ANZS_IDENTIFIERSTRING = ResourceFactory.createProperty(ANZS5478, "IdentifierString");

    /**
     * Check if the mandatory Identifier property and its subproperties are
     * present
     *
     * @param entity type of entity being checked
     * @param r resource
     */
    private void checkIdentifier(String entity, Resource r) {
        Statement stmt;
        Resource r1;
        String id;

        id = r.getURI();
        if ((stmt = r.getProperty(ANZS_IDENTIFIER)) == null) {
            createMesg("checkIdentifier", 1, "ANZS5478", entity, id, true, "Identifier", "anzs5478:Identifier");
        } else {
            try {
                r1 = stmt.getResource();
            } catch (ResourceRequiredException e) {
                addError(new VEOFailure(CLASSNAME, "checkIdentifier", 2, "ANZS5478 metadata package for " + entity + " '" + id + "' contains malformed anzs5478:Identifier element"));
                return;
            }
            if (r1.getProperty(ANZS_IDENTIFIERSTRING) == null) {
                createMesg("checkIdentifier", 3, "ANZS5478", entity, id, true, "Identifier String", "anzs5478:IdentifierString");
            }
        }
    }

    static final Property ANZS_NAME = ResourceFactory.createProperty(ANZS5478, "Name");
    static final Property ANZS_NAMEWORDS = ResourceFactory.createProperty(ANZS5478, "NameWords");

    /**
     * Check if the mandatory Name property and its subproperties are present
     *
     * @param entity type of entity being checked
     * @param r resource
     */
    private void checkName(String entity, Resource r) {
        Statement stmt;
        Resource r1;
        String id;

        id = r.getURI();
        if ((stmt = r.getProperty(ANZS_NAME)) == null) {
            createMesg("checkName", 1, "ANZS5478", entity, id, true, "Name", "anzs5478:Name");
        } else {
            try {
                r1 = stmt.getResource();
            } catch (ResourceRequiredException e) {
                addError(new VEOFailure(CLASSNAME, "checkName", 2, "ANZS5478 metadata package for " + entity + " '" + id + "' contains malformed anzs5478:Name element"));
                return;
            }
            if (r1.getProperty(ANZS_NAMEWORDS) == null) {
                createMesg("checkName", 3, "ANZS5478", entity, id, true, "Name String", "anzs5478:NameWords");
            }
        }
    }

    static final Property ANZS_DATERANGE = ResourceFactory.createProperty(ANZS5478, "DateRange");
    static final Property ANZS_STARTDATE = ResourceFactory.createProperty(ANZS5478, "StartDate");

    /**
     * Check if the mandatory DateRange property and its subproperties are
     * present
     *
     * @param entity type of entity being checked
     * @param r resource
     */
    private void checkDateRange(String entity, Resource r) {
        Statement stmt;
        Resource r1;
        String id;

        id = r.getURI();
        if ((stmt = r.getProperty(ANZS_DATERANGE)) == null) {
            createMesg("checkDateRange", 1, "ANZS5478", entity, id, true, "Date Range", "anzs5478:DateRange");
        } else {
            try {
                r1 = stmt.getResource();
            } catch (ResourceRequiredException e) {
                addError(new VEOFailure(CLASSNAME, "checkDateRange", 2, "ANZS5478 metadata package for " + entity + " '" + id + "' contains malformed anzs5478:DateRange element"));
                return;
            }
            if (r1.getProperty(ANZS_STARTDATE) == null) {
                createMesg("checkDateRange", 3, "ANZS5478", entity, id, true, "Start Date", "anzs5478:StartDate");
            }
        }
    }

    static final Property ANZS_RELATEDENTITY = ResourceFactory.createProperty(ANZS5478, "RelatedEntity");
    static final Property ANZS_ASSIGNEDENTITYID = ResourceFactory.createProperty(ANZS5478, "AssignedEntityID");
    static final Property ANZS_RELATIONSHIPROLE = ResourceFactory.createProperty(ANZS5478, "RelationshipRole");

    /**
     * Check if the Related Entity property and its subproperties are present
     *
     * @param entity type of entity being checked
     * @param r resource
     */
    private void checkRelatedEntity(String entity, Resource r) {
        Statement stmt, stmt1;
        Resource r1;
        String id;

        id = r.getURI();
        if ((stmt = r.getProperty(ANZS_RELATEDENTITY)) == null) {
            createMesg("checkRelatedEntity", 1, "ANZS5478", entity, id, true, "Related Entity", "anzs5478:RelatedEntity");
        } else {
            try {
                r1 = stmt.getResource();
            } catch (ResourceRequiredException e) {
                addError(new VEOFailure(CLASSNAME, "checkRelatedEntity", 2, "ANZS5478 metadata package for " + entity + " '" + id + "' contains malformed anzs5478:RelatedEntity element"));
                return;
            }
            if (r1.getProperty(ANZS_ASSIGNEDENTITYID) == null) {
                createMesg("checkRelatedEntity", 3, "ANZS5478", entity, id, true, "Assigned Entity ID", "anzs5478:AssignedEntityID");
            }
            if ((stmt1 = r1.getProperty(ANZS_RELATIONSHIPROLE)) == null) {
                createMesg("checkRelatedEntity", 4, "ANZS5478", entity, id, true, "Relationship Role", "anzs5478:Relationship Role");
            } else if (!(stmt1.getString().trim().equals("1") || stmt1.getString().trim().equals("2"))) {
                addError(new VEOFailure(CLASSNAME, "validate", 2, "ANZS5478 metadata package for " + entity + " '" + id + "' must contain relationship role property (anzs5478:RelationshipRole) with a value of '1' or '2'"));
            }
        }
    }

    static final Property ANZS_DISPOSAL = ResourceFactory.createProperty(ANZS5478, "Disposal");
    static final Property ANZS_RECORDSAUTHORITY = ResourceFactory.createProperty(ANZS5478, "RetentionAndDisposalAuthority");
    static final Property ANZS_DISPOSALCLASSID = ResourceFactory.createProperty(ANZS5478, "DisposalClassID");
    static final Property ANZS_DISPOSALACTION = ResourceFactory.createProperty(ANZS5478, "DisposalAction");
    static final Property ANZS_DISPOSALTRIGGERDATE = ResourceFactory.createProperty(ANZS5478, "DisposalTriggerDate");
    static final Property ANZS_DISPOSALACTIONDUE = ResourceFactory.createProperty(ANZS5478, "DisposalActionDue");

    /**
     * Check if the mandatory Disposal property and its subproperties are
     * present
     *
     * @param entity type of entity being checked
     * @param r resource
     */
    private void checkDisposal(String entity, Resource r) {
        Statement stmt, stmt1;
        Resource r1;
        String id;

        id = r.getURI();
        if ((stmt = r.getProperty(ANZS_DISPOSAL)) == null) {
            createMesg("checkDisposal", 1, "ANZS5478", entity, id, true, "Disposal", "anzs5478:Disposal");
        } else {
            try {
                r1 = stmt.getResource();
            } catch (ResourceRequiredException e) {
                addError(new VEOFailure(CLASSNAME, "checkDisposal", 2, "ANZS5478 metadata package for " + entity + " '" + id + "' contains malformed anzs5478:Disposal element"));
                return;
            }
            if ((stmt1 = r1.getProperty(ANZS_RECORDSAUTHORITY)) == null) {
                createMesg("checkDisposal", 3, "ANZS5478", entity, id, true, "Retention and Disposal Authority", "anzs5478:RetentionAndDisposalAuthority");
            }

            // remaining sub properties are mandatory unless authority is no disposal coverage
            if (stmt1 != null && stmt1.getString().trim().equals("No Disposal Coverage")) {
                return;
            }
            if (r1.getProperty(ANZS_DISPOSALCLASSID) == null) {
                addError(new VEOFailure(CLASSNAME, "checkDisposal", 4, "ANZS5478 metadata package for " + entity + " '" + id + "' must contain disposal class id property (anzs5478:DisposalClassID) unless the Retention and Disposal Authority is set to 'No Disposal Coverage'"));
            }
            if (r1.getProperty(ANZS_DISPOSALACTION) == null) {
                addError(new VEOFailure(CLASSNAME, "checkDisposal", 5, "ANZS5478 metadata package for " + entity + " '" + id + "' must contain disposal action property (anzs5478:DisposalAction) unless the Retention and Disposal Authority is set to 'No Disposal Coverage'"));
            }
            if (r1.getProperty(ANZS_DISPOSALTRIGGERDATE) == null) {
                addError(new VEOFailure(CLASSNAME, "checkDisposal", 6, "ANZS5478 metadata package for " + entity + " '" + id + "' must contain disposal trigger date property (anzs5478:DisposalTriggerDate) unless the Retention and Disposal Authority is set to 'No Disposal Coverage'"));
            }
            if (r1.getProperty(ANZS_DISPOSALACTIONDUE) == null) {
                addError(new VEOFailure(CLASSNAME, "validate", 7, "ANZS5478 metadata package for " + entity + " '" + id + "' must contain disposal action due property (anzs5478:DisposalActionDue) unless the Retention and Disposal Authority is set to 'No Disposal Coverage'"));
            }
        }
    }

    static final Property ANZS_FORMAT = ResourceFactory.createProperty(ANZS5478, "Format");
    static final Property ANZS_FORMATNAME = ResourceFactory.createProperty(ANZS5478, "FormatName");
    static final Property ANZS_CREATINGAPPLICATIONNAME = ResourceFactory.createProperty(ANZS5478, "CreatingApplicationName");

    /**
     * Check if the mandatory Format property and its subproperties are present
     *
     * @param entity type of entity being checked
     * @param r resource
     */
    private void checkFormat(String entity, Resource r) {
        Statement stmt;
        Resource r1;
        String id;
        boolean namePresent, applnPresent;

        /* test deleted a/c azns:Format has no use in VERS V3 information objects
        id = r.getURI();
        if ((stmt = r.getProperty(ANZS_FORMAT)) == null) {
            createMesg("ANZS5478", entity, id, true, "Format", "anzs5478:Format");
        } else {
            try {
                r1 = stmt.getResource();
            } catch (ResourceRequiredException e) {
                addError(new VEOFailure(classname, "checkFormat", 1, "ANZS5478 metadata package for " + entity + " '" + id + "' contains malformed anzs5478:Format element"));
                return;
            }
            namePresent = false;
            applnPresent = false;
            if (r1.getProperty(ANZS_FORMATNAME) != null) {
                namePresent = true;
            }
            if (r1.getProperty(ANZS_CREATINGAPPLICATIONNAME) != null) {
                applnPresent = true;
            }
            if (!namePresent && !applnPresent) {
                addError(new VEOFailure(classname, "checkFormat", 2, "ANZS5478 metadata package for " + entity + " '" + id + "' must contain either a format name or creating application name property (anzs5478:FormatName or anzs5478:CreatingApplicationName)"));
            } else if (namePresent && applnPresent) {
                addError(new VEOFailure(classname, "validate", 3, "ANZS5478 metadata package for " + entity + " '" + id + "' must not contain both a format name or creating application name property (anzs5478:FormatName or anzs5478:CreatingApplicationName)"));
            }
        }
         */
    }

    static final Property ANZS_EXTENT = ResourceFactory.createProperty(ANZS5478, "Extent");
    static final Property ANZS_PHYSICALDIMENSIONS = ResourceFactory.createProperty(ANZS5478, "PhysicalDimensions");
    static final Property ANZS_LOGICALSIZE = ResourceFactory.createProperty(ANZS5478, "LogicalSize");
    static final Property ANZS_QUANTITY = ResourceFactory.createProperty(ANZS5478, "Quantity");
    static final Property ANZS_UNITS = ResourceFactory.createProperty(ANZS5478, "Units");

    /**
     * Check if the mandatory Extent property and its subproperties are present
     *
     * @param entity type of entity being checked
     * @param r resource
     */
    private void checkExtent(String entity, Resource r) {
        Statement stmt;
        Resource r1;
        String id;

        id = r.getURI();
        if ((stmt = r.getProperty(ANZS_EXTENT)) == null) {
            createMesg("checkExtent", 1, "ANZS5478", entity, id, true, "Extent", "anzs5478:Extent");
        } else {
            try {
                r1 = stmt.getResource();
            } catch (ResourceRequiredException e) {
                addError(new VEOFailure(CLASSNAME, "checkExtent", 2, "ANZS5478 metadata package for " + entity + " '" + id + "' contains malformed anzs5478:Extent element"));
                return;
            }
            if (r1.getProperty(ANZS_PHYSICALDIMENSIONS) != null) {
                addError(new VEOFailure(CLASSNAME, "checkExtent", 3, "ANZS5478 metadata package for " + entity + " '" + id + "' must not contain a physical dimensions property (anzs5478:PhysicalDimensions)"));
            }
            if (r1.getProperty(ANZS_LOGICALSIZE) == null && r1.getProperty(ANZS_QUANTITY) == null) {
                addError(new VEOFailure(CLASSNAME, "validate", 4, "ANZS5478 metadata package for " + entity + " '" + id + "' must contain either a logical size or quantity property (anzs5478:LogicalSize or anzs5478:Quantity)"));
            }
            if (r1.getProperty(ANZS_UNITS) == null) {
                createMesg("checkExtent", 5, "ANZS5478", entity, id, true, "Units", "anzs5478:Units");
            }
        }
    }

    /**
     * Create a standard error message for complaining about an ANZS5478 problem
     *
     * @param method method calling
     * @param errno unique error identifier in the method
     * @param std the standard - always 'ANZS5478'
     * @param entity the type of entity being checked
     * @param id the identifier of the containing resource
     * @param mandatory true if property is mandatory
     * @param property the property being complained about
     * @param xmlTag the XML tag of the property
     */
    private void createMesg(String method, int errno, String std, String entity, String id, boolean mandatory, String property, String xmlTag) {
        if (mandatory) {
            addError(new VEOFailure(CLASSNAME, method, errno, std + " metadata package for " + entity + " '" + id + "' does not contain the mandatory " + property + " property (" + xmlTag + ")"));
        } else {
            addWarning(new VEOFailure(CLASSNAME, method, errno, std + " metadata package for " + entity + " '" + id + "' does not contain the conditional " + property + " property (" + xmlTag + ")"));
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
    static final Property AGLS_CREATOR = ResourceFactory.createProperty(DC_TERMS, "creator");
    static final Property AGLS_TITLE = ResourceFactory.createProperty(DC_TERMS, "title");
    static final Property AGLS_DATE = ResourceFactory.createProperty(DC_TERMS, "date");
    static final Property AGLS_AVAILABLE = ResourceFactory.createProperty(DC_TERMS, "available");
    static final Property AGLS_CREATED = ResourceFactory.createProperty(DC_TERMS, "created");
    static final Property AGLS_DATECOPYRIGHTED = ResourceFactory.createProperty(DC_TERMS, "dateCopyrighted");
    static final Property AGLS_DATELICENSED = ResourceFactory.createProperty(AGLS_TERMS, "dateLicensed");
    static final Property AGLS_INV_DATELICENSED = ResourceFactory.createProperty(DC_TERMS, "dateLicensed");
    static final Property AGLS_ISSUED = ResourceFactory.createProperty(DC_TERMS, "issued");
    static final Property AGLS_MODIFIED = ResourceFactory.createProperty(DC_TERMS, "modified");
    static final Property AGLS_VALID = ResourceFactory.createProperty(DC_TERMS, "valid");
    static final Property AGLS_DESCRIPTION = ResourceFactory.createProperty(DC_TERMS, "description");
    static final Property AGLS_FUNCTION = ResourceFactory.createProperty(AGLS_TERMS, "function");
    static final Property AGLS_SUBJECT = ResourceFactory.createProperty(DC_TERMS, "subject");
    static final Property AGLS_TYPE = ResourceFactory.createProperty(DC_TERMS, "type");
    static final Property AGLS_AGGREGATIONLEVEL = ResourceFactory.createProperty(AGLS_TERMS, "aggregationLevel");
    static final Property AGLS_INV_AGGREGATIONLEVEL = ResourceFactory.createProperty(DC_TERMS, "aggregationLevel");
    static final Property AGLS_CATEGORY = ResourceFactory.createProperty(AGLS_TERMS, "category");
    static final Property AGLS_INV_CATEGORY = ResourceFactory.createProperty(DC_TERMS, "category");
    static final Property AGLS_DOCUMENTTYPE = ResourceFactory.createProperty(AGLS_TERMS, "documentType");
    static final Property AGLS_INV_DOCUMENTTYPE = ResourceFactory.createProperty(DC_TERMS, "documentType");
    static final Property AGLS_SERVICETYPE = ResourceFactory.createProperty(AGLS_TERMS, "serviceType");
    static final Property AGLS_INV_SERVICETYPE = ResourceFactory.createProperty(DC_TERMS, "serviceType");
    static final Property AGLS_DISPOSALREVIEWDATE = ResourceFactory.createProperty(VERS_TERMS, "disposalReviewDate");
    static final Property AGLS_DISPOSALACTION = ResourceFactory.createProperty(VERS_TERMS, "disposalAction");
    static final Property AGLS_DISPOSALCONDITION = ResourceFactory.createProperty(VERS_TERMS, "disposalCondition");
    static final Property AGLS_DISPOSALREFERENCE = ResourceFactory.createProperty(VERS_TERMS, "disposalReference");

    private void checkAGLSProperties(boolean noRec) {

        // DC_TERMS:creator m
        if (!rdfModel.contains(null, AGLS_CREATOR)) {
            addError(new VEOFailure(CLASSNAME, "checkAGLSProperties", 1, "AGLS metadata package does not contain the mandatory creator element (dcterms:creator)"));
        }
        // DC_TERMS:date m format YYYY-MM-DD (available, created, dateCopyrighted, dateLicensed, issued, modified, valid) see AGLS Usage Guide for valid schemas and formats.
        if (!rdfModel.contains(null, AGLS_DATE)
                && !rdfModel.contains(null, AGLS_AVAILABLE)
                && !rdfModel.contains(null, AGLS_CREATED)
                && !rdfModel.contains(null, AGLS_DATECOPYRIGHTED)
                && !rdfModel.contains(null, AGLS_DATELICENSED)
                && !rdfModel.contains(null, AGLS_INV_DATELICENSED) // error in VERSV3 spec, see below
                && !rdfModel.contains(null, AGLS_ISSUED)
                && !rdfModel.contains(null, AGLS_MODIFIED)
                && !rdfModel.contains(null, AGLS_VALID)) {
            addError(new VEOFailure(CLASSNAME, "checkAGLSProperties", 2, "AGLS metadata package does not contain the mandatory date element or its subelements (available, created, dateCopyrighted, dateLicensed, issued, modified, or valid)"));
        }
        // This was an error in the VERSV3 spec, DateLicensed has the wrong namespace. Warn about it, but not mark it as an error
        if (rdfModel.contains(null, AGLS_INV_DATELICENSED)) {
            addWarning(new VEOFailure(CLASSNAME, "checkAGLSProperties", 3, "AGLS metadata package contains 'dcterms:dateLicensed' not 'aglsterms:dateLicensed'. This was an error in the specification. The VEO should be fixed."));
        }
        // DC_TERMS:title m
        if (!rdfModel.contains(null, AGLS_TITLE)) {
            addError(new VEOFailure(CLASSNAME, "checkAGLSProperties", 4, "AGLS metadata package does not contain the mandatory title element (dcterms:title)"));
        }
        // DC_TERMS:availability m for offline resources (can't test conditional)
        // DC_TERMS:identifier m for online resources (can't test conditional)
        // DC_TERMS:publisher m for information resources  (can't test conditional)

        // DC_TERMS:description r
        if (!noRec && !rdfModel.contains(null, AGLS_DESCRIPTION)) {
            addWarning(new VEOFailure(CLASSNAME, "checkAGLSProperties", 5, "AGLS metadata package does not contain the recommended description element (dcterms:description)"));
        }
        // DC_TERMS:function r
        // DC_TERMS:subject r if function not present
        if (!noRec && !rdfModel.contains(null, AGLS_FUNCTION) && !rdfModel.contains(null, AGLS_SUBJECT)) {
            addWarning(new VEOFailure(CLASSNAME, "checkAGLSProperties", 6, "AGLS metadata package does not contain the recommended function element (aglsterms:function or aglsterms:subject)"));
        }
        // DC_TERMS:language r if not in English (can't test conditional)
        // DC_TERMS:type r (aggregationLevel, category, documentType, serviceType)
        if (!noRec && !rdfModel.contains(null, AGLS_TYPE)
                && !rdfModel.contains(null, AGLS_AGGREGATIONLEVEL)
                && !rdfModel.contains(null, AGLS_INV_AGGREGATIONLEVEL)
                && !rdfModel.contains(null, AGLS_CATEGORY)
                && !rdfModel.contains(null, AGLS_INV_CATEGORY)
                && !rdfModel.contains(null, AGLS_DOCUMENTTYPE)
                && !rdfModel.contains(null, AGLS_INV_DOCUMENTTYPE)
                && !rdfModel.contains(null, AGLS_SERVICETYPE)
                && !rdfModel.contains(null, AGLS_INV_SERVICETYPE)) {
            addWarning(new VEOFailure(CLASSNAME, "checkAGLSProperties", 7, "AGLS metadata package does not contain the recommended type property (dcterms:type) or one of the subproperties (aglsterms:aggregationLevel, aglsterms:category, aglsterms:documentType, or aglsterms:serviceType)"));
        }
        // This was an error in the VERSV3 spec, the subtypes have the wrong namespace. Warn about it, but not mark it as an error
        if (rdfModel.contains(null, AGLS_INV_AGGREGATIONLEVEL)) {
            addWarning(new VEOFailure(CLASSNAME, "checkAGLSProperties", 8, "AGLS metadata package contains 'dcterms:aggregationLevel' not 'aglsterms:aggregationLevel'. This was an error in the specification. The VEO should be fixed."));
        }
        if (rdfModel.contains(null, AGLS_INV_CATEGORY)) {
            addWarning(new VEOFailure(CLASSNAME, "checkAGLSProperties", 9, "AGLS metadata package contains 'dcterms:category' not 'aglsterms:category'. This was an error in the specification. The VEO should be fixed."));
        }
        if (rdfModel.contains(null, AGLS_INV_DOCUMENTTYPE)) {
            addWarning(new VEOFailure(CLASSNAME, "checkAGLSProperties", 10, "AGLS metadata package contains 'dcterms:documentType' not 'aglsterms:documentType'. This was an error in the specification. The VEO should be fixed."));
        }
        if (rdfModel.contains(null, AGLS_INV_SERVICETYPE)) {
            addWarning(new VEOFailure(CLASSNAME, "checkAGLSProperties", 11, "AGLS metadata package contains 'dcterms:serviceType' not 'aglsterms:serviceType'. This was an error in the specification. The VEO should be fixed."));
        }
        // warn if disposal metadata is not present...
        if (!noRec && !rdfModel.contains(null, AGLS_DISPOSALREVIEWDATE) && !rdfModel.contains(null, AGLS_DISPOSALCONDITION)) {
            addWarning(new VEOFailure(CLASSNAME, "checkAGLSProperties", 12, "AGLS metadata package does not contain either the disposal review date or disposal condition properties (versterms:disposalReviewDate or versterms:disposalCondition)"));
        }
        if (!noRec && !rdfModel.contains(null, AGLS_DISPOSALACTION)) {
            addWarning(new VEOFailure(CLASSNAME, "checkAGLSProperties", 13, "AGLS metadata package does not contain the disposal action property (vers:disposalAction)"));
        }
        if (!noRec && !rdfModel.contains(null, AGLS_DISPOSALREFERENCE)) {
            addWarning(new VEOFailure(CLASSNAME, "checkAGLSProperties", 14, "AGLS metadata package does not contain the disposal reference property (vers:disposalReference)"));
        }
    }

    // check a value as a date, but this is too tedious
    /*
     seenDate = true;
     ResIterator iter;
     iter = rdfModel.listSubjectsWithProperty(CREATED);
     while (iter.hasNext()) {
     s = iter.nextResource().getProperty(CREATED).getString();
     try {
     RepnItem.testValueAsDate(s);
     } catch (IllegalArgumentException iae) {
     addError(errMesg(classname, method, "invalid DC_TERMS:created metadata", iae));
     }
     }
     }
     */
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
    public void addRDF() {
        // String syntax = "TURTLE";
        String syntax = "RDF/XML";
        // String syntax = "N-TRIPLE";
        StringWriter sw = new StringWriter();

        if (rdfModel == null) {
            return;
        }
        try {
            rdfModel.write(sw, syntax);
        } catch (BadURIException bue) {
            sw.append("Failed to generate RDF: ");
            sw.append(bue.getMessage());
            sw.append(" RepnMetadataPackage.addRDF()");
        }
        addTag("<pre>");
        addString(sw.toString());
        addTag("</pre>");
    }
}
