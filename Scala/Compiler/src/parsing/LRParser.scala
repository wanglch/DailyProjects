package parsing

import scala.collection.mutable
import scala.collection.immutable

trait ILRParser extends IParser {
  def actionTable : ILRActionTable
  def gotoTable : ILRGotoTable
}

object LRAction {
  sealed abstract class Action
  case class Shift(targetState : Int) extends Action
  case class Reduce(production : IProduction) extends Action
  case class Accept(production : IProduction) extends Action
}

trait ILRActionTable extends ((Int, Int) => LRAction.Action) {
}
final class LRActionTable(table : Array[Array[LRAction.Action]]) extends ILRActionTable {
  def apply(state : Int, tid : Int) = table(state)(tid)
}
final class CompressedLRActionTable(_table : Array[Array[LRAction.Action]]) extends ILRActionTable {
  val (table, state2Row) = {
    val state2Row = Array.fill(_table.length)(0)
    var id = 0
    ((for ((row, states) <- _table.zipWithIndex.groupBy(_._1.toList).iterator)
    yield {
      for ((_, state) <- states) state2Row(state) = id
      id += 1
      row
    }).toArray, state2Row)
  }

  def apply(state : Int, tid : Int) = table(state2Row(state))(tid)
}

trait ILRGotoTable extends ((Int, String) => Int) {
}
final class LRGotoTable(table : Array[mutable.HashMap[String, Int]]) extends ILRGotoTable {
  def apply(state : Int, nonTerm : String) = table(state)(nonTerm)
}
final class CompressedLRGotoTable(_table : Array[mutable.HashMap[String, Int]]) extends ILRGotoTable {
  val (table, state2Row) = {
    val state2Row = Array.fill(_table.length)(0)
    var id = 0
    ((for ((row, states) <- _table.zipWithIndex.groupBy(_._1).iterator)
    yield {
      for ((_, state) <- states) state2Row(state) = id
      id += 1
      row
    }).toArray, state2Row)
  }

  def apply(state : Int, nonTerm : String) = table(state2Row(state))(nonTerm)
}

trait ITableDrivenLRParserBuilder {
  def create : ILRParser
  def grammar : Grammar

  protected trait LRItem {
    def production : IProduction
    def pos : Int
    def closure() : List[LRItem]
    def move() : Option[(IGrammarSymbol, LRItem)]
    def complete : Boolean = pos == production.right.length
  }

  protected case class LR0Item(production : IProduction, pos : Int) extends LRItem {
    def closure() : List[LR0Item] = production.right.drop(pos) match {
      case (nt : INonTerminalSymbol) :: tail => nt.productions.map(LR0Item(_, 0))
      case _ => Nil
    }
    def move() : Option[(IGrammarSymbol, LR0Item)] = production.right.drop(pos) match {
      case head :: _ => Some((head, LR0Item(production, pos + 1)))
      case _ => None
    }
  }

  protected case class LR1Item(production : IProduction, pos : Int, lookAhead : TerminalSymbol) extends LRItem {
    def closure() : List[LR1Item] = production.right.drop(pos) match {
      case (nt : INonTerminalSymbol) :: tail =>
        for (la <- grammar.firstOfSymbols(tail ::: List(lookAhead), grammar.firstMap).toList.map(grammar.id2Term);
             p <- nt.productions) yield LR1Item(p, 0, la)
      case _ => Nil
    }
    def move() : Option[(IGrammarSymbol, LR1Item)] = production.right.drop(pos) match {
      case head :: _ => Some((head, LR1Item(production, pos + 1, lookAhead)))
      case _ => None
    }
  }

  protected def closure(set : List[LRItem]) : mutable.Set[LRItem] = {
    val c = mutable.Set[LRItem](set : _*)

    var workList = set
    while (workList.nonEmpty) {
      val head = workList.head
      workList = workList.tail

      for (item <- head.closure() if !c(item)) {
        workList = item :: workList
        c += item
      }
    }

    c
  }

  protected def buildCanonicalCollection(start : List[LRItem]) : (Array[List[(IGrammarSymbol, Int)]], Array[List[LRItem]]) = {
    val set2ID = mutable.Map[List[LRItem], Int](start -> 0)
    val transitions = mutable.ArrayBuffer[List[(IGrammarSymbol, Int)]](Nil)

    var workList = List(start)
    while (workList.nonEmpty) {
      val set = workList.head
      val id = set2ID(set)
      workList = workList.tail

      transitions(id) =
        (for (Some((symbol, targetItem)) <- closure(set).iterator.map(_.move())) yield (symbol, targetItem)).toList.groupBy(_._1).toList
          .map { case (symbol, symbolItems) =>
          val targetSet = symbolItems.map(_._2)
          (symbol, set2ID.getOrElseUpdate(targetSet, {
            transitions += Nil
            workList = targetSet :: workList
            set2ID.size
          }))
        }
    }

    (transitions.toArray, set2ID.toList.sortBy(_._2).map(_._1).toArray)
  }

  protected def create(
    transitions : Array[List[(IGrammarSymbol, Int)]],
    id2Set : Array[List[LRItem]],
    item2LookAheads : (Int, LRItem) => List[TerminalSymbol],
    reportConflict : Boolean = true) : TableDrivenLRParser = {

    val actionTable = Array.fill(id2Set.size, grammar.maxTermID + 1)(null : LRAction.Action)
    val gotoTable = Array.fill(id2Set.size)(mutable.HashMap[String, Int]())

    for ((trans, id) <- transitions.zipWithIndex) {
      val gotos = gotoTable(id)
      for ((nt : INonTerminalSymbol, target) <- trans) {
        gotos(nt.name) = target
      }

      val reduces = for (item <- id2Set(id).filter(_.complete);
                         action = if (item.production.left == grammar.start) LRAction.Accept(item.production) else LRAction.Reduce(item.production);
                         term <- item2LookAheads(id, item)) yield (term, action)
      val shifts = trans.collect { case (t : TerminalSymbol, target) => (t, LRAction.Shift(target) : LRAction.Action)}

      if (reportConflict && reduces.map(_._1).distinct.length < reduces.length) {
        println(s"Found Reduce-reduce conflict: $reduces")
      }
      if (reportConflict && (reduces.map(_._1).toSet & shifts.map(_._1).toSet).nonEmpty) {
        println(s"Found Shift-reduce conflict: $reduces, $shifts")
      }

      val actions = actionTable(id)
      for ((t, action) <- reduces ::: shifts) actions(t.token.id) = action
    }

    new TableDrivenLRParser(new CompressedLRActionTable(actionTable), new CompressedLRGotoTable(gotoTable))
  }
}

final class TableDrivenLR0ParserBuilder(val grammar : Grammar) extends ITableDrivenLRParserBuilder {
  def create : TableDrivenLRParser = {
    val (transitions, id2Set) = buildCanonicalCollection(grammar.start.productions.map(LR0Item(_, 0)))
    create(transitions, id2Set, (id, item) => grammar.terms, false)
  }
}

final class TableDrivenSLRParserBuilder(val grammar : Grammar) extends ITableDrivenLRParserBuilder {
  def create : TableDrivenLRParser = {
    val (transitions, id2Set) = buildCanonicalCollection(grammar.start.productions.map(LR0Item(_, 0)))
    create(transitions, id2Set, (id, item) => grammar.followMap(item.production.left).toList.map(grammar.id2Term))
  }
}

final class TableDrivenLALRParserBuilder(val grammar : Grammar) extends ITableDrivenLRParserBuilder {
  def create : TableDrivenLRParser = {
    val (transitions, id2Set) = buildCanonicalCollection(grammar.start.productions.map(LR0Item(_, 0)))

    import immutable.BitSet
    type LALRItem = (Int, LRItem)
    val item2LAs = mutable.Map[LALRItem, BitSet](id2Set(0).map(item => ((0, item), grammar.firstMap(TerminalSymbol.EOF))) : _*)
    val itemSpreadMap = mutable.Map[LALRItem, List[LALRItem]]()

    for (id <- 0 until transitions.length;
         item <- id2Set(id);
         Some((symbol, targetLR1Item : LR1Item)) <- closure(List(LR1Item(item.production, item.pos, TerminalSymbol.ERROR))).iterator.map(_.move());
         targetID = transitions(id).find(_._1 == symbol).get._2;
         targetItem = LR0Item(targetLR1Item.production, targetLR1Item.pos)) {
      val srcLALRItem = (id, item)
      val targetLALRItem = (targetID, targetItem)
      if (targetLR1Item.lookAhead == TerminalSymbol.ERROR) {
        itemSpreadMap(srcLALRItem) = targetLALRItem :: itemSpreadMap.getOrElse(srcLALRItem, Nil)
      } else {
        item2LAs(targetLALRItem) = item2LAs.getOrElse(targetLALRItem, BitSet.empty) + targetLR1Item.lookAhead.token.id
      }
    }

    var changed = true
    while (changed) {
      changed = false

      for ((item, las) <- item2LAs;
           target <- itemSpreadMap.getOrElse(item, Nil)) {
        val oldLAs = item2LAs.getOrElse(target, BitSet.empty)
        val newLAs = oldLAs | las
        if (oldLAs != newLAs) {
          changed = true
          item2LAs(target) = newLAs
        }
      }
    }

    create(transitions, id2Set, (id, item) => item2LAs.getOrElse((id, item), BitSet.empty).toList.map(grammar.id2Term))
  }
}

final class TableDrivenLR1ParserBuilder(val grammar : Grammar) extends ITableDrivenLRParserBuilder {
  def create : TableDrivenLRParser = {
    val (transitions, id2Set) = buildCanonicalCollection(grammar.start.productions.map(LR1Item(_, 0, TerminalSymbol.EOF)))
    create(transitions, id2Set, (id, item) => List(item.asInstanceOf[LR1Item].lookAhead))
  }
}

final class TableDrivenLRParser(val actionTable : ILRActionTable, val gotoTable : ILRGotoTable) extends ILRParser {

  def parse(_scanner : Iterator[lexical.IToken]) : Any = {
    val scanner = _scanner.buffered

    val stateStack = mutable.Stack[Int](0)
    val valueStack = mutable.Stack[Any]()

    try {
      while (stateStack.nonEmpty) {
        actionTable(stateStack.top, scanner.head.id) match {
          case LRAction.Shift(target) =>
            valueStack.push(scanner.next())
            stateStack.push(target)
          case LRAction.Reduce(p) =>
            p.action(valueStack)
            for (_ <- 0 until p.right.length) stateStack.pop()
            stateStack.push(gotoTable(stateStack.top, p.left.name))
          case LRAction.Accept(p) if scanner.head == lexical.IToken.Eof =>
            p.action(valueStack)
            return valueStack.ensuring(_.length == 1).pop()
          case LRAction.Accept(p) =>
            p.action(valueStack)
            for (_ <- 0 until p.right.length) stateStack.pop()
            stateStack.push(gotoTable(stateStack.top, p.left.name))
          case _ =>
            actionTable(stateStack.top, lexical.IToken.Empty.id) match {
              case LRAction.Shift(target) =>
                valueStack.push(null)
                stateStack.push(target)
              case _=>
                errors = s"Parse failed: found token ${scanner.head}" :: errors
                return null
            }
        }
      }
    } catch {
      case e : Exception =>
        errors = s"Parse failed: found token ${scanner.head}" :: errors
        return null
    }

    errors = s"Parse failed: input is too long, ${scanner.head}" :: errors
    null
  }
}

object TableDrivenLR0ParserFactory extends IParserFactory {
  def create(grammar : Grammar) : IParser = new TableDrivenLR0ParserBuilder(grammar).create
}
object TableDrivenSLRParserFactory extends IParserFactory {
  def create(grammar : Grammar) : IParser = new TableDrivenSLRParserBuilder(grammar).create
}
object TableDrivenLALRParserFactory extends IParserFactory {
  def create(grammar : Grammar) : IParser = new TableDrivenLALRParserBuilder(grammar).create
}
object TableDrivenLR1ParserFactory extends IParserFactory {
  def create(grammar : Grammar) : IParser = new TableDrivenLR1ParserBuilder(grammar).create
}