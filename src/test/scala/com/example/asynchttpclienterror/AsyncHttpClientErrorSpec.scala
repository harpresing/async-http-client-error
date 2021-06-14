package com.example.asynchttpclienterror

import cats.effect.{ContextShift, IO, Resource}
import cats.implicits.catsSyntaxApplicativeId
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, urlPathEqualTo}
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.client.asynchttpclient.AsyncHttpClient
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s._
import org.log4s.{Logger, getLogger}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global


class AsyncHttpClientErrorSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  val wiremockServer: WireMockServer = new WireMockServer(1234)

  override def beforeAll(): Unit = {
    wiremockServer.start()
  }

  override def afterAll(): Unit = {
    wiremockServer.stop()
  }

  wiremockServer.stubFor(
    post(urlPathEqualTo("/user"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody("""{"name": "John Doe"}""")
      )
  )

  case class User(name: String)

  val log: Logger = getLogger

  def logResponse(response: Response[IO]): IO[Unit] =
    response.bodyText.compile.foldMonoid map {
      r => log.info(s"resp: $r")
    }

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  def blazeHttpClient: Resource[IO, Client[IO]] = BlazeClientBuilder[IO](global).resource

  def asyncHttpClient: Resource[IO, Client[IO]] =
    AsyncHttpClient.resource[IO](new DefaultAsyncHttpClientConfig.Builder().build())

  /**
    * This calls the method logResponse twice which blows up the AsyncHttpClient but works
    * with the BlazeHttpClient
    */
  def makeRequest[A](client: Client[IO], request: Request[IO])(implicit decoder: EntityDecoder[IO, A]): IO[A] =
    client
      .run(request)
      .use { resp =>
        logResponse(resp) *> logResponse(resp) *> resp.as[A]
      }

  def testRequest(client: Client[IO]): IO[User] =
    for {
      uri     <- IO.fromEither(Uri.fromString("http://localhost:1234/user"))
      request <- Request[IO](Method.POST, uri).pure[IO]
      user    <- makeRequest[User](client, request)(jsonOf[IO, User])
    } yield user

  val assertResponse: Client[IO] => IO[Unit] = client =>
    for {
      user <- testRequest(client)
      _     = user.name shouldBe "John Doe"
    } yield ()

  it should "correctly decode user response with BlazeHttpClient" in {
    blazeHttpClient.use(assertResponse).unsafeRunSync()
  }

  // This fails with the error: This publisher only supports one subscriber
  it should "correctly decode user response with AsyncHttpClient" in {
    asyncHttpClient.use(assertResponse).unsafeRunSync()
  }
}