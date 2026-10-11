# GnuCOBOL harness for CardDemo batch programs (all targets run in Docker).
#   make build                    build the harness image
#   make run PROGRAM=CBACT04C     run one program -> out/<PROGRAM>/
#   make golden                   regenerate golden/ (runs twice, must match)
#   make verify                   re-run and diff against committed golden/
# PLATFORM=linux/amd64 builds/runs the image for another architecture.

IMAGE      ?= carddemo-gnucobol:local
PLATFORM   ?=
PROGRAM    ?= CBACT04C
CARDDEMO_REPO ?= https://github.com/V2-Digital/aisdlc-carddemo

LEGACY_DIR := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
REPO_ROOT  := $(abspath $(LEGACY_DIR)/..)
PLATFORM_FLAG := $(if $(PLATFORM),--platform $(PLATFORM))
DOCKER_RUN := docker run --rm $(PLATFORM_FLAG) -u $(shell id -u):$(shell id -g) \
              -v $(REPO_ROOT):/src -w /src/$(notdir $(LEGACY_DIR)) $(IMAGE)

.PHONY: build source run golden verify clean

build:
	docker build $(PLATFORM_FLAG) -t $(IMAGE) $(LEGACY_DIR)

# CardDemo sources: the enclosing repo when it is the CardDemo fork,
# otherwise a shallow clone in legacy/.carddemo (gitignored).
source:
	@if [ ! -d $(REPO_ROOT)/app/cbl ] && [ ! -d $(LEGACY_DIR)/.carddemo/app/cbl ]; then \
	  git clone --depth 1 $(CARDDEMO_REPO) $(LEGACY_DIR)/.carddemo; \
	fi

run: build source
	$(DOCKER_RUN) harness/run.sh $(PROGRAM)

golden: build source
	$(DOCKER_RUN) harness/golden.sh

verify: build source
	$(DOCKER_RUN) harness/golden.sh --check

clean:
	rm -rf $(LEGACY_DIR)/out
