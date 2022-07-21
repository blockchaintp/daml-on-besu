package com.blockchaintp.besu.daml.rpc

import com.daml.metrics.MetricsReporter

import concurrent.duration.{Duration,  NANOSECONDS}
import scala.util.Try

object Metrics {
  type Setter[T, B] = (B => B, T) => T
  private case class DurationFormat(unwrap: Duration)

  // We're trying to parse the java duration first for backwards compatibility as
  // removing it and only supporting the scala duration variant would be a breaking change.
  private implicit val scoptDurationFormat: scopt.Read[DurationFormat] = scopt.Read.reads {
    duration =>
      Try {
        Duration.fromNanos(
          java.time.Duration.parse(duration).toNanos
        )
      }.orElse(Try {
        Duration(duration)
      }).flatMap(duration =>
        Try {
          if (!duration.isFinite)
            throw new IllegalArgumentException(s"Input duration $duration is not finite")
          else DurationFormat(Duration(duration.toNanos, NANOSECONDS))
        }
      ).get
  }

  def metricsReporterParse[C](parser: scopt.OptionParser[C])(
    metricsReporter: Setter[C, Option[MetricsReporter]],
    metricsReportingInterval: Setter[C, Duration],
  ): Unit = {
    import parser.opt

    opt[MetricsReporter]("metrics-reporter")
      .action((reporter, config) => metricsReporter(_ => Some(reporter), config))
      .optional()
      .text(s"Start a metrics reporter. ${MetricsReporter.cliHint}")

    opt[DurationFormat]("metrics-reporting-interval")
      .action((interval, config) => metricsReportingInterval(_ => interval.unwrap, config))
      .optional()
      .text("Set metric reporting interval.")

    ()
  }
}
