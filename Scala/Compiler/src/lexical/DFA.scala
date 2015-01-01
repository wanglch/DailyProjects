package lexical

import scala.collection.mutable
import scala.collection.immutable

trait IDFATransition[T] extends INFATransition[T] {
  def target : IDFAState[T]
}

trait IDFAState[T] extends INFAState[T] {
  def transitions : List[IDFATransition[T]]
}

class DFATransition[T](val symbol : T, val target : IDFAState[T]) extends IDFATransition[T]

class DFAState[T](var transitions : List[IDFATransition[T]]) extends IDFAState[T]

trait IDFA[T] extends INFA[T] {
  def start : IDFAState[T]

  override def states : List[IDFAState[T]] = super.states.asInstanceOf[List[IDFAState[T]]]

  def accepts : List[IDFAState[T]]

  def acceptsAttr : List[(IDFAState[T], IStateAttribute)]

  def dead : IDFAState[T]

  lazy val activeStates = states.diff(List(dead))
}

class TokenizedDFA(
  override val charTable : CharClassifyTable,
  override val start : IDFAState[CharCategory],
  override val accepts : List[IDFAState[CharCategory]],
  override val acceptsAttr : List[(IDFAState[CharCategory], IStateAttribute)])
  extends TokenizedNFA(charTable, start, accepts, acceptsAttr)
  with IDFA[CharCategory] {

  val dead : IDFAState[CharCategory] = (states.diff(accepts).filter(s => s.transitions.forall(t => t.target == s)) ::: List(null)).head

  import RegexAST._

  // Kleene construction
  def toRegex2 : Tree = {
    type State = IDFAState[CharCategory]

    assert(acceptsAttr.map(_._2).distinct.length == 1)

    var pathMapPair = (mutable.Map[(State, State), Tree](), mutable.Map[(State, State), Tree]())
    for (
      s1 <- activeStates;
      s2 <- activeStates
    ) {
      val path = s1.transitions.filter(_.target == s2).foldLeft[Tree](if (s1 == s2) Empty else null) { (path, t) =>
        Chars(charTable.rlookup(t.symbol)) | path
      }
      pathMapPair._1((s1, s2)) = path
    }

    for (s0 <- activeStates) {
      val prevMap = pathMapPair._1
      val curMap = pathMapPair._2

      for (
        s1 <- activeStates;
        s2 <- activeStates
      ) {
        curMap((s1, s2)) = prevMap(s1, s0) & (prevMap(s0, s0).kleeneStar & prevMap(s0, s2)) | prevMap(s1, s2)
      }

      pathMapPair = pathMapPair.swap
    }

    accepts.foldLeft[Tree](null) { (path, s) => path | pathMapPair._1((start, s))}
  }

  // States reducing
  def toRegex : Tree = {
    type State = IDFAState[CharCategory]
    type EdgeMap = immutable.Map[(State, State), Tree]

    assert(acceptsAttr.map(_._2).distinct.length == 1)

    def lookup(edgeMap : EdgeMap, s1 : State, s2 : State) : Tree = {
      edgeMap.getOrElse((s1, s2), null)
    }

    def remove(edgeMap : EdgeMap, state : State) : EdgeMap = {
      var newMap = edgeMap.filter(kv => kv._1._1 != state && kv._1._2 != state)
      for (
        predecessor <- edgeMap.iterator.filter(_._1._2 == state).map(_._1._1);
        successor <- edgeMap.iterator.filter(_._1._1 == state).map(_._1._2)
      ) {
        val path = lookup(edgeMap, predecessor, state) & (lookup(edgeMap, state, state).kleeneStar & lookup(edgeMap, state, successor))
        newMap = newMap.updated((predecessor, successor), path | lookup(edgeMap, predecessor, successor))
      }
      newMap
    }

    def iterate(start : State, accepts : List[State], edgeMap : EdgeMap) : List[Tree] = {
      if (accepts.isEmpty) return Nil

      val state = accepts.head
      val privateEdgeMap = accepts.tail.foldLeft[EdgeMap](edgeMap)(remove)
      val path = if (start == state) {
        lookup(privateEdgeMap, start, state).kleeneStar
      } else {
        (lookup(privateEdgeMap, start, start) |
          (lookup(privateEdgeMap, start, state) &
            (lookup(privateEdgeMap, state, state).kleeneStar & lookup(privateEdgeMap, state, start)))).kleeneStar &
          (lookup(privateEdgeMap, start, state) &
            lookup(privateEdgeMap, state, state).kleeneStar)
      }

      path :: iterate(start, accepts.tail, remove(edgeMap, state))
    }

    val edgeMap : EdgeMap = (for (s1 <- activeStates; s2 <- activeStates) yield ((s1, s2), s1.transitions.filter(_.target == s2).foldLeft[Tree](null) {
      (path, t) => Chars(charTable.rlookup(t.symbol)) | path
    })).filter(_._2 != null).toMap
    val edgeMapContainsStartAccepts = activeStates.diff(start :: accepts).foldLeft(edgeMap)(remove)

    iterate(start, accepts, edgeMapContainsStartAccepts).foldRight[Tree](null)(_ | _)
  }

  def toRegexPattern2 : String = toRegex2.toPattern

  def toRegexPattern : String = toRegex.toPattern

  def complement : TokenizedDFA = {
    val attr = acceptsAttr.map(_._2).distinct.ensuring(_.length == 1).head
    val newAccepts = states.diff(accepts)
    new TokenizedDFA(charTable, start, newAccepts, newAccepts.map((_, attr)))
  }

  def |(other : TokenizedDFA) : TokenizedDFA = {
    super.|(other).subset
  }

  def &(other : TokenizedDFA) : TokenizedDFA = {
    (complement | other.complement).complement
  }

  def -(other : TokenizedDFA) : TokenizedDFA = {
    this & other.complement
  }
}

final class TokenizedDFAEmulator(
  val charTable : CharClassifyTable,
  val start : Int,
  val acceptAttrs : Array[IStateAttribute],
  val transitions : Array[Array[Int]]) {
  outer =>

  val dead = (states.filter(i => acceptAttrs(i) == null && transitions(i).forall(_ == i)) ++ List(-1)).head

  override def equals(other : Any) : Boolean = {
    other.isInstanceOf[TokenizedDFAEmulator] && other.asInstanceOf[TokenizedDFAEmulator].equals(this)
  }

  def equals(other : TokenizedDFAEmulator) : Boolean = {
    (charTable == other.charTable
      && start == other.start
      && acceptAttrs.view == other.acceptAttrs.view
      && transitions.length == other.transitions.length
      && transitions.iterator.zip(other.transitions.iterator).forall {
      case ((a1, a2)) => a1.view == a2.view
    })
  }

  def states : Seq[Int] = 0 until transitions.length

  def toDFA : TokenizedDFA = {
    class Transition(val symbol : CharCategory, val target : State) extends IDFATransition[CharCategory]
    case class State(id : Int) extends IDFAState[CharCategory] {
      def transitions : List[Transition] = outer.transitions(id).toList.zipWithIndex.map {
        case ((target, ci)) => new Transition(new CharCategory(ci), State(target))
      }
    }
    new TokenizedDFA(
      outer.charTable,
      State(outer.start),
      outer.acceptAttrs.toList.zipWithIndex.filter(_._1 != null).map(p => State(p._2)),
      acceptAttrs.toList.zipWithIndex.filter(_._1 != null).map(p => (State(p._2), p._1)))
  }

  def minimized : TokenizedDFAEmulator = {

    val state2Group = Array.fill(states.length)(0)
    var gid = 0
    states.groupBy(acceptAttrs(_)).foreach {
      case (_, l) =>
        l.foreach { c => state2Group(c) = gid}
        gid += 1
    }

    def iterate() {
      for (
        (_, group) <- states.groupBy(state2Group(_));
        category <- charTable.categories
      ) {
        val newGroups = group.groupBy(state => state2Group(transitions(state)(category.value)))
        if (newGroups.size > 1) {
          newGroups.foreach {
            case (_, l) =>
              l.foreach { c => state2Group(c) = gid}
              gid += 1
          }
          iterate()
        }
      }
    }
    iterate()

    gid = 0
    states.groupBy(state2Group(_)).foreach {
      case (_, l) =>
        l.foreach { c => state2Group(c) = gid}
        gid += 1
    }

    val newTransitions = Array.fill(state2Group.distinct.length, charTable.categories.length)(0)
    val newAcceptAttrs = Array.fill(newTransitions.length)(acceptAttrs(0))
    states.foreach { s => newAcceptAttrs(state2Group(s)) = acceptAttrs(s)}
    for (
      state <- states;
      category <- charTable.categories
    ) {
      newTransitions(state2Group(state))(category.value) = state2Group(transitions(state)(category.value))
    }
    new TokenizedDFAEmulator(charTable, state2Group(start), newAcceptAttrs, newTransitions)
  }

  def minimized2 : TokenizedDFAEmulator = {
    toDFA.reversed.subset.reachable.reversed.subset.reachable.toEmulator.toDFAEmulator
  }

  def charTableCompacted : TokenizedDFAEmulator = {

    val oldCategory2New = charTable.categories.groupBy(c => transitions.toList.map(t => t(c.value))).toList.map(_._2).sortBy(_.head.value).zipWithIndex.
      flatMap {
      case ((l, i)) => l.map((_, new CharCategory(i)))
    }.toMap
    val newColumns = oldCategory2New.map(p => (p._2.value, p._1.value)).toList.sortBy(_._1).map(_._2).toArray

    val newCharTable = charTable.map(oldCategory2New)
    new TokenizedDFAEmulator(newCharTable, start, acceptAttrs, transitions.map(t => newColumns.map(t)))
  }

  def optimized : TokenizedDFAEmulator = minimized.charTableCompacted

  def optimized2 : TokenizedDFAEmulator = minimized2.charTableCompacted
}

object TokenizedDFAEmulator {

  def fromNFAEmulator(nfa : TokenizedNFAEmulator) : TokenizedDFAEmulator = {

    val stateSet2ID = mutable.Map[mutable.BitSet, Int]()
    val transitions = mutable.ArrayBuffer[Array[Int]]()
    var workList = List(nfa.closure(nfa.start))
    stateSet2ID(workList.head) = 0
    transitions += new Array[Int](nfa.charTable.categories.length)

    while (workList.nonEmpty) {
      val stateSet = workList.head
      val id = stateSet2ID(stateSet)
      workList = workList.tail

      for (category <- nfa.charTable.categories) {
        val targetSet = nfa.move(stateSet, category)
        transitions(id)(category.value) = stateSet2ID.getOrElseUpdate(targetSet, {
          transitions += new Array[Int](nfa.charTable.categories.length)
          workList = targetSet :: workList
          stateSet2ID.size
        })
      }
    }

    val start = stateSet2ID.filter(p => p._1.contains(nfa.start)).map(_._2).ensuring(_.size == 1).head
    val accept2Attr = stateSet2ID.toList.map { case (set, id) => (set & nfa.acceptSet, id)}.filter(_._1.nonEmpty).map {
      case (set, id) =>
        (id, nfa.acceptsAttr(set.maxBy { x => nfa.acceptsAttr(x).priority}))
    }.toMap

    new TokenizedDFAEmulator(
      nfa.charTable,
      start,
      (0 until transitions.length).map(accept2Attr.getOrElse(_, null)).toArray,
      transitions.toArray)

  }

}