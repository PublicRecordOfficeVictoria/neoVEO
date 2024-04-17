@echo off
if exist "J:/PROV/TECHNOLOGY MANAGEMENT/Application Development/VERS/VERSCode" (
	set code="J:/PROV/TECHNOLOGY MANAGEMENT/Application Development/VERS/VERSCode"
) else if exist "C:/Program Files/VERSCode" (
	set code="C:/Program Files/VERSCode"
) else if exist "Z:/VERSCode" (
	set code="Z:/VERSCode"
) else (
	set code="C:/Users/Andrew/Documents/Work/VERSCode"
)
java -classpath %code%/neoVEO/dist/* VEOResign.SignVEOs -support %code%/VERSCommon/VERSSupportFiles %*
