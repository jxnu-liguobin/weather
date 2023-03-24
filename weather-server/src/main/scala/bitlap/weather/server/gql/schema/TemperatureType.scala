package bitlap.weather.server.gql.schema

import bitlap.weather.server.gql.fetcher.MainRepo
import cats.effect.*
import sangria.schema.*

object TemperatureType {

  final case class Temperature(value: Double)

  def apply[F[_]: Async]: ObjectType[MainRepo[F], Temperature] =
    ObjectType(
      name = "Temperature",
      fieldsFn = () =>
        fields[MainRepo[F], Temperature](
          Field(name = "value", fieldType = FloatType, description = None, resolve = _.value.value)
        )
    )

}
