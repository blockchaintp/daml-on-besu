package com.blockchaintp.besu.daml.rpc;

import java.io.IOException;
import java.util.List;

import com.daml.ledger.api.health.HealthStatus;
import com.daml.ledger.participant.state.v1.Offset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.datatypes.Event;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthLog.LogResult;

import akka.NotUsed;
import akka.stream.scaladsl.Source;
import io.reactivex.processors.UnicastProcessor;
import scala.Option;

/**
 * Abstract base class for all readers
 */
public abstract class AbstractJsonRpcReader<T> {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractJsonRpcReader.class);

  private final String jsonRpcUrl;

  private Web3j web3Connection;

  private final String ledgerId;

  private final String participantId;

  private static final Event DAML_LOG_EVENT = new Event("daml/log-event", List.of());

  protected AbstractJsonRpcReader(final String rpcUrl, final String configuredLedgerId, final String theParticipantId) {
    this.jsonRpcUrl = rpcUrl;
    this.ledgerId = configuredLedgerId;
    this.participantId = theParticipantId;
  }

  /**
   * Get the participantId.
   *
   * @return the participantId
   */
  public String getParticipantId() {
    return participantId;
  }

  public final HealthStatus currentHealth() {
    return HealthStatus.healthy();
  }

  public Source<T, NotUsed> events(final Option<Offset> beginAfter) {
    final Web3j web3 = connect();
    final DefaultBlockParameter earliestBlock = earliestBlockFromOffset(beginAfter);
    final UnicastProcessor<T> processor = UnicastProcessor.create();
    web3.replayPastAndFutureBlocksFlowable(earliestBlock, true).subscribe(block -> handleBlock(block, processor));
    return Source.fromPublisher(processor);
  }

  public final String ledgerId() {
    return this.ledgerId;
  }

  private Web3j connect() {
    if (this.web3Connection == null) {
      synchronized (this) {
        this.web3Connection = Web3jFactory.web3j(this.jsonRpcUrl);
      }
    }
    return this.web3Connection;
  }

  private DefaultBlockParameter earliestBlockFromOffset(final Option<Offset> beginAfter) {
    DefaultBlockParameter earliestBlock = DefaultBlockParameterName.EARLIEST;
    if (beginAfter.nonEmpty()) {
      final long[] offsetFields = Utils.fromOffset(beginAfter.get());
      if (offsetFields[0] >= 0) {
        final long startAt = offsetFields[0] + 1;
        LOG.info("Begin logs at {}", startAt);
        earliestBlock = new DefaultBlockParameterNumber(startAt);
      }
    } else {
      LOG.info("Begin logs at the beginning");
    }
    return earliestBlock;
  }

  @SuppressWarnings("rawtypes")
  protected void handleBlock(final EthBlock block, UnicastProcessor<T> processor) throws IOException {
    final Web3j web3 = connect();
    Web3Utils utils = new Web3Utils(web3);
    final List<LogResult> logs = utils.logsFromBlock(block, JsonRpcWriter.DAML_PUBLIC_ADDRESS, DAML_LOG_EVENT);
    if (!logs.isEmpty()) {
      LOG.debug("Handling {} logs from block {}", logs.size(), block.getBlock().getNumber());
      handleEthLogs(logs, processor);
    }
  }

  @SuppressWarnings("rawtypes")
  protected abstract void handleEthLogs(List<LogResult> logs, UnicastProcessor<T> processor);

}
