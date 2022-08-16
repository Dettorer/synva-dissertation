#!/usr/bin/env sh
usage() {
    echo "usage: $0 [-m|--mapped] <folder>/<prefix>"
    echo
    echo "-m | --mapped: optional, instruct swh-graph to load the given graph by"
    echo "	mapping it mmap(2)-style instead of fully loading it in memory,"
    echo "	this approach is slower but uses less memory"
    echo
    echo "folder: a relative path to a folder containing a SWH graph"
    echo "prefix: the name of that graph, aka the word that starts all the graph's files names"
    echo
    echo "for example, if you have a graph called \"example\" (that is, its files"
    echo "are named \"example.graph\", \"example.properties\", etc.) in a"
    echo "\"example-graph/compressed\" folder, you should invoke this script by doing:"
    echo
    echo "\$ $0 example-graph/compressed/example"
    echo
    echo "or, if the graph is too large to fit in memory:"
    echo
    echo "\$ $0 -m example-graph/compressed/example"
}

# Check loading mode
LOAD_MODE="memory"
if [ x$1 == 'x-m' -o x$1 == 'x--mapped' ]; then
    LOAD_MODE="mapped"
    shift
fi

# Check the graph path
if [ $# != 1 ]; then
    echo "Missing an argument"
    echo
    usage
    exit 1
elif [ ! -f $1.graph ]; then
    echo "Invalid path given: $1.graph does not exist or isn't a valid file"
    echo
    usage
    exit 1
fi
GRAPH_PATH=$1

JAR_NAME="swh-graph-1.0.1.jar"
if [ ! -f $JAR_NAME ]; then
    >&2 echo "$JAR_NAME does not exist, fetching it from dettorer.net"
    >&2 echo "the $JAR_NAME that will be fetched is an updated version of the one in https://pypi.org/project/swh.graph/ that makes a lot of graph querying methods thread safe."
    wget https://dettorer.net/$JAR_NAME
fi

java -ea -cp $JAR_NAME -Dlogback.configurationFile=$PWD/logback.xml CollectData.java $LOAD_MODE $GRAPH_PATH
