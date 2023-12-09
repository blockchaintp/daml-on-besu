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
package com.blockchaintp.besu.daml.rpc;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.daml.ledger.participant.state.v1.Offset;
import com.google.protobuf.ByteString;

/**
 *
 */
public final class Utils {
  private static final int OFFSET_BYTES = 16;
  private static final int MID_BYTES_START = 8;
  private static final int MID_BYTES_END = 12;
  private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

  private Utils() {
  }

  /**
   * @param bytes
   * @return Hex encoded representation.
   */
  @SuppressWarnings("checkstyle:MagicNumber")
  public static String bytesToHex(final byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = HEX_ARRAY[v >>> 4];
      hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
    }
    return new String(hexChars);
  }

  /**
   *
   * @param strData
   * @return Decode hex endoded string to bytes.
   */
  @SuppressWarnings({ "java:S127", "checkstyle:MagicNumber" })
  public static byte[] hexToBytes(final String strData) {
    char[] data;
    if (strData.startsWith("0x")) {
      data = strData.substring(2).toCharArray();
    } else {
      data = strData.toCharArray();
    }
    final int len = data.length;

    if ((len & 0x01) != 0) {
      throw new IllegalArgumentException("Odd number of characters.");
    }

    final byte[] out = new byte[len >> 1];

    // two characters form the hex value.
    for (int i = 0, j = 0; j < len; i++) {
      int f = toDigit(data[j], j) << 4;
      j++;
      f = f | toDigit(data[j], j);
      j++;
      out[i] = (byte) (f & 0xFF);
    }

    return out;
  }

  private static int toDigit(final char ch, final int index) {
    final int digit = Character.digit(ch, 16);
    if (digit == -1) {
      throw new IllegalArgumentException("Illegal hexadecimal character " + ch + " at index " + index);
    }
    return digit;
  }

  /**
   *
   * @param elements
   * @return An opaque offset composed from the supplied elements.
   */
  public static Offset toOffset(final long... elements) {
    StringBuilder sb = new StringBuilder();
    boolean second = false;
    for (long e : elements) {
      if (second) {
        sb.append("-");
      }
      sb.append(e);
      second = true;
    }
    return new Offset(ByteString.copyFromUtf8(sb.toString()));
  }

  /**
   *
   * @param offset
   * @return Offset decomposed into component bytes.
   */
  public static long[] fromOffset(final Offset offset) {
    byte[] sourceBytes = offset.toByteArray();
    ByteBuffer highBuf = ByteBuffer.wrap(Arrays.copyOfRange(sourceBytes, 0, MID_BYTES_START));
    ByteBuffer midBuf = ByteBuffer.wrap(Arrays.copyOfRange(sourceBytes, MID_BYTES_START, MID_BYTES_END));
    ByteBuffer lowBuf = ByteBuffer.wrap(Arrays.copyOfRange(sourceBytes, MID_BYTES_END, OFFSET_BYTES));

    return new long[] { highBuf.getLong(), midBuf.getInt(), lowBuf.getInt() };
  }
}
