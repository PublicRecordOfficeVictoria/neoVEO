/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Tests;

import VEOCreate.CreateVEO;
import VERSCommon.PFXUser;
import VEOCreate.Templates;
import VERSCommon.VEOError;
import java.io.*;
import java.nio.file.*;
import java.util.logging.Logger;

/**
 * This class tests the CreateVEO class
 *
 * @author Andrew Waugh (andrew.waugh@prov.vic.gov.au) Copyright 2014 PROV
 *
 * Versions
 */
public class TestCreateVEO {

    private final static Logger log = Logger.getLogger("veocreate.CreateVEO");
    String classname = "TestCreateVEO"; // for reporting
    int failures;
    Templates templates;                // database of templates

    /**
     * Main constructor.
     * @throws VEOError if a fatal error occurred
     *
     */
    public TestCreateVEO() throws VEOError {
        failures = 0;
        templates = new Templates(Paths.get("Test", "Templates"));
    }

    /**
     * Test constructor.
     */
    private void testConsCreateVEO() {
        String module = "CreateVEO";
        String test;
        CreateVEO cv = null;

        System.out.println("TESTING: " + module);
        failures = 0;

        test = "Test 1: null directory parameter";
        try {
            cv = new CreateVEO(null, "testVEO", "SHA-1", false);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 1);
        }
        cleanup(cv);

        test = "Test 2: working directory doesn't exist";
        try {
            cv = new CreateVEO(Paths.get("TestNotThere"), "testVEO", "SHA-1", false);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 2);
        }
        cleanup(cv);

        test = "Test 3: working directory is an ordinary file";
        try {
            cv = new CreateVEO(Paths.get("Test", "VERSContent.xsd"), "testVEO", "SHA-1", true);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 3);
        }
        cleanup(cv);

        // can't seem to test 4: working directory isn't writeable
        test = "Test 5: veoDir is null";
        try {
            cv = new CreateVEO(Paths.get("Test"), null, "SHA-1", false);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 5);
        }
        cleanup(cv);

        test = "Test 6: veoDir already exists";
        try {
            cv = new CreateVEO(Paths.get("Test"), "VEODirectoryAlreadyExists", "SHA-1", true);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 6);
        }
        cleanup(cv);

        // can't seem to test 7: can't test IOException when creating VEO directory
        System.out.println("Finished testing VEOCreate constructor");
    }

    /**
     * test getVEODir().
     */
    private void testGetVEODir() {
        String module = "getVEODir()";
        String test;
        CreateVEO cv = null;
        Path veoDir;

        System.out.println("TESTING: " + module);
        failures = 0;

        test = "Test 1: ";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            System.out.println(module + " passed " + test + " " + " veoDir was '" + veoDir.toString() + "'");
        } catch (VEOError e) {
            handleVEOError(module, test, e, 1);
        }
        cleanup(cv);
        System.out.println("Finished testing VEOCreate constructor");
    }

    /**
     * Test addVEOReadMe.
     */
    private void testAddVEOReadMe() {
        String module = "addVEOReadMe()";
        String test;
        CreateVEO cv = null;

        System.out.println("TESTING: " + module);
        failures = 0;

        test = "Test 1: null template directory parameter";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(null);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 1);
        }
        cleanup(cv);

        test = "Test 2: template directory doesn't exist";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "TemplateNotThere"));
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 2);
        }
        cleanup(cv);

        // cannot test Test 3 - failure to create path to VEOReadme.txt
        test = "Test 4: veoReadMe.txt doesn't exist";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "TemplateMissingFiles"));
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 4);
        }
        cleanup(cv);

        test = "Test 5: veoReadMe.txt is a directory";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "TemplateWrongFiles"));
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 5);
        }
        cleanup(cv);

        // cannot check Test 6 - IO Exception when copying VEOReadme.txt
        System.out.println("Finished testing " + module);
    }

    /**
     * Test addContent().
     */
    private void testAddContent() throws VEOError {
        String module = "addContent()";
        String test;
        CreateVEO cv = null;
        Path veoDir;
        DirectoryStream<Path> ds;
        PFXUser signer;

        System.out.println("TESTING: " + module);
        signer = new PFXUser(new File("Test", "signer.pfx").toString(), "Ag0nc1eS");
        failures = 0;

        test = "Test 1: no content to add";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent();
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 1);
        }
        cleanup(cv);

        test = "Test 2: adding content after VEO has been finalised";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.addContent(Paths.get("Test", "S-37-7"));
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 2);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        test = "Test 3: adding content after VEO has been signed";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.sign(signer, "SHA1withRSA");
            cv.addContent(Paths.get("Test", "S-37-7"));
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 3);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        test = "Test 4: adding content after VEO has been finished";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.sign(signer, "SHA1withRSA");
            cv.finalise(false);
            cv.addContent(Paths.get("Test", "S-37-7"));
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 4);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        test = "Test 5: a content directory is null";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent((Path) null);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 5);
        }
        cleanup(cv);

        test = "Test 6: a content directory does not exist";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-5"));
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 6);
        }
        cleanup(cv);

        test = "Test 7: a content directory being added twice";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 7);
        }
        cleanup(cv);

        // cannot test 8: IOException when adding a content file
        System.out.println("Finished testing " + module);
    }

    /**
     * Test addInformationObject().
     */
    private void testAddInformationObject() throws VEOError {
        String module = "addInformationObject()";
        String test;
        CreateVEO cv = null;
        Path veoDir;
        DirectoryStream<Path> ds;
        PFXUser signer;

        System.out.println("TESTING: " + module);
        signer = new PFXUser(new File("Test", "signer.pfx").toString(), "Ag0nc1eS");
        failures = 0;

        test = "Test 1: null label parameter";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addInformationObject(null, 1);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 1);
        }
        cleanup(cv);

        test = "Test 2: negative depth parameter";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addInformationObject("Record", -1);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 2);
        }
        cleanup(cv);

        test = "Test 3: adding Information Object after VEO has been finalised";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.addInformationObject("Record", 1);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 3);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        test = "Test 4: adding information object after VEO has been signed";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.sign(signer, "SHA1withRSA");
            cv.addInformationObject("Record", 1);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 4);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        test = "Test 5: adding information object after VEO has been finished";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.sign(signer, "SHA1withRSA");
            cv.finalise(false);
            cv.addInformationObject("Record", 1);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 5);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        System.out.println("Finished testing " + module);

    }

    /**
     * test addMetadataPackage().
     */
    private void testAddMetadataPackage() throws VEOError {
        String module = "addMetadataPackage()";
        String test;
        CreateVEO cv = null;
        Path veoDir;
        DirectoryStream<Path> ds;
        PFXUser signer;

        System.out.println("TESTING: " + module);
        signer = new PFXUser(new File("Test", "signer.pfx").toString(), "Ag0nc1eS");
        failures = 0;

        test = "Test 1: null template parameter";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addMetadataPackage(null, new String[]{"First", "Second"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 1);
        }
        cleanup(cv);

        test = "Test 2: null data parameter";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addMetadataPackage(templates.findTemplate("agls"), null);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 2);
        }
        cleanup(cv);

        test = "Test 3: metadata package added before information package";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addMetadataPackage(templates.findTemplate("agls"), new String[]{"first", "second"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 3);
        }
        cleanup(cv);

        test = "Test 4: metadata package added after information piece";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            cv.addMetadataPackage(templates.findTemplate("agls"), new String[]{"first", "second"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 4);
        }
        cleanup(cv);

        test = "Test 5: metadata package added after VEO has been finalised";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.addMetadataPackage(templates.findTemplate("agls"), new String[]{"first", "second"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 5);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        test = "Test 6: metadata package added after VEO has been signed";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.sign(signer, "SHA1withRSA");
            cv.addMetadataPackage(templates.findTemplate("agls"), new String[]{"first", "second"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 6);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        test = "Test 7: metadata package added after VEO has been finished";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.sign(signer, "SHA1withRSA");
            cv.finalise(false);
            cv.addMetadataPackage(templates.findTemplate("agls"), new String[]{"first", "second"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 7);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        System.out.println("Finished testing " + module);
    }

    /**
     * test continueMetadataPackage().
     */
    private void testContinueMetadataPackage() throws VEOError {
        String module = "continueMetadataPackage()";
        String test;
        CreateVEO cv = null;
        Path veoDir;
        DirectoryStream<Path> ds;
        PFXUser signer;

        System.out.println("TESTING: " + module);
        signer = new PFXUser(new File("Test", "signer.pfx").toString(), "Ag0nc1eS");
        failures = 0;

        test = "Test 1: null template parameter";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addMetadataPackage(templates.findTemplate("agls"), new String[]{"First", "Second"});
            cv.continueMetadataPackage(null, new String[]{"First", "Second"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 1);
        }
        cleanup(cv);

        test = "Test 2: null data parameter";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addMetadataPackage(templates.findTemplate("agls"), new String[]{"First", "Second"});
            cv.continueMetadataPackage(templates.findTemplate("agls"), null);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 2);
        }
        cleanup(cv);

        test = "Test 3: continue metadata package before information package";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.continueMetadataPackage(templates.findTemplate("agls"), new String[]{"first", "second"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 3);
        }
        cleanup(cv);

        test = "Test 3: continue metadata package after information piece";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            cv.continueMetadataPackage(templates.findTemplate("agls"), new String[]{"first", "second"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 3);
        }
        cleanup(cv);

        test = "Test 3: continue metadata package after VEO has been finalised";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.continueMetadataPackage(templates.findTemplate("agls"), new String[]{"first", "second"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 3);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        test = "Test 3: continue metadata package after VEO has been signed";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.sign(signer, "SHA1withRSA");
            cv.continueMetadataPackage(templates.findTemplate("agls"), new String[]{"first", "second"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 3);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        test = "Test 3: continue metadata package after VEO has been finished";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.sign(signer, "SHA1withRSA");
            cv.finalise(false);
            cv.continueMetadataPackage(templates.findTemplate("agls"), new String[]{"first", "second"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 3);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        System.out.println("Finished testing " + module);
    }

    /**
     * test addMetadataPackage().
     */
    private void testAddInformationPiece() throws VEOError {
        String module = "addInformationPiece()";
        String test;
        CreateVEO cv = null;
        Path veoDir;
        DirectoryStream<Path> ds;
        PFXUser signer;

        System.out.println("TESTING: " + module);
        signer = new PFXUser(new File("Test", "signer.pfx").toString(), "Ag0nc1eS");
        failures = 0;

        test = "Test 1: Information Piece added before information package";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationPiece("label");
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 1);
        }
        cleanup(cv);

        test = "Test 2: information piece added after VEO has been finalised";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.addInformationPiece("label");
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 2);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        test = "Test 3: information piece added after VEO has been signed";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.sign(signer, "SHA1withRSA");
            cv.addInformationPiece("label");
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 3);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        test = "Test 4: information piece added after VEO has been finished";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.sign(signer, "SHA1withRSA");
            cv.finalise(false);
            cv.addInformationPiece("label");
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 4);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        System.out.println("Finished testing " + module);
    }

    /**
     * test addContentFile().
     */
    private void testAddContentFile() throws VEOError {
        String module = "addContentFile()";
        String test;
        CreateVEO cv = null;
        Path veoDir;
        DirectoryStream<Path> ds;
        PFXUser signer;

        System.out.println("TESTING: " + module);
        signer = new PFXUser(new File("Test", "signer.pfx").toString(), "Ag0nc1eS");
        failures = 0;

        test = "Test 1: null file parameter";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece(null);
            cv.addContentFile(null);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 1);
        }
        cleanup(cv);

        test = "Test 2: content file added before information object";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addContentFile(Paths.get("S-37-6", "S-37-6.docx").toString());
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 2);
        }
        cleanup(cv);

        test = "Test 2: content file added after information object";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 0);
            cv.addContentFile(Paths.get("S-37-6", "S-37-6.docx").toString());
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 2);
        }
        cleanup(cv);

        test = "Test 2: content file added after metadata package";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 0);
            cv.addMetadataPackage(templates.findTemplate("agls"), new String[]{"First", "Second"});
            cv.addContentFile(Paths.get("S-37-6", "S-37-6.docx").toString());
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 2);
        }
        cleanup(cv);

        test = "Test 2: content file added after VEO has been finalised";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.addContentFile(Paths.get("S-37-6", "S-37-6.docx").toString());
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 2);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        test = "Test 2: content file added after VEO has been signed";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.sign(signer, "SHA1withRSA");
            cv.addContentFile(Paths.get("S-37-6", "S-37-6.docx").toString());
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 2);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        test = "Test 2: content file added  after VEO has been finished";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.sign(signer, "SHA1withRSA");
            cv.finalise(false);
            cv.addContentFile(Paths.get("S-37-6", "S-37-6.docx").toString());
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 2);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        System.out.println("Finished testing " + module);
    }

    /**
     * test addEvent().
     */
    private void testAddEvent() throws VEOError {
        String module = "addEvent()";
        String test;
        CreateVEO cv = null;
        Path veoDir;
        DirectoryStream<Path> ds;
        PFXUser signer;

        System.out.println("TESTING: " + module);
        signer = new PFXUser(new File("Test", "signer.pfx").toString(), "Ag0nc1eS");
        failures = 0;

        test = "Test 1: null date parameter";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addEvent(null, "Created", "Andrew", new String[]{"One", "Two"}, new String[]{"Three", "Four"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 1);
        }
        cleanup(cv);

        test = "Test 2: null event parameter";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addEvent("20140101", null, "Andrew", new String[]{"One", "Two"}, new String[]{"Three", "Four"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 2);
        }
        cleanup(cv);

        test = "Test 3: null initiator parameter";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addEvent("20140101", "Created", null, new String[]{"One", "Two"}, new String[]{"Three", "Four"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 3);
        }
        cleanup(cv);

        test = "Test 4: null description parameter";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addEvent("20140101", "Created", "Andrew", null, new String[]{"Three", "Four"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 4);
        }
        cleanup(cv);

        test = "Test 5: null error parameter";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addEvent("20140101", "Created", "Andrew", new String[]{"One", "Two"}, null);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 5);
        }
        cleanup(cv);

        test = "Test 6: event added after VEO has been finalised";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.addEvent("20140101", "Created", "Andrew", new String[]{"One", "Two"}, new String[]{"Three", "Four"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 6);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        test = "Test 7: event added after VEO has been signed";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.sign(signer, "SHA1withRSA");
            cv.addEvent("20140101", "Created", "Andrew", new String[]{"One", "Two"}, new String[]{"Three", "Four"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 7);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        test = "Test 8: event added  after VEO has been finished";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.sign(signer, "SHA1withRSA");
            cv.finalise(false);
            cv.addEvent("20140101", "Created", "Andrew", new String[]{"One", "Two"}, new String[]{"Three", "Four"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 8);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        System.out.println("Finished testing " + module);
    }

    /**
     * test finishFiles().
     */
    private void testFinishFiles() throws VEOError {
        String module = "finishFiles()";
        String test;
        CreateVEO cv = null;
        Path veoDir;
        DirectoryStream<Path> ds;
        PFXUser signer;

        System.out.println("TESTING: " + module);
        signer = new PFXUser(new File("Test", "signer.pfx").toString(), "Ag0nc1eS");
        failures = 0;

        test = "Test 1: finish files before information package";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.finishFiles();
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 1);
        }
        cleanup(cv);

        test = "Test 2: finish files after VEO has been finalised";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.finishFiles();
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 2);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        test = "Test 3: finish files after VEO has been signed";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.sign(signer, "SHA1withRSA");
            cv.finishFiles();
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 3);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        test = "Test 4: finish files after VEO has been finished";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.sign(signer, "SHA1withRSA");
            cv.finalise(false);
            cv.finishFiles();
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 4);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        System.out.println("Finished testing " + module);
    }

    /**
     * test finishFiles().
     */
    private void testSign() throws VEOError {
        String module = "sign()";
        String test;
        CreateVEO cv = null;
        Path veoDir;
        DirectoryStream<Path> ds;
        PFXUser signer;

        System.out.println("TESTING: " + module);
        signer = new PFXUser(new File("Test", "signer.pfx").toString(), "Ag0nc1eS");
        failures = 0;

        test = "Test 1: null signer parameter";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.sign(null, "SHA1withRSA");
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 1);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        test = "Test 2: null signature algorithm parameter";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.sign(signer, null);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 2);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        test = "Test 3: sign() before finishFiles() called";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.sign(signer, "SHA1withRSA");
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 3);
        }
        cleanup(cv);

        test = "Test 3: sign() before finishFiles() called";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.sign(signer, "SHA1withRSA");
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 3);
        }
        cleanup(cv);

        test = "Test 3: sign() before finishFiles() called";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addMetadataPackage(templates.findTemplate("agls"), new String[]{"First", "Second"});
            cv.sign(signer, "SHA1withRSA");
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 3);
        }
        cleanup(cv);

        test = "Test 3: sign() before finishFiles() called";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addMetadataPackage(templates.findTemplate("agls"), new String[]{"First", "Second"});
            cv.addInformationPiece("Label");
            cv.sign(signer, "SHA1withRSA");
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 3);
        }
        cleanup(cv);

        test = "Test 4: sign() twice";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addMetadataPackage(templates.findTemplate("agls"), new String[]{"First", "Second"});
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.sign(signer, "SHA1withRSA");
            cv.sign(signer, "SHA1withRSA");
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 4);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);

        test = "Test 5: sign() after finish";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addMetadataPackage(templates.findTemplate("agls"), new String[]{"First", "Second"});
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.sign(signer, "SHA1withRSA");
            cv.finalise(false);
            cv.sign(signer, "SHA1withRSA");
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 5);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);
        System.out.println("Finished testing " + module);
    }

    /**
     * test finishFiles().
     */
    private void testFinalise() throws VEOError {
        String module = "finalise()";
        String test;
        CreateVEO cv = null;
        Path veoDir;
        DirectoryStream<Path> ds;
        PFXUser signer;

        System.out.println("TESTING: " + module);
        signer = new PFXUser(new File("Test", "signer.pfx").toString(), "Ag0nc1eS");
        failures = 0;

        test = "Test 1: finalise() before sign() called";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.finalise(false);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 1);
        }
        cleanup(cv);

        test = "Test 1: finalise() before sign() called";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.finalise(false);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 1);
        }
        cleanup(cv);

        test = "Test 1: finalise() before sign() called";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addMetadataPackage(templates.findTemplate("agls"), new String[]{"First", "Second"});
            cv.finalise(false);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 1);
        }
        cleanup(cv);

        test = "Test 1: finalise() before sign() called";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addMetadataPackage(templates.findTemplate("agls"), new String[]{"First", "Second"});
            cv.addInformationPiece("Label");
            cv.finalise(false);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 1);
        }
        cleanup(cv);

        test = "Test 1: finalise twice";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            veoDir = cv.getVEODir();
            cv.addVEOReadme(Paths.get("Test", "Templates"));
            cv.addContent(Paths.get("Test", "S-37-6"));
            cv.addInformationObject("Record", 1);
            cv.addMetadataPackage(templates.findTemplate("agls"), new String[]{"First", "Second"});
            cv.addInformationPiece("Label");
            ds = Files.newDirectoryStream(Paths.get(veoDir.toString(), "S-37-6"));
            for (Path p : ds) {
                cv.addContentFile((veoDir.relativize(p)).toString());
            }
            ds.close();
            cv.finishFiles();
            cv.sign(signer, "SHA1withRSA");
            cv.finalise(false);
            cv.finalise(false);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 2);
        } catch (IOException e) {
            handleException(module, test, e);
        }
        cleanup(cv);
        System.out.println("Finished testing " + module);
    }
    
    /**
     * Generic Test.
     */
    private void testGeneric() {
        String module = "getVEODir()";
        String test;
        CreateVEO cv = null;

        System.out.println("TESTING: " + module);
        failures = 0;

        test = "Test 1: null directory parameter";
        try {
            cv = new CreateVEO(Paths.get("Test"), "testVEO", "SHA-1", false);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 1);
        }
        cleanup(cv);
        System.out.println("Finished testing " + module);
    }

    private void testFailed(String module, String test) {
        System.out.println(module + " FAILED " + test + " wasn't detected");
        failures++;
    }

    private void handleVEOError(String module, String test, VEOError e, int errno) {
        if (e.getId() != errno) {
            System.out.println(module + " FAILED " + test + " WRONG RETURN " + e.getId() + " " + e.toString());
            failures++;
        } else {
            System.out.println(module + " passed " + test + " " + " " + e.getMessage());
        }
    }

    private void handleException(String module, String test, Exception e) {
        System.out.println(module + " FAILED " + test + " NOT A VEOERROR " + e.toString());
        failures++;
    }

    private CreateVEO cleanup(CreateVEO cv) {
        if (cv != null) {
            cv.abandon(false);
        }
        return null;
    }

    private void test() throws VEOError {
        /*
         test constructor
         testConsCreateVEO();
         testGetVEODir();
         testAddVEOReadMe();
         testAddContent();
         testAddInformationObject();
         testAddMetadataPackage();
         testContinueMetadataPackage();
         testAddInformationPiece();
         testAddContentFile();
         testAddEvent();
        testFinishFiles();
        testSign();
         */
        testFinalise();
    }

    /**
     * Test program...
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        TestCreateVEO tcv;

        try {
            tcv = new TestCreateVEO();
            tcv.test();
        } catch (VEOError e) {
            System.out.println("Error: " + e.toString());
            System.exit(-1);
        }
    }

}
