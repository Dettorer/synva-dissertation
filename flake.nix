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
      inherit (pkgs.texlive) scheme-small latex-bin latexmk;
    };
  in
  rec {
    packages.dissertation = pkgs.stdenv.mkDerivation {
      name = "PaulHervotSYNVADissertation";
      src = ./.;
      buildInputs = [ texDistribution ];
      phases = ["unpackPhase" "buildPhase"];
      buildPhase = ''
          latexmk -interaction=nonstopmode -outdir=$out
      '';
    };

    defaultPackage = packages.dissertation;

    devShell = pkgs.mkShell {
      buildInputs = [ texDistribution ];
    };
  }));
}
