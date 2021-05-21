package com.blockchaintp.besu.daml.rpc;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.daml.ledger.participant.state.v1.Offset;
import com.google.protobuf.ByteString;

public class Utils {
  private static final int OFFSET_BYTES = 16;
  private static final int MID_BYTES_START = 8;
  private static final int MID_BYTES_END = 12;
  private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

  private Utils() {
  }

  public static String bytesToHex(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = HEX_ARRAY[v >>> 4];
      hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
    }
    return new String(hexChars);
  }

  @SuppressWarnings("java:S127")
  public static byte[] hexToBytes(String strData) {
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

  public static Offset toOffset(long... elements) {
    StringBuilder sb = new StringBuilder();
    boolean second = false;
    for (long e : elements) {
      if (second) {
        sb.append("-");
      }
      sb.append(Long.toString(e));
      second = true;
    }
    return new Offset(ByteString.copyFromUtf8(sb.toString()));
  }

  public static long[] fromOffset(Offset offset) {
    byte[] sourceBytes = offset.toByteArray();
    ByteBuffer highBuf = ByteBuffer.wrap(Arrays.copyOfRange(sourceBytes, 0, MID_BYTES_START));
    ByteBuffer midBuf = ByteBuffer.wrap(Arrays.copyOfRange(sourceBytes, MID_BYTES_START, MID_BYTES_END));
    ByteBuffer lowBuf = ByteBuffer.wrap(Arrays.copyOfRange(sourceBytes, MID_BYTES_END, OFFSET_BYTES));

    return new long[] {
      highBuf.getLong(),
      midBuf.getInt(),
      lowBuf.getInt()
    };
  }
}
