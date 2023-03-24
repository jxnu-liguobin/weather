package bitlap.weather.server.gql

import io.circe.{Json, JsonObject}

trait GraphQL[F[_]] {

  def query(request: Json): F[Either[Json, Json]]

  def query(query: String, operationName: Option[String], variables: JsonObject): F[Either[Json, Json]]
}
