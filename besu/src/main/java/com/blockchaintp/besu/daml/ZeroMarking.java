/*
 * Copyright 2021 Blockchain Technology Partners
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

import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public final class ZeroMarking {
  private static final int MAX_DEAD_BEEF_SIZE = 59;
  private static final String DEAD_HEAD = "0xDEAD";

  // This is strange that it is different from the original STR_MARKER_DEAD
  private ZeroMarking() {
  }

  private static UInt256 makeDeadBeef(final Integer numberOfZeros) {
    final String deadBeef = DEAD_HEAD + numberOfZeros.toString();
    return UInt256.fromHexString(deadBeef);
  }

  protected static UInt256 maybeMakeDeadBeef(final Bytes data) {
    UInt256 part = UInt256.fromBytes(data);
    if (data.size() > 0 && part.isZero()) {
      return makeDeadBeef(data.size());
    } else {
      return part;
    }
  }

  private static List<UInt256> leadingZeroDeadBeef(final Bytes data) {
    Bytes remainder = data;
    var retList = new ArrayList<UInt256>();
    if (data.hasLeadingZeroByte()) {
      var leadingZeros = remainder.numberOfLeadingZeroBytes();
      UInt256 value = ZeroMarking.makeDeadBeef(leadingZeros);
      retList.add(value);
      remainder = data.slice(leadingZeros);
    }

    if (remainder.size() > 0) {
      retList.add(ZeroMarking.maybeMakeDeadBeef(remainder));
    }
    return retList;
  }

  public static List<UInt256> dataToSlotVals(final Bytes sourceData, final int slotSize) {
    var retList = new ArrayList<UInt256>();

    int offset = 0;
    while (offset < sourceData.size()) {
      final int length = Math.min(slotSize, sourceData.size() - offset);
      Bytes data = sourceData.slice(offset, length);
      if (offset == 0) {
        UInt256 slotValue = maybeMakeDeadBeef(data);
        retList.add(slotValue);
      } else {
        retList.addAll(leadingZeroDeadBeef(data));
      }
      offset += slotSize;
    }
    // tombstone
    retList.add(UInt256.ZERO);
    return retList;
  }

  public static Bytes unmarkZeros(final UInt256 data) {
    final String testString = data.toMinimalBytes().toHexString();

    if (testString.startsWith("0xdead") || testString.startsWith("0x0dead")) {
      final String remainder = testString.substring(testString.indexOf("dead") + 8);
      try {
        // A dead beef string integer will never be this long
        if (remainder.length() > MAX_DEAD_BEEF_SIZE) {
          return data.toMinimalBytes();
        }
        // no character in a deadbeef string is a character
        // this will be a short loop if it is a deadbeef string
        // if it is not a dead beef chances are an a-f char will occur quickly
        for (int i = 0; i < remainder.length(); i++) {
          if (!Character.isDigit(remainder.charAt(i))) {
            return data.toMinimalBytes();
          }
        }
        final int numOfZeroBytes = Integer.parseInt(remainder);
        final byte[] dataB = new byte[numOfZeroBytes];
        return Bytes.of(dataB);
      } catch (final NumberFormatException e) {
        // whatever this is, it is not an integer string so it must not be a dead beef
        // string
        return data.toMinimalBytes();
      }
    } else {
      return data.toMinimalBytes();
    }
  }
}
