# Weather

> Original Project [weather-rpc](https://github.com/dimaopen/weather-rpc)

# Description
A web server that allows to get the current temperature at most of the big cities in the world.
It is written in Scala3 using cats-effect 3, fs2-grpc and http4s.

# How to setup project
You need [sbt](https://www.scala-sbt.org/) for building the project.

1. clone this project
  ```shell
  git clone <this project url>
  ```
1. Import the project into your favorite IDE.

# Running

* You need to run the server app. Go to the project dir and execute
```shell
sbt "server/runMain bitlap.weather.server.WeatherApplication"
```

## Client apps
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