package org.scalamsi

import cats.effect._
import cats.implicits._
import fs2.Stream
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import eu.timepit.refined.auto._
import com.typesafe.scalalogging.StrictLogging
import scala.concurrent.ExecutionContext.Implicits.global

object Main {

  lazy val (server, jdbc, _) =
    AppConfig
      .load()
      .fold(e => sys.error(s"Failed to load configuration:\n${e.toList.mkString("\n")}"), identity)

  implicit val timer: Timer[IO] = IO.timer(global)
  implicit val cs: ContextShift[IO] = IO.contextShift(global)

  def stream[F[_]: ConcurrentEffect: ContextShift: Timer]: Stream[F, ExitCode] = {
    val module = new Module[F](jdbc)

    Stream.eval(module.createDatabase()) *> {

      val httpApp = module.routes.orNotFound
      val finalHttpApp = Logger.httpApp(logHeaders = true, logBody = true)(httpApp)

      BlazeServerBuilder[F]
        .bindHttp(server.port.value, server.host.value)
        .withHttpApp(finalHttpApp)
        .serve
    }
  }
}
