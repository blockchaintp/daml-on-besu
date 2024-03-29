# CHANGELOG

## Unreleased

* build(deps): update jackson companion dependencies [view commit](https://github.com/blockchaintp/daml-on-besu/commit/22fd2ab8e0ccd68baa88c745137549f31e88edd7)
* build(deps): Bump jackson-databind from 2.10.5.1 to 2.12.7.1 [view commit](https://github.com/blockchaintp/daml-on-besu/commit/b007cc16358b07415d8c3b18c29bda3cb5a652de)
* docs(docker): Update copyright headers in changed files [view commit](https://github.com/blockchaintp/daml-on-besu/commit/d5556006798a848fb96eed5ca8017770b10b1c7d)
* style(docker): minor changes to tidy up formatting for consistency [view commit](https://github.com/blockchaintp/daml-on-besu/commit/c53ed5aecdd6b1cd33b5b19937830402b224f76f)
* build(docker): use Azul Zulu Docker image instead of zulu-11 package for OpenJDK [view commit](https://github.com/blockchaintp/daml-on-besu/commit/b28a68f015c6be09b519c4c7c8fe75ef99ec09d9)

## v1.13.8

* style(besu): replace cachSz with cacheSize [view commit](https://github.com/blockchaintp/daml-on-besu/commit/9e268e3df189bb2e638f8c741b18bbf68a3c74af)
* fix(besu): fix modifier orders and typos [view commit](https://github.com/blockchaintp/daml-on-besu/commit/3e694f38409664a98fe88940fcbc89696e07b69f)
* docs(readthedocs): pin mkdocs version [view commit](https://github.com/blockchaintp/daml-on-besu/commit/47182c92d24705c02a543740adff3be731358f6e)
* test(docker): turn off slf4jjson reporting [view commit](https://github.com/blockchaintp/daml-on-besu/commit/950ba8ee9727fc44b354579d93a164693606d190)
* feat(besu): configure caches via env var [view commit](https://github.com/blockchaintp/daml-on-besu/commit/93082f68fbfbf2b73e3f9401be6bdbb901cd7a5a)
* refactor(besu): move caches to singletons [view commit](https://github.com/blockchaintp/daml-on-besu/commit/bc3ab4f26dbe9fed90d58e06ff021c4c33918630)

## v1.13.7

* fix(besu): add direct caching of packages and parties [view commit](https://github.com/blockchaintp/daml-on-besu/commit/607daad9433052b03260fe9d8f75a991309072b0)

## v1.13.6

* fix: Log message formatting [view commit](https://github.com/blockchaintp/daml-on-besu/commit/21680d7bb3a0fde4df6ae984bf487e34fe3c60b4)
* feat: Port in a json log formatter for metrics [view commit](https://github.com/blockchaintp/daml-on-besu/commit/5bb9a5859d2a17b1a03babd9a352c6ccc3009c56)
* feat: Sl4j reporter, still plaintext [view commit](https://github.com/blockchaintp/daml-on-besu/commit/3c95dbe236d63ebf35aeaf36eb1469b6f5a30592)
* fix: Sonar issues [view commit](https://github.com/blockchaintp/daml-on-besu/commit/ce58e8ca2ab2fabb2e870f08a90ea31ef26314f0)
* fix: Add reporting to test docker images [view commit](https://github.com/blockchaintp/daml-on-besu/commit/556fc228ace14eba681f6dd45fe45bf47d596df1)
* feat: Env var based metrics config [view commit](https://github.com/blockchaintp/daml-on-besu/commit/01c8df4b821bc85649b56d91b1c6acd82485dcf2)
* feat: Add metrics cli extracted from sandbox-common [view commit](https://github.com/blockchaintp/daml-on-besu/commit/336f97b5d9e0a6bb2478f06a2bc87e071934a9c5)

## v1.13.5

* fix(besu): eliminate some false positive conditions in 0xdead detection [view commit](https://github.com/blockchaintp/daml-on-besu/commit/93d2018f397e3ee521452f02dcac04958122ba61)

## v1.13.4

* fix(besu): provide better logging of the situation for NFE problem [view commit](https://github.com/blockchaintp/daml-on-besu/commit/0c089eeb9b5cacb555b8798e97e3cc6191b14a14)
* build(deps): Bump log4j-core from 2.17.0 to 2.17.1 [view commit](https://github.com/blockchaintp/daml-on-besu/commit/73ad5efc72051825f9c7d5518e61ba6ea9659e19)
* build(deps): Bump log4j-api from 2.17.0 to 2.17.1 [view commit](https://github.com/blockchaintp/daml-on-besu/commit/abb964539eed2ad51307cb2a72871690ca5bec75)

## v1.13.3

* build(deps): Bump log4j-core from 2.16.0 to 2.17.0 [view commit](https://github.com/blockchaintp/daml-on-besu/commit/8d9937bea12951d7ab2737a99bfc3de560be8d1c)
* build(deps): Bump log4j-api from 2.16.0 to 2.17.0 [view commit](https://github.com/blockchaintp/daml-on-besu/commit/745a8fc1f472e3352cbfa30ba9ad21415b361c1e)
* build(deps): Bump log4j-core from 2.13.2 to 2.16.0 [view commit](https://github.com/blockchaintp/daml-on-besu/commit/a5f8f3d21e41e38521b16cd068101489c3e57626)
* build(deps): Bump log4j-api from 2.13.0 to 2.16.0 [view commit](https://github.com/blockchaintp/daml-on-besu/commit/f4ef105cc0eb721fcd42acb940201e48d7c365c3)

## v1.13.2

* style(besu): spotless apply [view commit](https://github.com/blockchaintp/daml-on-besu/commit/e1598e839cd097bccbe6a391554e1da6229893a3)
* fix(besu):  resolve issues with zero marking [view commit](https://github.com/blockchaintp/daml-on-besu/commit/b1bc83703dea87033d92d6724a26f9e976febfaa)

## v1.13.1

* test: remove concurrency restriction [view commit](https://github.com/blockchaintp/daml-on-besu/commit/a6db0a16830b81ef6de66d75f43308e25dfd969d)

## v1.13.0

* fix: Restore direct dependency on sl4j [view commit](https://github.com/blockchaintp/daml-on-besu/commit/cca51a9bfe055be0b3563fe530aba6c3a2d230b7)
* fix: Stage CD test runs so this passes more often [view commit](https://github.com/blockchaintp/daml-on-besu/commit/8fa22ac1b326117f33a28291e691c11570004f02)
* fix: Make failures verbose, attempt the failing test alone [view commit](https://github.com/blockchaintp/daml-on-besu/commit/384fd49996f3a88366b9cab16d4261acef8238d3)
* fix: up the timeout [view commit](https://github.com/blockchaintp/daml-on-besu/commit/889c3ec824f186290c600ec736df11f705920720)
* fix: Assert, try a multithreaded executor [view commit](https://github.com/blockchaintp/daml-on-besu/commit/566197278925d89aa84b0f4d1c7262885e25a685)
* fix: Sonar [view commit](https://github.com/blockchaintp/daml-on-besu/commit/3fb375e211570624b047b709aa2db861aad9f19b)
* fix: Update the test tool docker to 1.31.1 [view commit](https://github.com/blockchaintp/daml-on-besu/commit/52d34a7038a2237ef94335fc290c2c8551c22681)
* fix: Rollback encapsulation in Filter [view commit](https://github.com/blockchaintp/daml-on-besu/commit/d1a07844e9c3d2058b56d428d96b9c2a382d2402)
* fix: compare bytestrings in test [view commit](https://github.com/blockchaintp/daml-on-besu/commit/c151a945133e82e57e9ce4465a0b2e574b954eea)
* fix: Checkstyle issues, formatting etc [view commit](https://github.com/blockchaintp/daml-on-besu/commit/7ea26cf8a91a1ef99622a03498d7f8c415ae5fbe)
* feat: Upgrade [view commit](https://github.com/blockchaintp/daml-on-besu/commit/8e9fe65aa19abd4de5eceeb95af21d78562ee00d)

## v1.4.8

* build(deps): Bump log4j-core from 2.13.0 to 2.13.2 [view commit](https://github.com/blockchaintp/daml-on-besu/commit/3185806d8ac0dec5f3473e1585c0e7326194d236)
* build(deps): Bump jackson-databind from 2.10.3 to 2.10.5.1 [view commit](https://github.com/blockchaintp/daml-on-besu/commit/9cd253c7ed814502473db07b49a3c8f61ef58e17)
* build(deps): Bump junit from 4.12 to 4.13.1 [view commit](https://github.com/blockchaintp/daml-on-besu/commit/faebaec29e3035b8c36c3345b13af5c76023f2de)
* docs: add readthedocs config [view commit](https://github.com/blockchaintp/daml-on-besu/commit/9cc8edf7e768e990f2e4941a0d6c139d0edb3dd9)
* docs: add top level index.md [view commit](https://github.com/blockchaintp/daml-on-besu/commit/21bd4d36d845748e0cccae74bfb36bec48166bfa)
* docs: add basic deployment diagram [view commit](https://github.com/blockchaintp/daml-on-besu/commit/e836af130915f4e8204d04a14322e52531c91ece)

## v1.4.7


## stopBuild/v1.4.5

* Log derserialisation can throw [view commit](https://github.com/blockchaintp/daml-on-besu/commit/dcfc6819b4835ed41acea850c751cb7d0495ca5e)
* fix: adjust visibility of methods on Web3Utils [view commit](https://github.com/blockchaintp/daml-on-besu/commit/2ddfc1578a5858e1d56b50b3b94168d718685ea4)
* refactor: clean up sonar detected code smell [view commit](https://github.com/blockchaintp/daml-on-besu/commit/c852049db5fddaadd7e467059e34f8e21563ee88)
* fix: remove generic exceptions in favor of project specific ones [view commit](https://github.com/blockchaintp/daml-on-besu/commit/8c286971f11fd07752d9a9c394fe69a7807761bc)
* fix(besu): stop using the global EC and use a dedicated one [view commit](https://github.com/blockchaintp/daml-on-besu/commit/b0e87f7be1298f39fdba6dd2f720909fc3f5e127)
* fix: fix NPE issues [view commit](https://github.com/blockchaintp/daml-on-besu/commit/e0b239c400d9e8130fb3c9833a6e6d7a03cb9e34)
* refactor: properly handle InterruptedException [view commit](https://github.com/blockchaintp/daml-on-besu/commit/7fa18d63526ec6ef0fff146446840162b41a4267)
* refactor: correct sonar issues [view commit](https://github.com/blockchaintp/daml-on-besu/commit/e15efd2a8f6c03a9e76e624ed813f47e01377fb5)
* fix: enhance error handling on getNonce and more [view commit](https://github.com/blockchaintp/daml-on-besu/commit/71f5455cc7a4cff070bcb752cba19c8df8609a60)
* build: update gitignore [view commit](https://github.com/blockchaintp/daml-on-besu/commit/01e5fe5d7397a518fefb525be2bf93717698cc26)
* build: update pre-commit-config [view commit](https://github.com/blockchaintp/daml-on-besu/commit/8396fbc395362b27d35dd418379c8aba428d7f95)

