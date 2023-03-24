package bitlap.weather.server.gql.schema

import bitlap.weather.server.gql.fetcher.MainRepo
import cats.effect.*
import cats.effect.std.Dispatcher
import sangria.schema.*
import sangria.schema.ValidOutType.valid

object QueryType {

  val name: Argument[String] =
    Argument(
      name = "name",
      argumentType = StringType,
      description = "city name",
      defaultValue = ""
    )

  val countryCode: Argument[String] =
    Argument(
      name = "countryCode",
      argumentType = StringType,
      description = "countryCode",
      defaultValue = ""
    )

  def apply[F[_]: Async](dispatcher: Dispatcher[F]): ObjectType[MainRepo[F], Unit] =
    ObjectType(
      name = "Query",
      fields[MainRepo[F], Unit](
        Field(
          name = "city",
          fieldType = TemperatureType.apply[F],
          description = Some("Returns the city weather"),
          arguments = List(name, countryCode),
          resolve = { c =>
            dispatcher.unsafeToFuture[TemperatureType.Temperature](
              c.ctx.city.fetchWeather(c.arg(countryCode), c.arg(name))
            )
          }
        )
      )
    )

  def schema[F[_]: Async](dispatcher: Dispatcher[F]): Schema[MainRepo[F], Unit] =
    Schema(QueryType[F](dispatcher))
}
