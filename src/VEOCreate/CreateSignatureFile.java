/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOCreate;

import VERSCommon.VERSDate;
import VERSCommon.PFXUser;
import VERSCommon.VEOError;
import VERSCommon.VEOFatal;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.*;
import java.util.logging.*;
import java.util.Base64;

/**
 * This class creates a VEO_Content_Signature.xml or VEO_History_Signature.xml
 * file. This class should not be called directly, instead the CreateVEOs class
 * should be used.
 *
 * @author Andrew Waugh (andrew.waugh@prov.vic.gov.au)
 *
 * Copyright 2014 Public Record Office Victoria
 */
class CreateSignatureFile extends CreateXMLDoc {

    String version; // version to use (default is "3.0"
    String classname = "CreateSignatureFile";
    private final static Logger LOG = Logger.getLogger("veocreate.createSignatureFile");

    /**
     * Create an instance which can be used multiple times to create signature
     * files.
     *
     * @param veoDir the directory in which the signature files will be created
     * @param version the version number (currently always "3.0")
     * @throws VEOError  if a fatal error occurred
     */
    public CreateSignatureFile(Path veoDir, String version) throws VEOError {
        super(veoDir);
        this.version = version;
    }

    static String contentsSig5
            = " <vers:Version>";
    static String contentsSig6
            = "</vers:Version>\r\n <vers:SignatureAlgorithm>";
    static String contentsSig7
            = "  </vers:SignatureAlgorithm>\r\n <vers:SignatureDateTime>";
    static String contentsSig8
            = "</vers:SignatureDateTime>\r\n <vers:Signer>\r\n";
    static String contentsSig9
            = "</vers:Signer>\r\n <vers:Signature>\r\n";
    static String contentsSig11
            = "  </vers:Signature>\r\n <vers:CertificateChain>\r\n";
    static String contentsSig12 = "  <vers:Certificate>\r\n";
    static String contentsSig13 = "  </vers:Certificate>\r\n";
    static String contentsSig14 = " </vers:CertificateChain>\r\n";

    public final static int CREATE_CONTENT_FILE = 0; // create a content signature file
    public final static int CREATE_HISTORY_FILE = 1; // create a history signature file

    /**
     * Digitally signs a Content or History file and creates a signature file.
     * This method is passed the file to sign (which must be named
     * "VEOContent.xml" or "VEOHistory.xml"), a PFXUser containing details about
     * about the signer, and the hash algorithm to use.
     * <p>
     * Valid hash algorithms are: "SHA256", "SHA384", "SHA512", or "SHA1". This
     * algorithm combined with the signature algorithm used to generate the PFX
     * user.
     * <p>
     *
     * @param toSign string containing the name of the file to sign
     * @param signer the details about the signer
     * @param algorithmId the hash algorithm to use
     * @throws VEOError if this VEO had an issue
     * @throws VEOFatal if an error occurred that would stop any VEO
     * construction
     */
    public void sign(String toSign, PFXUser signer, String algorithmId)
            throws VEOError, VEOFatal {
        String method = "sign";
        String sigAlg;    // sig algorithm
        Path fileToSign;        // representation of the file to sign
        Path sigFile;           // created VERS signature file
        Signature sig;          // representation of the signature algorithm
        PrivateKey priKey;      // private key to use generating the signature
        FileInputStream fis;    // input streams to read file to sign
        BufferedInputStream bis;//
        byte[] signature;       // generated signature
        int i;
        X509Certificate cert;   // certificate retrieved from PFXUser
        byte[] b = new byte[1000]; // buffer used to read input file
        Principal subject;      // subject in PFXUser
        String preamble;
        // MessageDigest md;

        // general
        LOG.entering(classname, method, new Object[]{toSign, signer, algorithmId});
        preamble = null;
        try {
            fileToSign = veoDir.resolve(toSign);
        } catch (InvalidPathException ipe) {
            throw new VEOFatal(classname, 1, "File name to sign ("+toSign+") was invalid: "+ipe.getMessage());
        }

        // Check that the algorithm associated with the private key matches the
        // selected algorithm
        priKey = signer.getPrivate();
        if (priKey == null) {
            throw new VEOFatal(classname, method, 1,
                    "PFX file didn't contain a private key");
        }

        // check that the hash and signature algorithms are valid
        sigAlg = priKey.getAlgorithm();
        switch (algorithmId.trim()) {
            case "SHA-1":
                algorithmId = "SHA1with" + sigAlg;
                break;
            case "SHA-256":
                algorithmId = "SHA256with" + sigAlg;
                break;
            case "SHA-384":
                algorithmId = "SHA384with" + sigAlg;
                break;
            case "SHA-512":
                algorithmId = "SHA512with" + sigAlg;
                break;
            default:
                throw new VEOFatal(classname, 1, "hash algorithm '" + algorithmId + "' must be one of SHA-1, SHA-256, SHA-384, or SHA-512");
        }

        // check that the combination of hash and signature algorithm is supported
        switch (algorithmId) {
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
                throw new VEOFatal(classname, 1, "hash/signature algorithm combination '" + algorithmId + "' is not supported");
        }

        // check that the file exists, and is a content or history file
        if (!Files.exists(fileToSign)) {
            throw new VEOError(classname, method, 1,
                    "File to sign '" + fileToSign.toString() + "' does not exist");
        }
        switch (fileToSign.getFileName().toString()) {
            case "VEOContent.xml":
                preamble = "VEOContent";
                break;
            case "VEOHistory.xml":
                preamble = "VEOHistory";
                break;
            default:
                throw new VEOError(classname, method, 1,
                        "File to sign '" + fileToSign.toString()
                        + "' must be 'VEOContent.xml' or 'VEOHistory.xml'");
        }

        // work out what to call the resulting signature file
        i = 0;
        do {
            i++;
            sigFile = veoDir.resolve(preamble + "Signature" + i + ".xml");
        } while (Files.exists(sigFile));

        // initialise signature calculation
        try {
            // md = MessageDigest.getInstance("SHA1");
            // md2 = MessageDigest.getInstance("MD2");
            // md5 = MessageDigest.getInstance("MD5");
            // md = MessageDigest.getInstance("SHA-256");
            sig = Signature.getInstance(algorithmId);
            sig.initSign(signer.getPrivate());
        } catch (NoSuchAlgorithmException e) {
            throw new VEOFatal(classname, method, 1,
                    "Java doesn't support algorithm: '" + algorithmId + "'");
        } catch (InvalidKeyException e) {
            throw new VEOFatal(classname, method, 1,
                    "The private key was invalid: " + e.getMessage());
        }

        // process the file to sign
        bis = null;
        fis = null;
        try {
            fis = new FileInputStream(fileToSign.toString());
            bis = new BufferedInputStream(fis);
            while ((i = bis.read(b)) != -1) {
                sig.update(b, 0, i);
            }
        } catch (SignatureException e) {
            throw new VEOError(classname, method, 1,
                    "failed updating the signature: " + e.getMessage());
        } catch (FileNotFoundException e) {
            throw new VEOError(classname, method, 1,
                    "File to sign ('" + fileToSign.toString() + "') was not found");
        } catch (IOException e) {
            throw new VEOError(classname, method, 1,
                    "failed reading file to sign: " + e.getMessage());
        } finally {
            try {
                if (bis != null) {
                    bis.close();
                }
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) { /* ignore */ }
        }
        
        // calculate the mesage digest
        /*
            byte[] ba;
            ba = md.digest();
            BigInteger bi = new BigInteger(1, ba);
            System.out.println("Hash Value: "+String.format("%0" + (ba.length << 1) + "X", bi));
        */

        // calculate the digital signature over the input file
        try {
            signature = sig.sign();
        } catch (SignatureException e) {
            throw new VEOError(classname, method, 1,
                    "failed signing: " + e.getMessage());
        }

        // start signature file
        startXMLDoc(sigFile.getFileName().toString(), "vers:SignatureBlock");

        // output preamble
        // VEO Version
        write(contentsSig5);
        write(version);

        // output signature algorithm id
        write(contentsSig6);
        write(algorithmId);

        // output date
        write(contentsSig7);
        write(VERSDate.versDateTime(0));

        write(contentsSig8);

        // output signer
        cert = signer.getX509Certificate();
        if (cert != null) {
            subject = cert.getSubjectX500Principal();
            if (subject != null) {
                writeValue(subject.toString());
            } else {
                write("unknown subject");
            }
        } else {
            write("Unknown");
        }

        write(contentsSig9);

        // output signature
        write(Base64.getEncoder().encodeToString(signature));

        // output certificate chain from PFXUser
        write(contentsSig11);

        // output certificates
        for (i = 0; i < signer.getCertificateChainLength(); i++) {
            write(contentsSig12);
            b = signer.getCertificateFromChain(i);
            write(Base64.getEncoder().encodeToString(b));
            write(contentsSig13);
        }

        // finalise signture file and close it
        write(contentsSig14);

        // close signature file
        endXMLDoc();

        LOG.exiting("CreateSignatureFile", "sign");
    }

    /**
     * Abandon construction of this signature file and free any resources
     * associated with it.
     *
     * @param debug true if in debugging mode
     */
    @Override
    public void abandon(boolean debug) {
        super.abandon(debug);
    }
}
