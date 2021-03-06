import org.parboiled2._

case class Check(comp: (Double, Double) => Boolean, w: Double, c: Double, errorLevel: Int)

class ThresholdParser(val input: ParserInput) extends Parser {
  def Digits = rule { oneOrMore( (CharPredicate.Digit | '.') ) }
  def Number = rule { capture(Digits) ~> (_.toDouble) }
  def Values = rule { '|' ~ Number ~ '|' ~ Number }
  def Def: Rule1[Check] = rule { ('>' ~ Values ~> (
    new Check(_ > _, (_:Double), (_:Double), 0))  | '<' ~ Values ~> (
    new Check(_ < _, (_:Double), (_:Double), 0))) }
  def Parens = rule { '(' ~ Def ~ ')' }
  def Ops = rule { Parens ~ zeroOrMore('|' ~ Parens) ~> (List(_:Check) ::: _.toList) }
  def InputLine = rule { Ops ~ EOI }
}
