package bitlap.weather.server

import java.io.{BufferedReader, BufferedWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import cats.effect._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._

object GenerateCityFile extends IOApp {

  private def readCountries[F[_]: Sync](path: Path): F[Map[String, String]] = {

    def toEntry(line: String): Option[(String, String)] = {
      val pair = line.split(',')
      if pair.length != 2 then None else (pair(0) -> pair(1)).some
    }

    def readLine(reader: BufferedReader, accum: Map[String, String]): F[Map[String, String]] =
      for
        line <- Sync[F].delay(reader.readLine())
        result <-
          if line != null then readLine(reader, accum ++ toEntry(line))
          else Sync[F].pure(accum)
      yield result

    inputReader(path).use(reader => readLine(reader, Map()))
  }

  private def inputReader[F[_]: Sync](f: Path): Resource[F, BufferedReader] =
    Resource.make {
      Sync[F].delay(Files.newBufferedReader(f))
    } { reader =>
      Sync[F].delay(reader.close())
    }

  private def writer[F[_]: Sync](f: Path): Resource[F, BufferedWriter] =
    Resource.make {
      Sync[F].delay(Files.newBufferedWriter(f, StandardCharsets.UTF_8))
    } { reader =>
      Sync[F].delay(reader.close())
    }

  private def generateCityFile[F[_]: Sync](inputPath: Path, outPath: Path, countries: Map[String, String]): F[Int] = {
    def toOurFormat(dataLine: String): Option[String] = {
      val data = dataLine.split(',')
      if data.length != 4 then None
      else {
        val city        = data(0)
        val country     = data(1)
        val region      = data(2)
        val id          = data(3)
        val countryCode = countries.get(country)
        countryCode.map(code => s"$city,$code,$region,$id")
      }
    }

    def processLines(reader: BufferedReader, writer: BufferedWriter, accum: Int): F[Int] =
      for
        line <- Sync[F].delay(reader.readLine())
        result <-
          if line != null then {
            val ourLine = toOurFormat(line)
            Sync[F].delay(ourLine.foreach { l =>
              writer.write(l)
              writer.newLine()
            }) >> processLines(reader, writer, accum + ourLine.map(_ => 1).getOrElse(0))
          } else Sync[F].pure(accum)
      yield result

    (for
      inStream  <- inputReader(inputPath)
      outStream <- writer(outPath)
    yield (inStream, outStream)).use { case (in, out) =>
      processLines(in, out, 0)
    }
  }

  import cats.effect.IO.asyncForIO
  override def run(args: List[String]): IO[ExitCode] = {
    val countryPath = Paths.get("./Downloads/country-list_zip/data/data_csv.csv")
    val cityPath    = Paths.get("./Downloads/world-cities_zip/data/world-cities_csv.csv")
    val outPath     = Paths.get("./Downloads/world-cities.csv")
    for
      countries <- readCountries(countryPath)
      count     <- generateCityFile(cityPath, outPath, countries)
      _         <- IO(println(s"number of processed countries: $count"))
    yield ExitCode.Success
  }
}
