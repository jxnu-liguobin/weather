package bitlap.weather.server

import java.io.{BufferedReader, InputStreamReader}
import cats.effect._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import cats.syntax.either._
import bitlap.weather.server.model._

class DataProvider(private val map: Map[CityInCountry, Map[String, String]]) {

  private def getCity(
    cityName: String,
    countryCode: String,
    regionMap: Map[String, String],
    region: Option[String]
  ): Either[Int, City] =
    regionMap.size match {
      case 1 =>
        val regionWeHave = regionMap.head._1
        regionMap
          .get(region.getOrElse(regionWeHave))
          .map(id => City(cityName, regionWeHave, countryCode, id))
          .toRight(0)
      case size =>
        region match {
          case Some(reg) =>
            regionMap
              .get(reg)
              .map(id => City(cityName, reg, countryCode, id))
              .toRight(0)
          case None => size.asLeft
        }
    }

  def findCity(cityToFind: CitySearch): Either[Int, City] =
    map.get(CityInCountry(cityToFind.name, cityToFind.countryCode)) match {
      case Some(regionMap) => getCity(cityToFind.name, cityToFind.countryCode, regionMap, cityToFind.region)
      case None            => 0.asLeft
    }

  def findCities(citiesToFind: List[CitySearch]): (List[(CitySearch, Int)], List[City]) = {
    val results = citiesToFind.map(cityToFind => cityToFind -> findCity(cityToFind))
    val errors  = results.flatMap { case (citySearch, result) => result.swap.toList.map(num => citySearch -> num) }
    val found   = results.flatMap { case (_, result) => result.toList }
    (errors, found)
  }
}

object DataProvider {

  def dataProvider[F[_]: Sync]: F[DataProvider] = readCities.map(new DataProvider(_))

  private def readCities[F[_]: Sync]: F[Map[CityInCountry, Map[String, String]]] = {

    def toEntry(line: String): Option[(CityInCountry, String, String)] = {
      val data = line.split(',')
      if data.length != 4 then None else (CityInCountry(data(0), data(1)), data(2), data(3)).some
    }

    def putToMap(map: Map[CityInCountry, Map[String, String]], entry: Option[(CityInCountry, String, String)]) =
      entry match {
        case Some((city, region, id)) =>
          val previous = map.getOrElse(city, Map())
          map.updated(city, previous + (region -> id))
        case None => map
      }

    def readLine(
      reader: BufferedReader,
      accum: Map[CityInCountry, Map[String, String]]
    ): F[Map[CityInCountry, Map[String, String]]] =
      for
        line <- Sync[F].delay(reader.readLine())
        result <-
          if line != null then readLine(reader, putToMap(accum, toEntry(line)))
          else Sync[F].pure(accum)
      yield result

    inputReader().use(reader => readLine(reader, Map()))
  }

  private def inputReader[F[_]: Sync](): Resource[F, BufferedReader] =
    Resource.make {
      Sync[F].delay(new BufferedReader(new InputStreamReader(getClass.getResourceAsStream("/world-cities.csv"))))
    } { reader =>
      Sync[F].delay(reader.close())
    }
}
