package bitlap.weather.server.model

enum WeatherServiceError:
  case NotFound      extends WeatherServiceError
  case ProtocolError extends WeatherServiceError
