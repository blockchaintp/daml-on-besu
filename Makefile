export ISOLATION_ID ?= local
PWD = $(shell pwd)

ORGANIZATION ?= $(shell git remote show -n origin | grep Fetch | \
	awk '{print $$NF}' | sed -e 's/git@github.com://' | \
	sed -e 's@https://github.com/@@' | awk -F'[/.]' '{print $$1}' )
REPO ?= $(shell git remote show -n origin | grep Fetch | awk '{print $$NF}' | \
	sed -e 's/git@github.com://' | sed -e 's@https://github.com/@@' | \
	awk -F'[/.]' '{print $$2}' )
BRANCH_NAME ?= $(shell git symbolic-ref -q HEAD |sed -e 's@refs/heads/@@')
SAFE_BRANCH_NAME ?= $(shell if [ -n "$$BRANCH_NAME" ]; then echo $$BRANCH_NAME;\
	else git symbolic-ref -q HEAD|sed -e 's@refs/heads/@@'|sed -e 's@/@_@g'; fi)
PR_KEY=$(shell echo $(BRANCH_NAME) | cut -c4-)
VERSION ?= $(shell git describe | cut -c2-  )
LONG_VERSION ?= $(shell git describe --long --dirty |cut -c2- )
UID := $(shell id -u)
GID := $(shell id -g)

MAVEN_SETTINGS ?= $(HOME)/.m2/settings.xml
MAVEN_REVISION != if [ "$(LONG_VERSION)" = "$(VERSION)" ] || \
	(echo "$(LONG_VERSION)" | grep -q dirty); then \
		echo `bin/semver bump patch $(VERSION)`-SNAPSHOT; \
	else \
		echo $(VERSION); \
	fi

MAVEN_REPO_BASE ?= https://dev.catenasys.com/repository/catenasys-maven
MAVEN_REPO_TARGET != if [ "$(LONG_VERSION)" = "$(VERSION)" ] || \
	(echo "$(LONG_VERSION)" | grep -q dirty); then \
		echo snapshots; \
	else \
		echo releases; \
	fi
MAVEN_UPDATE_RELEASE_INFO != if [ "$(LONG_VERSION)" = "$(VERSION)" ] || \
	(echo "$(LONG_VERSION)" | grep -q dirty); then \
		echo false; \
	else \
		echo true; \
	fi
MAVEN_DEPLOY_TARGET = $(MAVEN_REPO_TARGET)::default::$(MAVEN_REPO_BASE)-$(MAVEN_REPO_TARGET)

TOOLCHAIN := docker run --rm -v $(HOME)/.m2/repository:/root/.m2/repository \
		-v $(MAVEN_SETTINGS):/root/.m2/settings.xml -v `pwd`:/project/daml-on-besu \
		toolchain:$(ISOLATION_ID)
DEPLOY_MVN := $(TOOLCHAIN) mvn -Drevision=$(MAVEN_REVISION)
DOCKER_MVN := $(TOOLCHAIN) mvn -Drevision=$(MAVEN_REVISION) -B

SONAR_HOST_URL ?= https://sonarqube.dev.catenasys.com
SONAR_AUTH_TOKEN ?=
PMD_IMAGE ?= blockchaintp/pmd:latest

export TEST_SPEC ?= --exclude ConfigManagementServiceIT:CMSetAndGetTimeModel

.PHONY: all
all: clean build test archive

.PHONY: dirs
dirs:
	mkdir -p build
	mkdir -p test-dars

.PHONY: clean_dirs
clean_dirs:
	rm -rf build test-dars

.PHONY: build
build: build_toolchain
	$(DOCKER_MVN) compile
	$(TOOLCHAIN) chown -R $(UID):$(GID) /root/.m2/repository
	$(TOOLCHAIN) find /project -type d -name target -exec chown \
		-R $(UID):$(GID) {} \;

.PHONY: fix_permissions
fix_permissions: build_toolchain
	$(TOOLCHAIN) chown -R $(UID):$(GID) /root/.m2/repository
	$(TOOLCHAIN) find /project -type d -name target -exec chown \
		-R $(UID):$(GID) {} \;

.PHONY: build_toolchain
build_toolchain: dirs
	docker-compose -f docker/docker-compose-build.yaml build --parallel
	mkdir -p test-dars && \
		docker run --rm -v `pwd`/test-dars:/out \
			ledger-api-testtool:$(ISOLATION_ID) bash \
			-c "java -jar ledger-api-test-tool.jar -x && cp *.dar /out"

.PHONY: package
package: build
	$(DOCKER_MVN) package verify
	$(TOOLCHAIN) chown -R $(UID):$(GID) /root/.m2/repository
	$(TOOLCHAIN) find /project -type d -name target -exec chown \
		-R $(UID):$(GID) {} \;
	docker-compose -f docker-compose.yaml build

.PHONY: test
test: test_public_ibft

.PHONY: test_public_ibft
test_public_ibft: package
	docker-compose -p $(ISOLATION_ID) -f docker/daml-test-public-ibft.yaml down \
		-v || true
	docker-compose -p $(ISOLATION_ID) -f docker/daml-test-public-ibft.yaml up \
		--exit-code-from ledger-api-testtool || true
	docker logs $(ISOLATION_ID)_ledger-api-testtool_1 > build/results.txt 2>&1
	./run_tests ./build/results.txt PUBLIC > build/daml-test-public-ibft.results
	docker-compose -p $(ISOLATION_ID) -f docker/daml-test-public-ibft.yaml down \
	 || true

.PHONY: clean_test_public_ibft
clean_test_public_ibft:
	docker-compose -p $(ISOLATION_ID) -f docker/daml-test-public-ibft.yaml \
		rm -f || true
	docker-compose -p $(ISOLATION_ID) -f docker/daml-test-public-ibft.yaml down \
		-v || true

.PHONY: analyze
analyze: analyze_sonar

.PHONY: analyze_sonar
analyze_sonar: package
	[ -z "$(SONAR_AUTH_TOKEN)" ] || \
		if [ -z "$(CHANGE_BRANCH)" ]; then \
			$(DOCKER_MVN) package sonar:sonar \
					-Dsonar.organization=$(ORGANIZATION) \
					-Dsonar.projectKey=$(ORGANIZATION)_$(REPO) \
					-Dsonar.projectName="$(ORGANIZATION)/$(REPO)" \
					-Dsonar.branch.name=$(BRANCH_NAME) \
					-Dsonar.projectVersion=$(VERSION) \
					-Dsonar.host.url=$(SONAR_HOST_URL) \
					-Dsonar.login=$(SONAR_AUTH_TOKEN) ; \
		else \
			$(DOCKER_MVN) package sonar:sonar \
					-Dsonar.organization=$(ORGANIZATION) \
					-Dsonar.projectKey=$(ORGANIZATION)_$(REPO) \
					-Dsonar.projectName="$(ORGANIZATION)/$(REPO)" \
					-Dsonar.pullrequest.key=$(PR_KEY) \
					-Dsonar.pullrequest.branch=$(CHANGE_BRANCH) \
					-Dsonar.pullrequest.base=$(CHANGE_TARGET) \
					-Dsonar.projectVersion=$(VERSION) \
					-Dsonar.host.url=$(SONAR_HOST_URL) \
					-Dsonar.login=$(SONAR_AUTH_TOKEN) ; \
		fi

.PHONY: clean
clean: clean_dirs clean_test_public_ibft
	$(DOCKER_MVN) clean || true
	docker-compose -f docker/docker-compose-build.yaml rm -f || true
	docker-compose -f docker/docker-compose-build.yaml down -v || true

.PHONY: archive
archive: dirs
	git archive HEAD --format=zip -9 --output=build/$(REPO)-$(VERSION).zip
	git archive HEAD --format=tgz -9 --output=build/$(REPO)-$(VERSION).tgz

.PHONY: publish
publish: build_toolchain
	$(DOCKER_MVN) -Drevision=0.0.0 versions:set -DnewVersion=$(MAVEN_REVISION)
	$(DOCKER_MVN) clean deploy -DupdateReleaseInfo=$(MAVEN_UPDATE_RELEASE_INFO) \
		-DaltDeploymentRepository=$(MAVEN_DEPLOY_TARGET)
