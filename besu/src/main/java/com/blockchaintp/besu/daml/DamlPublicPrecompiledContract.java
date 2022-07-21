/*
 * Copyright 2020-2022 Blockchain Technology Partners
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

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import com.blockchaintp.besu.daml.protobuf.DamlLogEvent;
import com.blockchaintp.besu.daml.protobuf.DamlTransaction;
import com.daml.caching.Cache;
import com.daml.ledger.participant.state.kvutils.Raw;
import com.daml.ledger.participant.state.kvutils.store.DamlLogEntryId;
import com.daml.ledger.validator.SubmissionValidator;
import com.daml.ledger.validator.ValidationFailed;
import com.google.protobuf.InvalidProtocolBufferException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

import scala.compat.java8.FutureConverters;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.util.Either;

public final class DamlPublicPrecompiledContract extends DamlPrecompiledContract {
  private static final Logger LOG = LogManager.getLogger();

  private static final String CONTRACT_NAME = "DamlPublic";

  private final ExecutionContext ec;

  public DamlPublicPrecompiledContract(final GasCalculator gasCalculator) {
    super(CONTRACT_NAME, gasCalculator);
    this.ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool());
    initalize();
  }

  @Override
  protected LedgerState<DamlLogEvent> messageFrameToLedgerState(final MessageFrame messageFrame) {
    return new MutableAccountLedgerState(messageFrame);
  }

  @Override
  protected void handleTransaction(final LedgerState<DamlLogEvent> ledgerState, final DamlTransaction tx,
      final String participantId, final String correlationId) throws InternalError, InvalidTransactionException {
    DamlLogEntryId logEntryId;
    try {
      logEntryId = DamlLogEntryId.parseFrom(tx.getLogEntryId());
    } catch (final InvalidProtocolBufferException e1) {
      LOG.warn("InvalidProtocolBufferException when parsing log entry id", e1);
      throw new InvalidTransactionException(e1.getMessage());
    }
    final SubmissionValidator<DamlLogEvent> validator =
      com.daml.ledger.validator.SubmissionValidator$.MODULE$.createForTimeMode(
        ledgerState, () -> logEntryId, false, Cache.none(), getEngine(), getMetricsRegistry());
    final com.daml.lf.data.Time.Timestamp recordTime = ledgerState.getRecordTime();
    final Future<Either<ValidationFailed, DamlLogEvent>> validateAndCommit = validator.validateAndCommit(
        Raw.Envelope$.MODULE$.apply(tx.getSubmission()), correlationId, recordTime, participantId, getEc());
    final CompletionStage<Either<ValidationFailed, DamlLogEvent>> validateAndCommitCS = FutureConverters
        .toJava(validateAndCommit);
    try {
      final Either<ValidationFailed, DamlLogEvent> either = validateAndCommitCS.toCompletableFuture().get();
      final var left = either.left().toOption();
      if (!left.isEmpty()) {
        final var validationFailed = left.get();
        throw new InvalidTransactionException(validationFailed.toString());
      } else {
        LOG.debug("Processed transaction into log event cid={}", correlationId);
      }
    } catch (InterruptedException e) {
      LOG.warn("Interrupted while handling transaction", e);
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      throw new InternalError(e.getMessage());
    }
  }

  private ExecutionContext getEc() {
    return ec;
  }

}
