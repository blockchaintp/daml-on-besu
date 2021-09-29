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
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import com.blockchaintp.besu.daml.exceptions.DamlBesuRuntimeException;
import com.blockchaintp.besu.daml.protobuf.DamlOperation;
import com.blockchaintp.besu.daml.protobuf.DamlOperationBatch;
import com.blockchaintp.besu.daml.protobuf.TimeKeeperUpdate;
import com.google.protobuf.util.Timestamps;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.exceptions.ClientConnectionException;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;

/**
 * DamlOperation submitter takes DamlOperations off of a queue and assembles them into a batch for
 * sending to the validator.
 */
public final class DamlOperationSubmitter implements Submitter<DamlOperation> {

  private static final long MAX_WAIT_MS = 2000L;

  private static final Logger LOG = LoggerFactory.getLogger(DamlOperationSubmitter.class);

  private static final int MAX_OPS_PER_BATCH = 100;

  private static final long DEFAULT_TIME_UPDATE_MAX_INTERVAL_SECONDS = 20;

  private static final Integer NONCE_TOO_LOW = -32001;

  private final Duration timeUpdateInterval;

  private final PollingTransactionReceiptProcessor txReceiptProcessor;
  private final String privateKey;
  private final String participantId;

  private final BlockingDeque<DamlOperation> submitQueue;
  private boolean keepRunning = true;
  private Web3j web3;
  private Credentials credentials;

  private final boolean pessimisticNonce;

  /**
   *
   * @param jsonRpcUrl
   * @param privateKey
   * @param participantId
   */
  public DamlOperationSubmitter(final String jsonRpcUrl, final String privateKey, final String participantId) {
    this(jsonRpcUrl, privateKey, participantId, false);
  }

  /**
   *
   * @param jsonRpcUrl
   * @param privateKey
   * @param participantId
   * @param pessimisticNonce
   */
  public DamlOperationSubmitter(final String jsonRpcUrl, final String privateKey, final String participantId,
      final boolean pessimisticNonce) {
    this.timeUpdateInterval = Duration.ofSeconds(DEFAULT_TIME_UPDATE_MAX_INTERVAL_SECONDS);
    this.pessimisticNonce = pessimisticNonce;
    this.submitQueue = new LinkedBlockingDeque<>();
    this.privateKey = privateKey;
    this.participantId = participantId;
    connect(jsonRpcUrl);
    this.txReceiptProcessor = new PollingTransactionReceiptProcessor(this.web3, 1_000L, 120);
  }

  @Override
  public void setKeepRunning(final boolean running) {
    this.keepRunning = running;
  }

  @Override
  public void put(final DamlOperation operation) throws InterruptedException {
    this.submitQueue.put(operation);
  }

  @Override
  public void run() {
    Instant lastInstantUpdate = null;
    long batchCounter = 0;
    Queue<CompletableFuture<EthSendTransaction>> outstandingItem = new LinkedList<>();
    Queue<DamlOperationBatch> submittedBatches = new LinkedList<>();
    while (keepRunning) {
      try {
        if (outstandingItem.isEmpty()) {
          long startPoll = System.currentTimeMillis();
          DamlOperation op = submitQueue.poll(MAX_WAIT_MS, TimeUnit.MILLISECONDS);
          long timeSpent = System.currentTimeMillis() - startPoll;
          long nextPoll = Math.max(MAX_WAIT_MS - timeSpent, 1L);
          final DamlOperationBatch.Builder builder = DamlOperationBatch.newBuilder();
          var opsAdded = false;
          var opCounter = 0;
          while (op != null) {
            builder.addOperations(op);
            opCounter++;
            if (opCounter > MAX_OPS_PER_BATCH) {
              break;
            }
            opsAdded = true;
            startPoll = System.currentTimeMillis();
            op = submitQueue.poll(nextPoll, TimeUnit.MILLISECONDS);
            timeSpent = System.currentTimeMillis() - startPoll;
            nextPoll = Math.max(nextPoll - timeSpent, 1L);
          }

          final Instant now = Instant.now();
          if (lastInstantUpdate == null || opsAdded) {
            // send time update
            // update lastInstantUpdate
            final DamlOperation tUpdate = sendTimeUpdate(now);
            builder.addOperations(tUpdate);
            opCounter++;
            lastInstantUpdate = now;
            opsAdded = true;
          } else if (lastInstantUpdate != null) {
            var res = Duration.between(lastInstantUpdate, now);
            if (res.compareTo(this.timeUpdateInterval) >= 0) {
              final DamlOperation tUpdate = sendTimeUpdate(now);
              builder.addOperations(tUpdate);
              opCounter++;
              lastInstantUpdate = now;
              opsAdded = true;
            }
          }

          if (opsAdded) {
            final DamlOperationBatch batch = builder.build();
            LOG.info("Sending batch {} opCount={}", batchCounter, opCounter);
            final Request<?, EthSendTransaction> txRequest = createTxRequest(web3, batch);
            submittedBatches.add(batch);
            outstandingItem.add(txRequest.sendAsync());
            batchCounter++;
          }
        } else {
          final DamlOperationBatch txBatch = submittedBatches.remove();
          final CompletableFuture<EthSendTransaction> txRespCf = outstandingItem.remove();

          batchCounter += waitForSubmissionComplete(outstandingItem, submittedBatches, txBatch, txRespCf);
        }
      } catch (final InterruptedException e) {
        LOG.warn("Operation submitter thread interrupted", e);
        Thread.currentThread().interrupt();
      } catch (IOException | TransactionException e) {
        throw new DamlBesuRuntimeException("Unhandled exception, exiting thread", e);
      }
    }
  }

  private long waitForSubmissionComplete(final Queue<CompletableFuture<EthSendTransaction>> outstandingItem,
      final Queue<DamlOperationBatch> submittedBatches, final DamlOperationBatch txBatch,
      final CompletableFuture<EthSendTransaction> txRespCf)
      throws IOException, TransactionException, JsonProcessingException, JsonMappingException {
    long batchCounter = 0;
    try {
      final EthSendTransaction txResp = txRespCf.join();
      final String txHash = txResp.getTransactionHash();
      if (this.pessimisticNonce) {
        this.txReceiptProcessor.waitForTransactionReceipt(txHash);
        LOG.info("Sending item complete {}", batchCounter);
      }
    } catch (CompletionException ce) {
      if (ce.getCause() instanceof ClientConnectionException) {
        Integer errorCode = extractError(ce);
        if (NONCE_TOO_LOW.equals(errorCode)) {
          LOG.warn("Received nonce too low exception, resubmitted batch {}", batchCounter);
          final Request<?, EthSendTransaction> txRequest = createTxRequest(web3, txBatch);
          submittedBatches.add(txBatch);
          outstandingItem.add(txRequest.sendAsync());
          batchCounter++;
        } else {
          LOG.warn(String.format("Unhandled errorCode = %s at batch %s", errorCode, batchCounter), ce.getCause());
        }
      }
    }
    return batchCounter;
  }

  @SuppressWarnings("all")
  private Integer extractError(CompletionException ce) throws JsonProcessingException, JsonMappingException {
    ClientConnectionException cce = (ClientConnectionException) ce.getCause();
    String message = cce.getMessage();

    String jsonPayload = message.substring(message.indexOf(';') + 2);
    HashMap<String, Object> errorMap = new ObjectMapper().readValue(jsonPayload, HashMap.class);
    if (errorMap.containsKey("error")) {
      HashMap<String, Object> error = (HashMap<String, Object>) errorMap.get("error");
      return Integer.parseInt(error.getOrDefault("code", "0").toString());
    }
    return 0;
  }

  private DamlOperation sendTimeUpdate(final Instant now) {
    final com.google.protobuf.Timestamp nowMillis = Timestamps.fromMillis(now.toEpochMilli());
    final TimeKeeperUpdate tkUpdate = TimeKeeperUpdate.newBuilder().setTimeUpdate(nowMillis).build();
    return DamlOperation.newBuilder().setSubmittingParticipant(participantId).setTimeUpdate(tkUpdate).build();
  }

  @SuppressWarnings("java:S1452")
  protected Request<?, EthSendTransaction> createTxRequest(final Web3j web3Inner, final DamlOperationBatch batch) {
    final Web3Utils utils = new Web3Utils(web3Inner);
    return utils.sendBytes(getCredentials(), JsonRpcWriter.DAML_PUBLIC_ADDRESS, batch.toByteArray());
  }

  protected Credentials getCredentials() {
    if (credentials == null) {
      synchronized (this) {
        credentials = Credentials.create(privateKey);
      }
    }
    return credentials;
  }

  private Web3j connect(final String rpcUrl) {
    if (this.web3 == null) {
      synchronized (this) {
        this.web3 = Web3jFactory.web3j(rpcUrl);
      }
    }
    return this.web3;
  }

  protected void setWeb3(final Web3j web3j) {
    this.web3 = web3j;
  }

}
