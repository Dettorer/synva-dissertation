# Data collection pipeline

## Initial collection

**Requirements**: a java runtime (we used Openjdk11 but the version shouldn't be
very important).

This replication package comes with a modified version of the `swh-graph`
library (from <https://docs.softwareheritage.org/devel/swh-graph/java-api.html>)
that makes a lot of graph-querying functions thread-safe. If you want to use the
official version (which hasn't been made similarly thread-safe yet), you'll need
to either run the collection code with one single thread, or to add a lot of
`synchronized` blocks int the java class, which are likely to essentially
serialize the execution and nullify the benefits of multi-threading.

The preferred way of running the initial collection code on a Software Heritage
graph is to run the `collect_data.sh` shell script, which will in turn invoke
the Java class `CollectData.java`. Check the output of `./collect_data.sh -h` to
know how to invoke the script and what are the available options.

To use the `test_with_python3k.sh` test, download the
[`python3k`](https://docs.softwareheritage.org/devel/swh-dataset/graph/dataset.html#popular-3k-python)
subgraph. It is a "teaser" subset of the complete graph that we used as a
reasonably small (15 GiB) dataset to test the collection pipeline locally before
running on the full graph.

## Readme content Check

**Requirements**: a Python3 interpreter with the packages listed in
`requirements.txt`, which can typically be installed with pip in a virtualenv.

The `CACHED_READMES` folder initially contains all the readme files strictly
needed for the python3k dataset, compressed in gzip. The
`sha1_git_to_sha1.csv` file, however, contains the translation of hashes from
`sha1_git` to `sha1` for all the `README`s strictly needed for the python3k
dataset *and* the complete graph run that was done for the real experiment (the
complete graph as of August 18th, 2022).

Check the output of `./check_readme_contents.py -h` to know how to invoke the
script.
