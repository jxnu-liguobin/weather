package bitlap.weather.server.http

import bitlap.weather.server.{DataProvider, OpenWeatherClient}
import bitlap.weather.server.model.CitySearch
import cats.effect.IO
import cats.Monad
import cats.FlatMap
import cats.data.Validated
import cats.implicits.*
import cats.effect.*
import cats.effect.std.Dispatcher
import fs2.Stream
import io.circe.{Decoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io.*
import io.circe.Decoder.importedDecoder
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.http4s.circe.CirceSensitiveDataEntityDecoder.circeEntityDecoder
import org.http4s.server.Server

import java.nio.charset.Charset
import scala.util.Left

object WeatherHttpServer extends Http4sDsl[IO] {

  given EntityDecoder[IO, CitySearch] = jsonOf[IO, CitySearch]

  private def routes[F[_]: Async](
    dispatcher: Dispatcher[F],
    dataProvider: DataProvider,
    weatherClient: OpenWeatherClient[F]
  ) =
    HttpRoutes
      .of[F] { case req @ POST -> Root / "weather" =>
        req.as[CitySearch].map { city =>
          val f = implicitly[Async[F]]
          val resp = dataProvider.findCity(city) match {
            case Left(e) =>
              f.pure("".asJson)
            case Right(c) =>
              weatherClient.getTemperature(c.id).map(_.toOption.asJson)
          }
          val response: Array[Byte] = dispatcher.unsafeRunSync(resp).noSpaces.getBytes
          Response(
            status = Status.Ok,
            body = Stream.fromIterator.apply(response.toList.iterator, response.length)
          )
        }

      }
      .orNotFound

  def service[F[_]: Async](
    dispatcher: Dispatcher[F],
    dataProvider: DataProvider,
    weatherClient: OpenWeatherClient[F]
  ): Resource[F, Server] =
    BlazeServerBuilder[F]
      .bindHttp(8888, "0.0.0.0")
      .withHttpApp(routes(dispatcher, dataProvider, weatherClient))
      .resource

}
