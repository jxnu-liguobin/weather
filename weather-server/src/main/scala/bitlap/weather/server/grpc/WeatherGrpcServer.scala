package bitlap.weather.server.grpc

import bitlap.weather.server.{DataProvider, OpenWeatherClient}
import bitlap.weather.server.grpc.WeatherServiceImpl
import bitlap.weather.weather.WeatherServiceFs2Grpc
import cats.effect.*
import cats.effect.std.Dispatcher
import io.grpc.Server
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import fs2.grpc.syntax.all.*
import cats.syntax.all.toFlatMapOps
import cats.syntax.all.catsSyntaxApply

object WeatherGrpcServer {

  val port = 9999

  def service[F[_]: Async](
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
}
