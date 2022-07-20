# VERS V3 Development Package (neoVEO)

Copyright Public Records Office Victoria 2022
License CC BY 4.0

## What is VERS?

This package is part of the Victorian Electronic Records Strategy (VERS)
software release. For more information about VERS see
[here](https://prov.vic.gov.au/recordkeeping-government/vers).

## What is V2Signer?

This package contains the code for tools to produce and analyse Version 3 (V3) VERS
Encapsulated Objects (VEOs). Version 3 VEOs are specified in Specifications 4 & 5 of 
PROS 19/05 (which replaced PROS 15/003).

It contains:
* Java code for the tools
* Supporting Jar files
* Jar files built from the code for the tools
* Javadoc describing the public interfaces to the tools
* A Word file describing how to use the tools
* BAT files demonstrating how the applications can be run
* A sample PFX file that can be used to sign the VEOs
* Schema files necessary to validate and analyse V3 VEOs
* Sample template files necessary to create VEOs

## The Tools

There are three tools in this package:
* CreateVEOs (neoVEO/src/VEOCreate) This tool creates VEOs using a text control file.
* CreateVEO (neoVEO/src/VEOCreate) This is an API that creates a single VEO.
* VEOAnalysis (neoVEO/src/VEOAnalysis) This verifies and analyses VEOs.

## Java dependencies

The tools are written in Java 8.0.

## The Java Packages

There are three Java packages:
* VEOCreate (neoVEO/src/VEOCreate) contains the CreateVEOs and CreateVEO tools
* VEOAnalyis (neoVEO/src/VEOAnalysis) contains the VEOAnalysis tool
* VERSCommon (VERSCommon/src/VERSCommon) contains utility classes

The VERS code has been written over a very long period of time. The VERSCommon
package, in particular, has multiple ways of doing things (these classes are shared
with the tools used to construct VERS V2 VEOs, the code for which dates back over
20 years).

## The Libraries

The tools depend on the following public domain libraries:
* Jena Core (2.12.1)
* Jena IRA (1.1.1)
* Xerces (2.11.0)
* Xerces APIs (1.4.01)
* Log4j (1.2.17)

JAR files containing these libraries can be found in neoVEO/srclib and
VERSCommon/srclib (Log4j), together with the relevant licenses.

## The built JAR files and JavaDoc

The built JAR files and the JavaDoc can be found in neoVEO/dist (neoVEO.jar), and
VERSCommon/dist (VERSCommon.jar).

## Executing the tools - CreateVEOs

CreateVEOs is intended to be run as a program from the command line.

A sample BAT file can be found in the neoVEO/demo directory (V3Create.bat). The necessary
templates and VEOReadme.txt file to create V3 VEOs can be found in the
neoVEO/demo/templates directory.

A sample PFX file to sign the created VEOs can be found in the demo directory
(neoVEO/demo/testSigner.pfx). The password for this PFX file is 'password'. This PFX file
should be used for test purposes and must not be used to sign production VEOs.

## Executing the tools - VEOAnalysis

VEOAnalysis is intended to be run as a program from the command line.

A sample BAT file can be found in the neoVEO/demo directory (V3Analysis.bat). The
necessary schemas to validate and analyse V3 VEOs can be found in
neoVEO/neoVEOSchemas.

## Executing the tools - CreateVEO

Sample code to create a VEO can be found as the main() method in the CreateVEO.java
class.

neoVEO is the suite of tools dealing with  (VERS V3). The three tools are
V3Create (Create V3 VEOs from templates), V3Analysis (Check V3 VEOs against schemas), and
V3Resign (Add digital signatures to an unzipped V3 VEO and zip it).
