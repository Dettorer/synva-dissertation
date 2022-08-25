# SYNVA M2 dissertation

[![workflow status badge](https://github.com/Dettorer/synva-dissertation/actions/workflows/build-and-release.yml/badge.svg)](https://github.com/Dettorer/synva-dissertation/releases/latest/download/Paul_Hervot_M2_dissertation.pdf)

Replication package: [![replication package zenodo badge](https://zenodo.org/badge/DOI/10.5281/zenodo.7023495.svg)](https://doi.org/10.5281/zenodo.7023495)

My research dissertation for Strasbourg University's
[SYNVA](https://sfc.unistra.fr/formations/formation_-_ingenierie-de-formation_-_master-2-ingenierie-des-systemes-numeriques-virtuels-pour-lapprentissage-synva_-_2393/)
master's degree.

Latest successful build is available
[here](https://github.com/Dettorer/synva-dissertation/releases/latest/download/Paul_Hervot_M2_dissertation.pdf).

## Build

The most reliable way to build this document is using the
[nix](https://nixos.org/download.html) package manager with
[flakes enabled](https://nixos.wiki/wiki/Flakes):

```shell-session
$ nix build
$ ls result/
Paul_Hervot_M2_dissertation.pdf
```

Without nix, make sure you have a LaTeX distribution with
[latexmk](https://ctan.org/pkg/latexmk),
[xelatex](http://xetex.sourceforge.net/),
[biber](http://biblatex-biber.sourceforge.net/) and the "extra packages" listed
in the [flake.nix](flake.nix) file. Then simply run:

```shell-session
$ latexmk
$ ls build/
...
main.pdf
...
```

Alternatively, building on [Overleaf](https://overleaf.com/) should work out of
the box.
