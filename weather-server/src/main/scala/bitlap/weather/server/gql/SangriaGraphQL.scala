package bitlap.weather.server.gql

import bitlap.weather.server.{DataProvider, OpenWeatherClient}
import bitlap.weather.server.gql.fetcher.{FetcherContext, MainRepo}
import bitlap.weather.server.gql.schema.QueryType
import sangria.ast.*
import sangria.schema.*
import sangria.execution.*
import sangria.validation.*
import sangria.marshalling.circe.*
import sangria.execution.deferred.*
import sangria.execution.WithViolations
import sangria.parser.{QueryParser, SyntaxError}
import cats.effect.*
import cats.effect.std.Dispatcher
import cats.implicits.*
import io.circe.Json.*
import io.circe.{Json, JsonObject}

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext

object SangriaGraphQL {

  def graphQL[F[_]: Async](fetcherContext: FetcherContext[F]): GraphQL[F] = {
    val v: F[MainRepo[F]] = MainRepo.fetch(fetcherContext).pure[F]
    SangriaGraphQL[F].apply(Schema(query = QueryType[F](fetcherContext.dispatcher)), v)
  }

  private def formatSyntaxError(e: SyntaxError): Json = Json.obj(
    "errors" -> arr(
      obj(
        "message" -> fromString(e.getMessage),
        "locations" -> arr(
          obj(
            "line"   -> fromInt(e.originalError.position.line),
            "column" -> fromInt(e.originalError.position.column)
          )
        )
      )
    )
  )

  private def formatWithViolations(e: WithViolations): Json = obj("errors" -> fromValues(e.violations.map {
    case v: AstNodeViolation =>
      obj(
        "message" -> fromString(v.errorMessage),
        "locations" -> fromValues(
          v.locations.map(loc => obj("line" -> fromInt(loc.line), "column" -> fromInt(loc.column)))
        )
      )
    case v => obj("message" -> fromString(v.errorMessage))
  }))

  private def formatString(s: String): Json = obj(
    "errors" -> arr(obj("message" -> fromString(s)))
  )

  private def formatThrowable(e: Throwable): Json = obj(
    "errors" -> arr(obj("class" -> fromString(e.getClass.getName), "message" -> fromString(e.getMessage)))
  )

  def apply[F[_]: Async] = new Partial[F]

  final class Partial[F[_]: Async] {
    val F = implicitly[Async[F]]

    def apply[A](
      schema: Schema[A, Unit],
      userContext: F[A]
    ): GraphQL[F] =
      new GraphQL[F] {
        def query(request: Json): F[Either[Json, Json]] = {
          val queryString   = request.hcursor.get[String]("query")
          val operationName = request.hcursor.get[String]("operationName").toOption
          val variables     = request.hcursor.get[JsonObject]("variables").toOption.getOrElse(JsonObject())
          queryString match {
            case Right(qs) => query(qs, operationName, variables)
            case Left(_)   => fail(formatString("No 'query' property was present in the request."))
          }
        }

        def query(query: String, operationName: Option[String], variables: JsonObject): F[Either[Json, Json]] =
          QueryParser.parse(query) match {
            case Success(ast) =>
              implicit val runtime = cats.effect.unsafe.IORuntime.global.compute
              exec(schema, userContext, ast, operationName, variables)
            case Failure(e @ SyntaxError(_, _, _)) =>
              fail(formatSyntaxError(e))
            case Failure(e) =>
              e.printStackTrace()
              fail(formatThrowable(e))
          }

        def fail(j: Json): F[Either[Json, Json]] =
          F.pure(j.asLeft)

        def exec(
          schema: Schema[A, Unit],
          userContext: F[A],
          query: Document,
          operationName: Option[String],
          variables: JsonObject
        )(implicit ec: ExecutionContext): F[Either[Json, Json]] =
          userContext.flatMap { ctx =>
            F.fromFuture(
              F.pure(
                Executor
                  .execute(
                    schema = schema,
                    queryAst = query,
                    userContext = ctx,
                    variables = Json.fromJsonObject(variables),
                    operationName = operationName,
                    exceptionHandler = ExceptionHandler { case (_, e) =>
                      HandledException(e.getMessage)
                    }
                  )
              )
            ).attempt
              .map {
                case Left(value) =>
                  value.printStackTrace()
                  Left(formatThrowable(value))
                case Right(value) =>
                  Right(value.as[Json].getOrElse(Json.Null))
              }
          }
      }
  }

}
