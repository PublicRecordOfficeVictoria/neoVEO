/*
 * Copyright Public Record Office Victoria 2006 & 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */

package VEOCreate;

import VERSCommon.VERSDate;
import VERSCommon.VEOError;

/**
 * This fragment represent dynamic content that is replaced by the current
 * date and time in an ISO8061 format. Warning: Do not directly call this
 * class. Use the CreateVEO or CreateVEOs classes.
 */
public class DateFragment extends Fragment {

/**
 * Construct a fragment that will output the current date/time.
 *
 * @param location source of the substitution (file/line)
 * @param uri URIs identifying semantics and syntax of fragment
 */
public DateFragment(String location, String[] uri) {
	super(location, uri);
}

/**
 * Output the current data and time (in ISO8061 format) to the VEO. The data array
 * is not used.
 * @param data the data to finalise fragment
 * @param document the place to put the resulting text
 * @throws VEOError if a fatal error occurred
 */
@Override
public void finalise(String data[], CreateXMLDoc document) throws VEOError {

	// output current data time to VEO
	document.write(VERSDate.versDateTime((long) 0));

	// finalise any trailing fragments (if any)
	if (next != null)
		next.finalise(data, document);
}

/**
 * Outputs this fragment as a string.
 * @return the string representation of the fragment
 */
@Override
public String toString() {
	String s;
	s = "Date Fragment:\n";
	if (next != null)
		s += next.toString();
	return s;
}
}
