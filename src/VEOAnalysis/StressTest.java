/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015.
 */
package VEOAnalysis;

import VERSCommon.LTSF;
import VERSCommon.VEOError;
import VERSCommon.VEOFatal;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Andrew Waugh
 */
final class StressTest {

    Path schemaDir; // directory in which XML schemas are to be found
    Path outputDir; // directory in which the VEOs are generated
    String veo;
    LTSF ltsfs; // valid long term preservation formats
    private final static Logger LOG = Logger.getLogger("VEOAnalysis.StressTest");

    public StressTest(String args[]) throws VEOError {
        // schemaDir = Paths.get("Test/Demo/Schemas");
        // outputDir = Paths.get("../neoVEOOutput/TestAnalysis");
        // veo = "../neoVEOOutput/TestAnalysis/testVEO5.veo.zip";
        schemaDir = Paths.get("neoVEOSchemas");
        outputDir = Paths.get("testOutput");
        veo = "testVEOs";
        ltsfs = new LTSF(schemaDir.resolve("validLTSF.txt"));
        LOG.getParent().setLevel(Level.INFO);
    }

    public void test() throws VEOError {
        Path veoFile;
        DirectoryStream<Path> ds;
        int i;

        veoFile = Paths.get(veo);
        for (i = 0; i < 1000; i++) {
            if (Files.isDirectory(veoFile)) {
                try {
                    ds = Files.newDirectoryStream(veoFile);
                    for (Path p : ds) {
                        if (Files.isRegularFile(p) && p.toString().toLowerCase().endsWith(".veo.zip")) {
                            testVEO(p, i);
                        }
                    }
                    ds.close();
                } catch (IOException e) {
                    System.out.println("Failed to process directory '" + veo + "': " + e.getMessage());
                }
            } else {
                testVEO(veoFile, i);
            }
        }
    }

    public void testVEO(Path veo, int i) throws VEOError {
        RepnVEO rv;

        rv = null;

        System.out.println((System.currentTimeMillis() / 1000) + ": (" + i + ")" + veo);

        // perform the analysis
        try {
            rv = new RepnVEO(schemaDir, veo, true, outputDir, null);
            rv.constructRepn();
            rv.validate(ltsfs, false, false);
            rv.genReport(false, "1.0", "Copyright");
        } catch (VEOError e) {
            System.out.println(e.getMessage());
        } finally {
            if (rv != null) {
                rv.deleteVEO();
                rv.abandon();
            }
        }
    }
    
    public static void main(String args[]) {
        StressTest va;

        if (args.length == 0) {
            args = new String[]{"-r", "-s", "../VERS Std 2013/Release/test/neoVEOSchemas", "-o", "../VERS Std 2013/Release/test/testOutput", "../VERS Std 2013/Release/test/testOutput/demoVEO1.veo.zip"};
            // args = new String[]{"-r", "-s", "Test/Demo/Schemas", "-o", "../neoVEOOutput/TestAnalysis", "../neoVEOOutput/TestAnalysis/parseError.veo.zip"};
        }
        try {
            va = new StressTest(args);
            va.test();
        } catch (VEOFatal e) {
            System.out.println(e.getMessage());
        } catch (VEOError e) {
            System.out.println(e.getMessage());
        }
    }
}
