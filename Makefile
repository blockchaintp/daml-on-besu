MAKEFILE_DIR := $(dir $(lastword $(MAKEFILE_LIST)))
include $(MAKEFILE_DIR)/standard_defs.mk

export TEST_SPEC ?= --exclude ConfigManagementServiceIT:CMSetAndGetTimeModel

clean: clean_mvn clean_dirs_daml clean_containers

distclean: clean_docker

build: $(MARKERS)/build_mvn $(MARKERS)/build_ledgertest

package: $(MARKERS)/package_mvn $(MARKERS)/package_docker

test: $(MARKERS)/test-dars $(MARKERS)/test_mvn $(MARKERS)/test_public_ibft

analyze: analyze_sonar_mvn

publish: $(MARKERS)/publish_mvn

$(MARKERS)/test-dars:
	mkdir -p test-dars
	touch $@

.PHONY: clean_dirs_daml
clean_dirs_daml:
	rm -rf test-dars
	rm -f $(MARKERS)/test-dars

$(MARKERS)/build_ledgertest:
	docker build -f docker/ledger-api-testtool.docker -t \
		ledger-api-testtool:$(ISOLATION_ID) . ;
	docker run --rm -v `pwd`/test-dars:/out \
		ledger-api-testtool:$(ISOLATION_ID) bash \
		-c "java -jar ledger-api-test-tool.jar -x && cp *.dar /out"
	touch $@

$(MARKERS)/package_docker:
	docker-compose -f docker-compose.yaml build
	touch $@

$(MARKERS)/test_public_ibft: package
	docker-compose -p $(ISOLATION_ID) -f docker/daml-test-public-ibft.yaml down \
		-v || true
	docker-compose -p $(ISOLATION_ID) -f docker/daml-test-public-ibft.yaml up \
		--exit-code-from ledger-api-testtool || true
	docker-compose -p $(ISOLATION_ID) logs ledger-api-testtool > build/results.txt 2>&1
	./run_tests ./build/results.txt PUBLIC > build/daml-test-public-ibft.results
	docker-compose -p $(ISOLATION_ID) -f docker/daml-test-public-ibft.yaml down \
		|| true
	touch $@

.PHONY: clean_containers
clean_containers:
	docker-compose -p $(ISOLATION_ID) -f docker/daml-test-public-ibft.yaml \
		rm -f || true
	docker-compose -p $(ISOLATION_ID) -f docker/daml-test-public-ibft.yaml down \
		-v || true
	docker-compose -f docker/docker-compose-build.yaml rm -f || true
	docker-compose -f docker/docker-compose-build.yaml down -v || true

.PHONY: clean_docker
clean_docker:
	docker-compose -p $(ISOLATION_ID) -f docker/daml-test-public-ibft.yaml \
		rm -f || true
	docker-compose -p $(ISOLATION_ID) -f docker/daml-test-public-ibft.yaml down \
		-v --rmi all || true
	docker-compose -f docker/docker-compose-build.yaml rm -f || true
	docker-compose -f docker/docker-compose-build.yaml down -v --rmi all || true
