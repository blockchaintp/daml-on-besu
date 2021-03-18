package com.blockchaintp.besu.daml.rpc;

import java.util.ArrayList;
import java.util.List;

import com.blockchaintp.besu.daml.protobuf.DamlLogEvent;
import com.daml.ledger.participant.state.kvutils.OffsetBuilder;
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

public class JsonRpcReader extends AbstractJsonRpcReader<LedgerRecord> implements LedgerReader {

  private static final Logger LOG = LoggerFactory.getLogger(JsonRpcReader.class);

  public JsonRpcReader(final String rpcUrl, final String configuredLedgerId) {
    super(rpcUrl, configuredLedgerId, null);
  }

  @SuppressWarnings("rawtypes")
  protected final void handleEthLogs(List<LogResult> logs, UnicastProcessor<LedgerRecord> processor) {
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
          LOG.error("Failure parsing log information", e);
          throw new RuntimeException(e);
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
      final LedgerRecord lr = new LedgerRecord(ko, event.getLogEntryId(), event.getLogEntry());
      return List.of(lr);
    }
    return List.of();
  }
}
