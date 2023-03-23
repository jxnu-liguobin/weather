package bitlap.weather.server.model

final case class CityInCountry(name: String, countryCode: String)
final case class City(name: String, region: String, countryCode: String, id: String)
final case class CitySearch(name: String, countryCode: String, region: Option[String])

object CitySearch {
  def apply(name: String, countryCode: String, region: String): CitySearch =
    CitySearch(name, countryCode, if region.trim.isEmpty then None else Some(region))

  def apply(name: String, countryCode: String): CitySearch =
    CitySearch(name, countryCode, None)
}
