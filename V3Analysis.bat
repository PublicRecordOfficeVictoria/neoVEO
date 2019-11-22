@echo off
rem set code="C:\Users\Andrew\Documents\Work\VERS-2015\VPA"
set code="J:\PROV\TECHNOLOGY MANAGEMENT\Application Development\VERS\VERSCode\neoVEO"
set versclasspath=%code%/dist/*
java -classpath %versclasspath% VEOAnalysis.VEOAnalysis -c -v -r -norec -s %code%\neoVEOSchemas %*
