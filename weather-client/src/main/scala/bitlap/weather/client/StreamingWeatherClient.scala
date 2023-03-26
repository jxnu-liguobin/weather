package bitlap.weather.client

import bitlap.weather.client.SimpleWeatherClient.readWeatherAndStopServer
import bitlap.weather.weather.*
import cats.effect.*
import cats.effect.std.Dispatcher
import cats.implicits.catsSyntaxApply
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import io.grpc.stub.StreamObserver
import io.grpc.{Channel, Metadata}

import scala.concurrent.duration.Duration

object StreamingWeatherClient extends IOApp {

  private def readWeather[F[_]: Async](
    cities: List[(String, String)],
    channel: Channel,
    finishedFlag: Deferred[F, Unit]
  ): F[Boolean] = {
    val resource: Resource[F, WeatherServiceFs2Grpc[F, Metadata]] = WeatherServiceFs2Grpc.stubResource[F](channel)
    resource.use { stub =>
      val cs = cities.map(f => new WeatherRequest(f._1, countryCode = f._2))
      stub
        .listWeather(fs2.Stream.fromIterator[F].apply(cs.iterator, cs.size), new Metadata())
        .map { response =>
          if response.status.isOk then
            println(
              s"temperature in ${response.city}, ${response.region}, ${response.countryCode} is ${response.temperature}"
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

    for {
      finishedFlag <- Deferred[IO, Unit]
      client <- fs2GrpcClient
        .use(channel => readWeather[IO](cities, channel, finishedFlag))
        .timeout(Duration("10s"))
        .andWait(Duration("10s")) <& finishedFlag.get
      _ <- IO.delay(println(s"client status: $client"))
    } yield ExitCode.Success
  }
}
