# Weather

> Original Project [weather-rpc](https://github.com/dimaopen/weather-rpc)

# Description
A web server that allows to get the current temperature at most of the big cities in the world.

# technology stack
 
- Scala3
- cats-effect 3
- fs2-grpc
- http4s
- circe

# Running

* You need to run the server app. Go to the project dir and execute
```shell
sbt "server/runMain bitlap.weather.server.WeatherApplication"
```

## client apps
There are 2 clients in the project:
1. a simple client that gets the temperature for a single city and sends a shutdown server command
2. a streaming client that gets the temperature for multiple cities in a stream mode.
   This client does not stop the server.

To run the simple client execute
```shell
sbt "client/runMain bitlap.weather.client.SimpleWeatherClient"
```
To run the streaming client execute
```shell
sbt "client/runMain bitlap.weather.client.StreamingWeatherClient"
```

## http4s http

```
curl --location 'http://localhost:8888/weather' \
--header 'Content-Type: application/json' \
--data '{
    "name": "Beijing",
    "countryCode": "CN",
    "region": "Beijing"
}'
```

## sangria graphql
```
curl --location 'http://localhost:8888/graphql' \
--header 'Content-Type: application/json' \
--data '{"query":"query test {\n    city(name:\"Beijing\",countryCode:\"CN\"){\n     value\n    }\n}","variables":{}}'
```