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

import com.blockchaintp.besu.daml.protobuf.DamlOperation;
import com.blockchaintp.besu.daml.protobuf.DamlOperationBatch;
import com.blockchaintp.besu.daml.protobuf.TimeKeeperUpdate;
import com.google.protobuf.util.Timestamps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.exceptions.ClientConnectionException;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DamlOperation submitter takes DamlOperations off of a queue and assembles
 * them into a batch for sending to the validator.
 */
public class DamlOperationSubmitter implements Submitter<DamlOperation> {

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

  public DamlOperationSubmitter(final String jsonRpcUrl, final String privateKey, final String participantId) {
    this(jsonRpcUrl, privateKey, participantId, false);
  }

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
        if (outstandingItem.size() == 0) {
          long startPoll = System.currentTimeMillis();
          DamlOperation op = submitQueue.poll(MAX_WAIT_MS, TimeUnit.MILLISECONDS);
          long timeSpent = System.currentTimeMillis() - startPoll;
          long nextPoll = Math.max(MAX_WAIT_MS - timeSpent, 1L);
          final DamlOperationBatch.Builder builder = DamlOperationBatch.newBuilder();
          boolean opsAdded = false;
          int opCounter = 0;
          while (op != null ) {
            builder.addOperations(op);
            opCounter++;
            if (opCounter > MAX_OPS_PER_BATCH){
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
            Duration res = Duration.between(lastInstantUpdate, now);
            if (res.compareTo(this.timeUpdateInterval) >= 0 ) {
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

          try  {
            final EthSendTransaction txResp = txRespCf.join();
            final String txHash = txResp.getTransactionHash();
            if (this.pessimisticNonce) {
              this.txReceiptProcessor.waitForTransactionReceipt(txHash);
              LOG.info("Sending item complete {}", batchCounter);
            }
          } catch ( CompletionException ce ) {
            if (ce.getCause() instanceof ClientConnectionException) {
              Integer errorCode = extractError(ce);
              if ( NONCE_TOO_LOW.equals(errorCode) ) {
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
        }
      } catch (final InterruptedException | IOException | TransactionException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private Integer extractError(CompletionException ce) throws JsonProcessingException, JsonMappingException {
    ClientConnectionException cce = (ClientConnectionException) ce.getCause();
    String message = cce.getMessage();

    String jsonPayload = message.substring(message.indexOf(';')+2);
    HashMap<String, Object> errorMap = new ObjectMapper().readValue(jsonPayload, HashMap.class);
    if (errorMap.containsKey("error")) {
      HashMap<String,Object> error = (HashMap<String, Object>) errorMap.get("error");
      Integer errorCode = Integer.parseInt(error.getOrDefault("code","0").toString());
      return errorCode;
    }
    return 0;
  }

  private DamlOperation sendTimeUpdate(final Instant now) {
    final com.google.protobuf.Timestamp nowMillis = Timestamps.fromMillis(now.toEpochMilli());
    final TimeKeeperUpdate tkUpdate = TimeKeeperUpdate.newBuilder().setTimeUpdate(nowMillis).build();
    final DamlOperation operation = DamlOperation.newBuilder().setSubmittingParticipant(participantId)
        .setTimeUpdate(tkUpdate).build();

    return operation;
  }

  protected Request<?, EthSendTransaction> createTxRequest(final Web3j web3, final DamlOperationBatch batch) {
    final Web3Utils utils = new Web3Utils(web3);
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
