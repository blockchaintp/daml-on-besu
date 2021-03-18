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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;

import com.blockchaintp.besu.daml.protobuf.DamlLogEvent;
import com.blockchaintp.besu.daml.protobuf.DamlOperation;
import com.blockchaintp.besu.daml.protobuf.DamlOperationBatch;
import com.blockchaintp.besu.daml.protobuf.DamlTransaction;
import com.blockchaintp.besu.daml.protobuf.TimeKeeperUpdate;
import com.codahale.metrics.SharedMetricRegistries;
import com.daml.lf.engine.Engine;
import com.daml.metrics.Metrics;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.mainnet.AbstractPrecompiledContract;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

public abstract class DamlPrecompiledContract extends AbstractPrecompiledContract {
  private static final Logger LOG = LogManager.getLogger();

  private Engine engine;

  private Metrics metricsRegistry;

  public DamlPrecompiledContract(final String name, final GasCalculator gasCalculator) {
    super(name, gasCalculator);
    initalize();
  }

  protected void initalize() {
    this.engine = DamlEngineSingleton.getInstance();

    String hostname;
    try {
      hostname = InetAddress.getLocalHost().getHostName();
    } catch (final UnknownHostException e) {
      throw new RuntimeException(e);
    }
    this.metricsRegistry = new Metrics(SharedMetricRegistries.getOrCreate(hostname));
  }

  protected final Metrics getMetricsRegistry() {
    return metricsRegistry;
  }

  protected final Engine getEngine() {
    return engine;
  }

  @Override
  public final Gas gasRequirement(final Bytes input) {
    return Gas.of(0L);
  }

  @Override
  public final Bytes compute(final Bytes input, final MessageFrame messageFrame) {
    final LedgerState<DamlLogEvent> ledgerState = messageFrameToLedgerState(messageFrame);

    try {
      final DamlOperationBatch batch = DamlOperationBatch.parseFrom(input.toArray());
      for (final DamlOperation operation : batch.getOperationsList()) {
        final String participantId = operation.getSubmittingParticipant();
        if (operation.hasTransaction()) {
          final DamlTransaction tx = operation.getTransaction();
          handleTransaction(ledgerState, tx, participantId, operation.getCorrelationId());
        } else if (operation.hasTimeUpdate()) {
          final TimeKeeperUpdate timeUpdate = operation.getTimeUpdate();
          handleTimeUpdate(ledgerState, timeUpdate, participantId, operation.getCorrelationId());
        } else {
          LOG.debug("DamlOperation contains no supported operation, ignoring ...");
        }
      }
    } catch (final InvalidTransactionException e) {
      LOG.error("InvalidTransactionException in compute", e);
    } catch (final InvalidProtocolBufferException ipbe) {
      final Exception e = new RuntimeException(String.format("Payload is unparseable and not a valid DamlSubmission %s",
          ipbe.getMessage().getBytes(Charset.defaultCharset())), ipbe);
      LOG.error("Failed to parse DamlSubmission protocol buffer:", e);
    } catch (final InternalError e) {
      LOG.error("Internal error in compute", e);
      throw e;
    }
    return Bytes.EMPTY;
  }

  private void handleTimeUpdate(final LedgerState<DamlLogEvent> ledgerState, final TimeKeeperUpdate timeUpdate,
      final String participantId, final String correlationId) {
    final Timestamp currentTime = ledgerState.updateTime(participantId, timeUpdate);
    ledgerState.sendTimeEvent(currentTime);
    LOG.debug("Global Time Update: {}", ledgerState.getRecordTime());
  }

  abstract protected LedgerState<DamlLogEvent> messageFrameToLedgerState(final MessageFrame messageFrame);

  abstract protected void handleTransaction(final LedgerState<DamlLogEvent> ledgerState, final DamlTransaction tx,
      final String participantId, final String correlationId) throws InternalError, InvalidTransactionException;
}
