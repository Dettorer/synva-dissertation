{
  description = "A dissertation built with Pandoc, XeLaTex and a custom font";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";
    utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, utils }:
  (utils.lib.eachSystem utils.lib.allSystems (system:
    let
      pkgs = nixpkgs.legacyPackages.${system};
      texDependencies = with pkgs; [ inkscape coreutils glibcLocales ];
      texDistribution = pkgs.texlive.combine {
        inherit (pkgs.texlive)
          # base scheme
          scheme-small

          # extra packages
          abstract
          algorithm2e
          biblatex
          biblatex-apa
          bigfoot
          blindtext
          catchfile
          ccicons
          changepage
          chngcntr
          cleveref
          csquotes
          datatool
          enumitem
          etoc
          floatrow
          footmisc
          footnotebackref
          glossaries
          hyphenat
          ifmtarg
          ifoddpage
          imakeidx
          marginfix
          marginnote
          mdframed
          mfirstuc
          morewrites
          multirow
          needspace
          nomencl
          options
          placeins
          relsize
          sidenotes
          silence
          subfiles
          svg
          thmtools
          tikzpagenodes
          todonotes
          transparent
          trimspaces
          xfor
          xifthen
          xpatch
          xstring
          zref

          # utilities
          biber
          latex-bin
          latexmk
          xindy
        ;
      };
      FONTCONFIG_FILE = pkgs.makeFontsConf {
        fontDirectories = with pkgs; [ libertinus liberation_ttf ];
      };
    in
    rec {
      packages.dissertation = pkgs.stdenv.mkDerivation {
        name = "PaulHervotSYNVADissertation";
        src = ./.;
        buildInputs = [ texDistribution ] ++ texDependencies;
        phases = [ "unpackPhase" "buildPhase" "installPhase" ];
        buildPhase = ''
          export FONTCONFIG_FILE=${FONTCONFIG_FILE}
          mkdir -p .cache/texmf-var
          DIR=$(mktemp -d)
          env \
            TEXMFHOME=.cache TEXMFVAR=.cache/texmf-var \
            SOURCE_DATE_EPOCHE=${toString self.lastModified} \
            latexmk -interaction=nonstopmode
        '';
        installPhase = ''
          mkdir -p $out
          mv build/main.pdf $out/Paul_Hervot_M2_dissertation.pdf
        '';
      };

      defaultPackage = packages.dissertation;

      devShell = pkgs.mkShell {
        inherit FONTCONFIG_FILE;
        buildInputs = [ texDistribution pkgs.evince ] ++ texDependencies;
      };
    }
  ));
}
