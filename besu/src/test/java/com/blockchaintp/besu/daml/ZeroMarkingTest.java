/*
 * Copyright © 2023 Paravela Limited
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
 */
package com.blockchaintp.besu.daml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZeroMarkingTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(ZeroMarkingTest.class);

  private static final String SXT419_RLP_INPUT = "0xb87108051002180122691f8b0800000000000000d30ae2723230484b354d4d4d4c4c353037494b4b4931484d35334b4c344c4d4c4d32b24c343737344e34b248314c324d4d4b33374b364e4a4d34b44c4e3235334d4949b1b03010e2e1b87c7b00000000000000000000000000720f9bc082e3d74e330200dead467b54000000";
  private static final String SXT419_PROBLEM_SLOT0 = "0xb87108051002180122691f8b0800000000000000d30ae2723230484b354d4d4d";
  private static final String SXT419_PROBLEM_SLOT1 = "0x4c4c353037494b4b4931484d35334b4c344c4d4c4d32b24c343737344e34b248";
  private static final String SXT419_PROBLEM_SLOT2 = "0x314c324d4d4b33374b364e4a4d34b44c4e3235334d4949b1b03010e2e1b87c7b";
  private static final String SXT419_PROBLEM_SLOT3 = "0x00000000000000000000000000720f9bc082e3d74e330200dead467b54000000";
  private static final String SXT849_PROBLEM_SLOT0 = "0xdead4b920253dd62cb58ed84c05e4ff223b594da7f094c732787a06679f461dd";

  @Test
  public void testRoundTripZeroMarking() {
    var dataValue = UInt256.fromHexString(SXT419_PROBLEM_SLOT3);
    var unmarked = ZeroMarking.unmarkZeros(dataValue);
    LOGGER.error("{} becomes {}", SXT419_PROBLEM_SLOT3, unmarked.toHexString());
    assertNotEquals(SXT419_PROBLEM_SLOT3, unmarked.toHexString());
    assertEquals(dataValue.toMinimalBytes(), unmarked);
  }

  @Test
  public void testSXT849ShouldNotThrow() {
    var dataValue = UInt256.fromHexString(SXT849_PROBLEM_SLOT0);
    var testBytes = ZeroMarking.unmarkZeros(dataValue);
    assertEquals(dataValue.toMinimalBytes(), testBytes);
  }

  @Test
  public void testDataWithZeros() {
    var zeroLeading = Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000");
    var value = ZeroMarking.maybeMakeDeadBeef(zeroLeading);
    LOGGER.error("{} becomes {}", zeroLeading.toHexString(), value.toHexString());
    assertNotEquals(zeroLeading.toHexString(), value.toHexString());
  }
}
