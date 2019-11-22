@echo off
rem set code="C:\Users\Andrew\Documents\Work\VERS-2015\VPA"
set code="J:\PROV\TECHNOLOGY MANAGEMENT\Application Development\VERS\VERS-2015\neoVEO"
set versclasspath=%code%/dist/*
java -classpath %versclasspath% VPA.DAIngest %*
