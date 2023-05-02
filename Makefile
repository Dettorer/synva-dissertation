DOCUMENTS=$(addprefix documents/,masters_dissertation.pdf masters_defense.pdf EIAH_2023_article.pdf)

all: documents

documents: ${DOCUMENTS}

# Master's dissertation
documents/masters_dissertation.pdf:
	mkdir -p documents
	cd master_dissertation && latexmk
	cp master_dissertation/build/main.pdf $@

# Master's defense slides
documents/masters_defense.pdf:
	mkdir -p documents
	cd defense_slides && latexmk
	cp defense_slides/build/slides.pdf $@

# EIAH article
documents/EIAH_2023_article.pdf:
	mkdir -p documents
	cd EIAH_2023_paper && latexmk
	cp EIAH_2023_paper/build/paper.pdf $@

# ReplicationPackage
replication_package.zip: swh-graph-1.0.1.jar
	mkdir replication_package

	# Copy and extract data_analysis
	cp -r experiment/data_analysis replication_package/data_analysis
	rm replication_package/data_analysis/*.tex replication_package/data_analysis/*.pdf
	xz -d replication_package/data_analysis/2022-08-18_completed.csv.xz

	# Copy and extract data_collection
	cp -r experiment/data_collection replication_package/data_collection
	tar xf replication_package/data_collection/python3k_CACHED_READMES.tar.gz -C replication_package/data_collection
	rm replication_package/data_collection/python3k_CACHED_READMES.tar.gz
	xz -d replication_package/data_collection/sha1_git_to_sha1.csv.xz
	cp $< replication_package/data_collection/swh-graph-1.0.1.jar

	# Copy the example graph
	cp -r experiment/example-graph replication_package/example_graph

	# Zip everything
	zip -r replication_package.zip replication_package

swh-graph-1.0.1.jar:
	wget https://dettorer.net/swh-graph-1.0.1.jar

# Make everything phony to force the latexmk invocation, which will handle the
# real dependencies
.PHONY: ${DOCUMENTS} documents

# I'm too lazy for now to properly define the dependencies of the replication
# package
.PHONY: replication_package.zip
