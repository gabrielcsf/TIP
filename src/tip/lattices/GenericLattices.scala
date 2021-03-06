package tip.lattices

import scala.language.implicitConversions

/**
 * A (semi-)lattice.
 */
trait Lattice {

  /**
   * The type of the elements of this lattice.
   */
  type Element

  /**
   * The bottom element of this lattice.
   */
  def bottom: Element

  /**
   * The least upper bound of x and y.
   */
  def lub(x: Element, y: Element): Element

  /**
   * Returns true whenever x <= y in the lattice.
   */
  def leq(x: Element, y: Element): Boolean = lub(x, y) == y // rarely used, but easy to implement :-)
}

/**
 * The n-th product lattice made of l lattices.
 */
class UniformProductLattice[L <: Lattice](val sublattice: L, n: Int) extends Lattice {

  type Element = List[sublattice.Element]

  override def bottom: Element = List.fill(n)(sublattice.bottom)

  override def lub(x: Element, y: Element) = {
    if (x.length != y.length)
      error()
    (x zip y).map { case (xc, yc) => sublattice.lub(xc, yc) }
  }

  private def error() = throw new IllegalArgumentException("products not of same length")
}

/**
 * The flat lattice made of element of X.
 * Top is greater than every other element, and Bottom is less than every other element.
 * No additional ordering is defined.
 */
class FlatLattice[X] extends Lattice {

  sealed trait FlatElement

  case class FlatEl(el: X) extends FlatElement {
    override def toString = el.toString
  }

  case class Top() extends FlatElement

  case class Bot() extends FlatElement

  type Element = FlatElement

  /**
   * Lift an element of X into an element of the flat lattice.
   */
  implicit def lift(a: X): Element = FlatEl(a)

  /**
   * Un-lift an element of the lattice to an element of X.
   * If the element is Top or Bot then IllegalArgumentException is thrown.
   */
  implicit def unlift(a: Element): X = a match {
    case FlatEl(n) => n
    case _ => throw new IllegalArgumentException(s"cannot unlift $a")
  }

  override def bottom: Element = Bot()

  override def lub(x: Element, y: Element) = {
    if (x == Bot() || y == Top() || x == y)
      y
    else if (y == Bot() || x == Top())
      x
    else
      Top()
  }
}

/**
 * The product lattice made by l1 and l2.
 */
class PairLattice[L1 <: Lattice, L2 <: Lattice](val sublattice1: L1, val sublattice2: L2) extends Lattice {

  type Element = (sublattice1.Element, sublattice2.Element)

  override def bottom: Element = (sublattice1.bottom, sublattice2.bottom)

  override def lub(x: Element, y: Element) = (sublattice1.lub(x._1, y._1), sublattice2.lub(x._2, y._2))
}

/**
 * A lattice of maps from the set X to the lattice l.
 * The set X a subset of A and it is defined by the characteristic function ch, i.e. 
 * a is in X if and only if ch(a) returns true.
 * Bottom is the default value.
 */
class MapLattice[A, +L <: Lattice](ch: A => Boolean, val sublattice: L) extends Lattice {
  // note: 'ch' isn't used in the class, but having it as a class parameter avoids a lot of type annotations 

  type Element = Map[A, sublattice.Element] // TODO: replace this with a more type safe solution

  override def bottom: Element = Map().withDefaultValue(sublattice.bottom)

  override def lub(x: Element, y: Element) = {
    x.keys.foldLeft(y)((m, a) => m + (a -> sublattice.lub(x(a), y(a))))
  }
}

/**
 * The powerset lattice of X, where X is the subset of A
 * defined by the characteristic function ch.
 */
class PowersetLattice[A](ch: A => Boolean) extends Lattice {
  // note: 'ch' isn't used in the class, but having it as a class parameter avoids a lot of type annotations 

  type Element = Set[A]

  override def bottom: Element = ???

  override def lub(x: Element, y: Element) = ???
}

/**
 * The powerset lattice of X, where X is the subset of A
 * defined by the characteristic function ch, with reverse
 * subset ordering.
 */
class ReversePowersetLattice[A](s: Set[A]) extends Lattice {

  type Element = Set[A]

  override def bottom: Element = s

  override def lub(x: Element, y: Element) = x intersect y
}

/**
 * The lift lattice for l.
 * Supports implicit lifting and unlifting.
 */
class LiftLattice[+L <: Lattice](val sublattice: L) extends Lattice {

  type Element = Lifted

  sealed trait Lifted

  object Bottom extends Lifted {
    override def toString = "LiftBot"
  }

  case class Lift(n: sublattice.Element) extends Lifted

  override def bottom: Element = Bottom

  override def lub(x: Element, y: Element) = {
    (x, y) match {
      case (Bottom, t) => t
      case (t, Bottom) => t
      case (Lift(a), Lift(b)) => Lift(sublattice.lub(a, b))
    }
  }

  implicit def lift(x: sublattice.Element) = Lift(x)

  implicit def unlift(x: Element) = {
    x match {
      case Lift(s) => s
      case Bottom => sublattice.bottom
    }
  }
}
