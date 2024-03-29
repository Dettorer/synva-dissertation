# vim: ft=perl
#
# By default, build a PDF file (using XeLaTeX) in the `build` folder from `main.tex`
$pdf_mode = 5;
$out_dir = 'build';
@default_files = ('./main.tex');
$pdf_previewer = 'start evince';

# Allow latex to run shell commands
set_tex_cmds( '--shell-escape %O %S' );

# template (custom .cls and .sty)
ensure_path( 'TEXINPUTS', './template/kaobook-0.9.7//' );
# vendored APA biblatex style
ensure_path( 'TEXINPUTS', '../biblatex-apa//' );
# most tex files are in the src folder
ensure_path( 'TEXINPUTS', './src//' );

# Glossaries generation
add_cus_dep('glo', 'gls', 0, 'run_makeglossaries');
add_cus_dep('acn', 'acr', 0, 'run_makeglossaries');
sub run_makeglossaries {
    my ($base_name, $path) = fileparse( $_[0] ); #handle -outdir param by splitting path and file, ...
    pushd $path; # ... cd-ing into folder first, then running makeglossaries ...

    if ( $silent ) {
        system "makeglossaries -q '$base_name'"; #unix
        # system "makeglossaries", "-q", "$base_name"; #windows
    }
    else {
        system "makeglossaries '$base_name'"; #unix
        # system "makeglossaries", "$base_name"; #windows
    };

    popd; # ... and cd-ing back again
}
push @generated_exts, 'glo', 'gls', 'glg';
push @generated_exts, 'acn', 'acr', 'alg';
$clean_ext .= ' %R.ist %R.xdy';
