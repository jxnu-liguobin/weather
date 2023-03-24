package bitlap.weather.server.gql

import io.circe.Json
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.*
import _root_.io.circe.generic.auto.*
import cats.*
import cats.effect.*
import cats.implicits.*
import cats.data.*
import org.http4s.headers.Location
import org.http4s.implicits.uri

object GraphQLRoutes {

  def apply[F[_]: Async](graphQL: GraphQL[F]): HttpRoutes[F] = {
    object dsl extends Http4sDsl[F]
    import dsl._
    HttpRoutes.of[F] {
      case req @ POST -> Root / "graphql" =>
        req.as[Json].flatMap(graphQL.query).flatMap {
          case Right(json) => Ok(json)
          case Left(json)  => BadRequest(json)
        }

      case GET -> Root / "playground.html" =>
        val f = implicitly[Sync[F]]
        StaticFile
          .fromResource[F]("/playground.html")
          .getOrElseF(
            f.pure(Response.notFound)
          )
      case _ => PermanentRedirect(Location(uri"""/playground.html"""))
    }
  }
}
