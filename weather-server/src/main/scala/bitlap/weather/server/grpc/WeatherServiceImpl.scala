package bitlap.weather.server.grpc

import bitlap.weather.server.model.{CitySearch, WeatherServiceError}
import bitlap.weather.server.{DataProvider, OpenWeatherClient}
import bitlap.weather.weather.Status._
import bitlap.weather.weather._
import cats.effect._
import cats.effect.std.Dispatcher
import cats.implicits.catsSyntaxApply
import cats.syntax.either._
import cats.syntax.functor._
import fs2.Chunk
import io.grpc.Metadata

import java.util.logging.Logger

class WeatherServiceImpl[F[_]: Async](
  weatherClient: OpenWeatherClient[F],
  dataProvider: DataProvider,
  dispatcher: Dispatcher[F],
  stop: Deferred[F, Unit]
) extends WeatherServiceFs2Grpc[F, Metadata] {

  private val streamTakeBuffer = 10

  private[this] val logger = Logger.getLogger("weather-app")

  val F: Async[F] = implicitly[Async[F]]

  override def getWeather(req: WeatherRequest, ctx: Metadata): F[WeatherReply] = {
    logger.info(s"getWeather: got request '$req'")
    val found = dataProvider.findCity(CitySearch(req.city, req.countryCode, req.region))
    logger.info(s"getWeather: found city = '$found'")
    found match {
      case Left(numCities) =>
        F.delay(toError(req.city, req.region, req.countryCode, numCities))
      case Right(city) =>
        for resp <- weatherClient.getTemperature(city.id)
        yield resp match {
          case Left(err) =>
            new WeatherReply(city.name, city.region, city.countryCode, status = toOurError(err))
          case Right(temperature) => new WeatherReply(city.name, city.region, city.countryCode, temperature.toInt)
        }
    }
  }

  override def listWeather(request: fs2.Stream[F, WeatherRequest], ctx: Metadata): fs2.Stream[F, WeatherReply] =
    request.buffer(streamTakeBuffer).mapChunks { req =>
      val (notFound, found) = dataProvider.findCities(req.map(r => CitySearch(r.city, r.countryCode, r.region)).toList)
      val errors = notFound.map { case (citySearch, num) =>
        toError(citySearch.name, citySearch.region, citySearch.countryCode, num)
      }
      val idMap = found.map(city => city.id -> city).toMap
      val replies = (if found.isEmpty then F.pure(Map[String, Double]().asRight[WeatherServiceError])
                     else weatherClient.getTemperature(found.map(_.id))).map {
        case Left(err) => List(WeatherReply(status = toOurError(err)))
        case Right(temperatureMap) =>
          temperatureMap.map { case (id, t) =>
            WeatherReply(
              city = idMap(id).name,
              region = idMap(id).region,
              countryCode = idMap(id).countryCode,
              temperature = t
            )
          }.toList
      }
      dispatcher.unsafeRunSync(replies.map(r => Chunk.apply(errors ++ r: _*)))
    }

  private def toOurError(err: WeatherServiceError): Recognized =
    err match {
      case WeatherServiceError.NotFound => NOT_FOUND
      case _                            => SERVICE_UNAVAILABLE
    }

  private def toError(city: String, region: String, code: String, numCities: Int): WeatherReply =
    new WeatherReply(city, region, code, status = if numCities == 0 then NOT_FOUND else AMBIGUOUS_CITY)

  override def serviceMessage(request: ServiceMessage, ctx: Metadata): F[ServiceReply] =
    request.signal match {
      case Signal.STOP =>
        logger.info("STOP")
        stop.complete(()) *> F.delay(ServiceReply(OK))
      case Signal.DROP_ALL =>
        logger.info("DROP_ALL")
        F.delay(ServiceReply(OK))
      case Signal.Unrecognized(value) =>
        logger.warning(s"Signal Unrecognized: $value")
        F.delay(ServiceReply(ERROR))
    }
}
