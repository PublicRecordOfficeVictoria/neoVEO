/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOAnalysis;

import VERSCommon.ResultSummary;
import VERSCommon.VEOError;
import VERSCommon.VERSDate;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Base64;

/**
 * This class represents the content of a VEO*Signature*.xml file. A valid
 * signature means that the signature validated, and that the certificate chain
 * also validated
 *
 * @author Andrew Waugh
 */
class RepnSignature extends RepnXML {

    String classname = "RepnSignature";
    Path source; // file that generated this signature file
    RepnItem version; // version identifier of this VEOSignature.xml file
    RepnItem sigAlgorithm; // signature algorithm to use
    String sa; // signature algorithm name
    RepnItem sigDateTime; // signature date and time
    RepnItem signer; // signer
    RepnItem signature; // signature
    ArrayList<RepnItem> certificates;    // list of certificates associated with this signature
    private final static Logger LOG = Logger.getLogger("VEOAnalysis.RepnSignature");

    /**
     * Build an internal representation of the VEO*Signature*.xml file,
     * validating it against the schema in vers2-signature.xsd. The named
     * signature file is contained in the veoDir directory. The schema
     *
     * @param veoDir VEO directory that contains the VEOSignature.xml file
     * @param sigFileName The signature file
     * @param schemaDir schemaDir directory that contains vers2-signature.xsd
     * @throws VEOError if an error occurred processing this VEO
     */
    public RepnSignature(Path veoDir, String sigFileName, Path schemaDir, ResultSummary results) throws VEOError {
        super(sigFileName, results);

        Path file;          // the signature file
        Path schema;        // the source of the VEO*Signature?.xml schema
        RepnItem ri;
        int i;

        version = new RepnItem(getId(), "Version of XML file", results);
        sigAlgorithm = new RepnItem(getId(), "Signature algorithm OID", results);
        sa = "";
        sigDateTime = new RepnItem(getId(), "Date/time signature created", results);
        signer = new RepnItem(getId(), "Signer", results);
        signature = new RepnItem(getId(), "Signature", results);
        certificates = new ArrayList<>();

        // get the files involved
        file = veoDir.resolve(sigFileName);
        schema = schemaDir.resolve("vers3-signature.xsd");

        // work out whether we are validating the content file or the history file
        if (!Files.exists(file)) {
            throw new VEOError(errMesg(classname, "Signature file '" + file.toString() + "' does not exist"));
        }
        if (file.toString().contains("VEOContentSignature")) {
            source = veoDir.resolve("VEOContent.xml");
        } else if (file.toString().contains("VEOHistorySignature")) {
            source = veoDir.resolve("VEOHistory.xml");
        } else {
            throw new VEOError(errMesg(classname, "File name must be of the form 'VEOContentSignature?.xml' or 'VEOHistorySignature?.xml' but is '" + file.toString() + "'"));
        }

        // parse the signature file and extract the data
        // parse the VEO*Signature?.xml file against the VEO signature schema
        if (!parse(file, schema)) {
            return;
        }

        // extract the information from the DOM representation
        // the first element is Signature Block
        gotoRootElement();
        checkElement("vers:SignatureBlock");
        gotoNextElement();

        // then the version
        if (checkElement("vers:Version")) {
            version.setValue(getTextValue());
        }
        gotoNextElement();

        // then the signature algorithm
        if (checkElement("vers:SignatureAlgorithm")) {
            sigAlgorithm.setValue(getTextValue());
        }
        gotoNextElement();

        // then the signature date and time
        if (checkElement("vers:SignatureDateTime")) {
            sigDateTime.setValue(getTextValue());
            gotoNextElement();
        }

        // then the signer
        if (checkElement("vers:Signer")) {
            signer.setValue(getTextValue());
            gotoNextElement();
        }

        // then the actual signature
        if (checkElement("vers:Signature")) {
            signature.setValue(getTextValue());
        }
        gotoNextElement();

        // then the certificate chain
        checkElement("vers:CertificateChain");
        gotoNextElement();

        // step through the certificates
        i = 0;
        while (checkElement("vers:Certificate")) {
            ri = new RepnItem(getId() + ":" + i, "Certificate(" + i + ")", results);
            ri.setValue(getTextValue());
            certificates.add(ri);
            gotoNextElement();
            i++;
        }
    }

    /**
     * Free resources associated with this object
     */
    @Override
    public void abandon() {
        int i;

        super.abandon();
        source = null;
        version.abandon();
        version = null;
        sigAlgorithm.abandon();
        sigAlgorithm = null;
        sa = null;
        sigDateTime.abandon();
        sigDateTime = null;
        signer.abandon();
        signer = null;
        signature.abandon();
        signature = null;
        for (i = 0; i < certificates.size(); i++) {
            certificates.get(i).abandon();
        }
        certificates.clear();
        certificates = null;
    }

    /**
     * Validate the data in the signature file
     */
    public final void validate() {

        // can't validate if parse failed...
        if (!contentsAvailable()) {
            return;
        }

        // validate the version number
        if (!version.getValue().equals("3.0")) {
            version.addWarning("VEOVersion has a value of '" + version + "' instead of '3.0'");
        }

        // validate the algorithm
        sa = sigAlgorithm.getValue();
        switch (sa) {
            case "SHA224withDSA":
            case "SHA224withRSA":
            case "SHA256withRSA":
            case "SHA256withDSA":
            case "SHA256withECDSA":
            case "SHA384withRSA":
            case "SHA384withECDSA":
            case "SHA512withRSA":
            case "SHA512withECDSA":
            case "SHA1withDSA":
            case "SHA1withRSA":
                break;
            default:
                sigAlgorithm.addError("hash/signature algorithm combination '" + sa + "' is not supported");
        }

        // validate a valid date and time
        try {
            VERSDate.testValueAsDate(sigDateTime.getValue());
        } catch (IllegalArgumentException e) {
            sigDateTime.addError("Date in event is invalid. Value is '" + sigDateTime + "'. Error was: " + e.getMessage());
        }

        // verify the digital signature
        verifySignature(source);

        // verify the certificate chain
        verifyCertificateChain();
    }

    /**
     * Verify the signature contained in the VEO*Signature?.xml file
     *
     * @param sourceFile the VEOContent.xml or VEOHistory.xml file to verify
     * @return true if the signature was valid
     */
    private boolean verifySignature(Path sourceFile) {
        String method = "verifySignature";
        byte[] sigba;
        X509Certificate x509c;  // certificate to validate
        Signature sig;          // representation of the signature algorithm
        FileInputStream fis;    // input streams to read file to verify
        BufferedInputStream bis;//
        int i;
        byte[] b = new byte[1000]; // buffer used to read input file

        // extract signature from base64 encoding
        try {
            sigba = Base64.getDecoder().decode(signature.getValue());
        } catch (IllegalArgumentException e) {
            signature.addError("Converting Base64 signature failed: " + e.getMessage());
            return false;
        }

        // check that we have at least one certificate
        if (certificates.size() < 1) {
            addError("The signature file does not contain any vers:Certificate elements");
            return false;
        }

        // decode the byte array into an X.509 certificate
        x509c = extractCertificate(certificates.get(0));
        if (x509c == null) {
            addError("Could not decode first vers:Certificate");
            return false;
        }

        // set up verification...
        try {
            sig = Signature.getInstance(sa);
            sig.initVerify(x509c.getPublicKey());
        } catch (NoSuchAlgorithmException nsae) {
            addError("Security package does not support the signature or message digest algorithm. Error reported: " + nsae.getMessage());
            return false;
        } catch (InvalidKeyException ike) {
            addError("Security package reports that public key is invalid. Error reported: " + ike.getMessage());
            return false;
        }

        // read and process the signed file
        bis = null;
        fis = null;
        try {
            fis = new FileInputStream(sourceFile.toString());
            bis = new BufferedInputStream(fis);
            while ((i = bis.read(b)) != -1) {
                sig.update(b, 0, i);
            }
        } catch (SignatureException e) {
            LOG.log(Level.WARNING, errMesg(classname, method, "failed updating the signature", e));
            return false;
        } catch (FileNotFoundException e) {
            addError("File to verify ('" + sourceFile.toString() + "') was not found");
            return false;
        } catch (IOException e) {
            LOG.log(Level.WARNING, errMesg(classname, method, "failed reading file to sign", e));
            return false;
        } finally {
            try {
                if (bis != null) {
                    bis.close();
                }
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, errMesg(classname, method, "failed to close file being verified", e));
            }
        }

        // verify the signature
        try {
            if (!sig.verify(sigba)) {
                addError("signature verification failed");
                return false;
            }
        } catch (SignatureException se) {
            addError("signature verification failed: "+se.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Verify the certificate chain in the signature file.
     *
     * @return true if the certificate chain validated
     */
    private boolean verifyCertificateChain() {
        String method = "verifyCertificateChain";
        int i;
        String issuer, subject;
        boolean failed;
        RepnItem r1, r2;
        X509Certificate certToVerify, certOfSigner;

        // get first certificate (to be verified)
        failed = false;
        if (certificates.size() < 1) {
            addWarning("No vers:Certificates found in signature");
            return false;
        }
        r1 = certificates.get(0);
        certToVerify = extractCertificate(r1);
        if (certToVerify == null) {
            addWarning("First certificate could not be extracted. Remaining certificates have not been checked.");
            return false;
        }
        subject = certToVerify.getSubjectX500Principal().getName();
        issuer = certToVerify.getIssuerX500Principal().getName();

        // verify chain
        for (i = 1; i < certificates.size(); i++) {
            r2 = certificates.get(i);
            certOfSigner = extractCertificate(r2);
            if (certOfSigner == null) {
                switch (i) {
                    case 1:
                        addError("Could not decode the second vers:Certificate. Remaining certificates have not been checked.");
                        break;
                    case 2:
                        addError("Could not decode the third vers:Certificate. Remaining certificates have not been checked.");
                        break;
                    default:
                        addError("Could not decode the " + i + "th vers:Certificate. Remaining certificates have not been checked.");
                        break;
                }
                return false;
            }
            if (!verifyCertificate(certToVerify, certOfSigner, r1, r2)) {
                addError("Certificate " + (i - 1) + " failed verification. Subject of certificate is: " + subject + ". Issuer of certificate is: " + issuer);
                failed = true;
            }
            certToVerify = certOfSigner;
            r1 = r2;
        }

        // final certificate should be self signed...
        if (!verifyCertificate(certToVerify, certToVerify, r1, r1)) {
            if (!subject.equals(issuer)) {
                addError("Final certificate failed verification. Certificate is not self signed.   Subject of final certificate is: " + subject + " Issuer of final certificate is: " + issuer);
            } else {
                addError("Final certificate failed verification. Subject of final certificate is: " + subject + ". Issuer of final certificate is: " + issuer);
            }
            // println(x509c.toString());
            failed = true;
        }
        return !failed;
    }

    /**
     * Verifies that the CA in the second certificate created the first
     * certificate
     *
     * @param first the certificate to verify
     * @param second the certificate that signed the first certificate
     * @param riFirst issues associated with the first certificate
     * @param riSecond issues associated with the second certificate
     * @return true if the certificate verified
     */
    private boolean verifyCertificate(X509Certificate first, X509Certificate second, RepnItem riFirst, RepnItem riSecond) {
        // println("First certificate: "+first.toString());
        try {
            first.verify(second.getPublicKey());
        } catch (SignatureException e) {
            riFirst.addError("Signature failed to verify: " + e.getMessage());
            return false;
        } catch (CertificateException e) {
            riFirst.addError("Certificate problem: " + e.getMessage());
            return false;
        } catch (NoSuchAlgorithmException e) {
            riFirst.addError("No Such Algorithm: " + e.getMessage());
            return false;
        } catch (InvalidKeyException e) {
            riSecond.addError("Invalid public key in certificate: " + e.getMessage());
            return false;
        } catch (NoSuchProviderException e) {
            riFirst.addError("No such provider: " + e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Decode a byte array into an X.509 certificate.
     *
     * @param b a byte array containing the X.509 encoded certificate
     * @param m messages associated with this certificate
     * @return an X509Certificate
     */
    private X509Certificate extractCertificate(RepnItem certificate) {
        byte[] b;
        CertificateFactory cf;
        ByteArrayInputStream bais;
        X509Certificate x509c;

        try {
            b = Base64.getDecoder().decode(certificate.getValue());
            // b = DatatypeConverter.parseBase64Binary(certificate.getValue());
        } catch (IllegalArgumentException e) {
            certificate.addError("Converting Base64 signature failed: " + e.getMessage());
            b = new byte[]{0};
        }
        try {
            cf = CertificateFactory.getInstance("X.509");
            bais = new ByteArrayInputStream(b);
            x509c = (X509Certificate) cf.generateCertificate(bais);
            bais.close();
        } catch (IOException | CertificateException e) {
            certificate.addError("Error decoding certificate: " + e.getMessage() + "\n");
            return null;
        }
        return x509c;
    }

    /**
     * Check if this object has any errors?
     *
     * @return true if errors have been detected
     */
    @Override
    public boolean hasErrors() {
        int i;

        hasErrors |= version.hasErrors() | sigAlgorithm.hasErrors();
        hasErrors |= sigDateTime.hasErrors() | signer.hasErrors() | signature.hasErrors();
        for (i = 0; i < certificates.size(); i++) {
            hasErrors |= certificates.get(i).hasErrors();
        }
        return hasErrors;
    }

    /**
     * Build a list of all of the errors generated by this object
     *
     * @return The concatenated error list
     */
    @Override
    public String getErrors() {
        StringBuffer sb;
        int i;

        sb = new StringBuffer();
        sb.append(super.getErrors());
        sb.append(version.getErrors());
        sb.append(sigAlgorithm.getErrors());
        sb.append(sigDateTime.getErrors());
        sb.append(signer.getErrors());
        sb.append(signature.getErrors());
        for (i = 0; i < certificates.size(); i++) {
            sb.append(certificates.get(i).getErrors());
        }
        return sb.toString();
    }

    /**
     * Has this object (or its children) any warnings?
     *
     * @return true if errors have been detected
     */
    @Override
    public boolean hasWarnings() {
        int i;

        hasWarnings |= version.hasWarnings() | sigAlgorithm.hasWarnings();
        hasWarnings |= sigDateTime.hasWarnings() | signer.hasWarnings() | signature.hasWarnings();
        for (i = 0; i < certificates.size(); i++) {
            hasWarnings |= certificates.get(i).hasWarnings();
        }
        return hasWarnings;
    }

    /**
     * Build a list of all of the warnings generated by this object
     *
     * @return The concatenated error list
     */
    @Override
    public String getWarnings() {
        StringBuffer sb;
        int i;

        sb = new StringBuffer();
        sb.append(super.getWarnings());
        sb.append(version.getWarnings());
        sb.append(sigAlgorithm.getWarnings());
        sb.append(sigDateTime.getWarnings());
        sb.append(signer.getWarnings());
        sb.append(signature.getWarnings());
        for (i = 0; i < certificates.size(); i++) {
            sb.append(certificates.get(i).getWarnings());
        }
        return sb.toString();
    }

    /**
     * Generate a String representation of the signature
     *
     * @return the String representation
     */
    @Override
    public String toString() {
        StringBuffer sb;
        int i;

        sb = new StringBuffer();
        sb.append(super.toString());
        if (contentsAvailable()) {
            sb.append(" VEOSignature (");
            sb.append(getId());
            sb.append(")\n");
            sb.append(version.toString());
            sb.append(sigAlgorithm.toString());
            sb.append(sigDateTime.toString());
            sb.append("  vers:Signer: ");
            sb.append(signer.toString());
            sb.append("\n");
            sb.append(signature.toString());
            for (i = 0; i < certificates.size(); i++) {
                sb.append(certificates.get(i).toString());
            }
        } else {
            sb.append(" VEOSignature: No valid content available as parse failed\n");
        }
        return sb.toString();
    }

    /**
     * Generate an XML representation of the signature
     *
     * @param verbose true if additional information is to be generated
     * @param veoDir the directory in which to create the report
     * @param fileName the file the report will be about
     * @throws VEOError  if a fatal error occurred
     */
    public void genReport(boolean verbose, Path veoDir, String fileName) throws VEOError {
        String reportName;
        int i;
        X509Certificate x509c;
        String mesg;

        // get name of report file to create (Report-XXX.hmtl)
        i = fileName.lastIndexOf(".xml");
        if (i == -1) {
            throw new VEOError(classname, 3, "File name must end in .xml, but is '" + fileName + "'");
        }
        reportName = "Report-" + fileName.substring(0, i) + ".html";
        createReport(veoDir, reportName, "Signature Report for '" + fileName + "'");
        setReportWriter(getReportWriter());
        startDiv("xml", null);
        addLabel("XML Document");
        if (hasErrors || hasWarnings) {
            addTag("<ul>\n");
            listIssues();
            addTag("</ul>\n");
        }
        if (contentsAvailable()) {
            version.genReport(verbose);
            signature.genReport(verbose);
            sigAlgorithm.genReport(verbose);
            sigDateTime.genReport(verbose);
            signer.genReport(verbose);
            for (i = 0; i < certificates.size(); i++) {
                x509c = extractCertificate(certificates.get(i));
                mesg = x509c.toString();
                certificates.get(i).genReport(verbose, mesg);
            }
            if (hasErrors || hasWarnings) {
                addTag("<ul>\n");
                listIssues();
                addTag("</ul>\n");
            }
        } else {
            addString(" VEOSignature: No valid content available as parse failed\n");
        }
        endDiv();
        finishReport();
    }

    /**
     * Tell all the Representations where to write the HTML
     *
     * @param bw  buffered writer to write output
     */
    @Override
    public void setReportWriter(BufferedWriter bw) {
        int i;

        // super.setReportWriter(bw); don't need to do this as set in createReport()
        version.setReportWriter(bw);
        signature.setReportWriter(bw);
        sigAlgorithm.setReportWriter(bw);
        sigDateTime.setReportWriter(bw);
        signer.setReportWriter(bw);
        for (i = 0; i < certificates.size(); i++) {
            certificates.get(i).setReportWriter(bw);
        }
    }

    public static void main(String args[]) {
        RepnSignature rh;
        Path veoDir;
        Path schemaDir;

        veoDir = Paths.get("..", "neoVEOOutput", "Demo", "BadVEO1.veo");
        schemaDir = Paths.get("Test", "Demo", "Schemas");
        try {
            rh = new RepnSignature(veoDir, "VEOContentSignature1.xml", schemaDir, null);
            System.out.println(rh.toString());
        } catch (VEOError e) {
            System.out.println(e.getMessage());
        }
    }
}
