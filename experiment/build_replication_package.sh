#!/usr/bin/env sh

mkdir replication_package

# Copy and extract data_analysis
cp -r data_analysis replication_package/data_analysis
rm replication_package/data_analysis/*.tex replication_package/data_analysis/*.pdf
xz -d replication_package/data_analysis/2022-08-18_completed.csv.xz

# Copy and extract data_collection
cp -r data_collection replication_package/data_collection
tar xf replication_package/data_collection/python3k_CACHED_READMES.tar.gz -C replication_package/data_collection
rm replication_package/data_collection/python3k_CACHED_READMES.tar.gz
xz -d replication_package/data_collection/sha1_git_to_sha1.csv.xz
wget https://dettorer.net/swh-graph-1.0.1.jar -O replication_package/data_collection/swh-graph-1.0.1.jar

# Copy the example graph
cp -r example-graph replication_package/example_graph

# Zip everything
zip -r replication_package.zip replication_package
