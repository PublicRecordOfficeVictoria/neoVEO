@echo on
if exist "J:/PROV/TECHNOLOGY MANAGEMENT/Application Development/VERS/VERSCode" (
	set code="J:/PROV/TECHNOLOGY MANAGEMENT/Application Development/VERS/VERSCode"
) else if exist "C:/Program Files/VERSCode" (
	set code="C:/Program Files/VERSCode"
) else if exist "Z:/VERSCode" (
	set code="Z:/VERSCode"
) else (
	set code="C:/Users/Andre/Documents/Work/VERSCode"
)
java -classpath %code%/neoVEO/dist/* VEOResign.SignVEOs -create -support %code%/VERSCommon/VERSSupportFiles -v -s testSigner.pfx password %*
