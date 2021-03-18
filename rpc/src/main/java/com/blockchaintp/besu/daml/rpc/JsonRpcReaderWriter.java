package com.blockchaintp.besu.daml.rpc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.daml.ledger.api.health.HealthStatus;
import com.daml.ledger.participant.state.kvutils.api.LedgerReader;
import com.daml.ledger.participant.state.kvutils.api.LedgerRecord;
import com.daml.ledger.participant.state.kvutils.api.LedgerWriter;
import com.daml.ledger.participant.state.v1.Offset;
import com.daml.ledger.participant.state.v1.SubmissionResult;
import com.daml.resources.Resource;
import com.daml.resources.ResourceOwner;
import com.google.protobuf.ByteString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.NotUsed;
import akka.stream.scaladsl.Source;
import scala.Option;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

public class JsonRpcReaderWriter implements LedgerReader, LedgerWriter {
  private static final Logger LOG = LoggerFactory.getLogger(JsonRpcWriter.class);

  private final String privateKey;

  final JsonRpcReader reader;
  final JsonRpcWriter writer;

  public JsonRpcReaderWriter(final String participantId, final String privateKeyFile, final String jsonRpcUrl,
      final String ledgerId) {
    this.privateKey = readBesuPrivateKeyFromFile(privateKeyFile);
    reader = new JsonRpcReader(jsonRpcUrl, ledgerId);
    writer = new JsonRpcWriter(participantId, jsonRpcUrl, this.privateKey);
  }

  private String readBesuPrivateKeyFromFile(final String privateKeyFile) {
    final File keyFile = new File(privateKeyFile);
    try {
      return new String(Files.readAllBytes(keyFile.toPath()));
    } catch (final IOException e) {
      LOG.error("Failed to read private key file {}", keyFile.toPath(), e);
      return "0x";
    }
  }

  @Override
  public HealthStatus currentHealth() {
    return reader.currentHealth();
  }

  @Override
  public Future<SubmissionResult> commit(final String correlationId, final ByteString envelope) {
    return writer.commit(correlationId, envelope);
  }

  @Override
  public String participantId() {
    return writer.participantId();
  }

  @Override
  public Source<LedgerRecord, NotUsed> events(final Option<Offset> startExclusive) {
    return reader.events(startExclusive);
  }

  @Override
  public String ledgerId() {
    return reader.ledgerId();
  }

  public static class Owner implements ResourceOwner<JsonRpcReaderWriter> {

    private final String participantId;
    private final String privateKeyFile;
    private final String jsonRpcUrl;
    private final String ledgerId;

    public Owner(final String configuredParticipantId, final String privateKeyFile, final String jsonRpcUrl,
        final String ledgerId) {
      this.participantId = configuredParticipantId;
      this.privateKeyFile = privateKeyFile;
      this.jsonRpcUrl = jsonRpcUrl;
      this.ledgerId = ledgerId;

    }

    @Override
    public Resource<JsonRpcReaderWriter> acquire(final ExecutionContext executionContext) {
      return Resource.successful(
          new JsonRpcReaderWriter(this.participantId, this.privateKeyFile, this.jsonRpcUrl, this.ledgerId),
          executionContext);
    }

  }
}
