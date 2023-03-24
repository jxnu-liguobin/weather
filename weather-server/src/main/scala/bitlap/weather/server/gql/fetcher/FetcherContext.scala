package bitlap.weather.server.gql.fetcher

import bitlap.weather.server.{DataProvider, OpenWeatherClient}
import cats.effect.*
import cats.effect.std.Dispatcher

/** @author
 *    梦境迷离
 *  @version 1.0,2023/3/24
 */
final case class FetcherContext[F[_]: Async](
  dataProvider: DataProvider,
  weatherClient: OpenWeatherClient[F],
  dispatcher: Dispatcher[F]
)
