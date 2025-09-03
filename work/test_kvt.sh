#!/bin/bash

# Check if a script argument was provided
if [ $# -eq 0 ]; then
    echo "Usage: $0 <groovy_script>"
    echo "Example: $0 test_kvt.groovy"
    exit 1
fi

GROOVY_SCRIPT="$1"

# Check if the script file exists
if [ ! -f "$GROOVY_SCRIPT" ]; then
    echo "Error: Script file '$GROOVY_SCRIPT' not found"
    exit 1
fi

export JAVA_OPTIONS="-Djava.library.path=$PWD/janusgraph-full-1.2.0-SNAPSHOT/lib"
./janusgraph-full-1.2.0-SNAPSHOT/bin/gremlin.sh -e "$GROOVY_SCRIPT"
