@echo off
set code="C:\Users\Andrew\Documents\Work\VERSCode\neoVEO"
rem set code="J:\PROV\TECHNOLOGY MANAGEMENT\Application Development\VERS\VERSCode\neoVEO"
set versclasspath=%code%/dist/*
java -classpath %versclasspath% VEOCreate.CreateVEOs -v -t %code%/neoVEOTemplates %*
