package bitlap.weather.client

import bitlap.weather.weather.Signal._
import bitlap.weather.weather._
import cats.effect._
import cats.implicits.{catsSyntaxFlatMapOps, toFunctorOps}
import fs2.grpc.syntax.all._
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.{Status => _, _}

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

  implicit val sync: Async[IO] = implicitly[Async[IO]]

  override def run(args: List[String]): IO[ExitCode] = {
    val city        = args.headOption.getOrElse("Athens")
    val countryCode = args.drop(1).headOption.getOrElse("GR")
    val managedChannelResource: Resource[IO, ManagedChannel] =
      NettyChannelBuilder
        .forAddress("127.0.0.1", 9999)
        .usePlaintext()
        .resource[IO]

    managedChannelResource.use(channel => readWeatherAndStopServer(city, countryCode, channel)) >>
      IO(ExitCode.Success).handleErrorWith(_ => IO(ExitCode.Error))

  }
}
