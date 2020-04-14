/*
 * Copyright Public Record Office Victoria 2006 & 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */

package VEOCreate;

import VERSCommon.VEOError;

/**
 * This fragment represents static content which will simply be output to the
 * VEO with no further processing.  Warning: Do not directly call this
 * class. Use the CreateVEO or CreateVEOs classes.
 */
class StringFragment extends Fragment {
	String string;

/**
 * Construct a static string fragment.
 *
 * @param location location of the fragment that generated this substitution
 * @param string the static content to be written to the VEO
 * @param uri URIs identifying the syntax and semantics for this metadata package
 */
public StringFragment(String location, String string, String[] uri) {
	super(location, uri);
	this.string = string;
}

/**
 * Output the static string to the VEO. The data array
 * is not used.
 * 
 * @param data  the data to be applied to this fragment
 * @param document where to put the resulting text
 * @throws VEOError if a fatal error occurred
 */
@Override
public void finalise(String[] data, CreateXMLDoc document)
	throws VEOError {

	// output to VEO
	document.write(string);

	// finalise any trailing fragments (if any)
	if (next != null)
		next.finalise(data, document);
}

/**
 * Outputs this fragment as a string.
 * @return string representation of a StringFragment
 */
@Override
public String toString() {
	String s;
	s = "String Fragment: '"+string+"'\n";
	if (next != null)
		s += next.toString();
	return s;
}
}
