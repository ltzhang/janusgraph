#!/bin/bash
cd /home/lintaoz/work/janusgraph/janusgraph-full-1.2.0-SNAPSHOT
export JAVA_OPTIONS="-Djava.library.path=$PWD/lib"
./bin/gremlin.sh -e test_kvt.groovy
