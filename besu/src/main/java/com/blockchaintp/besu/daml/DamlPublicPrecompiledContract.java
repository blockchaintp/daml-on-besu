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

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import com.blockchaintp.besu.daml.protobuf.DamlLogEvent;
import com.blockchaintp.besu.daml.protobuf.DamlTransaction;
import com.daml.caching.Cache;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
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

  public DamlPublicPrecompiledContract(final GasCalculator gasCalculator) {
    super(CONTRACT_NAME, gasCalculator);
    initalize();
  }

  @Override
  protected LedgerState<DamlLogEvent> messageFrameToLedgerState(final MessageFrame messageFrame) {
    final LedgerState<DamlLogEvent> ledgerState = new MutableAccountLedgerState(messageFrame);
    return ledgerState;
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
    final ExecutionContext ec = ExecutionContext.fromExecutor(ExecutionContext.global());
    final SubmissionValidator<DamlLogEvent> validator = SubmissionValidator.create(ledgerState, () -> {
      return logEntryId;
    }, false, Cache.none(), getEngine(), getMetricsRegistry(), ec);
    final com.daml.lf.data.Time.Timestamp recordTime = ledgerState.getRecordTime();
    final Future<Either<ValidationFailed, DamlLogEvent>> validateAndCommit = validator
        .validateAndCommit(tx.getSubmission(), correlationId, recordTime, participantId);
    final CompletionStage<Either<ValidationFailed, DamlLogEvent>> validateAndCommitCS = FutureConverters
        .toJava(validateAndCommit);
    try {
      final Either<ValidationFailed, DamlLogEvent> either = validateAndCommitCS.toCompletableFuture().get();
      if (either.isLeft()) {
        final ValidationFailed validationFailed = either.left().get();
        throw new InvalidTransactionException(validationFailed.toString());
      } else {
        final DamlLogEvent logEvent = either.right().get();
        LOG.debug("Processed transaction into log event {}",
            logEvent.getLogEntryId().toStringUtf8());
        return;
      }
    } catch (InterruptedException | ExecutionException e) {
      throw new InternalError(e.getMessage());
    }
  }
}
