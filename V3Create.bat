@echo off
if exist "J:/PROV/TECHNOLOGY MANAGEMENT/Application Development/VERS/VERSCode" (
	set code="J:/PROV/TECHNOLOGY MANAGEMENT/Application Development/VERS/VERSCode"
) else if exist "C:/Program Files/VERSCode" (
	set code="C:/Program Files/VERSCode"
) else if exist "Z:/VERSCode" (
	set code="Z:/VERSCode"
) else if exist "C:/Program Files/VERSCode" (
	set code="C:/Program Files/VERSCode"
) else (
	set code="C:/Users/Andrew/Documents/Work/VERSCode"
)
java -classpath %code%/neoVEO/dist/* VEOCreate.CreateVEOs -v -t %code%/neoVEO/V3MetadataTemplates -sf %code%/VERSCommon/VERSSupportFiles -s testSigner.pfx password %*
