package com.lnvortex.core

import akka.actor.ActorSystem
import com.typesafe.config.Config
import grizzled.slf4j.Logging
import org.bitcoins.commons.config.AppConfigFactoryBase
import org.bitcoins.core.currency.Satoshis
import org.bitcoins.core.util.FutureUtil
import org.bitcoins.db._
import org.bitcoins.tor.config.TorAppConfig
import org.bitcoins.tor.{Socks5ProxyParams, TorParams}

import java.net.{InetSocketAddress, URI}
import java.nio.file.{Path, Paths}
import java.time.Duration
import scala.concurrent._
import scala.util.Properties

/** Configuration for Ln Vortex
  *
  * @param directory The data directory of the wallet
  * @param conf      Optional sequence of configuration overrides
  */
case class VortexAppConfig(
    private val directory: Path,
    private val conf: Config*)(implicit system: ActorSystem)
    extends DbAppConfig
    with JdbcProfileComponent[VortexAppConfig]
    with DbManagement
    with Logging {
  import system.dispatcher

  override val configOverrides: List[Config] = conf.toList
  override val moduleName: String = VortexAppConfig.moduleName
  override type ConfigType = VortexAppConfig

  override val appConfig: VortexAppConfig = this

  import profile.api._

  override def newConfigOfType(configs: Seq[Config]): VortexAppConfig =
    VortexAppConfig(directory, configs: _*)

  override val baseDatadir: Path = directory

  override def start(): Future[Unit] = FutureUtil.unit

  override def stop(): Future[Unit] = Future.unit

  lazy val torConf: TorAppConfig =
    TorAppConfig(directory, conf: _*)

  lazy val socks5ProxyParams: Option[Socks5ProxyParams] =
    torConf.socks5ProxyParams

  lazy val torParams: Option[TorParams] = torConf.torParams

  lazy val listenAddress: InetSocketAddress = {
    val str = config.getString(s"$moduleName.listen")
    val uri = new URI("tcp://" + str)
    new InetSocketAddress(uri.getHost, uri.getPort)
  }

  lazy val coordinatorAddress: InetSocketAddress = {
    val str = config.getString(s"$moduleName.coordinator")
    val uri = new URI("tcp://" + str)
    new InetSocketAddress(uri.getHost, uri.getPort)
  }

  lazy val mixFee: Satoshis = {
    val long = config.getLong(s"$moduleName.mixFee")
    Satoshis(long)
  }

  lazy val interval: Duration = {
    config.getDuration(s"$moduleName.mixInterval")
  }

  override lazy val allTables: List[TableQuery[Table[_]]] = List.empty
}

object VortexAppConfig
    extends AppConfigFactoryBase[VortexAppConfig, ActorSystem] {
  override val moduleName: String = "vortex"

  val DEFAULT_DATADIR: Path = Paths.get(Properties.userHome, ".ln-vortex")

  override def fromDefaultDatadir(confs: Vector[Config] = Vector.empty)(implicit
      ec: ActorSystem): VortexAppConfig = {
    fromDatadir(DEFAULT_DATADIR, confs)
  }

  override def fromDatadir(datadir: Path, confs: Vector[Config])(implicit
      ec: ActorSystem): VortexAppConfig =
    VortexAppConfig(datadir, confs: _*)
}
