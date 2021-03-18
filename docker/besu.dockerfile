# Copyright 2020 Blockchain Technology Partners
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
# ------------------------------------------------------------------------------
FROM hyperledger/besu:1.4.0 as install

# ------------------------------------------------------------------------------
FROM ubuntu:bionic as ubuntu-zulu-base

RUN \
  apt-get update -y && \
  apt-get install -y \
  curl \
  gnupg \
  software-properties-common \
  wget

RUN apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 0xB1998361219BD9C9 && \
  apt-add-repository 'deb http://repos.azulsystems.com/ubuntu stable main' && \
  apt-get update -y && \
  apt-get install -y \
  zulu-11

# ------------------------------------------------------------------------------
FROM ubuntu:bionic as build

RUN \
  apt-get update -y && \
  apt-get install -y \
  zip

WORKDIR /opt/daml-on-besu
COPY besu/target /project
RUN unzip -qq /project/*-bin.zip && mv besu* besu && rm -rf /project/*

# -----
FROM ubuntu-zulu-base

COPY --from=install /opt/besu /opt/besu
COPY --from=build /opt/daml-on-besu /opt/daml-on-besu

WORKDIR /opt/daml-on-besu
RUN chmod 755 besu/entrypoint.sh
ENTRYPOINT /opt/daml-on-besu/besu/entrypoint.sh $@
