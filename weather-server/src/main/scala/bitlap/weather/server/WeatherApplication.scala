package bitlap.weather.server

import bitlap.weather.server.grpc.{WeatherGrpcServer, WeatherServiceImpl}
import bitlap.weather.WeatherServiceFs2Grpc
import cats.effect.*
import cats.effect.std.Dispatcher
import io.grpc.Server
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import fs2.grpc.syntax.all.*
import cats.syntax.all.toFlatMapOps
import cats.syntax.all.catsSyntaxApply
import cats.syntax.all.toFunctorOps
import bitlap.weather.server.http.WeatherHttpServer
import bitlap.weather.server.gql.SangriaGraphQL
import bitlap.weather.server.gql.fetcher.FetcherContext

object WeatherApplication extends IOApp {

  private def executeServer[F[_]: Async](weatherClient: OpenWeatherClient[F]): F[ExitCode] =
    val async = implicitly[Async[F]]
    Dispatcher.sequential[F].use { dispatcher =>
      for {
        stop         <- Deferred[F, Unit]
        dataProvider <- DataProvider.dataProvider[F]
        _ <- async.delay(
          println("""_____      ________                          __  .__                  
               |_/ ____\_____\_____  \  __  _  __ ____ _____ _/  |_|  |__   ___________ 
               |\   __\/  ___//  ____/  \ \/ \/ // __ \\__  \\   __\  |  \_/ __ \_  __ \
               | |  |  \___ \/       \   \     /\  ___/ / __ \|  | |   Y  \  ___/|  | \/
               | |__| /____  >_______ \   \/\_/  \___  >____  /__| |___|  /\___  >__|   
               |           \/        \/              \/     \/          \/     \/       """.stripMargin)
        ) *> async.delay(println(s"Started at port ${WeatherGrpcServer.port}"))

        s1 <- async.start(
          WeatherGrpcServer
            .service[F](weatherClient, dataProvider, dispatcher, stop)
            .use(s =>
              stop.get *> async.delay(s.shutdown()) *>
                async.delay(println(s"Grpc Stopped by client Signal: STOP"))
            )
        )
        fetcherContext = FetcherContext[F](
          dataProvider,
          weatherClient,
          dispatcher
        )
        s2 <- async.start(
          WeatherHttpServer
            .service[F](dispatcher, dataProvider, weatherClient, SangriaGraphQL.graphQL[F](fetcherContext))
            .use(s => async.delay(println(s"Started HTTP: ${s.address}")) *> async.never)
        )
        _ <- s2.join
        _ <- s1.join
      } yield ExitCode.Success
    }

  override def run(args: List[String]): IO[ExitCode] = {
    import pureconfig._
    // must
    import pureconfig.generic.derivation.*

    final case class MyConfig(openWeatherApiKey: String)
    implicit val reader: ConfigReader[MyConfig] =
      ConfigReader.forProduct1("open-weather-api-key")(MyConfig.apply)

    for
      cfg <- IO(ConfigSource.default.load[MyConfig])
      exitCode <- cfg match {
        case Left(failures) => IO { println(s"Error reading config: $failures"); ExitCode.Error }
        case Right(cfg) =>
          OpenWeatherClient
            .openWeatherClient[IO](cfg.openWeatherApiKey)
            .use(client => executeServer[IO](client))
      }
    yield exitCode
  }

}
