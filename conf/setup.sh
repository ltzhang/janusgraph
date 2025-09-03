export JANUSGRAPH_HOME="/home/lintaoz/work/janusgraph"

# Ensure KVT native library is accessible
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$JANUSGRAPH_HOME/janusgraph-kvt/src/main/nativa

alias build_clean="mvn clean install -DskipTests"
alias build="mvn install -DskipTests"
#alias build_clean="mvn clean install -DskipTests -pl janusgraph-kvt -am"
#alias build="mvn compile -pl janusgraph-kvt -DskipTests"
