{
  description = "A dissertation built with Pandoc, XeLaTex and a custom font";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";
    utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, utils }:
  (utils.lib.eachDefaultSystem (system:
    let
      pkgs = nixpkgs.legacyPackages.${system};
      texDependencies = with pkgs; [
        coreutils
        glibcLocales
        inkscape

        # for minted
        python39
        python39Packages.pygments
        which
      ];
      texDistribution = pkgs.texlive.combine {
        inherit (pkgs.texlive)
          # base scheme
          scheme-small

          # extra packages
          abstract
          algorithm2e
          biblatex
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
          framed
          fvextra
          glossaries
          hyphenat
          ifmtarg
          ifoddpage
          imakeidx
          marginfix
          marginnote
          mdframed
          mfirstuc
          minted
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
          xurl
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

      # For the Software Heritage graph manipulation
      javaDistribution = with pkgs; [
        jdk
      ];
      # for swh-perfecthash, a dependency of swh-graph
      cmph = pkgs.stdenv.mkDerivation {
        name = "cmph-2.0.2";
        version = "2.0.2";
        src = pkgs.fetchurl {
          url = "https://deac-fra.dl.sourceforge.net/project/cmph/v2.0.2/cmph-2.0.2.tar.gz";
          sha256 = "365f1e8056400d460f1ee7bfafdbf37d5ee6c78e8f4723bf4b3c081c89733f1e";
        };

        nativeBuildInputs = [ pkgs.autoreconfHook ];
      };
      # for the README content checking script
      pythonDistribution = with pkgs; [
        python39
        python39Packages.boto3 # aws s3 querying
        python39Packages.charset-normalizer # encoding detection
        python39Packages.mypy-boto3-s3 # type hints for boto3
        python39Packages.requests # http querying
      ];
      experimentDependencies =
        pythonDistribution ++ javaDistribution ++ [ cmph pkgs.postgresql ];
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

      packages.default = packages.dissertation;

      devShells.default = pkgs.mkShell {
        inherit FONTCONFIG_FILE;
        buildInputs = (
          [ texDistribution pkgs.evince ]
          ++ texDependencies
          ++ experimentDependencies
        );
      };
    }
  ));
}
