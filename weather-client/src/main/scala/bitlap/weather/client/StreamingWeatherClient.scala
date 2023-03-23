package bitlap.weather.client

import bitlap.weather.weather._
import cats.effect._
import cats.effect.std.Dispatcher
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.grpc.stub.StreamObserver
import io.grpc.{Channel, ManagedChannelBuilder}

object StreamingWeatherClient extends IOApp {

  private def readWeather[F[_]: Async](
    cities: List[(String, String)],
    channel: Channel,
    finishedFlag: Ref[F, Unit]
  ): F[Unit] = {

    val F = Sync[F]

    def finishReading(): Unit =
      Dispatcher.sequential[F].use { dispatcher =>
        F.delay(dispatcher.unsafeRunAndForget(finishedFlag.set(())))
      }
    val stub = WeatherServiceGrpc.stub(channel)
    for
      requestObserver <- F.delay(
        stub.listWeather(
          new StreamObserver[WeatherReply] {
            override def onNext(response: WeatherReply): Unit =
              response.status match {
                case Status.OK =>
                  println(s"temperature in ${response.city} is ${response.temperature}")
                case Status.Unrecognized(value) =>
                  println(s"unrecognized response $value")
                case x @ _ =>
                  println(s"error for ${response.city} is $x")
              }

            override def onError(t: Throwable): Unit = {
              finishReading()
              println(s"error~: ${t.getMessage}")
            }

            override def onCompleted(): Unit =
              finishReading()
          }
        )
      )
      _ <- F.delay(cities.foreach { case (city, countryCode) =>
        requestObserver.onNext(new WeatherRequest(city, countryCode = countryCode))
      })
      _ <- F.delay(requestObserver.onCompleted())
    yield ()
  }

  override def run(args: List[String]): IO[ExitCode] = {
    val cities =
      if args.size < 2 then List(("Rome", "IT"), ("Athens", "GR"), ("Moscow", "RU"), ("Kazan", "RU"))
      else args.zip(args.tail)
    (
      for
        finishedFlag <- Ref[IO].empty[Unit]
        _ <- IO(
          ManagedChannelBuilder
            .forAddress("localhost", 9999)
            .usePlaintext()
            .asInstanceOf[ManagedChannelBuilder[_]]
            .build
        ).bracketCase(channel => readWeather[IO](cities, channel, finishedFlag) >> finishedFlag.get) {
          (channel, exit) =>
            channel.shutdown()
            exit match {
              case Outcome.Succeeded(_) => IO.unit
              case Outcome.Errored(e)   => IO(println(s"error!: ${e.getMessage}"))
              case Outcome.Canceled()   => IO(println("client cancelled"))
            }
        }
      yield ExitCode.Success
    ).handleErrorWith(_ => IO(ExitCode.Error))
  }
}
