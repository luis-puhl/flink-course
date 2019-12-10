package examples.scala.KMeansVector

import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.functions.sink.SocketClientSink
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.util.Collector

import scala.util.{Failure, Try}

object KMeansVector extends App {
  val env = StreamExecutionEnvironment.getExecutionEnvironment
  val kddString$: DataStream[String] =
    // env.socketTextStream("127.0.0.1", 3232)
    env.readTextFile("./tmpfs/kddcup.data")
  val kdd$: DataStream[Kdd.MaybeEntry] = kddString$.map { line => Kdd.fromLine(line) }
  val words: DataStream[(Int, String)] = kdd$.flatMap{(x, out) => x match {
    case Right(Kdd.ConversionFail(msg)) => msg.foreach(m => out.collect((1, m)))
    case Left(e) => out.collect((1, e.protocol_type))
    case _ =>
  }}
  val count: DataStream[(Int, String)] = words
    .keyBy(1)
    // .countWindow(1000)
    // .window(TumblingProcessingTimeWindows.of(Time.seconds(2L)))
    // .timeWindowAll(Time.seconds(2L))
    .sum(0)
    // .aggregate((value: (Int, String), acc: (Int, String), out: Collector[(Int, String)]) => {})
  count.print()
  kdd$.print()
  env.execute()
}

object Kdd {
  type continuous = Double
  type symbolic = String

  /**
   * stream lines in the form
   * "0,tcp,http,SF,215,45076,0,0,0,0,0,1,
   *    0,0,0,0,0,0,0,0,0,0,1,1,0.00,0.00,0.00,0.00,1.00,0.00,0.00,0,0,0.00,0.00,0.00,0.00,0.00,0.00,0.00,0.00,normal."
   * duration, protocol_type, service, flag, src_bytes, dst_bytes, land, wrong_fragment, urgent, hot, num_failed_logins, logged_in,
   *    num_compromised, root_shell, su_attempted, num_root, num_file_creations, num_shells, num_access_files, num_outbound_cmds,
   *    is_host_login, is_guest_login, count, srv_count, serror_rate, srv_serror_rate, rerror_rate, srv_rerror_rate, same_srv_rate,
   *    diff_srv_rate, srv_diff_host_rate, dst_host_count, dst_host_srv_count, dst_host_same_srv_rate, dst_host_diff_srv_rate,
   *    dst_host_same_src_port_rate, dst_host_srv_diff_host_rate, dst_host_serror_rate, dst_host_srv_serror_rate, dst_host_rerror_rate,
   *    dst_host_srv_rerror_rate
   * duration: continuous.
   * protocol_type: symbolic.
   * service: symbolic.
   * flag: symbolic.
   * src_bytes: continuous.
   * dst_bytes: continuous.
   * land: symbolic.
   * wrong_fragment: continuous.
   * urgent: continuous.
   * hot: continuous.
   * num_failed_logins: continuous.
   * logged_in: symbolic.
   * num_compromised: continuous.
   * root_shell: continuous.
   * su_attempted: continuous.
   * num_root: continuous.
   * num_file_creations: continuous.
   * num_shells: continuous.
   * num_access_files: continuous.
   * num_outbound_cmds: continuous.
   * is_host_login: symbolic.
   * is_guest_login: symbolic.
   * count: continuous.
   * srv_count: continuous.
   * serror_rate: continuous.
   * srv_serror_rate: continuous.
   * rerror_rate: continuous.
   * srv_rerror_rate: continuous.
   * same_srv_rate: continuous.
   * diff_srv_rate: continuous.
   * srv_diff_host_rate: continuous.
   * dst_host_count: continuous.
   * dst_host_srv_count: continuous.
   * dst_host_same_srv_rate: continuous.
   * dst_host_diff_srv_rate: continuous.
   * dst_host_same_src_port_rate: continuous.
   * dst_host_srv_diff_host_rate: continuous.
   * dst_host_serror_rate: continuous.
   * dst_host_srv_serror_rate: continuous.
   * dst_host_rerror_rate: continuous.
   * dst_host_srv_rerror_rate: continuous.
   */
  case class KddEntry(
       duration: Double, protocol_type: String, service: String, flag: String,
       src_bytes: Double, dst_bytes: Double,
       land: String, wrong_fragment: Double, urgent: Double, hot: Double,
       num_failed_logins: Double, logged_in: String,
       num_compromised: Double, root_shell: Double, su_attempted: Double,
       num_root: Double, num_file_creations: Double,
       num_shells: Double, num_access_files: Double, num_outbound_cmds: Double,
       is_host_login: String, is_guest_login: String,
       count: Double, srv_count: Double, serror_rate: Double, srv_serror_rate: Double,
       rerror_rate: Double,
       srv_rerror_rate: Double, same_srv_rate: Double, diff_srv_rate: Double,
       srv_diff_host_rate: Double, dst_host_count: Double,
       dst_host_srv_count: Double, dst_host_same_srv_rate: Double, dst_host_diff_srv_rate: Double,
       dst_host_same_src_port_rate: Double,
       dst_host_srv_diff_host_rate: Double, dst_host_serror_rate: Double, dst_host_srv_serror_rate: Double,
       dst_host_rerror_rate: Double,
       dst_host_srv_rerror_rate: Double, label: String = ""
     )
  case class ConversionFail(msg: List[String])
  type MaybeEntry = Either[KddEntry, ConversionFail]
  val ignore = Set(2, 3, 4, 7, 12, 21, 21, 42).map(_ - 1)

  /**
   * stream lines in the form
   * "0,tcp,http,SF,215,45076,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,1,1,0.00,0.00,0.00,0.00,1.00,0.00,0.00,0,0,0.00,0.00,0.00,0.00,0.00,0.00,0.00,0.00,normal."
   *
   * @param line
   * @return
   */
  def fromLine(line: String): MaybeEntry =
    fromList(line.split(",").toList)
  private def fromListUnsafe(args: List[String]) =
    KddEntry(
      duration =                    args(1).toDouble,
      protocol_type =               args(2),
      service =                     args(3),
      flag =                        args(4),
      src_bytes =                   args(5).toDouble,
      dst_bytes =                   args(6).toDouble,
      land =                        args(7),
      wrong_fragment =              args(8).toDouble,
      urgent =                      args(9).toDouble,
      hot =                         args(10).toDouble,
      num_failed_logins =           args(11).toDouble,
      logged_in =                   args(12),
      num_compromised =             args(13).toDouble,
      root_shell =                  args(14).toDouble,
      su_attempted =                args(15).toDouble,
      num_root =                    args(16).toDouble,
      num_file_creations =          args(17).toDouble,
      num_shells =                  args(18).toDouble,
      num_access_files =            args(19).toDouble,
      num_outbound_cmds =           args(20).toDouble,
      is_host_login =               args(21),
      is_guest_login =              args(22),
      count =                       args(23).toDouble,
      srv_count =                   args(24).toDouble,
      serror_rate =                 args(25).toDouble,
      srv_serror_rate =             args(26).toDouble,
      rerror_rate =                 args(27).toDouble,
      srv_rerror_rate =             args(28).toDouble,
      same_srv_rate =               args(29).toDouble,
      diff_srv_rate =               args(30).toDouble,
      srv_diff_host_rate =          args(31).toDouble,
      dst_host_count =              args(32).toDouble,
      dst_host_srv_count =          args(33).toDouble,
      dst_host_same_srv_rate =      args(34).toDouble,
      dst_host_diff_srv_rate =      args(35).toDouble,
      dst_host_same_src_port_rate = args(36).toDouble,
      dst_host_srv_diff_host_rate = args(37).toDouble,
      dst_host_serror_rate =        args(38).toDouble,
      dst_host_srv_serror_rate =    args(39).toDouble,
      dst_host_rerror_rate =        args(40).toDouble,
      dst_host_srv_rerror_rate =    args(41).toDouble,
      label =                       args(42)
    )
  def fromList(args: List[String]): MaybeEntry =
    if (args.length != 42) throw new RuntimeException("Invalid arguments length")
    else Try {
      Left[KddEntry, ConversionFail](fromListUnsafe(args))
    }.getOrElse(
      Right[KddEntry, ConversionFail](ConversionFail(
        (for {
          i <- args.indices if !ignore.contains(i) && Try {args(i).toDouble}.isFailure
        } yield s"args[$i] = '${args(i)}'")
          .toList
          /*
        args.indices
        .filter(ignore.contains)
        .map(x => (s"$x => ${args(x)}", Try {args(x).toDouble} ))
        .filter(x=> x._2.isFailure).map(x => x._1).toList
           */
      ))
    )
}

//object KddTcpSock extends Runnable {
//  override def run(): Unit = {
//    val env = StreamExecutionEnvironment.getExecutionEnvironment
//    env.readTextFile("./tmpfs/kddcup.data").addSink(
//      new SocketClientSink[String]("127.0.0.1", 3233, new SimpleStringSchema)
//    )
//  }
//}