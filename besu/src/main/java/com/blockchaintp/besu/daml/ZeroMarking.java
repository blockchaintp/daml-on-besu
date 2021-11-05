package com.blockchaintp.besu.daml;

import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ZeroMarking {
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
      final String remainder = testString.substring(testString.lastIndexOf("dead") + 4);
      final int numOfZeroBytes = Integer.parseInt(remainder);
      final byte[] dataB = new byte[numOfZeroBytes];
      return Bytes.of(dataB);
    } else {
      return data.toMinimalBytes();
    }
  }
}
