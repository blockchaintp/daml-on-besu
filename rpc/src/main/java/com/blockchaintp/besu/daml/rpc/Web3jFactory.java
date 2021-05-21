package com.blockchaintp.besu.daml.rpc;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.blockchaintp.besu.daml.exceptions.DamlBesuRuntimeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.websocket.WebSocketService;

public class Web3jFactory {

  public static final long DEFAULT_POLLING_INTERVAL = 1000L;

  private static final Logger LOG = LoggerFactory.getLogger(Web3jFactory.class);

  public static Web3j web3j(final String jsonRpcUrl) {
    return web3j(jsonRpcUrl, DEFAULT_POLLING_INTERVAL);
  }

  public static Web3j web3j(final String jsonRpcUrl, final long pollingMs) {
    return web3j(jsonRpcUrl, pollingMs, Executors.newScheduledThreadPool(1));
  }

  public static Web3j web3j(final String jsonRpcUrl, final long pollingMs, ScheduledExecutorService executorService) {
    Web3jService service = web3jService(jsonRpcUrl);
    return Web3j.build(service, pollingMs, executorService);
  }

  public static Web3jService web3jService(final String jsonRpcUrl) {
    LOG.info("Connecting to {}",jsonRpcUrl);
    try {
      Web3jService service;
      if (jsonRpcUrl.startsWith("ws://") || jsonRpcUrl.startsWith("wss://")) {
        final WebSocketService wsSrv = new WebSocketService(jsonRpcUrl, false);
        wsSrv.connect();
        service = wsSrv;
      } else {
        service = new HttpService(jsonRpcUrl, false);
      }
      return service;
    } catch (final IOException e) {
      throw new DamlBesuRuntimeException("Failed to connect to client node", e);
    }
  }

  private Web3jFactory() {
  }
}
