package bitlap.weather.server.model

enum WeatherServiceError extends Throwable:
  case NotFound      extends WeatherServiceError
  case ProtocolError extends WeatherServiceError
