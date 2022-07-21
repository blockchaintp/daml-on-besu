/*
 * Copyright 2021 Blockchain Technology Partners
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

import java.util.UUID;

import com.blockchaintp.besu.daml.protobuf.DamlOperation;
import com.blockchaintp.besu.daml.protobuf.DamlTransaction;
import com.daml.ledger.api.health.HealthStatus;
import com.daml.ledger.participant.state.kvutils.Raw;
import com.daml.ledger.participant.state.kvutils.api.CommitMetadata;
import com.daml.ledger.participant.state.kvutils.api.LedgerWriter;
import com.daml.ledger.participant.state.kvutils.store.DamlLogEntryId;
import com.daml.ledger.participant.state.v2.SubmissionResult;
import com.daml.telemetry.TelemetryContext;
import com.google.protobuf.ByteString;
import com.google.protobuf.any.Any;
import com.google.rpc.code.Code;
import com.google.rpc.status.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.collection.immutable.Seq;
import scala.concurrent.Future;

/**
 * Submits to a remote submission service.
 */
public final class JsonRpcWriter implements LedgerWriter {

  private static final Logger LOG = LoggerFactory.getLogger(JsonRpcWriter.class);

  /**
   * Public address prefix.
   */
  public static final String DAML_PUBLIC_ADDRESS = "0x" + String.format("%1$40s", "75").replace(' ', '0');
  private final String participantId;

  private final Submitter<DamlOperation> submitter;

  /**
   * Creates a JsonRpcWriter
   * 
   * @param configuredParticipantId
   *          the participantId
   * @param configuredUrl
   *          the url of the besu instance
   * @param cfgPrivateKey
   *          the private key of the participant
   */
  public JsonRpcWriter(final String configuredParticipantId, final String configuredUrl, final String cfgPrivateKey) {
    this.participantId = configuredParticipantId;
    this.submitter = new DamlOperationSubmitter(configuredUrl, cfgPrivateKey, this.participantId);
    Thread submitterThread = new Thread(submitter);
    submitterThread.start();
  }

  @Override
  public HealthStatus currentHealth() {
    return HealthStatus.healthy();
  }

  @Override
  public Future<SubmissionResult> commit(final String correlationId, final Raw.Envelope envelope,
      final CommitMetadata metadata, final TelemetryContext telemetryContext) {
    final DamlTransaction tx = DamlTransaction.newBuilder().setSubmission(envelope.bytes())
        .setLogEntryId(newLogEntryId()).build();
    final DamlOperation operation = DamlOperation.newBuilder().setCorrelationId(correlationId).setTransaction(tx)
        .setSubmittingParticipant(participantId()).build();

    try {
      LOG.debug("Submitting request");
      submitter.put(operation);
    } catch (final InterruptedException e) {
      LOG.warn("JsonRpcWriter interrupted while submitting a request");
      Thread.currentThread().interrupt();
      @SuppressWarnings("unchecked")
      final Seq<Any> noDetails = scala.collection.immutable.Seq$.MODULE$.<Any>empty();
      return Future
          .successful(new SubmissionResult.SynchronousError(Status.of(Code.INTERNAL$.MODULE$.value(),
          "JsonRpcWriter interrupted while submitting a request", noDetails)));
    }
    LOG.debug("Acknowledging Submission");
    return Future.successful(SubmissionResult.Acknowledged$.MODULE$);
  }

  @Override
  public String participantId() {
    return this.participantId;
  }

  private ByteString newLogEntryId() {
    return DamlLogEntryId.newBuilder().setEntryId(ByteString.copyFromUtf8(UUID.randomUUID().toString())).build()
        .toByteString();
  }
}
