package bitlap.weather.client

import bitlap.weather.client.SimpleWeatherClient.readWeatherAndStopServer
import bitlap.weather.*

import cats.effect.*
import cats.implicits.catsSyntaxApply
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import io.grpc.{Channel, Metadata}

import scala.concurrent.duration.Duration

object StreamingWeatherClient extends IOApp {

  private def readWeather[F[_]: Async](
    cities: List[(String, String)],
    channel: Channel,
    finishedFlag: Deferred[F, Unit]
  ): F[Boolean] = {
    val resource: Resource[F, WeatherServiceFs2Grpc[F, Metadata]] = WeatherServiceFs2Grpc.stubResource[F](channel)
    var i                                                         = 0
    resource.use { stub =>
      val cs = cities.map(f => new WeatherRequest(f._1, countryCode = f._2))
      stub
        .listWeather(fs2.Stream.fromIterator[F].apply(cs.iterator, cs.size), new Metadata())
        .map { response =>
          i += 1
          if response.status.isOk then
            println(
              s"$i,temperature in ${response.city}, ${response.region}, ${response.countryCode} is ${response.temperature}"
            )
          else ()
        }
        .compile
        .toList
    }
      >> finishedFlag.complete(())
  }

  override def run(args: List[String]): IO[ExitCode] = {
    val cities =
      if args.size < 2 then
        List(
          ("Rome", "IT"),
          ("Athens", "GR"),
          ("Yudong", "CN"),
          ("Yongchuan", "CN"),
          ("Wanxian", "CN"),
          ("Jijiang", "CN"),
          ("Hechuan", "CN"),
          ("Fuling", "CN"),
          ("Chongqing", "CN"),
          ("Beibei", "CN")
        )
      else args.zip(args.tail)
    val citiess = (0 until 10000).flatMap { _ =>
      cities
    }.toList

    for {
      finishedFlag <- Deferred[IO, Unit]
      client <- fs2GrpcClient
        .use(channel => readWeather[IO](citiess, channel, finishedFlag))
        .timeout(Duration("300s"))
        .andWait(Duration("300s")) <& finishedFlag.get
      _ <- IO.delay(println(s"client status: $client"))
    } yield ExitCode.Success
  }
}
