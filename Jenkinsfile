#!groovy

// Copyright 2020 Blockchain Technology Partners
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ------------------------------------------------------------------------------

pipeline {
  agent {
    node { label 'worker' }
  }

  triggers{cron('H H * * *')}

  options{
    ansiColor('xterm')
    timestamps()
    buildDiscarder(logRotator(daysToKeepStr: '31'))
    disableConcurrentBuilds()
  }

  environment{
    ISOLATION_ID = sh(returnStdout: true, script: 'echo $BUILD_TAG | sha256sum | cut -c1-32').trim()
    PROJECT_ID = sh(returnStdout: true, script: 'echo $BUILD_TAG | sha256sum | cut -c1-32').trim()
  }

  stages {
    stage('Fetch Tags') {
      steps {
        checkout([$class:'GitSCM',branches:[[name:"${GIT_BRANCH}"]],
                  doGenerateSubmoduleConfigurations:false,
                  extensions:[],
                  submoduleCfg:[],
                  userRemoteConfigs:
                    [[credentialsId:'github-credentials',
                      noTags:false, url:"${GIT_URL}"]],
                      extensions:[[$class:'CloneOption',
                                    shallow:false, noTags:false,
                                    timeout:60]]])
      }
    }

    stage('Toolchain Build') {
      steps {
        sh 'docker-compose -f docker/docker-compose-build.yaml build --parallel'
        sh 'mkdir -p test-dars && docker run --rm -v `pwd`/test-dars:/out ledger-api-testtool:${ISOLATION_ID} bash -c "java -jar ledger-api-test-tool.jar -x && cp *.dar /out"'
      }
    }

    stage('Build') {
      steps {
        configFileProvider([configFile(fileId: 'global-maven-settings', variable: 'MAVEN_SETTINGS')]) {
          sh 'docker run --rm -v $HOME/.m2/repository:/root/.m2/repository -v $MAVEN_SETTINGS:/root/.m2/settings.xml -v `pwd`:/project/daml-on-besu toolchain:${ISOLATION_ID} mvn -B clean compile'
          sh 'docker run --rm -v $HOME/.m2/repository:/root/.m2/repository -v $MAVEN_SETTINGS:/root/.m2/settings.xml toolchain:${ISOLATION_ID} chown -R $UID:$GROUPS /root/.m2/repository'
          sh 'docker run --rm -v `pwd`:/project/daml-on-besu toolchain:${ISOLATION_ID} find /project -type d -name target -exec chown -R $UID:$GROUPS {} \\;'
          sh 'mkdir -p test-dars && docker run --rm -v `pwd`/test-dars:/out ledger-api-testtool:${ISOLATION_ID} bash -c "java -jar ledger-api-test-tool.jar -x && cp *.dar /out"'
        }
      }
    }

    stage('Package') {
      steps {
        configFileProvider([configFile(fileId: 'global-maven-settings', variable: 'MAVEN_SETTINGS')]) {
          sh 'docker run --rm -v $HOME/.m2/repository:/root/.m2/repository -v $MAVEN_SETTINGS:/root/.m2/settings.xml -v `pwd`:/project/daml-on-besu toolchain:${ISOLATION_ID} mvn -B package verify'
          sh 'docker run --rm -v $HOME/.m2/repository:/root/.m2/repository -v $MAVEN_SETTINGS:/root/.m2/settings.xml toolchain:${ISOLATION_ID} chown -R $UID:$GROUPS /root/.m2/repository'
          sh 'docker run --rm -v `pwd`:/project/daml-on-besu toolchain:${ISOLATION_ID} find /project -type d -name target -exec chown -R $UID:$GROUPS {} \\;'
        }
        sh 'docker-compose -f docker-compose.yaml build'
      }
    }

    stage('Public IBFT Integration Tests') {
      steps {
        script {
          sh 'docker-compose -p ${PROJECT_ID} -f docker/daml-test-public-ibft.yaml down -v || true'
          sh '''
            export TEST_SPEC="--exclude ConfigManagementServiceIT:CMSetAndGetTimeModel"
            docker-compose -p ${PROJECT_ID} -f docker/daml-test-public-ibft.yaml up --exit-code-from ledger-api-testtool || true
          '''
          sh '''
            docker logs ${PROJECT_ID}_ledger-api-testtool_1 > results.txt 2>&1
            ./run_tests ./results.txt PUBLIC > daml-test-public-ibft.results
          '''
          sh 'docker-compose -p ${PROJECT_ID} -f docker/daml-test-public-ibft.yaml down -v || true'
          step([$class: "TapPublisher", testResults: "daml-test-public-ibft.results"])
        }
      }
    }
  }

  post {
    always{
      sh 'docker-compose -p ${PROJECT_ID} -f docker/daml-test-public-ibft.yaml down -v || true'
    }
    aborted {
      error "Aborted, exiting now"
    }
    failure {
      error "Failed, exiting now"
    }
  }
}

def runCommand(script) {
    echo "[runCommand:script] ${script}"

    def stdoutFile = "rc.${BUILD_NUMBER}.out"
    def stderrFile = "rc.${BUILD_NUMBER}.err"
    script = script + " > " + stdoutFile +" 2> " + stderrFile

    def res = [:]
    res["exitCode"] = sh(returnStatus: true, script: script)
    res["stdout"] = sh(returnStdout: true, script: "cat " + stdoutFile)
    res["stderr"] = sh(retStdout: true, script: "cat "+stderrFile)
    sh(returnStatus: true, script: "rm -f " + stdoutFile)
    sh(returnStatus: true, script: "rm -f " + stderrFile)

    echo "[runCommand:response] ${res["exitCode"]}"
    return res
}
