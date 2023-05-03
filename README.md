# SYNVA M2 dissertation and corresponding EIAH 2023 article

Last successful build of each document (click the badge to access the
corresponding PDF):

- Master's dissertation: [![workflow status badge](https://github.com/Dettorer/synva-dissertation/actions/workflows/build-and-release.yml/badge.svg)](https://github.com/Dettorer/synva-dissertation/releases/latest/download/masters_dissertation.pdf)
- Master's defense slides: [![workflow status badge](https://github.com/Dettorer/synva-dissertation/actions/workflows/build-and-release.yml/badge.svg)](https://github.com/Dettorer/synva-dissertation/releases/latest/download/masters_defense.pdf)
- EIAH 2023 article: [![workflow status badge](https://github.com/Dettorer/synva-dissertation/actions/workflows/build-and-release.yml/badge.svg)](https://github.com/Dettorer/synva-dissertation/releases/latest/download/EIAH_2023_article.pdf)

My research dissertation for Strasbourg University's
[SYNVA](https://sfc.unistra.fr/formations/formation_-_ingenierie-de-formation_-_master-2-ingenierie-des-systemes-numeriques-virtuels-pour-lapprentissage-synva_-_2393/)
master's degree, and the article submitted to the [EIAH
2023](https://eiah2023.sciencesconf.org/) for the same work.

## Build the documents

The most reliable way to build the documents is to use the
[nix](https://nixos.org/download.html) package manager with
[flakes enabled](https://nixos.wiki/wiki/Flakes):

```console
$ nix build  # equivalent to nix build '.#documents'
$ ls result/
EIAH_2023_article.pdf  masters_defense.pdf  masters_dissertation.pdf
```

Without nix, make sure you have a LaTeX distribution with
[latexmk](https://ctan.org/pkg/latexmk),
[xelatex](http://xetex.sourceforge.net/),
[biber](http://biblatex-biber.sourceforge.net/) and the "extra packages" listed
in the [flake.nix](flake.nix) file. Then simply run:

```console
$ make documents
...  # latexmk output
$ ls documents
EIAH_2023_article.pdf  masters_defense.pdf  masters_dissertation.pdf
```

Or, to build one document manually, use either `make documents/<document_name>`
or manually call latexmk in its source directory:

```console
$ cd master_dissertation  # or defense_slides or EIAH_2023_paper
$ latexmk
$ ls build/
...
main.pdf  # or slides.pdf or paper.pdf
...
```

Alternatively, building on [Overleaf](https://overleaf.com/) should work out of
the box.

## Replication package

A replication package for the research results is available on zenodo:
[![replication package zenodo
badge](https://zenodo.org/badge/DOI/10.5281/zenodo.7888415.svg)](https://doi.org/10.5281/zenodo.7888415).
It contains the source code used to collect the data and to analyze it
statistically, along with the special `swh-graph.jar` library used to do
efficient paraller traversal of the graph (see
[experiment/data_collection/README.md](experiment/data_collection/README.md)),
an example graph for testing and a snapshot of the data collected on the full
graph on August 18 2022.

You can also build the replication package from this repository yourself, using
either nix:

```console
$ nix build '.#replicationPackage'
$ ls result/
replication_package.zip
```

Or make directly, but be aware that any extraneous files you added in the
experiment's folders might be included in the final zip (the build expects a
fresh clone, or a pure nix build environment):

```console
$ make replication_package.zip
$ file replication_package.zip
replication_package.zip: Zip archive data, at least v1.0 to extract, compression
method=store
```
