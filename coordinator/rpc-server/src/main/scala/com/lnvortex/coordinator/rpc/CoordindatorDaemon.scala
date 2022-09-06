package com.lnvortex.coordinator.rpc

import akka.actor.ActorSystem
import com.lnvortex.coordinator.config.LnVortexRpcServerConfig
import com.lnvortex.server.config.VortexCoordinatorAppConfig
import com.lnvortex.server.coordinator.VortexCoordinator
import com.lnvortex.server.networking.VortexHttpServer
import grizzled.slf4j.Logging

import scala.concurrent._
import scala.concurrent.duration.DurationInt
import scala.util.Random

object CoordindatorDaemon extends App with Logging {

  val serverArgParser = new ServerArgParser(args.toVector)

  System.setProperty(
    "bitcoins.log.location",
    LnVortexRpcServerConfig.DEFAULT_DATADIR.toAbsolutePath.toString)

  val randomStr = 0.until(5).map(_ => Random.alphanumeric.head).mkString

  implicit val system: ActorSystem = ActorSystem(
    s"ln-vortex-coordinator-${System.currentTimeMillis()}-$randomStr")
  implicit val ec: ExecutionContext = system.dispatcher

  implicit val config: LnVortexAppConfig = serverArgParser.datadirOpt match {
    case Some(datadir) =>
      LnVortexAppConfig.fromDatadir(datadir, Vector(serverArgParser.toConfig))
    case None =>
      LnVortexAppConfig.fromDefaultDatadir(Vector(serverArgParser.toConfig))
  }

  implicit val serverConfig: LnVortexRpcServerConfig =
    config.rpcConfig

  implicit val coordinatorConfig: VortexCoordinatorAppConfig =
    config.coordinatorConfig

  logger.info("Starting...")

  val f = for {
    _ <- config.start()
    _ <- serverConfig.start()
    coordinator <- VortexCoordinator.initialize(config.bitcoind)

    httpServer = new VortexHttpServer(coordinator)
    _ <- httpServer.start()

    routes = LnVortexRoutes(httpServer)
    server = RpcServer(
      handlers = Vector(routes),
      rpcBindOpt = serverConfig.rpcBind,
      rpcPort = serverConfig.rpcPort,
      rpcUser = serverConfig.rpcUsername,
      rpcPassword = serverConfig.rpcPassword
    )

    _ = logger.info("Starting rpc server")
    _ <- server.start()
    _ = logger.info("Ln Vortex Coordinator started!")
  } yield {
    sys.addShutdownHook {
      logger.info("Shutting down...")
      val f = httpServer.stop().map { _ =>
        system.terminate()
        logger.info("Shutdown complete")
      }

      Await.result(f, 60.seconds)
    }
  }

  f.failed.foreach { ex =>
    ex.printStackTrace()
    logger.error("Error", ex)
  }
}