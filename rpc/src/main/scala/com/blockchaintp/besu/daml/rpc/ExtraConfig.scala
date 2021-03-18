// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.blockchaintp.besu.daml.rpc

final case class ExtraConfig(
    jsonRpcUrl: String,
    privateKeyFile: String,
    logLevel: String,
    authType: String,
    secret: String,
    jwksUrl: String
)

object ExtraConfig {
  val default =
    ExtraConfig(
      jsonRpcUrl = "http://localhost:8545",
      privateKeyFile = "/data/keys/key",
      logLevel = "info",
      authType = "none",
      secret = "",
      jwksUrl = ""
    )
}
