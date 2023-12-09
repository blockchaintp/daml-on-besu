/*
 * Copyright © 2023 Paravela Limited
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.blockchaintp.besu.daml.exceptions.DamlBesuRuntimeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.websocket.WebSocketService;

/**
 *
 */
public final class Web3jFactory {

  private static final long DEFAULT_POLLING_INTERVAL = 1000L;

  private static final Logger LOG = LoggerFactory.getLogger(Web3jFactory.class);

  /**
   *
   * @param jsonRpcUrl
   * @return
   */
  public static Web3j web3j(final String jsonRpcUrl) {
    return web3j(jsonRpcUrl, DEFAULT_POLLING_INTERVAL);
  }

  /**
   *
   * @param jsonRpcUrl
   * @param pollingMs
   * @return
   */
  public static Web3j web3j(final String jsonRpcUrl, final long pollingMs) {
    return web3j(jsonRpcUrl, pollingMs, Executors.newScheduledThreadPool(1));
  }

  /**
   *
   * @param jsonRpcUrl
   * @param pollingMs
   * @param executorService
   * @return
   */
  public static Web3j web3j(final String jsonRpcUrl, final long pollingMs,
      final ScheduledExecutorService executorService) {
    Web3jService service = web3jService(jsonRpcUrl);
    return Web3j.build(service, pollingMs, executorService);
  }

  /**
   *
   * @param jsonRpcUrl
   * @return
   */
  public static Web3jService web3jService(final String jsonRpcUrl) {
    LOG.info("Connecting to {}", jsonRpcUrl);
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
