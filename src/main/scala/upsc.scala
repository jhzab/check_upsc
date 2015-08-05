import scala.language.postfixOps
import scala.util.matching._
import scala.sys.process._
import scalaz._
import Scalaz._
import org.parboiled2._

case class Config(dev: String = "", check: Map[String, String] = Map())

object UPSC {
  val upscLocation = "/usr/bin/upsc"

  def parseConfig(args: Array[String]): \/[String, Config] = {
      val parser = new scopt.OptionParser[Config]("check_upsc") {
      head("check_upsc", "1.0")
      opt[String]('d', "dev") required() action { (x, c) =>
        c.copy(dev = x) } text("dev is a required parameter, ie. USER@HOSTNAME")
      opt[Map[String, String]]('c', "check") required() unbounded() valueName("k1=v1,...") action { (x, c) =>
          c.copy(check = c.check ++ x) } text("This specifies what to check and when to give a warning or critical message, ie. ups.load=w:80&c:90")
      help("help") text("prints this usage text")
      note("\nBoth --check and --dev need to be defined!")
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

  def getValueFromLine(param: String): \/[String, Double] = {
    val Regex = """[\w\.]+:\s+(\d+.*)""".r
    param match {
      case Regex(v) => v.toDouble.right
      case _ => s"Could not match value from param line: $param".left
    }
  }

  def toEither(t: scala.util.Try[List[Check]]): \/[String, List[Check]] = t match {
    case scala.util.Success(v) => v.right
    case scala.util.Failure(v) => v.getMessage.left
  }

  def calculateThreshold(l: List[Check], value: Double): (String, (Double, Long)) = {
    val errorLevel = l.map(check =>
      math.max(check.comp(value, check.w) match { case true => 0l case false => 1l},
        check.comp(value, check.c) match { case true => 0l case false => 2l})).max

    errorLevel
  }

  def getThresholds(line: String, param: String, c: String): \/[String, (String, (Double, Long))] = {
    for {
      value <- getValueFromLine(line)
      checks <- toEither(new ThresholdParser(c).InputLine.run())
    } yield calculateThreshold(checks, value)

//      case _ => s"Couldn't extract thresholds for $k = $v".left
  }

  def parseOutput(output: Array[String], config: Config): \/[String, Map[String, (Double, Long)]] = {
    val params = config.check.map{
      case (k,v) => output.find(_.startsWith(k)).\/>(s"Could not find $k in upsc output.") >>= { getThresholds(_,k,v) }
    }

    if (params.collect{ case -\/(v) => v }.nonEmpty)
      params.collect{ case -\/(v) => v }.head.left // FIXME
    else
      params.collect{ case \/-(v) => v }.toMap.right
  }

  def createReadableOutput(p: Map[String, (Double, Long)], level: Long): String = {
    val msgStart = level match {
      case 3 => "UNKNOWN: "
      case 2 => "CRITICAL: "
      case 1 => "WARNING: "
      case 0 => "OK: "
    }

    msgStart + p.map{ case (k,v) => s"${k}: ${v._1} " }.foldLeft("")(_ + _)
  }

  // TODO: add warn:crit values to perfdata
  def createPerfdataOutput(p: Map[String, (Double, Long)]): String = "| " + p.map{ case (k,v) => s"${k}=${v._1} "}.foldLeft("")(_ + _)

  def getLevel(p: Map[String, (Double, Long)]): \/[String, Long] = p.maxBy(_._2._2)._2._2.right

  def main(args: Array[String]): Unit = {
    val p = new ThresholdParser("(>,80.5,90),(<,30,10)").InputLine.run()
    println(p)
    val output = for {
      config <- parseConfig(args)
      output <- getUpscOutput(config.dev)
      parsed <- parseOutput(output, config)
      level <- getLevel(parsed)
    } yield (createReadableOutput(parsed, level) + createPerfdataOutput(parsed), level)

    output match {
      case \/-((output,level)) => println(output); sys.exit(level.toInt)
      case -\/(msg) => println(msg); sys.exit(3)
    }
  }
}
