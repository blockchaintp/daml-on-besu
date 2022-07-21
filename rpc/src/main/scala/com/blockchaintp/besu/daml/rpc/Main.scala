/*
 * Copyright 2020-2021 Blockchain Technology Partners
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
package com.blockchaintp.besu.daml.rpc

import java.nio.file.Paths
import java.time.Duration
import akka.stream.Materializer
import com.daml.ledger.api.auth.AuthService
import com.daml.ledger.api.auth.AuthServiceJWT
import com.daml.ledger.api.auth.AuthServiceWildcard
import com.daml.jwt.JwksVerifier
import com.daml.jwt.RSA256Verifier
import com.daml.ledger.configuration.Configuration
import com.daml.ledger.configuration.LedgerTimeModel
import com.daml.ledger.participant.state.kvutils.api.KeyValueLedger
import com.daml.ledger.participant.state.kvutils.api.KeyValueParticipantState
import com.daml.ledger.participant.state.kvutils.app.Config
import com.daml.ledger.participant.state.kvutils.app.LedgerFactory
import com.daml.ledger.participant.state.kvutils.app.ParticipantConfig
import com.daml.ledger.participant.state.kvutils.app.Runner
import com.daml.ledger.resources.ResourceContext
import com.daml.ledger.resources.ResourceOwner
import com.daml.lf.engine.Engine
import com.daml.logging.LoggingContext
import com.daml.platform.apiserver.ApiServerConfig
import com.daml.platform.configuration.InitialLedgerConfiguration
import com.daml.resources.FutureResourceOwner
import com.daml.resources.ProgramResource
import org.slf4j.event.Level
import org.web3j.protocol.http.HttpService

import scala.util.Try
import scopt.OptionParser

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object Main {

  def main(args: Array[String]): Unit = {
    val factory = new JsonRpcLedgerFactory()
    val runner  = new Runner("JsonRPC Ledger", factory).owner(args)
    new ProgramResource(runner).run(ResourceContext.apply)
  }

  class JsonRpcLedgerFactory() extends LedgerFactory[KeyValueParticipantState, ExtraConfig] {

    final override def readWriteServiceOwner(
        config: Config[ExtraConfig],
        participantConfig: ParticipantConfig,
        engine: Engine
    )(implicit
        materializer: Materializer,
        logCtx: LoggingContext
    ): ResourceOwner[KeyValueParticipantState] = {
      LogUtils.setRootLogLevel(config.extra.logLevel)
      LogUtils.setLogLevel(classOf[HttpService], Level.INFO.name())
      LogUtils.setLogLevel("org.flywaydb.core.internal", Level.INFO.name())
      val resourceContext = ResourceContext.apply(ExecutionContext.fromExecutor(Executors.newCachedThreadPool()));
      for {
        readerWriter <- owner(config, participantConfig, engine)(materializer, logCtx, resourceContext)
      } yield new KeyValueParticipantState(
        readerWriter,
        readerWriter,
        createMetrics(participantConfig, config),
        false
      )
    }

    def owner(config: Config[ExtraConfig], participantConfig: ParticipantConfig, engine: Engine)(implicit
        materializer: Materializer,
        logCtx: LoggingContext,
        context: ResourceContext
    ): ResourceOwner[KeyValueLedger] = {
      new FutureResourceOwner(() =>
        Future.successful(
          new JsonRpcReaderWriter(
            participantConfig.participantId,
            config.extra.privateKeyFile,
            config.extra.jsonRpcUrl,
            config.ledgerId
          )
        )
      )
    }

    override def apiServerConfig(
         participantConfig: ParticipantConfig,
         config: Config[ExtraConfig],
    ): ApiServerConfig =
      {
        val updatedConfig = config.copy(configurationLoadTimeout = Duration.ofSeconds(10))
        super.apiServerConfig(participantConfig, updatedConfig)
      }

    override def initialLedgerConfig(config: Config[ExtraConfig]): InitialLedgerConfiguration =
      InitialLedgerConfiguration(
        configuration = Configuration(
          generation = 1,
          timeModel = LedgerTimeModel(
            avgTransactionLatency = Duration.ofSeconds(1L),
            minSkew = Duration.ofSeconds(40L),
            maxSkew = Duration.ofSeconds(80L)
          ).get,
          maxDeduplicationTime = Duration.ofDays(1)
        ),
        delayBeforeSubmitting = Duration.ofSeconds(5)
      )

    override def authService(config: Config[ExtraConfig]): AuthService = {
      config.extra.authType match {
        case "none" => AuthServiceWildcard
        case "rsa256" =>
          val verifier = RSA256Verifier
            .fromCrtFile(config.extra.secret)
            .valueOr(err => sys.error(s"Failed to create RSA256 verifier for: $err"))
          AuthServiceJWT(verifier)
        case "jwks" =>
          val verifier = JwksVerifier(config.extra.jwksUrl)
          AuthServiceJWT(verifier)
      }
    }

    override val defaultExtraConfig: ExtraConfig = ExtraConfig.default

    private def validatePath(path: String, message: String): Either[String, Unit] = {
      val valid = Try(Paths.get(path).toFile.canRead).getOrElse(false)
      if (valid) Right(()) else Left(message)
    }


    final override def extraConfigParser(parser: OptionParser[Config[ExtraConfig]]): Unit = {
      parser
        .opt[String]("json-rpc-url")
        .optional()
        .text("URL of the daml-on-besu json-rpc to connect to")
        .action { case (v, config) =>
          config.copy(extra = config.extra.copy(jsonRpcUrl = v))
        }
      parser
        .opt[String]("logging")
        .optional()
        .text("Logging level, one of error|warn|info|debug|trace")
        .action { case (l, config) =>
          config.copy(extra = config.extra.copy(logLevel = l.toLowerCase()))
        }
      parser
        .opt[String]("auth-jwt-rs256-crt")
        .optional()
        .validate(
          validatePath(_, "The certificate file specified via --auth-jwt-rs256-crt does not exist")
        )
        .text(
          "Enables JWT-based authorization, where the JWT is signed by RSA256 with a public key loaded from the given X509 certificate file (.crt)"
        )
        .action { case (v, config) =>
          config.copy(
            extra = config.extra.copy(
              secret = v,
              authType = "rsa256"
            )
          )
        }
      parser
        .opt[String]("auth-jwt-rs256-jwks")
        .optional()
        .validate(v => Either.cond(v.length > 0, (), "JWK server URL must be a non-empty string"))
        .text(
          "Enables JWT-based authorization, where the JWT is signed by RSA256 with a public key loaded from the given JWKS URL"
        )
        .action { case (v, config) =>
          config.copy(
            extra = config.extra.copy(
              jwksUrl = v,
              authType = "jwks"
            )
          )
        }
      parser
        .opt[String]("private-key-file")
        .text("Absolute path of file containing Besu node private key")
        .action { case (f, config) =>
          config.copy(extra = config.extra.copy(privateKeyFile = f))
        }
      Metrics.metricsReporterParse(parser)(
        (f, c) => c.copy(metricsReporter = f(c.metricsReporter)),
        (f, c) => c.copy(metricsReportingInterval = (Duration.ofNanos(c.metricsReportingInterval.toNanos))),
      )

      ()
    }
  }
}
