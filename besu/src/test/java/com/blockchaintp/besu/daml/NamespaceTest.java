/*
 * Copyright 2020 Blockchain Technology Partners.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.blockchaintp.besu.daml;

import static org.assertj.core.api.Assertions.assertThat;

import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class NamespaceTest {
  @Test
  public void damlPublicAddressCheck() {
    assertThat(Namespace.getDamlPublicAddress()).isEqualTo("75");
  }

  @Test
  public void damlStateKeyAddressPrefixCheck() {
    assertThat(Namespace.DamlKeyType.STATE.rootAddress()).isEqualTo("7500");
  }

  @Test
  public void damlLogEntryIdAddressPrefixCheck() {
    assertThat(Namespace.DamlKeyType.LOG.rootAddress()).isEqualTo("7501");
  }

  @Test
  public void damlHashCheck() {
    assertThat(Namespace.getHash(new byte[] {0})).isEqualTo(
        Bytes.fromHexString("0x6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d"));
  }

  @Test
  public void emptyDamlStateKeyAddressCheck() {
    DamlStateKey key = DamlStateKey.newBuilder().build();
    assertThat(Namespace.makeDamlStateKeyAddress(key.toByteString()).toHexString())
        .isEqualTo("0x7500c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    assertThat(Namespace.makeAddress(Namespace.DamlKeyType.STATE, key.toByteString()))
        .isEqualTo(Namespace.makeDamlStateKeyAddress(key.toByteString()));
  }
}
