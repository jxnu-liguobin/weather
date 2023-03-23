package bitlap.weather.server

import _root_.io.circe.Json
import bitlap.weather.server.model.WeatherServiceError
import bitlap.weather.server.model.WeatherServiceError.ProtocolError
import cats.effect._
import cats.instances.either._
import cats.instances.vector._
import cats.syntax.either._
import cats.syntax.functor._
import cats.syntax.traverse._
import org.http4s.UriTemplate._
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.{EntityDecoder, EntityEncoder, Uri, UriTemplate}

object OpenWeatherClient {

  def openWeatherClient[F[_]: Async](apiKey: String): Resource[F, OpenWeatherClient[F]] =
    BlazeClientBuilder[F].resource.map(client => new OpenWeatherClient[F](client, apiKey))

  private[server] def getSingleTemperature(json: Json): Either[WeatherServiceError, Double] =
    json.hcursor.downField("main").get[Double]("temp") match {
      case Left(_)            => ProtocolError.asLeft
      case Right(temperature) => temperature.asRight
    }

  private[server] def getSingleId(json: Json): Either[WeatherServiceError, String] =
    json.hcursor.get[Int]("id") match {
      case Left(_)   => ProtocolError.asLeft
      case Right(id) => id.toString.asRight
    }

  private[server] def getMultiTemperature(json: Json): Either[WeatherServiceError, Map[String, Double]] =
    json.hcursor.downField("list").values match {
      case Some(cities) =>
        cities.toVector
          .map(cityJson =>
            for
              id   <- getSingleId(cityJson)
              temp <- getSingleTemperature(cityJson)
            yield id -> temp
          )
          .sequence
          .map(_.toMap)
      case None => ProtocolError.asLeft
    }

}
final class OpenWeatherClient[F[_]: Async](client: Client[F], appId: String) {

  implicit val entityEncoder: EntityEncoder[F, Json] = jsonEncoderOf[F, Json]
  implicit val entityDecoder: EntityDecoder[F, Json] = jsonDecoder[F]

  private val template = UriTemplate(
    authority = Some(Uri.Authority(host = Uri.RegName("api.openweathermap.org"))),
    scheme = Some(Uri.Scheme.https),
    path = List(PathElm("data"), PathElm("2.5"), VarExp("pathVar")),
    query = List(ParamVarExp("id", "cityId"), ParamElm("appid", appId))
  )

  def getTemperature(cityId: String): F[Either[WeatherServiceError, Double]] = {
    val uri: Uri = template
      .expandPath("pathVar", "weather")
      .expandQuery("cityId", cityId)
      .toUriIfPossible
      .get
    println(uri)
    for json <- client.expect[Json](uri)
    yield OpenWeatherClient.getSingleTemperature(json)
  }

  def getTemperature(cityIds: List[String]): F[Either[WeatherServiceError, Map[String, Double]]] = {
    val uri: Uri = template
      .expandPath("pathVar", "group")
      .expandQuery("cityId", cityIds.mkString(","))
      .toUriIfPossible
      .get
    println(uri)
    for json <- client.expect[Json](uri)
    yield OpenWeatherClient.getMultiTemperature(json)
  }
}
