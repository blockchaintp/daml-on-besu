#!/bin/bash
# Copyright 2020 Blockchain Techology Partners
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# monkey patch: use custom versions of org.hyperledger.besu classes in BTP's besu jar file,
# in place of the standard versions in the /opt/besu/lib/* jar files
daml_on_besu_jar=$(ls -t /opt/daml-on-besu/besu/besu-*.jar | head -n 1) && [ -z "$daml_on_besu_jar" ] && exit 1
daml_on_besu_lib=/opt/daml-on-besu/besu/lib/*
besu_lib=/opt/besu/lib/*
classpath=$daml_on_besu_jar:$daml_on_besu_lib:$besu_lib

java ${JAVA_ARGS} -classpath $classpath org.hyperledger.besu.Besu $@
