# By default, build a PDF file (using XeLaTeX) in the `build` folder from `main.tex`
$pdf_mode = 5;
$out_dir = 'build';
@default_files = ('./main.tex');
$pdf_previewer = 'start evince';

# template (custom .cls and .sty)
ensure_path( 'TEXINPUTS', './template/kaobook-0.9.7//' );
