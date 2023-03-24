package bitlap.weather.server.gql.fetcher

import bitlap.weather.server.{DataProvider, OpenWeatherClient}
import cats.effect.*

final case class MainRepo[F[_]](city: CityRepo[F])

object MainRepo {

  def fetch[F[_]: Async](fetcherContext: FetcherContext[F]): MainRepo[F] =
    MainRepo(
      CityRepo.fetch(fetcherContext)
    )

}
