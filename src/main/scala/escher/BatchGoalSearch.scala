package escher

import escher.Synthesis.{IndexValueMap, ValueVector}
import collection.mutable
import BatchGoalSearch._

object BatchGoalSearch{
  sealed trait SearchResult{
    def foreach(f: (Int, Term) => Unit): Unit = this match {
      case FoundAtCost(cost, term) => f(cost, term)
      case NotFoundUnderCost(_) =>
    }
  }

  case class NotFoundUnderCost(cost: Int) extends SearchResult

  case class FoundAtCost(cost: Int, term: Term) extends SearchResult

  def emptyBuffer(): mutable.Map[Set[Int], SearchResult] = mutable.Map[Set[Int], SearchResult]()
}


class BatchGoalSearch(
                       val buffer: mutable.Map[Set[Int], SearchResult],
                       termOfCostAndVM: (Int, IndexValueMap) => Option[Term],
                       termsOfCost: Int => Iterable[(ValueVector,Term)],
                       boolOfVM: IndexValueMap => Option[Term]) {


  def search(cost: Int, currentGoal: IndexValueMap): Option[Term] = {
    val keySet = currentGoal.keySet
    buffer.get(keySet).foreach{
      case FoundAtCost(c, term) if c <= cost =>
        return Some(term)
      case NotFoundUnderCost(c) if c >= cost =>
        return None
      case _ =>
    }

    termOfCostAndVM(cost, currentGoal) match {
      case Some(term) =>
        buffer(keySet) = FoundAtCost(cost, term)
        Some(term)
      case None =>
        for(
          c <- 1 until cost;
          (thenVec ,tThen) <- termsOfCost(c);
          (vm1,_,vm3) <- IndexValueMap.splitValueMap(currentGoal, thenVec);
          tCond <- boolOfVM(vm1);
          tElse <- search(cost-c, vm3)
        ){
          import DSL._
          val t = `if`(tCond)(tThen)(tElse)
          buffer(keySet) = FoundAtCost(cost, t)
          return Some(t)
        }
        buffer(keySet) = NotFoundUnderCost(cost)
        None
    }
  }
}

class BatchGoalSearchLoose(maxCompCost: Int,
                           termOfCostAndVM: (Int, IndexValueMap) => Option[Term],
                           termsOfCost: Int => Iterable[(ValueVector,Term)],
                           boolOfVM: IndexValueMap => Option[(Int,Term)]) {
  private val buffer: mutable.Map[Set[Int], SearchResult] = mutable.Map()


  def maxSatConditions(vm: IndexValueMap): Option[((Int, Term), List[Int])] = {
    var keyList = vm.keys.filter(i => vm(i) == ValueBool(true)).toList.sorted.reverse
    var vm1 = vm
    while(keyList.nonEmpty){
      boolOfVM(vm1).foreach(x => return Some(x -> keyList))
      vm1 = vm1.updated(keyList.head, ValueBool(false))
      keyList = keyList.tail
    }
    None
  }

  def search(cost: Int, currentGoal: IndexValueMap): Option[(Int,Term)] = {
    val keySet = currentGoal.keySet
    buffer.get(keySet).foreach {
      case FoundAtCost(c, term) if c <= cost =>
        return Some(c -> term)
      case NotFoundUnderCost(c) if c >= cost =>
        return None
      case _ =>
    }

    def buffered(searchResult: SearchResult): Option[(Int,Term)] = {
      buffer(keySet) = searchResult
      searchResult match {
        case NotFoundUnderCost(_) => None
        case FoundAtCost(c, term) => Some(c -> term)
      }
    }

    val maxCost = math.min(maxCompCost, cost)
    for (c <- 1 to maxCost) {
      termOfCostAndVM(c, currentGoal).foreach { term =>
        return buffered(FoundAtCost(c, term))
      }
    }

    val ifCost = 1
    //noinspection ReplaceToWithUntil
    for (
      cThen <- 1 to maxCost - 1 - ifCost; // save one for cCond
      (thenVec, tThen) <- termsOfCost(cThen);
      (vm, _, _) <- IndexValueMap.splitValueMap(currentGoal, thenVec);
      ((cCond, tCond), trueKeys) <- maxSatConditions(vm); //this requirement maybe too much
      elseGoal = currentGoal -- trueKeys;
      (cElse, tElse) <- search(cost - cThen - cCond - ifCost, elseGoal)
    ) {
      import DSL._

      val totalCost = cElse + cThen + cCond + ifCost
      val t = `if`(tCond)(tThen)(tElse)
      return buffered(FoundAtCost(totalCost, t))
    }

    buffered(NotFoundUnderCost(cost))
  }

  def searchMin(cost: Int, currentGoal: IndexValueMap): Option[(Int,Term)] = {
    val keySet = currentGoal.keySet
    buffer.get(keySet).foreach {
      case FoundAtCost(c, term) if c <= cost =>
        return Some(c -> term)
      case NotFoundUnderCost(c) if c >= cost =>
        return None
      case _ =>
    }

    def buffered(searchResult: SearchResult): Option[(Int,Term)] = {
      buffer(keySet) = searchResult
      searchResult match {
        case NotFoundUnderCost(_) => None
        case FoundAtCost(c, term) => Some(c -> term)
      }
    }

    val maxCost = math.min(maxCompCost, cost)
    for (c <- 1 to maxCost) {
      termOfCostAndVM(c, currentGoal).foreach { term =>
        return buffered(FoundAtCost(c, term))
      }
    }

    val ifCost = 1

    var minCostCandidate: Option[(Int, Term)] = None
    //noinspection ReplaceToWithUntil
    for (
      cThen <- 1 to maxCost - 1 - ifCost; // save one for cCond
      (thenVec, tThen) <- termsOfCost(cThen);
      (vm, _, _) <- IndexValueMap.splitValueMap(currentGoal, thenVec);
      ((cCond, tCond), trueKeys) <- maxSatConditions(vm); //this requirement maybe too much
      elseGoal = currentGoal -- trueKeys;
      (cElse, tElse) <- searchMin(cost - cThen - cCond - ifCost, elseGoal)
    ) {
      import DSL._

      val totalCost = cElse + cThen + cCond + ifCost
      val t = `if`(tCond)(tThen)(tElse)

      minCostCandidate match {
        case Some((c, _)) =>
          if (c > totalCost)
            minCostCandidate = Some(totalCost -> t)
        case None =>
          minCostCandidate = Some(totalCost -> t)
      }
    }
    minCostCandidate match{
      case Some((c, t)) => buffered(FoundAtCost(c,t))
      case None => buffered(NotFoundUnderCost(cost))
    }
  }

}
