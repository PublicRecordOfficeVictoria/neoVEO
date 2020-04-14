/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015.
 */
package Tests;

// import VEOAnalysis.RepnVEO;
import VERSCommon.VEOError;
import VERSCommon.VEOFatal;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
final class StressTest {

    Path schemaDir; // directory in which XML schemas are to be found
    Path outputDir; // directory in which the VEOs are generated
    String veo;
    HashMap<String, String> ltpfs; // valid long term preservation formats
    private final static Logger log = Logger.getLogger("VEOAnalysis.StressTest");

    public StressTest(String args[]) throws VEOError {
        // schemaDir = Paths.get("Test/Demo/Schemas");
        // outputDir = Paths.get("../neoVEOOutput/TestAnalysis");
        // veo = "../neoVEOOutput/TestAnalysis/testVEO5.veo.zip";
        schemaDir = Paths.get("neoVEOSchemas");
        outputDir = Paths.get("testOutput");
        veo = "testVEOs";
        ltpfs = new HashMap<>();
        readValidLTPFs(schemaDir);
        log.getParent().setLevel(Level.INFO);
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
                            testVEO(p.toString(), i);
                        }
                    }
                    ds.close();
                } catch (IOException e) {
                    System.out.println("Failed to process directory '" + veo + "': " + e.getMessage());
                }
            } else {
                testVEO(veo, i);
            }
        }
    }

    public void testVEO(String veo, int i) throws VEOError {
        /*
        RepnVEO rv;

        rv = null;

        System.out.println((System.currentTimeMillis() / 1000) + ": (" + i + ")" + veo);

        // perform the analysis
        try {
            rv = new RepnVEO(veo, true, outputDir);
            rv.constructRepn(schemaDir);
            rv.validate(ltpfs, false);
            rv.genReport(false);
        } catch (VEOError e) {
            System.out.println(e.getMessage());
        } finally {
            if (rv != null) {
                rv.deleteVEO();
                rv.abandon();
            }
        }*/
    }

    /**
     * Read a file containing a list of accepted LTPFs. The file is
     * 'validLTPF.txt' located in schemaDir. The file will contain multiple
     * lines. Each line will contain one file format extension string (e.g.
     * '.pdf').
     *
     * @param schemaDir the directory in which the file is to be found
     * @throws VEOFatal if the file could not be read
     */
    public void readValidLTPFs(Path schemaDir) throws VEOFatal {
        String method = "readValidLTPF";
        Path f;
        FileReader fr;
        BufferedReader br;
        String s;

        f = Paths.get(schemaDir.toString(), "validLTPF.txt");

        // open validLTPF.txt for reading
        fr = null;
        br = null;
        try {
            fr = new FileReader(f.toString());
            br = new BufferedReader(fr);

            // go through validLTPF.txt line by line, copying patterns into hash map
            // ignore lines that do begin with a '!' - these are comment lines
            while ((s = br.readLine()) != null) {
                s = s.trim();
                if (s.length() == 0 || s.charAt(0) == '!') {
                    continue;
                }
                ltpfs.put(s, s);
            }
        } catch (FileNotFoundException e) {
            throw new VEOFatal("StressTest", method, 2, "Failed to open LTPF file '" + f.toAbsolutePath().toString() + "'" + e.toString());
        } catch (IOException ioe) {
            throw new VEOFatal("StressTest", method, 1, "unexpected error: " + ioe.toString());
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) { /* ignore */ }
            }
            if (fr != null) {
                try {
                    fr.close();
                } catch (IOException e) { /* ignore */ }
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
