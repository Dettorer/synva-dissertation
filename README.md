# SYNVA M2 dissertation

Last successful build of each documents (click the badge to access the
corresponding PDF):

- Master's dissertation: [![workflow status badge](https://github.com/Dettorer/synva-dissertation/actions/workflows/build-and-release.yml/badge.svg)](https://github.com/Dettorer/synva-dissertation/releases/latest/download/masters_dissertation.pdf)
- Master's defense slides: [![workflow status badge](https://github.com/Dettorer/synva-dissertation/actions/workflows/build-and-release.yml/badge.svg)](https://github.com/Dettorer/synva-dissertation/releases/latest/download/masters_defense.pdf)
- EIAH 2023 article: [![workflow status badge](https://github.com/Dettorer/synva-dissertation/actions/workflows/build-and-release.yml/badge.svg)](https://github.com/Dettorer/synva-dissertation/releases/latest/download/EIAH_2023_article.pdf)

Replication package: [![replication package zenodo badge](https://zenodo.org/badge/DOI/10.5281/zenodo.7888415.svg)](https://doi.org/10.5281/zenodo.7888415)

My research dissertation for Strasbourg University's
[SYNVA](https://sfc.unistra.fr/formations/formation_-_ingenierie-de-formation_-_master-2-ingenierie-des-systemes-numeriques-virtuels-pour-lapprentissage-synva_-_2393/)
master's degree.

## Build

The most reliable way to build the documents is to use the
[nix](https://nixos.org/download.html) package manager with
[flakes enabled](https://nixos.wiki/wiki/Flakes):

```shell-session
$ nix build
$ ls result/
EIAH_2023_article.pdf  masters_defense.pdf  masters_dissertation.pdf
```

Without nix, make sure you have a LaTeX distribution with
[latexmk](https://ctan.org/pkg/latexmk),
[xelatex](http://xetex.sourceforge.net/),
[biber](http://biblatex-biber.sourceforge.net/) and the "extra packages" listed
in the [flake.nix](flake.nix) file. Then simply run:

```shell-session
$ cd master_dissertation  # or defense_slides or EIAH_2023_paper
$ latexmk
$ ls build/
...
main.pdf  # or slides.pdf or paper.pdf
...
```

Alternatively, building on [Overleaf](https://overleaf.com/) should work out of
the box.
