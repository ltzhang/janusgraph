export JANUSGRAPH_HOME="/home/lintaoz/work/janusgraph/conf/janusgraph-full-1.2.0-SNAPSHOT"

# Ensure KVT native library is accessible
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/home/lintaoz/work/janusgraph/janusgraph-kvt/src/main/nativa

alias build_clean="mvn clean install -DskipTests"
alias build="mvn install -DskipTests"
#alias build_clean="mvn clean install -DskipTests -pl janusgraph-kvt -am"
#alias build="mvn compile -pl janusgraph-kvt -DskipTests"
