/*
 * Copyright © 2023 Paravela Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.blockchaintp.besu.daml;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public final class Sandbox {
  private static final Charset CS = Charset.defaultCharset();

  private static final String STATE_KEY = "DAML state key";
  private static final String ENTRY_ID = "DAML log entry id";

  public static void main(final String... args) throws IOException {

    System.out.println(Level.getLevel("debug".toUpperCase()));

    System.out.println(String.format("0x%040x", 0x75));

    String data = "Shay Gordon";
    System.out.println(String.format("Hash of %s: %02x", data, superFastHash(data.getBytes(Charset.defaultCharset()))));
    data = "Kevin O'Donnell";
    System.out.println(String.format("Hash of %s: %02x", data, superFastHash(data.getBytes(Charset.defaultCharset()))));

    System.out.println("Bytes.EMPTY: " + Bytes.EMPTY);
    System.out.println("Daml public contract address: " + Namespace.getDamlPublicAddress());
    System.out.println(String.format("%s: %s (%d)", Namespace.DamlKeyType.STATE,
        Namespace.DamlKeyType.STATE.rootAddress(), Namespace.DamlKeyType.STATE.rootAddress().length()));
    System.out.println(String.format("%s: %s (%d)", Namespace.DamlKeyType.LOG, Namespace.DamlKeyType.LOG.rootAddress(),
        Namespace.DamlKeyType.LOG.rootAddress().length()));

    System.out.println(String.format("%-20s %s", "State key hash", Namespace.getHash(STATE_KEY.getBytes(CS))));
    System.out.println(String.format("%-20s %s", "State key address",
        Namespace.makeAddress(Namespace.DamlKeyType.STATE, STATE_KEY.getBytes(CS)).toHexString()));
    System.out.println(String.format("%-20s %s", "Entry id hash", Namespace.getHash(ENTRY_ID.getBytes(CS))));
    System.out.println(String.format("%-20s %s", "Entry id address",
        Namespace.makeAddress(Namespace.DamlKeyType.LOG, ENTRY_ID.getBytes(CS)).toHexString()));

    Bytes hash = Namespace.getHash("0".getBytes(CS));
    System.out.println("Hash: " + hash.toHexString());
    ByteBuffer buf = ByteBuffer.allocate(Namespace.STORAGE_SLOT_SIZE).put((byte) 0x75).put((byte) 0)
        .put(hash.slice(2).toArray());
    UInt256 slot = UInt256.fromBytes(Bytes.of(buf.array()));
    System.out.println("Slot: " + slot.toHexString());
  }

  private static int superFastHash(byte[] data) {
    int len = data.length;
    int hash = len;
    int rem = len & 3;
    len >>= 2;

    for (int index = 0; len > 0; index += 4, --len) {
      hash += get16bits(data[index], data[index + 1]);
      int tmp = (get16bits(data[index + 2], data[index + 3]) << 11) ^ hash;
      hash = (hash << 16) ^ tmp;
      hash += hash >> 11;
    }

    // Handle end cases
    switch (rem) {
      case 3:
        hash += get16bits(data[0], data[1]);
        hash ^= hash << 16;
        hash ^= data[2] << 18;
        hash += hash >> 11;
        break;
      case 2:
        hash += get16bits(data[0], data[1]);
        hash ^= hash << 11;
        hash += hash >> 17;
        break;
      case 1:
        hash += data[0];
        hash ^= hash << 10;
        hash += hash >> 1;
    }

    /* Force "avalanching" of final 127 bits */
    hash ^= hash << 3;
    hash += hash >> 5;
    hash ^= hash << 4;
    hash += hash >> 17;
    hash ^= hash << 25;
    hash += hash >> 6;

    return hash;
  }

  private static short get16bits(byte a, byte b) {
    return (short)(((a & 0xFF) << 8) | (b & 0xFF));
  }
}
