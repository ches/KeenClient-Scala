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
  type Params = Map[String, Option[String]]
  type Body = Option[String]

  def get(path: String, authKey: String, body: Body = None, params: Params = Map.empty): Future[Response]

  def post(path: String, authKey: String, body: Body = None, params: Params = Map.empty): Future[Response]

  def delete(path: String, authKey: String, body: Body = None, params: Params = Map.empty): Future[Response]

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

  def get(path: String, authKey: String, body: Body = None, params: Params = Map.empty): Future[Response] =
    makeRequest(Get, path, authKey, body, params)

  def post(path: String, authKey: String, body: Body = None, params: Params = Map.empty): Future[Response] =
    makeRequest(Post, path, authKey, body, params)

  def delete(path: String, authKey: String, body: Body = None, params: Params = Map.empty): Future[Response] =
    makeRequest(Delete, path, authKey, body, params)

  /**
   * Perform the request, with some debugging for good measure.
   */
  private def makeRequest(
    method: RequestBuilder,
    path: String,
    authKey: String,
    body: Body = None,
    params: Params = Map.empty): Future[Response] = {

    val uri = Uri(path = Path("/" + path), query = params2query(params))
    val request = method(uri, body)

    debug(s"${method.method}: $path")

    reqPipeline(authKey)(request).mapTo[HttpResponse].map { res =>
      Response(res.status.intValue, res.entity.asString)
    }
  }

  // Remove possible None values which Query.apply will not accept
  private def params2query(params: Params): Query = {
    val withValue = params.collect { case (key, Some(value)) => (key, value) }.toMap
    Query(withValue)
  }

  def shutdown {
    Http.CloseAll
  }
}

