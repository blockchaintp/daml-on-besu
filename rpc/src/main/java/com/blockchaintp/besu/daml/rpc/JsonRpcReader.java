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

import java.util.ArrayList;
import java.util.List;

import com.blockchaintp.besu.daml.exceptions.DamlBesuRuntimeException;
import com.blockchaintp.besu.daml.protobuf.DamlLogEvent;
import com.daml.ledger.participant.state.kvutils.OffsetBuilder;
import com.daml.ledger.participant.state.kvutils.Raw;
import com.daml.ledger.participant.state.kvutils.api.LedgerReader;
import com.daml.ledger.participant.state.kvutils.api.LedgerRecord;
import com.daml.ledger.participant.state.v1.Offset;
import com.google.protobuf.InvalidProtocolBufferException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.EthLog.LogResult;
import org.web3j.protocol.core.methods.response.Log;

import io.reactivex.processors.UnicastProcessor;

/**
 *
 */
public class JsonRpcReader extends AbstractJsonRpcReader<LedgerRecord> implements LedgerReader {

  private static final Logger LOG = LoggerFactory.getLogger(JsonRpcReader.class);

  /**
   *
   * @param rpcUrl
   * @param configuredLedgerId
   */
  public JsonRpcReader(final String rpcUrl, final String configuredLedgerId) {
    super(rpcUrl, configuredLedgerId, null);
  }

  @SuppressWarnings("rawtypes")
  protected final void handleEthLogs(final List<LogResult> logs, final UnicastProcessor<LedgerRecord> processor) {
    long logInBlockCtr = 0L;
    final List<LedgerRecord> updates = new ArrayList<>();
    for (final LogResult lr : logs) {
      if (lr instanceof EthLog.LogObject) {
        final Log log = ((EthLog.LogObject) lr).get();
        LOG.debug("Processing Log {} in block {}", logInBlockCtr, log.getBlockNumber());
        try {
          final List<LedgerRecord> lofLr = logToLogRecord(log, logInBlockCtr++);
          updates.addAll(lofLr);
        } catch (final InvalidProtocolBufferException e) {
          throw new DamlBesuRuntimeException("Failure parsing log information", e);
        }
      }
    }
    for (final LedgerRecord t : updates) {
      LOG.info("Sending update for offset {}", t.offset());
      processor.onNext(t);
    }
  }

  private List<LedgerRecord> logToLogRecord(final Log log, final long suboffset) throws InvalidProtocolBufferException {
    final long blockNum = log.getBlockNumber().longValue();
    final DamlLogEvent event = DamlLogEvent.parseFrom(Utils.hexToBytes(log.getData()));
    if (!event.hasTimeUpdate()) {
      final Offset ko = OffsetBuilder.fromLong(blockNum, (int) suboffset, 0);
      final LedgerRecord lr = new LedgerRecord(ko, Raw.LogEntryId$.MODULE$.apply(event.getLogEntryId()),
          Raw.Envelope$.MODULE$.apply(event.getLogEntry()));
      return List.of(lr);
    }
    return List.of();
  }
}
