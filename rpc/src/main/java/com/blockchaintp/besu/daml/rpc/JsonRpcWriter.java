package com.blockchaintp.besu.daml.rpc;

import java.util.UUID;

import com.blockchaintp.besu.daml.protobuf.DamlOperation;
import com.blockchaintp.besu.daml.protobuf.DamlTransaction;
import com.daml.ledger.api.health.HealthStatus;
import com.daml.ledger.participant.state.kvutils.DamlKvutils.DamlLogEntryId;
import com.daml.ledger.participant.state.kvutils.api.LedgerWriter;
import com.daml.ledger.participant.state.v1.SubmissionResult;
import com.google.protobuf.ByteString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.concurrent.Future;

public class JsonRpcWriter implements LedgerWriter {

  private static final Logger LOG = LoggerFactory.getLogger(JsonRpcWriter.class);

  public static final String DAML_PUBLIC_ADDRESS =
      "0x" + String.format("%1$40s", "75").replace(' ', '0');
  private final String participantId;

  private final String jsonRpcUrl;

  private final String privateKey;

  private final Submitter<DamlOperation> submitter;

  private final Thread submitterThread;

  /**
   * Creates a JsonRpcWriter
   * @param configuredParticipantId the participantId
   * @param configuredUrl the url of the besu instance
   * @param cfgPrivateKey the private key of the participant
   */
  public JsonRpcWriter(final String configuredParticipantId, final String configuredUrl, final String cfgPrivateKey) {
    this.privateKey = cfgPrivateKey;
    this.participantId = configuredParticipantId;
    this.jsonRpcUrl = configuredUrl;
    this.submitter = new DamlOperationSubmitter(this.jsonRpcUrl, this.privateKey, this.participantId);
    this.submitterThread = new Thread(submitter);
    this.submitterThread.start();
  }

  @Override
  public HealthStatus currentHealth() {
    return HealthStatus.healthy();
  }

  @Override
  public Future<SubmissionResult> commit(final String correlationId, final ByteString envelope) {
    final DamlTransaction tx =
        DamlTransaction.newBuilder().setSubmission(envelope).setLogEntryId(newLogEntryId()).build();
    final DamlOperation operation = DamlOperation.newBuilder().setCorrelationId(correlationId)
        .setTransaction(tx).setSubmittingParticipant(participantId()).build();

    try {
      LOG.debug("Submitting request");
      submitter.put(operation);
    } catch (final InterruptedException e) {
      LOG.warn("JsonRpcWriter interrupted while submitting a request");
      Thread.currentThread().interrupt();
      return Future.successful(new SubmissionResult.InternalError("JsonRpcWriter interrupted while submitting a request"));
    }
    LOG.debug("Acknowledging Submission");
    return Future.successful(SubmissionResult.Acknowledged$.MODULE$);
  }

  @Override
  public String participantId() {
    return this.participantId;
  }

  private ByteString newLogEntryId() {
    return DamlLogEntryId.newBuilder()
        .setEntryId(ByteString.copyFromUtf8(UUID.randomUUID().toString())).build().toByteString();
  }
}
