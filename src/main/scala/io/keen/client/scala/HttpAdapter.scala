package io.keen.client.scala

import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import grizzled.slf4j.Logging
import spray.can.Http
import spray.client.pipelining._
import spray.http._
import spray.http.Uri.{Path, Query}
import spray.httpx.encoding.{Gzip, Deflate}

trait HttpAdapterComponent {
  val httpAdapter: HttpAdapter
}

trait HttpAdapter {
  def doRequest(
    path: String,
    method: String,
    key: String,
    body: Option[String] = None,
    params: Map[String, Option[String]] = Map.empty): Future[Response]

  def shutdown: Unit
}

class SprayHttpAdapter(host: String, port: Int = 443) extends HttpAdapter with Logging {

  implicit val system = ActorSystem()
  import system.dispatcher             // execution context for futures
  implicit val timeout = Timeout(10)   // TODO: configurable

  private val connectionSetup =
    Http.HostConnectorSetup(host, port = port, sslEncryption = true)

  /**
   * A Spray-managed connection pool for servicing requests to our host.
   */
  private def hostPipeline: Future[SendReceive] =
    for (
      Http.HostConnectorInfo(connector, _) <- IO(Http) ? connectionSetup
    ) yield sendReceive(connector)

  // private def connection: Future[ActorRef] =
  //   for (
  //     Http.HostConnectorInfo(connector, _) <- IO(Http) ? connectionSetup
  //   ) yield connector

  // TODO: get rid of the Await choke point!
  // error handling for host connection pool unavailable?
  import scala.concurrent.duration._
  private def reqPipeline(authKey: String): SendReceive = (
    addHeader("Authorization", authKey)
    ~> encode(Gzip)
    ~> scala.concurrent.Await.result(hostPipeline, 1.second)
    // ~> ((req: HttpRequest) => connection.flatMap { sendReceive(_).apply(req) })
    ~> decode(Deflate)
  )

  /**
   * Perform the request with some debugging for good measure.
   */
  def doRequest(
    path: String,
    method: String,
    key: String,
    body: Option[String] = None,
    params: Map[String,Option[String]] = Map.empty): Future[Response] = {

    // Turn a map of string,opt[string] into a map of string,string which is
    // what Query wants
    val filteredParams = params.filter(
      // Filter out keys that are None
      _._2.isDefined
    ).map(
      // Convert the remaining tuples to str,str
      param => (param._1 -> param._2.get)
    )

    val uri = Uri(path = Path("/" + path), query = Query(filteredParams))

    val request: HttpRequest = method match {
      case "DELETE" => Delete(uri, body)
      case "GET" => Get(uri, body)
      case "POST" => Post(uri, body)
      case _ => throw new IllegalArgumentException("Unknown HTTP method: " + method)
    }

    debug("%s: %s".format(method, path))

    reqPipeline(key)(request).mapTo[HttpResponse].map { res =>
      Response(res.status.intValue, res.entity.asString)
    }
  }

  def shutdown {
    Http.CloseAll
  }
}
