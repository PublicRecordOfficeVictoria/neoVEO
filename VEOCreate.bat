@echo off
set code="C:\Users\Andrew\Documents\Work\VERS-2015\neoVEO"
set bin="C:\Program Files\Java\jdk1.8.0_65\bin"
rem set code="J:\PROV\TECHNOLOGY MANAGEMENT\Application Development\VERS\VERS3\neoVEO"
rem set bin="C:\Program Files\Java\jdk1.8.0_72\bin"
set versclasspath=%code%/dist/*
%bin%\java -classpath %versclasspath% VEOCreate.CreateVEOs -v -c ./Test/Demo/control.txt -t ./Test/Demo/templates -link -o ./Test/Demo/output %*
