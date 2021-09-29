/*
 * Copyright 2020-2021 Blockchain Technology Partners
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

import java.math.BigInteger;

import com.daml.ledger.participant.state.kvutils.Raw;
import com.google.protobuf.ByteString;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.ethereum.core.Address;

/** Utility class dealing with DAML namespace functions and values. */
public final class Namespace {
  /**
   * An ethereum address is 20 bytes represented as a hexadecimal string with a "0x" prefix, hence 42
   * characters in length.
   */
  public static final int ADDRESS_STRING_LENGTH = Address.SIZE * 2;

  public static final int ADDRESS_HEX_STRING_LENGTH = ADDRESS_STRING_LENGTH + "0x".length();

  /** The size of an ethereum storage slot. */
  public static final int STORAGE_SLOT_SIZE = Bytes32.SIZE;

  /** The ethereum address of the DAML precompiled contract. */
  public static final String DAML_PUBLIC_ADDRESS = String.format("%02x", Address.DAML_PUBLIC.toBigInteger());

  /**
   * Enumeration that maps a DAML key type to a 4-hexadecimal-character ethereum storage root address.
   */
  public enum DamlKeyType {
    /** DAML state value. */
    STATE,
    /** DAML log entry. */
    LOG,
    /** TimeKeeper Update */
    TIME_UPDATE;

    private final String rootAddress;

    private DamlKeyType() {
      rootAddress = String.format("%s%02d", DAML_PUBLIC_ADDRESS, ordinal());
    }

    /**
     * Return the 4-hexadecimal-character etheruem storage root address for this DAML key type.
     *
     * @return DAML root address
     */
    public String rootAddress() {
      return rootAddress;
    }
  }

  public static final UInt256 getGlobalTimeAddress() {
    return Namespace.makeAddress(DamlKeyType.TIME_UPDATE, "GLOBAL".getBytes());
  }

  /**
   * Return the 2-hexadecimal-character ethereum address prefix of the DAML precompiled contract.
   *
   * @return the ethereum address prefix of the DAML precompiled contract
   */
  public static String getDamlPublicAddress() {
    return DAML_PUBLIC_ADDRESS;
  }

  /**
   * Return a 256-bit ethereum storage address given a DAML storage key and data.
   *
   * @param key
   *          the DAML storage key
   * @param data
   *          the data
   * @return 256-bit ethereum storage slot address
   */
  public static UInt256 makeAddress(final DamlKeyType key, final byte[] data) {
    String hash = hashToHexString(getHash(data));

    // use only the last 28 bytes of the hash to allow room for the namespace
    final int begin = hash.length() - (STORAGE_SLOT_SIZE * 2) + key.rootAddress().length();
    hash = hash.substring(begin);
    return UInt256.fromHexString(key.rootAddress() + hash);
  }

  /**
   * Return a 256-bit ethereum storage address given a DAML storage key and data.
   *
   * @param key
   *          the DAML storage key
   * @param data
   *          the data
   * @return 256-bit ethereum storage slot address
   */
  public static UInt256 makeAddress(final DamlKeyType key, final Raw.StateKey data) {
    return makeAddress(key, data.bytes().toByteArray());
  }

  /**
   * Return a 256-bit ethereum storage address given a DAL state key.
   *
   * @param key
   *          DamlStateKey to be used for the address
   * @return the string address
   */
  public static UInt256 makeDamlStateKeyAddress(final Raw.StateKey key) {
    return makeAddress(DamlKeyType.STATE, key);
  }

  /**
   * Return the SHA-256 hash of an array of bytes of arbitrary length.
   *
   * @param input
   *          the byte array
   * @return the SHA-256 hash of the byte array
   */
  public static Bytes getHash(final byte[] input) {
    return Hash.sha256(Bytes.of(input));
  }

  /**
   * Return the 64-character hexadecimal string representation of a SHA-256 hash.
   *
   * @param hash
   *          the hash
   * @return the hexadecimal string representation of the hash
   */
  private static String hashToHexString(final Bytes hash) {
    return String.format("%064x", new BigInteger(1, hash.toArray()));
  }

  private Namespace() {
  }
}
