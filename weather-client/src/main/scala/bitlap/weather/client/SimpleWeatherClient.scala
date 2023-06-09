package bitlap.weather.client

import bitlap.weather.Signal.*
import bitlap.weather.*
import cats.effect.*
import cats.implicits.{catsSyntaxFlatMapOps, toFunctorOps}
import io.grpc.{Status as _, *}

import scala.concurrent.duration.Duration

object SimpleWeatherClient extends IOApp {

  private def readWeatherAndStopServer[F[_]: Async](
    city: String,
    countryCode: String,
    channel: Channel
  ): F[Status] = {
    val resource: Resource[F, WeatherServiceFs2Grpc[F, Metadata]] = WeatherServiceFs2Grpc.stubResource[F](channel)
    resource.use { stub =>
      val request = WeatherRequest(city, countryCode = countryCode)
      stub.getWeather(request, new Metadata).map { response =>
        if response.status.isOk then
          println(
            s"temperature in ${response.city}, ${response.region}, ${response.countryCode} is ${response.temperature}"
          )
        else ()
      }
    } >>
      resource.use { stub =>
        val dropMessage = new ServiceMessage(DROP_ALL)
        val stopMessage = new ServiceMessage(STOP)
        stub.serviceMessage(dropMessage, new Metadata)
        stub.serviceMessage(stopMessage, new Metadata).map(_.status)
      }
  }

  override def run(args: List[String]): IO[ExitCode] = {
    val city        = args.headOption.getOrElse("Beijing")
    val countryCode = args.drop(1).headOption.getOrElse("CN")
    for {
      client <- fs2GrpcClient
        .use(channel => readWeatherAndStopServer(city, countryCode, channel))
        .timeout(Duration("10s"))
        .andWait(Duration("10s"))
      _ <- IO.delay(println(s"client status: $client"))
    } yield ExitCode.Success
  }
}
