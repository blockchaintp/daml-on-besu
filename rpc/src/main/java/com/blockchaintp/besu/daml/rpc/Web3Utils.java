package com.blockchaintp.besu.daml.rpc;

import java.io.IOException;
import java.math.BigInteger;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.blockchaintp.besu.daml.exceptions.DamlBesuRuntimeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.datatypes.Event;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.EthLog.LogResult;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.gas.StaticGasProvider;

public class Web3Utils {

  private static final Logger LOG = LoggerFactory.getLogger(Web3Utils.class);

  private static final int DEFAULT_WAIT_BEFORE_RETRY_SECONDS = 30;
  private static final int DEFAULT_MAX_RETRIES = -1;

  private int maxRetries;

  private int retryWaitTimeSeconds;

  private Web3j web3;

  private StaticGasProvider gasProvider;

  public Web3Utils(final Web3j web3) {
    setWeb3(web3);
    this.gasProvider = new StaticGasProvider(BigInteger.ZERO, BigInteger.valueOf(3000000));
    this.setMaxRetries(DEFAULT_MAX_RETRIES);
    this.setRetryWaitTimeSeconds(DEFAULT_WAIT_BEFORE_RETRY_SECONDS);
  }

  private int getMaxRetries() {
    return maxRetries;
  }

  private void setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
  }

  private int getRetryWaitTimeSeconds() {
    return retryWaitTimeSeconds;
  }

  private void setRetryWaitTimeSeconds(int retryWaitTimeSeconds) {
    this.retryWaitTimeSeconds = retryWaitTimeSeconds;
  }

  protected Web3Utils() {
    this.gasProvider = new StaticGasProvider(BigInteger.ZERO, BigInteger.valueOf(3000000));
  }

  protected void setWeb3(final Web3j web3) {
    this.web3 = web3;
  }

  @SuppressWarnings("rawtypes")
  public List<LogResult> logsFromBlock(final EthBlock block, final String address, final Event event)
      throws IOException {
    final BigInteger nextBlockNum = block.getBlock().getNumber();
    BigInteger prevBlockNum = BigInteger.valueOf(nextBlockNum.longValue());
    if (nextBlockNum.compareTo(BigInteger.ONE) < 0) {
      prevBlockNum = nextBlockNum;
    }
    LOG.trace("Checking block {} for DAML transactions", block.getBlock().getNumber());

    final EthFilter filter = new EthFilter(new DefaultBlockParameterNumber(prevBlockNum),
        new DefaultBlockParameterNumber(nextBlockNum), address);
    if (null != event) {
      filter.addSingleTopic(EventEncoder.encode(event));
    }
    final EthLog ethlog = web3.ethGetLogs(filter).send();
    final List<LogResult> logs = ethlog.getLogs();
    return logs;
  }

  public Request<?, EthSendTransaction> sendBytes(final Credentials credentials, final String to,
      final byte[] dataBytes) {
    final String data = Utils.bytesToHex(dataBytes);
    int tries = 0;
    RecoverableException lastException = null;
    while (getMaxRetries() < 0 || tries < getMaxRetries()) {
      try {
        return sendEncodedString(credentials, to, data);
      } catch (RecoverableException e) {
        lastException = e;
        tries++;
        LOG.warn("Received recoverable exception, sleeping for {}s before retry attempt={}}", getRetryWaitTimeSeconds(),
            tries);
        try {
          TimeUnit.SECONDS.sleep(getRetryWaitTimeSeconds());
        } catch (InterruptedException e1) {
          LOG.warn("Interrupted while asleep waiting to retry", e1);
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    if (null == lastException) {
      throw new DamlBesuRuntimeException(
          String.format("Exceeded maximum retries for request %s >= %s ", tries, getMaxRetries()));
    } else {
      throw new DamlBesuRuntimeException(String.format("Failed to transmit request after %s retries", tries),
          lastException.getCause());
    }
  }

  public Request<?, EthSendTransaction> sendEncodedString(Credentials credentials, String to, String data)
      throws RecoverableException {
    LOG.debug("Creating transaction");
    final BigInteger gasLimit = getGasLimit(data.getBytes());
    final BigInteger nonce = getNonce(credentials);
    final BigInteger gasPrice = getGasPrice();
    final long maxSpend = (1L + gasPrice.longValue() * gasLimit.longValue());
    LOG.debug("Address={} gasPrice={} gasLimit={} value={} max={} nonce={}", credentials.getAddress(), gasPrice,
        gasLimit, 1L, maxSpend, nonce);
    final RawTransaction rtx = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, to, data);

    LOG.debug("Signing transaction");
    final byte[] rtxSignedBytes = TransactionEncoder.signMessage(rtx, credentials);
    final String rtxSigned = Utils.bytesToHex(rtxSignedBytes);

    LOG.debug("Creating request");
    final Request<?, EthSendTransaction> ethSendTx = web3.ethSendRawTransaction(rtxSigned);
    return ethSendTx;
  }

  protected BigInteger getNonce(final Credentials credentials) throws RecoverableException {
    EthGetTransactionCount ethGetTransactionCount = null;
    try {
      ethGetTransactionCount = web3.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING)
          .sendAsync().get();
    } catch (ExecutionException e) {
      if (e.getCause() instanceof SocketTimeoutException) {
        throw new RecoverableException("Socket timeout received while fetching nonce", e);
      } else {
        LOG.error("Severe error getting transaction nonce", e);
        throw new DamlBesuRuntimeException("Severe error getting transaction nonce", e);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RecoverableException("Interrupted while getting nonce", e);
    }
    final BigInteger nonce = ethGetTransactionCount.getTransactionCount();
    final long val = nonce.longValue();

    return BigInteger.valueOf(val);
  }

  private BigInteger longTo16byteBigInt(final long val) {
    final ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
    bb.putLong(0L);
    bb.putLong(val);
    return new BigInteger(bb.array()).abs();
  }

  protected BigInteger getGasLimit(final byte[] data) {
    final long myLimit = 100L * (21_000L + (100L * data.length));
    final long defaultLimit = this.gasProvider.getGasLimit().longValue();
    LOG.debug("Gas Limits:  default = {} requestLimit = {}", defaultLimit, myLimit);
    final long gasLimit = Math.max(defaultLimit, myLimit);
    return longTo16byteBigInt(gasLimit);
  }

  protected BigInteger getGasPrice() {
    return this.gasProvider.getGasPrice();
  }

  private class RecoverableException extends Exception {

    public RecoverableException(String message, Throwable t) {
      super(message, t);
    }

  }
}
