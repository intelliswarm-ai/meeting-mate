@echo off
echo Looking for Java 11...

if exist "C:\Program Files\Microsoft\jdk-11.0.16.101-hotspot\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Microsoft\jdk-11.0.16.101-hotspot"
    echo Found Java 11 at Microsoft path
) else if exist "C:\Program Files\Eclipse Adoptium\jdk-11.0.16.101-hotspot\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-11.0.16.101-hotspot"
    echo Found Java 11 at Eclipse Adoptium path
) else if exist "C:\Program Files\OpenJDK\jdk-11.0.16.1+1\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\OpenJDK\jdk-11.0.16.1+1"
    echo Found Java 11 at OpenJDK path
) else (
    echo Java 11 not found in standard locations
    echo Please install Java 11 or update the paths in this script
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Using Java:
"%JAVA_HOME%\bin\java" -version

echo Stopping Gradle daemon...
gradlew --stop

echo Building with Java 11...
gradlew assembleRelease

pause