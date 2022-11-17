# vim: ft=perl

# By default, build a PDF file (using XeLaTeX) in the `build` folder from `paper.tex`
$pdf_mode = 5;
$out_dir = 'build';
@default_files = ('./paper.tex');
$pdf_previewer = 'start evince';

# Allow latex to run shell commands
set_tex_cmds( '--shell-escape %O %S' );
