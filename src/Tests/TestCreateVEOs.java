/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Tests;

import VEOCreate.CreateVEO;
import VEOCreate.CreateVEOs;
import VEOCreate.Templates;
import VERSCommon.VEOError;
import java.nio.file.*;
import java.util.logging.Logger;

/**
 * This class tests the CreateVEO class
 *
 * @author Andrew Waugh (andrew.waugh@prov.vic.gov.au) Copyright 2014 PROV
 *
 * Versions
 */
class TestCreateVEOs {

    private final static Logger log = Logger.getLogger("veocreate.TestCreateVEOs");
    String classname = "TestCreateVEOs"; // for reporting
    int failures;
    Templates templates;                // database of templates

    /**
     * Main constructor.
     * @throws VEOError if a fatal error occurred
     *
     */
    public TestCreateVEOs() throws VEOError {
        failures = 0;
        templates = new Templates(Paths.get("Test", "Templates"));
    }

    /**
     * Test constructor.
     */
    private void testConsCreateVEOs() {
        String module = "CreateVEOs";
        String test;
        CreateVEOs cv = null;

        System.out.println("TESTING: " + module);
        failures = 0;

        test = "Test 1: null command line parameter";
        try {
            cv = new CreateVEOs(null);
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 1);
        }
        cleanup(cv);

        test = "Test 2: unknown command line parameter";
        try {
            cv = new CreateVEOs(new String[]{"-x"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 2);
        }
        cleanup(cv);

        test = "Test 3: missing command line parameter";
        try {
            cv = new CreateVEOs(new String[]{"-sa"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 3);
        }
        cleanup(cv);

        // Cannot test missing template specification as one is set by default
        test = "Test 5: missing control file command line parameter";
        try {
            cv = new CreateVEOs(new String[]{"-t", "Test\\Templates"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 5);
        }
        cleanup(cv);

        test = "Test 6a: template directory doesn't exist";
        try {
            cv = new CreateVEOs(new String[]{"-t", "Test\\TemplatesNotThere"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 6);
        }
        cleanup(cv);

        test = "Test 7a: template directory is a file";
        try {
            cv = new CreateVEOs(new String[]{"-t", "Test\\TestContent.txt"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 7);
        }
        cleanup(cv);

        test = "Test 6b: control file doesn't exist";
        try {
            cv = new CreateVEOs(new String[]{"-c", "Test\\ControlFileNotThere.txt"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 6);
        }
        cleanup(cv);

        test = "Test 8b: control file is a directory";
        try {
            cv = new CreateVEOs(new String[]{"-c", "Test\\Templates"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 8);
        }
        cleanup(cv);
        
        test = "Test 6c: signer file doesn't exist";
        try {
            cv = new CreateVEOs(new String[]{"-s", "Test\\SignerNotThere.pfx"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 6);
        }
        cleanup(cv);

        test = "Test 8c: signer file is a directory";
        try {
            cv = new CreateVEOs(new String[]{"-s", "Test\\Templates"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 8);
        }
        cleanup(cv);
        
        test = "Test 6d: output directory doesn't exist";
        try {
            cv = new CreateVEOs(new String[]{"-o", "Test\\OutputDirNotThere"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 6);
        }
        cleanup(cv);

        test = "Test 7d: output directory is a file";
        try {
            cv = new CreateVEOs(new String[]{"-o", "Test\\TestContent.txt"});
            testFailed(module, test);
        } catch (VEOError e) {
            handleVEOError(module, test, e, 7);
        }
        cleanup(cv);
        
        test = "Test sc1: all command line parameter";
        try {
            cv = new CreateVEOs(new String[]{"-v", "-d", "-c", "Test\\control.txt", "-s", "Test\\signer.pfx", "Ag0nc1es", "-o", "Test\\TestOutput", "-ha", "SHA-1", "-sa", "SHA1withRSA", "-t", "Test\\Templates", "-copy", "-link", "-move"});
            testPassed(module, test);
        } catch (VEOError e) {
            testFailed(module, test);
        }
        cleanup(cv);

        System.out.println("Finished testing CreateVEOs constructor");
    }

        /**
     * Test buildVEOs().
     */
    private void testBuildVEOs() {
        String module = "buildVEOs";
        String test;
        CreateVEOs cv = null;

        System.out.println("TESTING: " + module);
        failures = 0;

        // Cannot test 1: none existant control file
        // Cannot test 2: cannot open control file
        
        test = "Test 1: null command line parameter";
        try {
            cv = new CreateVEOs(new String[]{"-v", "-d", "-c", "Test\\control1.txt", "-s", "Test\\signer.pfx", "Ag0nc1eS", "-o", "Test\\TestOutput", "-ha", "SHA-1", "-t", "Test\\Templates", "-link"} );
            cv.buildVEOs();
        } catch (VEOError e) {
            handleVEOError(module, test, e, 1);
        }
        cleanup(cv);

        test = "Test sc1: all command line parameter";
        try {
            cv = new CreateVEOs(new String[]{"-v", "-d", "-c", "Test\\control.txt", "-s", "Test\\signer.pfx", "Ag0nc1eS", "-o", "Test\\TestOutput", "-ha", "SHA-1", "-t", "Test\\Templates", "-copy", "-link", "-move"});
            testPassed(module, test);
        } catch (VEOError e) {
            testFailed(module, test);
        }
        cleanup(cv);

        System.out.println("Finished testing "+module);
    }

            /**
     * Test buildVEOs().
     */
    private void testHashEtc() {
        String module = "testHashEtc";
        String test;
        CreateVEOs cv = null;

        System.out.println("TESTING: " + module);
        failures = 0;
        
        test = "Test 1: null command line parameter";
        try {
            cv = new CreateVEOs(new String[]{"-v", "-c", "Test\\control2.txt", "-o", "Test\\TestOutput", "-t", "Test\\Templates", "-link"} );
            cv.buildVEOs();
        } catch (VEOError e) {
            handleVEOError(module, test, e, 1);
        }
        cleanup(cv);

        System.out.println("Finished testing "+module);
    }
    
    /**
     * Generic Test.
     */
    private void testGeneric() {
        String module = "getVEODir()";
        String test;
        CreateVEOs cv = null;

        System.out.println("TESTING: " + module);
        failures = 0;

        test = "Test 1: null directory parameter";
        try {
            cv = new CreateVEOs(null);
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

    private void testPassed(String module, String test) {
        System.out.println(module + " passed " + test + " no error was thrown");
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

    private CreateVEO cleanup(CreateVEOs cv) {
        if (cv != null) {
            cv.abandon(false);
        }
        return null;
    }

    private void test() {
        /*
        testConsCreateVEOs();
        testBuildVEOs();
        */
        testHashEtc();
    }

    /**
     * Test program...
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        TestCreateVEOs tcv;

        try {
            tcv = new TestCreateVEOs();
            tcv.test();
        } catch (VEOError e) {
            System.out.println("Error in initialising TestCreateVEOs: " + e.toString());
            System.exit(-1);
        }
    }

}
