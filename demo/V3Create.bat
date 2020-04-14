@echo off
set code="F:/VERS-V3-Package/neoVEO"
java -classpath %code%/dist/* VEOCreate.CreateVEOs -v -t %code%/demo/templates  %* -s %code%/demo/testSigner.pfx password -c %code%/demo/demoVEOCreateControl.txt -o ../../..
