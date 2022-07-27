#!/usr/bin/env sh
if [ $# != 1 -o ! -f $1.graph ]; then
    echo "Missing an argument or invalid path given"
    echo
    echo "usage: $0 <folder>/<prefix>"
    echo
    echo "graph is a relative path to a folder containing a SWH graph"
    echo "prefix is the name of that graph, aka the word that starts all the graph's files names"
    echo
    echo "for example, if you have a graph called \"example\" (that is, its files"
    echo "are named \"example.graph\", \"example.properties\", etc.) in a"
    echo "\"example-graph/compressed\" folder, you should invoke this script by doing:"
    echo
    echo "	$0 example-graph/compressed/example"
    exit 1
fi

if [ ! -d .venv ]; then
    echo ".venv/ does not exist, creating a virtualenv and installing the content of requirements.txt"
    python -m venv .venv
    ./.venv/bin/python -m pip install -r requirements.txt
fi

java -cp .venv/share/swh-graph/swh-graph-1.0.1.jar Experiment.java $1
