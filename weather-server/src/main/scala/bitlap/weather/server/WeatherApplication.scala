package bitlap.weather.server

import bitlap.weather.server.grpc.WeatherServiceImpl
import bitlap.weather.weather.WeatherServiceFs2Grpc
import cats.effect._
import cats.effect.std.Dispatcher
import io.grpc.Server
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import fs2.grpc.syntax.all._

object WeatherApplication extends IOApp {

  val port = 9999

  private def app[F[_]: Async](
    weatherClient: OpenWeatherClient[F],
    dataProvider: DataProvider,
    dispatcher: Dispatcher[F],
    stop: Deferred[F, Unit]
  ): Resource[F, Server] =
    for
      service <- WeatherServiceFs2Grpc.bindServiceResource[F](
        new WeatherServiceImpl(weatherClient, dataProvider, dispatcher, stop)
      )
      server <- NettyServerBuilder
        .forPort(port)
        .addService(service)
        .resource[F]
        .map(_.start())
    yield server

  private def executeServer(weatherClient: OpenWeatherClient[IO]): IO[ExitCode] =
    Dispatcher.sequential[IO].use { dispatcher =>
      for
        stop         <- Deferred[IO, Unit]
        dataProvider <- DataProvider.dataProvider[IO]
        s <- IO(
          println("""_____      ________                          __  .__                  
               |_/ ____\_____\_____  \  __  _  __ ____ _____ _/  |_|  |__   ___________ 
               |\   __\/  ___//  ____/  \ \/ \/ // __ \\__  \\   __\  |  \_/ __ \_  __ \
               | |  |  \___ \/       \   \     /\  ___/ / __ \|  | |   Y  \  ___/|  | \/
               | |__| /____  >_______ \   \/\_/  \___  >____  /__| |___|  /\___  >__|   
               |           \/        \/              \/     \/          \/     \/       """.stripMargin)
        ) *> IO(println(s"Started at port $port"))
        _ <- app[IO](weatherClient, dataProvider, dispatcher, stop)
          .use(s => stop.get *> IO.delay(s.shutdown()) *> IO(println(s"Stopped by client Signal.STOP")))
      yield ExitCode.Success
    }

  override def run(args: List[String]): IO[ExitCode] = {
    import pureconfig._
    // must
    import pureconfig.generic.derivation.*

    final case class MyConfig(openWeatherApiKey: String)
    implicit val reader: ConfigReader[MyConfig] =
      ConfigReader.forProduct1("open-weather-api-key")(MyConfig.apply)
    implicit val writer: ConfigWriter[MyConfig] =
      ConfigWriter.forProduct1("open-weather-api-key") { case MyConfig(a) => a }

    for
      cfg <- IO(ConfigSource.default.load[MyConfig])
      exitCode <- cfg match {
        case Left(failures) => IO { println(s"Error reading config: $failures"); ExitCode.Error }
        case Right(cfg) =>
          OpenWeatherClient
            .openWeatherClient[IO](cfg.openWeatherApiKey)
            .use(client => executeServer(client))
      }
    yield exitCode
  }

}
