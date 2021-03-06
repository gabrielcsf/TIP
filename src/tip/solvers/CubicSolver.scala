package tip.solvers

import tip.logging.Log

import scala.collection.mutable
import scala.language.implicitConversions

/**
 * The cubic solver.
 * @tparam T type of tokens
 * @tparam V type of variables
 */
class CubicSolver[T, V]() {

  val log = Log.typeLogger[this.type](Log.Level.Verbose)

  var lastTknId = -1

  def nextTokenId = {
    lastTknId += 1; lastTknId
  }

  class Node(
              val succ: mutable.Set[V] = mutable.Set(), // note: the edges between nodes go via the variables
              val tokenSol: mutable.BitSet = new mutable.BitSet(), // the current solution bitvector
              val conditionals: mutable.Map[Int, mutable.Set[(V, V)]] = mutable.Map() // the pending conditional constraints
              ) {
    override def toString() = this.hashCode().toString()
  }

  /**
   * The map from variables to nodes.
   */
  val nodeState: mutable.Map[V, Node] = mutable.Map()

  /**
   * Provides an index for each token that we have seen.
   */
  val tokenToInt: mutable.Map[T, Int] = mutable.Map()

  /**
   * Returns the index associated with the given token.
   */
  implicit private def getTokenInt(tkn: T): Int = {
    tokenToInt.getOrElse(tkn, {
      tokenToInt += (tkn -> nextTokenId); tokenToInt(tkn)
    })
  }

  /**
   * Retrieve the node associated with the given variable.
   */
  private def getOrPutNode(x: V): Node = {
    nodeState.getOrElse(x, {
      nodeState(x) = new Node(); nodeState(x)
    })
  }

  /**
   * Detect a cycle along the graph
   */
  private def detectCycles(first: V, current: V, visited: Set[V]): Set[V] = {
    val currentNode = getOrPutNode(current)
    val firstNode = getOrPutNode(first)
    if(currentNode != firstNode && visited.contains(current)) {
      Set()
    }
    else if(currentNode == firstNode && visited.contains(current)) {
      visited
    }
    else {
      val cycles = currentNode.succ.toSet.map { v: V =>
        detectCycles(first, v, visited + current)
      }
      cycles.flatten
    }
  }

  /**
   * Collapses a cycle and returns one of the variable
   * together with all the tokens that need to be propagated
   * due to the collapsing.
   */
  private def collapseCycle(cycle: Set[V]) {
    log.debug(s"Collapsing cycle $cycle")
    if(cycle.nonEmpty) {
      val first = getOrPutNode(cycle.head)
      cycle.tail.foreach { cso =>
        val oldState = getOrPutNode(cso)
        first.succ ++= oldState.succ
        first.conditionals.keys.foreach { k => first.conditionals(k) ++= oldState.conditionals(k) }
        first.tokenSol |= oldState.tokenSol
        nodeState(cso) = first
      }
    }
  }

  /**
   * Add the set of tokens s to the variable x and propagate along the graph
   */
  private def addAndPropagateBits(s: mutable.BitSet, x: V) {
    val state = getOrPutNode(x)
    val old = state.tokenSol.clone()
    val newTokens = old | s
    if (newTokens != old) {
      state.tokenSol |= s

      val diff = newTokens &~ old

      // Add edges
      diff.foreach { t =>
        state.conditionals.getOrElse(t, Set()).foreach { case (v1, v2) =>
          addSubsetConstraint(v1, v2)
        }
      }
      diff.foreach { t => state.conditionals.remove(t) }

      // Flow
      state.succ.foreach { s =>
        addAndPropagateBits(newTokens, s)
      }
    }
  }

  /**
   * Adds a constraint of type <i>t</i> &#8712; <i>x</i>
   */
  def addConstantConstraint(t: T, x: V) = {
    log.debug(s"Adding constraint $t \u2208 $x")
    val bs = new mutable.BitSet()
    bs.add(t)
    addAndPropagateBits(bs, x)
  }

  /**
   * Adds a constraint of type <i>x</i> &#8838; <i>y</i>
   */
  def addSubsetConstraint(x: V, y: V) = {
    log.debug(s"Adding constraint $x \u2286 $y ")
    val nx = getOrPutNode(x)
    val ny = getOrPutNode(y)

    // Add the edge
    nx.succ += y

    // Collapse newly introduced cycle
    collapseCycle(detectCycles(x, x, Set()))

    // Propagate the bits
    addAndPropagateBits(nx.tokenSol, y)

  }

  /**
   * Adds a constraint of type <i>t</i> &#8712; <i>x</i> &#8658; <i>y</i> &#8838; <i>z</i>
   */
  def addConditionalConstraint(t: T, x: V, y: V, z: V) = {
    log.debug(s"Adding constraint $t \u2208 $x => $y \u2286 $z ")
    val xn = getOrPutNode(x)
    if (xn.tokenSol.contains(t)) {
      // Already enabled
      addSubsetConstraint(y, z)
    } else {
      // Not yet enabled, add to list
      xn.conditionals.getOrElse(t, {
        xn.conditionals(t) = mutable.Set(); xn.conditionals(t)
      }).add((y, z))
    }
  }

  /**
   * Returns the (partial) solution
   */
  def getSolution: Map[V, Set[T]] = {
    val intToToken = tokenToInt.map(p => p._2 -> p._1).toMap[Int, T]
    nodeState.keys.map(v => v -> getOrPutNode(v).tokenSol.map(i => intToToken(i)).toSet).toMap
  }

  override def toString = {
    val intToToken = tokenToInt.map(p => p._2 -> p._1).toMap[Int, T]
    nodeState.map { case (v, n) =>
      s"$v = { ${n.tokenSol.map(i => intToToken(i)).mkString(", ")} }"
    }.mkString("\n")
  }
}