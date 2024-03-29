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

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.blockchaintp.besu.daml.Namespace.DamlKeyType;
import com.blockchaintp.besu.daml.protobuf.DamlLogEvent;
import com.blockchaintp.besu.daml.protobuf.TimeKeeperUpdate;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlStateKey;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlSubmissionDedupKey;
import com.daml.ledger.participant.state.kvutils.Raw;
import com.daml.ledger.validator.LedgerStateOperations;
import com.daml.lf.data.Time;
import com.daml.lf.data.Time.Timestamp;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.Timestamps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Log;
import org.hyperledger.besu.ethereum.core.LogTopic;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.datatypes.Event;

import scala.Function1;
import scala.Option;
import scala.Tuple2;
import scala.collection.Iterable;
import scala.collection.JavaConverters;
import scala.collection.immutable.Seq;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.runtime.BoxedUnit;

/**
 *
 */
public final class MutableAccountLedgerState implements LedgerState<DamlLogEvent> {

  private static final Logger LOG = LogManager.getLogger();

  private static final LogTopic DAML_LOG_TOPIC = LogTopic
      .create(Bytes.fromHexString(EventEncoder.encode(new Event("daml/log-event", List.of()))));

  private final MutableAccount account;

  private final MessageFrame messageFrame;

  private Cache<ByteString, ByteString> cache = ValueCacheSingleton.getInstance().getCache();
  private Cache<ByteString, DamlStateKey> parsedKeys = KeyCacheSingleton.getInstance().getCache();

  private boolean caching;

  /**
   *
   * @param theMessageFrame
   */
  public MutableAccountLedgerState(final MessageFrame theMessageFrame) {
    final WorldUpdater updater = theMessageFrame.getWorldState();
    this.account = updater.getOrCreate(Address.DAML_PUBLIC).getMutable();
    this.messageFrame = theMessageFrame;
    this.caching = true;
  }

  private boolean isKeyCacheable(final Raw.StateKey key) {
    var dsk = parsedKeys.getIfPresent(key);
    if (dsk == null) {
      try {
        dsk = keyToDamlStateKey(key);
        parsedKeys.put(key.bytes(), dsk);
      } catch (InvalidProtocolBufferException e) {
        return false;
      }
    }
    if (dsk.getKeyCase().equals(DamlStateKey.KeyCase.SUBMISSION_DEDUP)) {
      DamlSubmissionDedupKey dsdk = dsk.getSubmissionDedup();
      if (dsdk.getSubmissionKind().equals(DamlSubmissionDedupKey.SubmissionKind.PACKAGE_UPLOAD)
          || dsdk.getSubmissionKind().equals(DamlSubmissionDedupKey.SubmissionKind.PARTY_ALLOCATION)) {
        this.caching = false;
        return false;
      } else {
        this.caching = true;
      }
    }
    var isPackage = dsk.getKeyCase().equals(DamlStateKey.KeyCase.PACKAGE_ID);
    var isParty = dsk.getKeyCase().equals(DamlStateKey.KeyCase.PARTY);
    LOG.trace("isKeyCacheable: {} isPackage: {} isCaching: {}", key, isPackage, this.caching);
    return this.caching && (isPackage || isParty);
  }

  @Override
  @SuppressWarnings("java:S1612")
  public Raw.Envelope getDamlState(final Raw.StateKey key) {
    boolean isCacheable = isKeyCacheable(key);
    if (isCacheable) {
      var val = this.cache.getIfPresent(key.bytes());
      if (val != null) {
        LOG.trace("Key cache hit size: {}", val.size());
        return Raw.Envelope$.MODULE$.apply(val);
      } else {
        LOG.trace("Key cache miss");
      }
    }

    LOG.trace("Getting DAML state for key={}", key);

    final UInt256 address = Namespace.makeDamlStateKeyAddress(key);
    LOG.trace("DAML state key address={}", () -> address.toHexString());
    final ByteBuffer buf = getLedgerEntry(address);
    if (buf == null) {
      LOG.trace("No ledger entry for DAML state key address={}", () -> address.toHexString());
      return null;
    }
    ByteString val = ByteString.copyFrom(buf);
    if (isCacheable) {
      LOG.debug("Key caching size: {}", val.size());
      this.cache.put(key.bytes(), val);
    }
    return Raw.Envelope$.MODULE$.apply(val);
  }

  @Override
  public Map<Raw.StateKey, Option<Raw.Envelope>> getDamlStates(final Collection<Raw.StateKey> keys) {
    final var states = new LinkedHashMap<Raw.StateKey, Option<Raw.Envelope>>();
    keys.forEach(key -> {
      final var val = getDamlState(key);
      if (val != null) {
        states.put(key, Option.apply(val));
      } else {
        states.put(key, Option.empty());
      }
    });
    return states;
  }

  @SuppressWarnings({ "java:S1612", "checkstyle:MagicNumber" })
  private ByteBuffer getLedgerEntry(final UInt256 key) {
    // reconstitute RLP bytes from all ethereum slices created for this ledger
    // entry
    final List<UInt256> data = readSlotSequence(key.toBytes());
    if (data.isEmpty()) {
      return null;
    }
    return decodeAndWrapOrThrow(data);
  }

  private List<UInt256> readSlotSequence(final Bytes32 initialAddress) {
    var retList = new ArrayList<UInt256>();
    MutableBytes32 address = initialAddress.mutableCopy();
    UInt256 slotValue = account.getStorageValue(UInt256.fromBytes(address));
    LOG.debug("Will fetch slices starting at rootKey={}", address::toHexString);
    while (!slotValue.isZero()) {
      retList.add(slotValue);
      address.increment();
      slotValue = account.getStorageValue(UInt256.fromBytes(address));
    }
    return retList;
  }

  private ByteBuffer decodeAndWrapOrThrow(final List<UInt256> data) throws RLPException {
    try {
      List<Bytes> byteData = data.stream().map(ZeroMarking::unmarkZeros).collect(Collectors.toList());
      Bytes rawRlp = Bytes.concatenate(byteData.toArray(new Bytes[] {}));
      if (LOG.isDebugEnabled()) {
        LOG.debug("Fetched from slices={} size={}", data.size(), rawRlp.size());
      }

      if (rawRlp.size() == 0) {
        return null;
      }

      final Bytes entry = RLP.decodeOne(rawRlp);
      return ByteBuffer.wrap(entry.toArray());
    } catch (RLPException | NumberFormatException e) {
      LOG.error("RLP Serialization error encountered, original input data to follow");
      int index = 0;
      for (UInt256 b : data) {
        LOG.error("slot={} data={}", index, b.toHexString());
        index++;
      }
      throw e;
    }
  }

  /**
   * Add the supplied data to the ledger, starting at the supplied ethereum storage slot address.
   *
   * @param rootAddress
   *          256-bit ethereum storage slot address
   * @param entry
   *          value to store in the ledger
   */
  @SuppressWarnings({ "java:S1612", "checkstyle:MagicNumber" })
  private void addLedgerEntry(final UInt256 rootAddress, final Raw.Envelope entry) {
    // RLP-encode the entry
    final Bytes encoded = RLP.encodeOne(Bytes.of(entry.bytes().toByteArray()));
    final MutableBytes32 slot = rootAddress.toBytes().mutableCopy();
    LOG.debug("Writing starting at address={} bytes={}", () -> rootAddress.toHexString(), () -> encoded.size());

    List<UInt256> storageSlots = ZeroMarking.dataToSlotVals(encoded, Namespace.STORAGE_SLOT_SIZE);
    int slices = 0;
    for (var slotValue : storageSlots) {
      account.setStorageValue(UInt256.fromBytes(slot), slotValue);
      if (LOG.isTraceEnabled()) {
        LOG.trace("Wrote to address={} slice={} total bytes={}", UInt256.fromBytes(slot).toHexString(), slices,
            slotValue.toShortHexString());
      }
      slices++;
      slot.increment();
    }
  }

  private DamlStateKey keyToDamlStateKey(final Raw.StateKey key) throws InvalidProtocolBufferException {
    return DamlStateKey.parseFrom(key.bytes().toByteArray());
  }

  @Override
  public void setDamlState(final Raw.StateKey key, final Raw.Envelope value) {
    final UInt256 rootAddress = Namespace.makeDamlStateKeyAddress(key);
    addLedgerEntry(rootAddress, value);
  }

  @Override
  public void setDamlStates(final Collection<Entry<Raw.StateKey, Raw.Envelope>> entries) {
    entries.forEach(e -> setDamlState(e.getKey(), e.getValue()));
  }

  @Override
  public DamlLogEvent sendLogEvent(final Raw.LogEntryId entryId, final Raw.Envelope entry) throws InternalError {
    return sendLogEvent(Address.DAML_PUBLIC, DAML_LOG_TOPIC, entryId, entry);
  }

  /**
   *
   * @param fromAddress
   * @param topic
   * @param entryId
   * @param entry
   * @return A log event appended to the current message frame.
   * @throws InternalError
   */
  public DamlLogEvent sendLogEvent(final Address fromAddress, final LogTopic topic, final Raw.LogEntryId entryId,
      final Raw.Envelope entry) throws InternalError {
    final DamlLogEvent logEvent = DamlLogEvent.newBuilder().setLogEntry(entry.bytes()).setLogEntryId(entryId.bytes())
        .build();
    LOG.info("Publishing DamlLogEvent from={} size={}", fromAddress, logEvent.toByteArray().length);
    final Log event = new Log(Address.DAML_PUBLIC, Bytes.of(logEvent.toByteArray()), Lists.newArrayList(topic));
    this.messageFrame.addLog(event);
    return logEvent;
  }

  /**
   *
   * @param timestamp
   * @return A time event appended to the current message frame.
   */
  public DamlLogEvent sendTimeEvent(final com.google.protobuf.Timestamp timestamp) {
    final TimeKeeperUpdate update = TimeKeeperUpdate.newBuilder().setTimeUpdate(timestamp).build();
    final DamlLogEvent logEvent = DamlLogEvent.newBuilder().setTimeUpdate(update).build();
    LOG.info("Publishing DamlLogEvent for time update size={}", logEvent.toByteArray().length);
    final Log event = new Log(Address.DAML_PUBLIC, Bytes.of(logEvent.toByteArray()),
        Lists.newArrayList(DAML_LOG_TOPIC));
    this.messageFrame.addLog(event);
    return logEvent;
  }

  @SuppressWarnings("checkstyle:MagicNumber")
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
      LOG.debug("Record Time = {}", timestamp);
      final long micros = Timestamps.toMicros(timestamp);
      return Time.Timestamp$.MODULE$.assertFromLong(micros);
    } else {
      try {
        final com.google.protobuf.Timestamp currentGlobalTime = com.google.protobuf.Timestamp.parseFrom(globalEntryBB);
        final long micros = Timestamps.toMicros(currentGlobalTime);
        return Time.Timestamp$.MODULE$.assertFromLong(micros);
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
    this.addLedgerEntry(participantAddress, Raw.Envelope$.MODULE$.apply(participantTime.toByteString()));

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
    this.addLedgerEntry(Namespace.getGlobalTimeAddress(), Raw.Envelope$.MODULE$.apply(newGlobalTime.toByteString()));
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
  public Future<DamlLogEvent> appendToLog(final Raw.LogEntryId key, final Raw.Envelope value,
      final ExecutionContext context) {
    return Future.successful(sendLogEvent(key, value));
  }

  @Override
  public Future<Option<Raw.Envelope>> readState(final Raw.StateKey key, final ExecutionContext context) {
    final var val = getDamlState(key);
    if (null != val) {
      return Future.successful(Option.apply(val));
    } else {
      return Future.successful(Option.empty());
    }
  }

  @Override
  public Future<Seq<Option<Raw.Envelope>>> readState(final Iterable<Raw.StateKey> keys,
      final ExecutionContext context) {
    final var keyColl = JavaConverters.asJavaCollection(keys);
    final var damlStates = getDamlStates(keyColl);
    final var retCollection = new ArrayList<Option<Raw.Envelope>>();
    for (final var k : keyColl) {
      if (damlStates.containsKey(k)) {
        retCollection.add(damlStates.get(k));
      } else {
        retCollection.add(Option.empty());
      }
    }
    return Future.successful(JavaConverters.asScalaIteratorConverter(retCollection.iterator()).asScala().toSeq());
  }

  @Override
  public Future<BoxedUnit> writeState(final Iterable<Tuple2<Raw.StateKey, Raw.Envelope>> keyValuePairs,
      final ExecutionContext executionContext) {
    final var setColl = JavaConverters.asJavaCollection(keyValuePairs);
    for (final var tup : setColl) {
      setDamlState(tup._1(), tup._2());
    }
    return Future.successful(BoxedUnit.UNIT);
  }

  @Override
  public Future<BoxedUnit> writeState(final Raw.StateKey key, final Raw.Envelope value, final ExecutionContext ec) {
    setDamlState(key, value);
    return Future.successful(BoxedUnit.UNIT);
  }

  @Override
  public <T> Future<T> inTransaction(final Function1<LedgerStateOperations<DamlLogEvent>, Future<T>> body,
      final ExecutionContext ec) {
    return body.apply(this);
  }
}
