package bitlap.weather.server.gql.fetcher

import bitlap.weather.server.gql.schema.TemperatureType.Temperature
import bitlap.weather.server.{DataProvider, OpenWeatherClient}
import bitlap.weather.server.model.{City, CitySearch, WeatherServiceError}
import cats.effect.*
import cats.implicits.*

trait CityRepo[F[_]] {
  def fetchWeather(code: String, name: String): F[Temperature]
}

object CityRepo {

  def fetch[F[_]: Async](fetcherContext: FetcherContext[F]): CityRepo[F] =
    new CityRepo[F] {
      def fetchWeather(code: String, name: String): F[Temperature] = {
        val cityOpt =
          fetcherContext.dataProvider.findCity(CitySearch(name = name, countryCode = code, region = "")).toOption
        val result = cityOpt
          .map(city => fetcherContext.weatherClient.getTemperature(city.id).map(_.toOption.getOrElse(0d)))
          .getOrElse(implicitly[Async[F]].pure(0d))
        result.map(Temperature)
      }

    }
}
