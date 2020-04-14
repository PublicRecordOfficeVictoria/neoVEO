@echo off
set code="F:/VERS-V3-Package/neoVEO"
java -classpath %code%/dist/* VEOAnalysis.VEOAnalysis -c -v -r -norec -s %code%/neoVEOSchemas -o F:/ %*
