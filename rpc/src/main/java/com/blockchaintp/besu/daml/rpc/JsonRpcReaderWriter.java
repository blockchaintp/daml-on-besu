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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.daml.daml_lf_dev.DamlLf1;
import com.daml.ledger.api.health.HealthStatus;
import com.daml.ledger.participant.state.kvutils.Raw;
import com.daml.ledger.participant.state.kvutils.api.CommitMetadata;
import com.daml.ledger.participant.state.kvutils.api.LedgerReader;
import com.daml.ledger.participant.state.kvutils.api.LedgerRecord;
import com.daml.ledger.participant.state.kvutils.api.LedgerWriter;
import com.daml.ledger.participant.state.v1.Offset;
import com.daml.ledger.participant.state.v1.SubmissionResult;
import com.daml.ledger.resources.ResourceOwner;
import com.daml.lf.transaction.Transaction;
import com.daml.resources.AbstractResourceOwner;
import com.daml.resources.FutureResourceOwner;
import com.daml.resources.HasExecutionContext;
import com.daml.resources.PureResource$;
import com.daml.resources.Resource;
import com.daml.telemetry.TelemetryContext;
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
  public Future<SubmissionResult> commit(final String correlationId, final Raw.Envelope envelope,
      final CommitMetadata metadata, final TelemetryContext context) {
    return writer.commit(correlationId, envelope, metadata, context);
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
}
