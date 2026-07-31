#!/bin/bash
set -e

echo "=== START DEBUG ==="
echo "Current Directory: $(pwd)"
ls -F
echo "==================="

if [ -d "udriBook" ]; then
    cd udriBook
fi

# Find the JAR file in target directory
JAR_FILE=$(find . -name "*.jar" | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo "ERROR: JAR file not found!"
    ls -R
    exit 1
fi

echo "Starting JAR: $JAR_FILE on port: $PORT"
java -Dserver.port=$PORT -jar "$JAR_FILE"
