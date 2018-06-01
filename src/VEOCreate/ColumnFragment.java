/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package VEOCreate;

import VERSCommon.VEOError;

/**
 * This class represents a dynamic fragment that will be finalised with the
 * value from a column. Warning: Do not directly call this class.
 * Use the CreateVEO or CreateVEOs classes.
 * <p>
 * This fragment represents dynamic content that is obtained from an array of
 * Strings.
 * <p>
 * The model of operation is of a table. The rows of the table represent the
 * data associated with individual VEOs, and the columns specific pieces of
 * metatata.
 * <p>
 * This is implemented using a callback mechanism. When this fragment is
 * finalised, the passed method is called with the column being given by an
 * argument. The value of this column in the current row is returned as a
 * string.
 * 
 * @author Andrew Waugh (andrew.waugh@dvc.vic.gov.au) Copyright 2010 and 2014 PROV
 *
 * Version 1.1 20100809 Added code to encode XML characters ampersand, less than, and greater than
 * Version 2.0 20141010 Repurposed for the new Toolkit.
 *
 */
public class ColumnFragment extends Fragment {

    int column; // the column to obtain value from
    boolean isXML; // true if no XML mapping is to be done to the column value
    static String classname = "ColumnFragment";   // for reporting

    /**
     * Construct a column fragment. When this fragment is finalised, the data
     * to be output to the VEO is obtained from the specified column. If isXML
     * is false, the special characters in XML values (ampersand, less than, greater than
     * single quote and double quote) are encoded.
     *
     * @param location the source of the fragment (file/line number)
     * @param column the column to obtain the value to finalise this fragment
     * @param isXML true if the value is already valid XML
     * @param uri URIs identifying semantics and syntax of the fragment
     */
    public ColumnFragment(String location, int column, boolean isXML, String[] uri) {
        super(location, uri);
        this.column = column;
        this.isXML = isXML;
    }

    /**
     * Extract the specified column from the data array and output it to the
     * VEO.
     *
     * @throws VEOError if the specified column does not exist or is null
     */
    @Override
    public void finalise(String[] data, CreateXMLDoc document) throws VEOError {
        String s;
        String method = "finalise";

        // ask data source for value of specific column
        if (data.length < column) {
            throw new VEOError(classname, method, 1,
                    location + "column " + column
                    + " is not available from the data source");
        }
        s = data[column - 1];
        if (s == null) {
            throw new VEOError(classname, method, 1,
                    location + "column " + column + " in the data is null");
        }

        // write value (encoding XML characters as required)
        if (!isXML) {
            document.writeValue(s);
        } else {
            document.write(s);
        }

        // finalise any trailing fragments (if any)
        if (next != null) {
            next.finalise(data, document);
        }
    }

    /**
     * Outputs this fragment as a string.
     *
     * @return the string representation of this fragment
     */
    @Override
    public String toString() {
        String s;
        s = "Column Fragment: column: " + column + "\n";
        if (next != null) {
            s += next.toString();
        }
        return s;
    }
}
