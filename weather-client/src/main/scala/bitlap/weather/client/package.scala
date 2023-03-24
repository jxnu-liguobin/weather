package bitlap.weather.client

import fs2.grpc.syntax.all._
import cats.effect.{IO, Resource}
import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder

/** @author
 *    梦境迷离
 *  @version 1.0,2023/3/24
 */
val fs2GrpcClient: Resource[IO, ManagedChannel] = NettyChannelBuilder
  .forAddress("127.0.0.1", 9999)
  .usePlaintext()
  .resource[IO]
