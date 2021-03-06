package escher

import scala.collection.immutable

/**
  * using "import DSL._" to write terms more easily
  */
object DSL {
  import Term._
  import Type._


  implicit class ComponentFromString(name: String){
    /** allowing us to write <i>"Component(n, args*)"</i> as <i>"n $ (args*)"</i> */
    def $ (args: Term*) = Component(name, args.toIndexedSeq)
  }

  def v(name: String) = Var(name)

  def c(name: String, args: Term*) = Component(name, args.toIndexedSeq)

  def `if`(condition: Term)(thenBranch: Term)(elseBranch: Term) = If(condition, thenBranch, elseBranch)

  def `var`(name: String) = Var(name)

  def tyVar(id: Int) = TVar(id)

  def tyFixVar(id: Int) = TFixedVar(id)

  implicit def intConversion(i: Int): ValueInt = ValueInt(i)
  implicit def boolConversion(b: Boolean): ValueBool = ValueBool(b)
  implicit def binaryTreeConversion(t: BinaryTree[TermValue]): ValueTree = ValueTree(t)
  implicit def binaryTreeConversion[A](t: BinaryTree[A])(implicit conv: A => TermValue): ValueTree = ValueTree(t.map(conv))
  implicit def pairConversion(pair: (TermValue, TermValue)): ValuePair = ValuePair(pair._1, pair._2)
  implicit def pairConversion2[A,B](pair: (A, B))(implicit convA: A => TermValue, convB: B => TermValue): ValuePair = {
    ValuePair(pair._1, pair._2)
  }
  implicit def listConversion[A](list: List[A])(implicit convA: A => TermValue): ValueList = ValueList(list.map(convA))

  def listValue(terms: TermValue*) = ValueList(terms.toList)

  val tyInt: TApply = TInt.of()
  val tyBool: TApply = TBool.of()
  def tyList(param: Type): TApply = TList.of(param)
  def tyMap(kt: Type, vt: Type): TApply = TMap.of(kt,vt)
  def tyTree(param: Type): TApply = TTree.of(param)
  def tyPair(t1: Type, t2: Type): TApply = TPair.of(t1,t2)

  def argList(termValues: TermValue*): IS[TermValue] = termValues.toIndexedSeq
}
