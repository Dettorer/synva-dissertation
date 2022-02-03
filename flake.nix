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
      texDistribution = pkgs.texlive.combine {
        inherit (pkgs.texlive)
          # base scheme
          scheme-small

          # template packages
          xifthen ifmtarg options xpatch xstring imakeidx hyphenat needspace
          placeins marginnote marginfix sidenotes changepage chngcntr footmisc
          footnotebackref tikzpagenodes ifoddpage multirow floatrow etoc
          morewrites enumitem mdframed zref subfiles todonotes algorithm2e relsize
          ccicons glossaries mfirstuc xfor datatool nomencl abstract csquotes
          blindtext thmtools bigfoot biblatex cleveref biblatex-apa

          # utilities
          latex-bin latexmk biber;
      };
      FONTCONFIG_FILE = pkgs.makeFontsConf {
        fontDirectories = with pkgs; [ libertinus liberation_ttf ];
      };
    in
    rec {
      packages.dissertation = pkgs.stdenv.mkDerivation {
        name = "PaulHervotSYNVADissertation";
        src = ./.;
        buildInputs = [ pkgs.coreutils texDistribution pkgs.glibcLocales ];
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
        buildInputs = [ texDistribution pkgs.glibcLocales pkgs.biber pkgs.evince ];
      };
    }
  ));
}
