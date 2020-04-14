/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOCreate;

import VERSCommon.VEOError;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * This class creates a VEOHistory.xml file. This class should not be called
 * directly, instead the CreateVEOs class should be used.
 */
class CreateVEOHistory extends CreateXMLDoc {

    private final String version; // version to use (default is "3.0")
    private int state;      // state of creation
    private final static int NOT_STARTED = 0; // History file has not been started
    private final static int ADDING_EVENTS = 2;// Opened and events being written
    private final static int CLOSED = 3;      // History file has been finalised

    private final static Logger log = Logger.getLogger("veocreate.createSignatureFile");

    /**
     * Default constructor.
     *
     * @param veoDir VEO directory in which to create history file
     * @param version version number
     * @throws VEOError if a fatal error occurred
     */
    public CreateVEOHistory(Path veoDir, String version) throws VEOError {
        super(veoDir);
        classname = "CreateVEOHistory";
        this.version = version;
        state = NOT_STARTED;
    }

    static String contents1
            = " <vers:Version>";
    static String contents2
            = "</vers:Version>\r\n";

    /**
     * Starts a VEOHistory.xml file. This will overwrite any existing file.
     *
     * @throws VEOError if a fatal error occurred
     */
    public void start() throws VEOError {
        String method = "start";
        log.entering("CreateVEOHistoryFile", "start");

        // check start is only called once...
        if (state != NOT_STARTED) {
            throw new VEOError(classname, method, 1, "start() called a second time");
        }

        // start VEO History file
        startXMLDoc("VEOHistory.xml", "vers:VEOHistory");
        state = ADDING_EVENTS;

        // VEO Version
        write(contents1);
        writeValue(version);
        write(contents2);

        log.exiting("CreateVEOHistoryFile", "start");
    }

    static String contents3
            = "  <vers:Event>\r\n   <vers:EventDateTime>";
    static String contents4
            = "</vers:EventDateTime>\r\n   <vers:EventType>";
    static String contents5
            = "</vers:EventType>\r\n   <vers:Initiator>";
    static String contents6
            = "</vers:Initiator>\r\n";
    static String contents7 = "   <vers:Description>\r\n";
    static String contents8 = "   </vers:Description>\r\n";
    static String contents9 = "   <vers:Error>\r\n";
    static String contents10 = "   </vers:Error>\r\n";
    static String contents11 = "  </vers:Event>\r\n";

    /**
     * Adds an event to the VEOHistory.xml file.
     *
     * @param timeStamp date and time the event occurred (in VERS format)
     * @param eventType type of event that occurred
     * @param initiator who or what initiated the even
     * @param descriptions array of descriptions of event
     * @param errors array of error messages
     * @throws VEOError if a fatal error occurred
     */
    public void addEvent(String timeStamp,
            String eventType,
            String initiator,
            String[] descriptions,
            String[] errors) throws VEOError {
        String method = "addEvent";
        int i;

        log.entering("CreateVEOHistoryFile", "addEvent");

        // check start is only called once...
        if (state != ADDING_EVENTS) {
            throw new VEOError(classname, method, 1, "addEvent() called before start()");
        }

        // sanity check
        if (timeStamp == null) {
            throw new VEOError(classname, method, 1, "timeStamp paramenter is null");
        }

        // general description of event
        write(contents3);
        writeValue(timeStamp);
        write(contents4);
        if (eventType == null || eventType.isEmpty() || eventType.trim().equals(" ")) {
            write("No event specified");
        } else {
            writeValue(eventType);
        }
        write(contents5);
        if (initiator == null || initiator.isEmpty() || initiator.trim().equals(" ")) {
            write("No initiator specified");
        } else {
            writeValue(initiator);
        }
        write(contents6);

        // write descriptions
        if (descriptions == null || descriptions.length == 0) {
            write(contents7);
            write("No event description specified");
            write(contents8);

        } else {
            for (i = 0; i < descriptions.length; i++) {
                write(contents7);
                writeValue(descriptions[i]);
                write(contents8);
            }
        }

        // write errors
        if (errors != null) {
            for (i = 0; i < errors.length; i++) {
                write(contents9);
                writeValue(errors[i]);
                write(contents10);
            }
        }

        // finish event
        write(contents11);

        log.exiting("CreateVEOHistoryFile", "addEvent");
    }

    /**
     * Finishes a VEOHistory.xml file.
     *
     * @throws VEOError if a fatal error occurred
     */
    public void finalise() throws VEOError {
        String method = "finalise";
        log.entering("CreateVEOHistoryFile", "finalise");

        // check start is only called once...
        if (state != ADDING_EVENTS) {
            throw new VEOError(classname, method, 1, "called before start() or after finalise()");
        }

        // close XML document
        endXMLDoc();

        log.exiting("CreateVEOHistoryFile", "finalise");
    }

    /**
     * Abandon construction of this VEOContent file.
     *
     * @param debug true if in debugging mode
     */
    @Override
    public void abandon(boolean debug) {
        super.abandon(debug);
    }

    /**
     * M A I N
     *
     * Test program to tell if CreateSignatureFile is working
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        CreateVEOHistory cvhf;
        String[] descriptions, errors;

        try {
            cvhf = new CreateVEOHistory(Paths.get("Test"), "3.0");
            cvhf.start();
            descriptions = new String[]{"One"};
            errors = new String[]{"Two"};
            cvhf.addEvent("20140101", "Created", "Andrew", descriptions, errors);
            cvhf.finalise();

        } catch (VEOError e) {
            System.out.println(e.getMessage());
        }
        System.out.println("Complete!");
    }
}
