DOCUMENTS=$(addprefix documents/,masters_dissertation.pdf masters_defense.pdf EIAH_2023_article.pdf)

all: ${DOCUMENTS}

# Master's dissertation
documents/masters_dissertation.pdf:
	mkdir -p documents
	cd master_dissertation && latexmk
	cp master_dissertation/build/main.pdf $@

# Master's defense slides
documents/masters_defense.pdf:
	mkdir -p documents
	cd defense_slides && latexmk
	cp defense_slides/build/slides.pdf $@

# EIAH article
documents/EIAH_2023_article.pdf:
	mkdir -p documents
	cd EIAH_2023_paper && latexmk
	cp EIAH_2023_paper/build/paper.pdf $@

# Make everything phony to force the latexmk invocation, which will handle the
# real dependencies
.PHONY: ${DOCUMENTS}
