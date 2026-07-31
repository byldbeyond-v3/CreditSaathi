#!/bin/bash
set -e

echo "=== DEBUG INFO ==="
echo "Current User: $(whoami)"
echo "Current Directory: $(pwd)"
echo "Listing Root Files:"
ls -F
echo "=================="

if [ -d "udriBook" ]; then
    echo "Entering udriBook directory..."
    cd udriBook
else
    echo "Already in udriBook or directory not found. Staying here."
fi

# Make mvnw executable if it exists
if [ -f "mvnw" ]; then
    chmod +x mvnw
    echo "Building with ./mvnw..."
    ./mvnw clean package -DskipTests
elif [ -f "../udriBook/mvnw" ]; then
    chmod +x ../udriBook/mvnw
    echo "Found mvnw in parent udriBook folder..."
    ../udriBook/mvnw clean package -DskipTests
else
    echo "mvnw not found, trying system mvn..."
    mvn clean package -DskipTests
fi

echo "Build completed successfully!"
