@echo off
set code="G:\PROV\TECHNOLOGY MANAGEMENT\Application Development\VERS\neoVEO"
set versclasspath=%code%;%code%/lib/*
java -classpath %versclasspath% VEOCreate.SignVEOs %*
