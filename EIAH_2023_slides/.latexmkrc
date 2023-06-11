# vim: ft=perl

# By default, build a PDF file (using XeLaTeX) in the `build` folder from `main.tex`
$pdf_mode = 5;
$out_dir = 'build';
@default_files = ('./slides.tex');
$pdf_previewer = 'start evince';

# Allow latex to run shell commands
set_tex_cmds( '--shell-escape %O %S' );

# vendored APA biblatex style
ensure_path( 'TEXINPUTS', './biblatex-lncs//' );
