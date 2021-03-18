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

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.blockchaintp.besu.daml.Namespace.DamlKeyType;
import com.blockchaintp.besu.daml.protobuf.DamlLogEvent;
import com.blockchaintp.besu.daml.protobuf.TimeKeeperUpdate;
import com.daml.ledger.validator.LedgerStateOperations;
import com.daml.lf.data.Time.Timestamp;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.Timestamps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Log;
import org.hyperledger.besu.ethereum.core.LogTopic;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.datatypes.Event;

import scala.Function1;
import scala.Option;
import scala.Tuple2;
import scala.collection.JavaConverters;
import scala.collection.Seq;
import scala.concurrent.Future;
import scala.runtime.BoxedUnit;

public class MutableAccountLedgerState implements LedgerState<DamlLogEvent> {
  private static final Logger LOG = LogManager.getLogger();

  private static final LogTopic DAML_LOG_TOPIC = LogTopic
      .create(Bytes.fromHexString(EventEncoder.encode(new Event("daml/log-event", List.of()))));

  private final MutableAccount account;

  private final MessageFrame messageFrame;

  public MutableAccountLedgerState(final MessageFrame theMessageFrame) {
    final WorldUpdater updater = theMessageFrame.getWorldState();
    this.account = updater.getOrCreate(Address.DAML_PUBLIC).getMutable();
    this.messageFrame = theMessageFrame;
  }

  @Override
  public ByteString getDamlState(final ByteString key) {
    LOG.trace("Getting DAML state for key={}", key);

    final UInt256 address = Namespace.makeDamlStateKeyAddress(key);
    LOG.trace("DAML state key address={}", address.toHexString());
    final ByteBuffer buf = getLedgerEntry(address);
    if (buf == null) {
      LOG.trace("No ledger entry for DAML state key address={}", address.toHexString());
      return null;
    }
    final ByteString bs = ByteString.copyFrom(buf);
    return bs;
  }

  @Override
  public Map<ByteString, ByteString> getDamlStates(final Collection<ByteString> keys) {
    final Map<ByteString, ByteString> states = new LinkedHashMap<>();
    keys.forEach(key -> {
      final ByteString val = getDamlState(key);
      if (val != null) {
        states.put(key, val);
      }
    });
    return states;
  }

  @Override
  public Map<ByteString, ByteString> getDamlStates(final ByteString... keys) {
    return getDamlStates(Lists.newArrayList(keys));
  }

  private ByteBuffer getLedgerEntry(final UInt256 key) {
    // reconstitute RLP bytes from all ethereum slices created for this ledger
    // entry
    final MutableBytes32 slot = key.toBytes().mutableCopy();
    LOG.debug("Will fetch slices starting at rootKey={}", slot.toHexString());
    UInt256 data = account.getStorageValue(UInt256.fromBytes(slot));
    int slices = 1;
    Bytes rawRlp = Bytes.EMPTY;
    final List<Bytes> dataSoFar = new ArrayList<>();
    String deadBeef = data.toHexString();
    if (deadBeef.contains("00dead")) {
      dataSoFar.add(fromDeadBeef(data));
    } else {
      if (!data.isZero()) {
        dataSoFar.add(data.toMinimalBytes());
      } else {
        return null;
      }
    }

    while (!data.isZero()) {
      slot.increment();
      slices += 1;
      data = account.getStorageValue(UInt256.fromBytes(slot));
      if (data.isZero()) {
        continue;
      }
      if (slices % 100 == 0) {
        LOG.trace("Fetched from rootKey={} slices={} size={}", key.toHexString(), slices, rawRlp.size());
      }
      deadBeef = data.toHexString();
      if (deadBeef.contains("00dead")) {
        dataSoFar.add(fromDeadBeef(data));
      } else {
        dataSoFar.add(data.toMinimalBytes());
      }
    }
    rawRlp = Bytes.concatenate(dataSoFar.toArray(new Bytes[] {}));
    LOG.debug("Fetched from rootKey={} slices={} size={}", key.toHexString(), slices, rawRlp.size());
    if (rawRlp.size() != 0) {
      final Bytes entry = RLP.decodeOne(rawRlp);
      return ByteBuffer.wrap(entry.toArray());
    } else {
      return null;
    }
  }

  private UInt256 makeDeadBeef(final Integer numberOfZeros) {
    final String deadBeef = "0xDEAD" + numberOfZeros.toString();
    return UInt256.fromHexString(deadBeef);
  }

  private Bytes fromDeadBeef(final UInt256 data) {
    final String testString = data.toHexString();
    if (testString.contains("0000dead")) {
      final String len = testString.substring(testString.lastIndexOf("dead") + 4);
      final int numOfZeroBytes = Integer.parseInt(len);
      final byte[] dataB = new byte[numOfZeroBytes];
      return Bytes.of(dataB);
    } else {
      return data.toBytes();
    }
  }

  /**
   * Add the supplied data to the ledger, starting at the supplied ethereum
   * storage slot address.
   *
   * @param rootAddress 256-bit ethereum storage slot address
   * @param entry       value to store in the ledger
   */
  private void addLedgerEntry(final UInt256 rootAddress, final ByteString entry) {
    // RLP-encode the entry
    final Bytes encoded = RLP.encodeOne(Bytes.of(entry.toByteArray()));
    final MutableBytes32 slot = rootAddress.toBytes().mutableCopy();
    LOG.debug("Writing starting at address={} bytes={}", rootAddress.toHexString(), encoded.size());

    // store the first part of the entry
    final int sliceSz = Math.min(Namespace.STORAGE_SLOT_SIZE, encoded.size());
    Bytes data = encoded.slice(0, sliceSz);
    UInt256 part = UInt256.fromBytes(data);
    if (data.size() > 0 && part.isZero()) {
      part = makeDeadBeef(data.size());
    }
    int slices = 0;
    account.setStorageValue(UInt256.fromBytes(slot), part);

    LOG.trace("Wrote to address={} slice={} total bytes={}", UInt256.fromBytes(slot).toHexString(),
        slices, part.toShortHexString());

    // Store remaining parts, if any. We ensure that the data is stored in
    // consecutive
    // ethereum storage slots by incrementing the slot by one each time
    int offset = Namespace.STORAGE_SLOT_SIZE;
    while (offset < encoded.size()) {
      final int length = Math.min(Namespace.STORAGE_SLOT_SIZE, encoded.size() - offset);
      data = encoded.slice(offset, length);
      if (data.hasLeadingZeroByte()) {
        slot.increment();
        slices++;
        final UInt256 deadBeefData = makeDeadBeef(data.numberOfLeadingZeroBytes());
        account.setStorageValue(UInt256.fromBytes(slot), deadBeefData);
        data = data.slice(data.numberOfLeadingZeroBytes());
        if (data.size() == 0) {
          offset += Namespace.STORAGE_SLOT_SIZE;
          continue;
        }
      }
      part = UInt256.fromBytes(data);

      if (data.size() > 0 && part.isZero()) {
        part = makeDeadBeef(data.size());
      }
      slot.increment();
      slices++;
      account.setStorageValue(UInt256.fromBytes(slot), part);

      LOG.trace("Wrote to address={} slice={} total bytes={}", UInt256.fromBytes(slot).toHexString(),
          slices, part.toShortHexString());
      if (slices % 100 == 0) {
        LOG.trace("Wrote to address={} slices={} offset={}", rootAddress.toHexString(), slices, offset);
      }
      offset += Namespace.STORAGE_SLOT_SIZE;
    }
    // Mark the tombstone
    slot.increment();
    slices++;
    account.setStorageValue(UInt256.fromBytes(slot), UInt256.ZERO);

    LOG.debug("Wrote to address={} slices={} total size={}", rootAddress.toHexString(), slices, encoded.size());
  }

  @Override
  public void setDamlState(final ByteString key, final ByteString value) {
    final UInt256 rootAddress = Namespace.makeDamlStateKeyAddress(key);
    addLedgerEntry(rootAddress, value);
  }

  @Override
  public void setDamlStates(final Collection<Entry<ByteString, ByteString>> entries) {
    entries.forEach(e -> setDamlState(e.getKey(), e.getValue()));
  }

  @Override
  public DamlLogEvent sendLogEvent(final ByteString entryId, final ByteString entry) throws InternalError {
    return sendLogEvent(Address.DAML_PUBLIC, DAML_LOG_TOPIC, entryId, entry);
  }

  public DamlLogEvent sendLogEvent(final Address fromAddress, final LogTopic topic, final ByteString entryId,
      final ByteString entry) throws InternalError {
    final DamlLogEvent logEvent = DamlLogEvent.newBuilder().setLogEntry(entry).setLogEntryId(entryId).build();
    LOG.info("Publishing DamlLogEvent size={}", logEvent.toByteArray().length);
    final Log event = new Log(Address.DAML_PUBLIC, Bytes.of(logEvent.toByteArray()), Lists.newArrayList(topic));
    this.messageFrame.addLog(event);
    return logEvent;
  }

  public DamlLogEvent sendTimeEvent(final com.google.protobuf.Timestamp timestamp) {
    final TimeKeeperUpdate update = TimeKeeperUpdate.newBuilder().setTimeUpdate(timestamp).build();
    final DamlLogEvent logEvent = DamlLogEvent.newBuilder().setTimeUpdate(update).build();
    LOG.info("Publishing DamlLogEvent for time update size={}", logEvent.toByteArray().length);
    final Log event = new Log(Address.DAML_PUBLIC, Bytes.of(logEvent.toByteArray()),
        Lists.newArrayList(DAML_LOG_TOPIC));
    this.messageFrame.addLog(event);
    return logEvent;
  }

  @Override
  public Timestamp getRecordTime() throws InternalError {
    final ByteBuffer globalEntryBB = this.getLedgerEntry(Namespace.getGlobalTimeAddress());

    if (globalEntryBB == null || globalEntryBB.array().length == 0) {
      final Instant time = Instant.now();
      // Reducing resolution to 10s for now
      long ts = time.getEpochSecond() / 100L;
      ts = ts * 100L;

      final com.google.protobuf.Timestamp timestamp = com.google.protobuf.Timestamp.newBuilder().setSeconds(ts)
          .setNanos(0).build();
      LOG.debug("Record Time = {}", timestamp.toString());
      final long micros = Timestamps.toMicros(timestamp);
      return new Timestamp(micros);
    } else {
      try {
        final com.google.protobuf.Timestamp currentGlobalTime = com.google.protobuf.Timestamp.parseFrom(globalEntryBB);
        final long micros = Timestamps.toMicros(currentGlobalTime);
        return new Timestamp(micros);
      } catch (final InvalidProtocolBufferException e) {
        LOG.warn("Buffer was {}", Bytes.of(globalEntryBB.array()).toHexString());
        throw new InternalError(e);
      }
    }
  }

  @Override
  public com.google.protobuf.Timestamp updateTime(final String participantId, final TimeKeeperUpdate update)
      throws InternalError {
    final UInt256 participantAddress = Namespace.makeAddress(DamlKeyType.TIME_UPDATE, participantId.getBytes());
    final com.google.protobuf.Timestamp timeUpdate = update.getTimeUpdate();
    com.google.protobuf.Timestamp participantTime;
    final ByteBuffer participantTimeBB = this.getLedgerEntry(participantAddress);

    if (participantTimeBB == null) {
      participantTime = timeUpdate;
    } else {
      try {
        final com.google.protobuf.Timestamp currentParticipantTime = com.google.protobuf.Timestamp
            .parseFrom(participantTimeBB);
        participantTime = maxTimestamp(currentParticipantTime, timeUpdate);
      } catch (final InvalidProtocolBufferException e1) {
        LOG.warn("Invalid time submitted stored for participant {}", participantId);
        participantTime = timeUpdate;
      }
    }
    this.addLedgerEntry(participantAddress, participantTime.toByteString());

    com.google.protobuf.Timestamp newGlobalTime;
    final ByteBuffer globalEntryBB = this.getLedgerEntry(Namespace.getGlobalTimeAddress());
    if (globalEntryBB == null) {
      newGlobalTime = timeUpdate;
    } else {
      try {
        final com.google.protobuf.Timestamp currentGlobalTime = com.google.protobuf.Timestamp.parseFrom(globalEntryBB);
        newGlobalTime = maxTimestamp(currentGlobalTime, participantTime);
      } catch (final InvalidProtocolBufferException e) {
        LOG.warn("Unable to parse global time entry", e);
        newGlobalTime = timeUpdate;
      }
    }
    this.addLedgerEntry(Namespace.getGlobalTimeAddress(), newGlobalTime.toByteString());
    return newGlobalTime;
  }

  private com.google.protobuf.Timestamp maxTimestamp(final com.google.protobuf.Timestamp x,
      final com.google.protobuf.Timestamp y) {
    final int comparison = Timestamps.compare(x, y);
    if (comparison < 0) {
      return y;
    } else {
      return x;
    }
  }

  protected com.google.protobuf.Timestamp averageTime(final com.google.protobuf.Timestamp... times) {
    long millisTotal = 0;
    for (int i = 0; i < times.length; i++) {
      millisTotal += Timestamps.toMillis(times[i]);
    }
    if (millisTotal < 0) {
      return Timestamps.fromMillis(0L);
    } else {
      final long aveMillis = millisTotal / times.length;
      return Timestamps.fromMillis(aveMillis);
    }
  }

  @Override
  public Future<DamlLogEvent> appendToLog(final ByteString key, final ByteString value) {
    return Future.successful(sendLogEvent(key, value));
  }

  @Override
  public Future<Option<ByteString>> readState(final ByteString key) {
    final ByteString val = getDamlState(key);
    if (null != val) {
      return Future.successful(Option.apply(val));
    } else {
      return Future.successful(Option.empty());
    }
  }

  @Override
  public Future<Seq<Option<ByteString>>> readState(final Seq<ByteString> keys) {
    final Collection<ByteString> keyColl = JavaConverters.asJavaCollection(keys);
    final Map<ByteString, ByteString> damlStates = getDamlStates(keyColl);
    final Collection<Option<ByteString>> retCollection = new ArrayList<>();
    for (final ByteString k : keyColl) {
      if (damlStates.containsKey(k)) {
        retCollection.add(Option.apply(damlStates.get(k)));
      } else {
        retCollection.add(Option.empty());
      }
    }
    return Future.successful(JavaConverters.asScalaIteratorConverter(retCollection.iterator()).asScala().toSeq());
  }

  @Override
  public Future<BoxedUnit> writeState(final Seq<Tuple2<ByteString, ByteString>> keyValuePairs) {
    final Collection<Tuple2<ByteString, ByteString>> setColl = JavaConverters.asJavaCollection(keyValuePairs);
    for (final Tuple2<ByteString, ByteString> tup : setColl) {
      setDamlState(tup._1(), tup._2());
    }
    return Future.successful(BoxedUnit.UNIT);
  }

  @Override
  public Future<BoxedUnit> writeState(final ByteString key, final ByteString value) {
    setDamlState(key, value);
    return Future.successful(BoxedUnit.UNIT);
  }

  @Override
  public <T> Future<T> inTransaction(final Function1<LedgerStateOperations<DamlLogEvent>, Future<T>> body) {
    return body.apply(this);
  }
}
