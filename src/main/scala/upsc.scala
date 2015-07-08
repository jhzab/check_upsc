import scala.language.postfixOps
import scala.util.matching._
import scala.sys.process._
import scalaz._
import Scalaz._

case class Config(dev: String = "", check: Map[String, String] = Map())

object UPSC {
  val upscLocation = "/usr/bin/upsc"

  def parseConfig(args: Array[String]): \/[String, Config] = {
      val parser = new scopt.OptionParser[Config]("scopt") {
      head("check_upsc", "1.x")
      opt[String]('d', "dev") action { (x, c) =>
        c.copy(dev = x) } text("dev is a required parameter, ie. USER@HOSTNAME")
      opt[Map[String, String]]('c', "check") valueName("k1=v1,k2=v2...") action { (x, c) =>
        c.copy(check = x) } text("This specifies what to check and when to give a warning or critical message, ie. ups.load=w:80;c:90")
      help("help") text("prints this usage text")
    }

    parser.parse(args, Config()) match {
      case Some(config) => config.right
      case None => "Couldn't parse input!".left
    }
  }

  def fileExists(file: String): Boolean = new java.io.File(file).exists

  def getUpscOutput(param: String): \/[String, Array[String]] = {
    if (fileExists(upscLocation))
      (s"$upscLocation $param" !!).split('\n').right
    else
      "upsc binary doesn't exist.".left
  }

  def getValueFromParam(param: String): \/[String, Double] = {
    val Regex = """[\w\.]+:\s+(\d+.*)""".r
    param match {
      case Regex(v) => v.toDouble.right
      case _ => s"Could not match value from param line: $param".left
    }
  }

  def getThresholds(param: String, k: String, v: String): \/[String, (String, (Double, Int))] = {
    val Regex = """w:(\d+);c:(\d+)""".r
    v match {
      case Regex(w, c) => getValueFromParam(param) map ( v => v match {
        case i if i > c.toDouble => (k, (i, 2))
        case i if i > w.toDouble => (k, (i, 1))
        case i => (k, (i, 0))
      } )
      case _ => s"Couldn't extract thresholds for $k = $v".left
    }
  }

  def parseOutput(output: Array[String], config: Config): \/[String, Map[String, (Double, Int)]] = {
    val params = config.check.map{
      case (k,v) => output.find(_.startsWith(k)).\/>(s"Could not find $k in upsc output.") >>= { getThresholds(_,k,v) }
    }

    if (params.collect{ case -\/(v) => v }.nonEmpty)
      params.collect{ case -\/(v) => v }.head.left // FIXME
    else
      params.collect{ case \/-(v) => v }.toMap.right
  }

  def createReadableOutput(p: Map[String, (Double, Int)], level: Int): String = {
    val msgStart = level match {
      case 3 => "UNKNOWN: "
      case 2 => "CRITICAL: "
      case 1 => "WARNING: "
      case 0 => "OK: "
    }

    msgStart + p.map{ case (k,v) => s"${k}: ${v._1} " }.foldLeft("")(_ + _)
  }

  // TODO: add warn:crit values to perfdata
  def createPerfdataOutput(p: Map[String, (Double, Int)]): String = "| " + p.map{ case (k,v) => s"${k}=${v._1} "}.foldLeft("")(_ + _)

  def getLevel(p: Map[String, (Double, Int)]): \/[String, Int] = p.maxBy(_._2._2)._2._2.right

  def main(args: Array[String]): Unit = {
    val output = for {
      config <- parseConfig(args)
      output <- getUpscOutput(config.dev)
      parsed <- parseOutput(output, config)
      level <- getLevel(parsed)
    } yield (createReadableOutput(parsed, level) + createPerfdataOutput(parsed), level)

    output match {
      case \/-((output,level)) => println(output); sys.exit(level)
      case -\/(msg) => println(msg); sys.exit(3)
    }
  }
}
