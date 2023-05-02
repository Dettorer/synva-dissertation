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
        ghostscript
        inkscape

        # for minted
        python310
        python310Packages.pygments
        which
      ];

      # dependency for mtheme
      latex-readprov = pkgs.stdenv.mkDerivation rec {
        version = "0.5";
        pname = "readprov";
        name = "${pname}-${version}";
        tlType = "run";

        src = pkgs.fetchurl {
          url = "https://mirrors.ctan.org/macros/latex/contrib/fileinfo.zip";
          sha256 = "Y+UHnq303p/WRHhJfXpDNDjGnc3VmKOYiQ6GX7W3P/A=";
        };

        buildInputs = [ pkgs.unzip ];
        installPhase = "
          mkdir -p $out/tex/latex/readprov
          cp -va *.sty $out/tex/latex/readprov
          mkdir -p $out/doc/latex/readprov
          cp -va doc/* $out/doc/latex/readprov
        ";
      };
      # the beamer template for my slides
      latex-mtheme = pkgs.stdenv.mkDerivation rec {
        version = "1.2";
        pname = "mtheme";
        name = "${pname}-${version}";
        tlType = "run";

        src = pkgs.fetchurl {
          url = "https://github.com/matze/mtheme/archive/refs/tags/v${version}.tar.gz";
          sha256 = "+9q4OzUeBiwQRFaP/X5koIl4Pdrr3vp/RiUq9FEEoTg=";
        };

        buildInputs = [
          pkgs.gnumake
          pkgs.unzip
          (pkgs.texlive.combine {
            inherit (pkgs.texlive) scheme-small latexmk enumitem;
            readprov.pkgs = [ latex-readprov ];
          })
        ];
        buildPhase = "make";
        installPhase = "
        mkdir -p $out/tex/latex/mtheme
        cp -va *.sty $out/tex/latex/mtheme
        mkdir -p $out/doc/latex/mtheme
        cp -va doc/* $out/doc/latex/mtheme
        ";
      };

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

          pgfopts # not sure why needed

          # utilities
          biber
          latex-bin
          latexmk
          xindy
        ;
        mtheme.pkgs = [latex-mtheme];
      };
      FONTCONFIG_FILE = pkgs.makeFontsConf {
        fontDirectories = with pkgs; [ libertinus liberation_ttf ];
      };

      # For the Software Heritage graph manipulation
      javaDistribution = with pkgs; [
        jdk11
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
      # for the README content checking script and the analysis script
      pythonDistribution = with pkgs; [
        python310
        python310Packages.boto3 # aws s3 querying
        python310Packages.charset-normalizer # encoding detection
        python310Packages.jinja2 # for pandas' latex styling
        python310Packages.matplotlib # graphical data visualisation
        python310Packages.more-itertools # for windowed iterators
        python310Packages.mypy-boto3-s3 # type hints for boto3
        python310Packages.pandas # data analysis
        python310Packages.requests # http querying
        python310Packages.scipy # for its statistical functions
        python310Packages.statsmodels # for drawing Q-Q plots
      ];
      experimentDependencies =
        pythonDistribution ++ javaDistribution ++ [ cmph pkgs.postgresql ];
    in
    rec {
      packages.documents = pkgs.stdenv.mkDerivation {
        name = "documents";
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
            make documents
        '';
        installPhase = ''
          mkdir -p $out
          mv documents/* $out/
        '';
      };

      packages.replicationPackage = let
        swhJar = pkgs.fetchurl {
          url = "https://dettorer.net/swh-graph-1.0.1.jar";
          sha256 = "Kr1NLejrVbiHcd4HE0n0nfTgbiZ0ShOsmYBEePTUxXc=";
        };
      in pkgs.stdenv.mkDerivation {
        name = "ReplicationPackage";
        src = ./.;
        buildInputs = with pkgs; [ xz zip wget ];
        phases = [ "unpackPhase" "buildPhase" "installPhase" ];
        buildPhase = ''
          cp ${swhJar} swh-graph-1.0.1.jar
          make replication_package.zip
        '';
        installPhase = ''
          mkdir -p $out
          mv replication_package.zip $out/replication_package.zip
        '';
      };

      packages.default = packages.documents;

      devShells.default = pkgs.mkShell {
        inherit FONTCONFIG_FILE;
        buildInputs = (
          [
            texDistribution
            pkgs.evince

            # for my code editor setup (neovim)
            pkgs.python310Packages.pynvim
            pkgs.python310Packages.black
          ]
          ++ texDependencies
          ++ experimentDependencies
        );
      };
    }
  ));
}
