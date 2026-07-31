@echo off
echo Building Spring Boot Application...
cd udriBook
call mvnw.cmd clean package -DskipTests
if %ERRORLEVEL% EQU 0 (
    echo Build successful!
    echo JAR file created in udriBook/target/
) else (
    echo Build failed!
    exit /b 1
)
