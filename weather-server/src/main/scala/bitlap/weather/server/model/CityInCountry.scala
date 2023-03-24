package bitlap.weather.server.model

final case class CityInCountry(name: String, countryCode: String)
final case class City(name: String, region: String, countryCode: String, id: String)
final case class CitySearch(name: String, countryCode: String, region: String = "")
