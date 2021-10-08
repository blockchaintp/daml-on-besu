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

import java.io.IOException;
import java.math.BigInteger;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.blockchaintp.besu.daml.exceptions.DamlBesuRuntimeException;
import com.blockchaintp.besu.daml.exceptions.RecoverableException;

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
import org.web3j.protocol.core.methods.response.EthLog.LogResult;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.gas.StaticGasProvider;

/**
 *
 */
public final class Web3Utils {

  private static final Logger LOG = LoggerFactory.getLogger(Web3Utils.class);

  private static final int DEFAULT_WAIT_BEFORE_RETRY_SECONDS = 30;
  private static final int DEFAULT_MAX_RETRIES = -1;
  public static final int GAS_LIMIT = 3000000;

  private int maxRetries;

  private int retryWaitTimeSeconds;

  private Web3j web3;

  private final StaticGasProvider gasProvider;

  /**
   *
   * @param innerWeb3j
   */
  public Web3Utils(final Web3j innerWeb3j) {
    setWeb3(innerWeb3j);
    this.gasProvider = new StaticGasProvider(BigInteger.ZERO, BigInteger.valueOf(GAS_LIMIT));
    this.setMaxRetries(DEFAULT_MAX_RETRIES);
    this.setRetryWaitTimeSeconds(DEFAULT_WAIT_BEFORE_RETRY_SECONDS);
  }

  protected Web3Utils() {
    this.gasProvider = new StaticGasProvider(BigInteger.ZERO, BigInteger.valueOf(GAS_LIMIT));
    this.setMaxRetries(DEFAULT_MAX_RETRIES);
    this.setRetryWaitTimeSeconds(DEFAULT_WAIT_BEFORE_RETRY_SECONDS);
  }

  protected int getMaxRetries() {
    return maxRetries;
  }

  protected void setMaxRetries(final int theMaxRetries) {
    this.maxRetries = theMaxRetries;
  }

  protected int getRetryWaitTimeSeconds() {
    return retryWaitTimeSeconds;
  }

  protected void setRetryWaitTimeSeconds(final int theRetryWaitTimeSeconds) {
    this.retryWaitTimeSeconds = theRetryWaitTimeSeconds;
  }

  protected void setWeb3(final Web3j theWeb3) {
    this.web3 = theWeb3;
  }

  /**
   * Retrieve log entries from block and address.
   * 
   * @param block
   * @param address
   * @param event
   * @return
   * @throws IOException
   */
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
    final var ethlog = web3.ethGetLogs(filter).send();
    return ethlog.getLogs();
  }

  /**
   *
   * @param credentials
   * @param to
   * @param dataBytes
   * @return A request to send bytes.
   */
  @SuppressWarnings({ "java:S1452" })
  public Request<?, EthSendTransaction> sendBytes(final Credentials credentials, final String to,
      final byte[] dataBytes) {
    final String data = Utils.bytesToHex(dataBytes);
    return sendBytes(credentials, to, data);
  }

  /**
   *
   * @param credentials
   * @param to
   * @param data
   * @return A request to send the specified transaction.
   */
  @SuppressWarnings({ "java:S1452", "java:S2583" })
  public Request<?, EthSendTransaction> sendBytes(final Credentials credentials, final String to, final String data) {
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
    if (lastException != null) {
      throw new DamlBesuRuntimeException(String.format("Failed to transmit request after %s retries", tries),
          lastException.getCause());
    } else {
      throw new DamlBesuRuntimeException(
          String.format("Exceeded maximum retries for request %s >= %s ", tries, getMaxRetries()));
    }
  }

  /**
   *
   * @param credentials
   * @param to
   * @param data
   * @return A request to send a raw transaction encoded as a string.
   * @throws RecoverableException
   */
  @SuppressWarnings("java:S1452")
  public Request<?, EthSendTransaction> sendEncodedString(final Credentials credentials, final String to,
      final String data) throws RecoverableException {
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
    return web3.ethSendRawTransaction(rtxSigned);
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
}
