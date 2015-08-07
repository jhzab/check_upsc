import scala.language.postfixOps
import scala.util.matching._
import scala.sys.process._
import scalaz._
import Scalaz._
import org.parboiled2._

case class Config(dev: String = "", check: Map[String, String] = Map())
case class Threshold(param: String, errorLevel: Int, value: Double)

object UPSC {
  val upscLocation = "/usr/bin/upsc"

  def parseConfig(args: Array[String]): \/[String, Config] = {
      val parser = new scopt.OptionParser[Config]("check_upsc") {
      head("check_upsc", "1.0")
      opt[String]('d', "dev") required() action { (x, c) =>
        c.copy(dev = x) } text("dev is a required parameter, ie. USER@HOSTNAME")
      opt[Map[String, String]]('c', "check") required() unbounded() valueName("k1=v1|k2=v2|...") action { (x, c) =>
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
    case scala.util.Failure(v) => "Error parsing check expressions!".left
  }

  def calculateErrorLevel(value: Double, checks: List[Check]): Int = {
    val errorLevels = checks.map(check => check.copy(errorLevel =
      math.max(
        check.comp(value, check.w) match { case true => 0 case false => 1},
        check.comp(value, check.c) match { case true => 0 case false => 2}
      )))
    println(errorLevels)
    errorLevels.maxBy(_.errorLevel).errorLevel
  }

  def getThreshold(line: String, param: String, c: String): \/[String, Threshold] = {
    for {
      value <- getValueFromLine(line)
      checks <- toEither(new ThresholdParser(c).InputLine.run())
    } yield Threshold(param, calculateErrorLevel(value, checks), value)
  }

  def parseOutput(output: Array[String], config: Config): \/[String, List[Threshold]] = {
    val params = config.check.map{
      case (param, checkExpr) => output.find(_.startsWith(param)).\/>(
        s"Could not find ${param} in upsc output.") >>= { getThreshold(_, param, checkExpr) }
    }

    if (params.collect{ case -\/(v) => v }.nonEmpty)
      params.collect{ case -\/(v) => v }.head.left
    else
      params.collect{ case \/-(v) => v }.toList.right
  }

  def createReadableOutput(thresholds: List[Threshold], level: Long): String = {
    val msgStart = level match {
      case 3 => "UNKNOWN: "
      case 2 => "CRITICAL: "
      case 1 => "WARNING: "
      case 0 => "OK: "
    }

    msgStart + thresholds.map{t => s"${t.param}: ${t.value} " }.foldLeft("")(_ + _)
  }

  def createPerfdataOutput(thresholds: List[Threshold]): String = "| " + thresholds.map{ t => s"${t.param}=${t.value} "}.foldLeft("")(_ + _)

  def getLevel(t: List[Threshold]): Int = t.maxBy(_.errorLevel).errorLevel

  def main(args: Array[String]): Unit = {
    val output = for {
      config <- parseConfig(args)
      output <- getUpscOutput(config.dev)
      parsed <- parseOutput(output, config)
    } yield (createReadableOutput(parsed, getLevel(parsed)) +
      createPerfdataOutput(parsed), getLevel(parsed))

    output match {
      case \/-((output,level)) => println(output); sys.exit(level.toInt)
      case -\/(msg) => println(msg); sys.exit(3)
    }
  }
}
