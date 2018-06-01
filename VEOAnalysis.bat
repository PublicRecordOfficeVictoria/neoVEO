@echo off
set code="C:\Users\Andrew\Documents\Work\VERS-2015\neoVEO"
set bin="C:\Program Files\Java\jdk1.8.0_65\bin"
rem set code="J:\PROV\TECHNOLOGY MANAGEMENT\Application Development\VERS\VERS3\neoVEO"
rem set bin="C:\Program Files\Java\jdk1.8.0_72\bin"
set versclasspath=%code%/dist/*
%bin%\java -classpath %versclasspath% VEOAnalysis.VEOAnalysis -c -v -r -s %code%\neoVEOSchemas %*
