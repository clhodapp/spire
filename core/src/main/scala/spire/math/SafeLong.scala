package spire.math

import scala.math.{abs, signum, ScalaNumber, ScalaNumericConversions}
import scala.annotation.tailrec

// TODO: might be nice to write a macro-version of fold here to
// avoid using closures for everything.

/**
 * Provides a type to do safe long arithmetic. This type will never overflow,
 * but rather convert the underlying long to a BigInt as need and back down
 * to a Long when possible.
 */
sealed trait SafeLong extends ScalaNumber with ScalaNumericConversions with Ordered[SafeLong] {
  lhs =>

  def signum: Int = fold(scala.math.signum(_).toInt, _.signum)

  def +(rhs: SafeLong): SafeLong = rhs.fold(this + _, this + _)
  def -(rhs: SafeLong): SafeLong = rhs.fold(this - _, this - _)
  def *(rhs: SafeLong): SafeLong = rhs.fold(this * _, this * _)
  def /(rhs: SafeLong): SafeLong = rhs.fold(this / _, this / _)
  def %(rhs: SafeLong): SafeLong = rhs.fold(this % _, this % _)
  def /~(rhs: SafeLong): SafeLong = lhs / rhs
  def /%(rhs: SafeLong): (SafeLong, SafeLong) = rhs.fold(this /% _, this /% _)

  def &(rhs: SafeLong): SafeLong = rhs.fold(this & _, this & _)
  def |(rhs: SafeLong): SafeLong = rhs.fold(this & _, this & _)
  def ^(rhs: SafeLong): SafeLong = rhs.fold(this & _, this & _)
  
  def +(rhs: Long): SafeLong
  def -(rhs: Long): SafeLong
  def *(rhs: Long): SafeLong
  def /(rhs: Long): SafeLong
  def %(rhs: Long): SafeLong
  def /%(rhs: Long): (SafeLong, SafeLong)
  def &(rhs: Long): SafeLong
  def |(rhs: Long): SafeLong
  def ^(rhs: Long): SafeLong
  
  def +(rhs: BigInt): SafeLong
  def -(rhs: BigInt): SafeLong
  def *(rhs: BigInt): SafeLong
  def /(rhs: BigInt): SafeLong
  def %(rhs: BigInt): SafeLong
  def /%(rhs: BigInt): (SafeLong, SafeLong)
  def &(rhs: BigInt): SafeLong
  def |(rhs: BigInt): SafeLong
  def ^(rhs: BigInt): SafeLong

  def min(that: SafeLong): SafeLong = if (this < that) this else that
  def max(that: SafeLong): SafeLong = if (this > that) this else that

  def <<(n: Int): SafeLong
  def >>(n: Int): SafeLong


  /**
   * Exponentiation function, e.g. x^y
   *
   * If base^exponent doesn't fit in a Long, the result will overflow (unlike
   * scala.math.pow which will return +/- Infinity). 
   */
  final def **(rhs:Int):SafeLong = pow(rhs)
  final def pow(rhs:Int):SafeLong = {
    assert (rhs >= 0)
    _pow(SafeLong.one, this, rhs)
  }

  @tailrec private final def _pow(total:SafeLong, base:SafeLong, exp:Int): SafeLong = {
    if (exp == 0) return total
    else if ((exp & 1) == 1) _pow(total * base, base * base, exp >> 1)
    else _pow(total, base * base, exp >> 1)
  }

  final def modPow(exp:Int, mod:SafeLong):SafeLong = {
    assert (exp >= 0)
    _modPow(SafeLong.one % mod, this, exp, mod)
  }

  @tailrec private final def _modPow(total:SafeLong, base:SafeLong, exp:Int, mod:SafeLong): SafeLong = {
    if (exp == 0) return total
    else if ((exp & 1) == 1) _modPow((total * base) % mod, (base * base) % mod, exp >> 1, mod)
    else _modPow(total, (base * base) % mod, exp >> 1, mod)
  }

  def abs = if (this.compare(SafeLong.zero) < 0) -this else this

  def gcd(that: SafeLong): SafeLong

  def unary_-(): SafeLong

  def isLong: Boolean = fold(_ => true, _ => false)
  def isBigInt: Boolean = fold(_ => false, _ => true)

  override def toByte: Byte = toLong.toByte
  override def toShort: Short = toLong.toShort
  override def toInt: Int = toLong.toInt
  def toBigInt: BigInt
  def toBigDecimal: BigDecimal

  override def toString: String = fold(_.toString, _.toString)

  final def isWhole: Boolean = true

  def fold[A,B <: A,C <: A](f: Long => B, g: BigInt => C): A

  def map(f: Long => Long, g: BigInt => BigInt): SafeLong =
    fold(x => SafeLongLong(f(x)), x => SafeLongBigInt(g(x)))
  
  /**
   * If `this` SafeLong is backed by a Long and `that` SafeLong is backed by
   * a Long as well, then `f` will be called with both values. Otherwise,
   * `this` and `that` will be converted to `BigInt`s and `g` will be called
   * with these `BigInt`s.
   */
  def foldWith[A,B <: A,C <: A](that: SafeLong)(f: (Long,Long) => B, g: (BigInt,BigInt) => C): A =
    fold(x => that.fold(f(x, _), g(BigInt(x), _)), g(_, that.toBigInt))
}


object SafeLong {
  final val SignBit = 0x8000000000000000L

  final val zero: SafeLong = SafeLongLong(0L)
  final val one: SafeLong = SafeLongLong(1L)

  implicit def apply(x: Long): SafeLong = SafeLongLong(x)

  implicit def apply(x: BigInt): SafeLong = if (x.bitLength > 63) {
    SafeLongBigInt(x)
  } else {
    SafeLongLong(x.toLong)
  }

  def apply(s: String): SafeLong = try {
    SafeLong(java.lang.Long.parseLong(s))
  } catch {
    case _: Exception => SafeLong(BigInt(s))
  }
}


case class SafeLongLong private[math] (x: Long) extends SafeLong {
  def +(y: Long): SafeLong = {
    val a = x + y

    // Check if the sign bit of x ^ y != 0 && sign bit of x ^ a is 0.
    if ((~(x ^ y) & (x ^ a)) >= 0L) {
      SafeLongLong(a)
    } else {
      SafeLongBigInt(BigInt(x) + y)
    }
  }

  def -(y: Long): SafeLong = {
    val a = x - y

    if (((x ^ y) & (x ^ a)) >= 0L) {
      SafeLongLong(a)
    } else {
      SafeLongBigInt(BigInt(x) - y)
    }
  }

  def *(y: Long): SafeLong = {
    val xy = x * y
    if (x == 0 || (y == xy / x && !(x == -1L && y == Long.MinValue)))
      SafeLongLong(x * y)
    else
      SafeLongBigInt(BigInt(x) * y)
  }

  def /(y: Long): SafeLong = if (y != -1 || x != Long.MinValue)
    SafeLongLong(x / y)
  else
    SafeLongBigInt(Long.MaxValue) + 1

  def %(y: Long): SafeLong = SafeLongLong(x % y)
  def /%(y: Long) = (SafeLongLong(x / y), SafeLongLong(x % y))

  def &(y: Long): SafeLong = SafeLongLong(x & y)
  def |(y: Long): SafeLong = SafeLongLong(x | y)
  def ^(y: Long): SafeLong = SafeLongLong(x ^ y)

  def +(y: BigInt): SafeLong = SafeLong(y + x)
  def -(y: BigInt): SafeLong = SafeLong(BigInt(x) - y)
  def *(y: BigInt): SafeLong = SafeLong(y * x)
  def /(y: BigInt): SafeLong =
    if (y.bitLength > 63) SafeLongLong(0L) else SafeLongLong(x / y.toLong)
  def %(y: BigInt): SafeLong =
    if (y.bitLength > 63) x else SafeLongLong(x % y.toLong)
  def /%(y: BigInt) =
    if (y.bitLength > 63) {
      (SafeLongLong(0), this)
    } else {
      val n = y.toLong
      (SafeLongLong(x / n), SafeLongLong(x % n))
    }

  def &(y: BigInt): SafeLong = SafeLongLong(x & y.toLong)
  def |(y: BigInt): SafeLong = SafeLongLong(x | y.toLong)
  def ^(y: BigInt): SafeLong = SafeLongLong(x ^ y.toLong)

  def unary_-(): SafeLong = if (x == Long.MinValue)
    SafeLongBigInt(-BigInt(x))
  else
    SafeLongLong(-x)

  override def <(that: SafeLong): Boolean = that match {
    case SafeLongLong(y) => x < y
    case SafeLongBigInt(y) => x < y
  }
  override def <=(that: SafeLong): Boolean = that match {
    case SafeLongLong(y) => x <= y
    case SafeLongBigInt(y) => x <= y
  }
  override def >(that: SafeLong): Boolean = that match {
    case SafeLongLong(y) => x > y
    case SafeLongBigInt(y) => x > y
  }
  override def >=(that: SafeLong): Boolean = that match {
    case SafeLongLong(y) => x >= y
    case SafeLongBigInt(y) => x >= y
  }
  def compare(that: SafeLong): Int = that match {
    case SafeLongLong(y) => x compare y
    case SafeLongBigInt(y) => if (x < y) -1 else if (x > y) 1 else 0
  }

  def <<(n: Int): SafeLong = if (n < 0)
    >>(-n)
  else if (n < 64 && x <= (0x7fffffffffffffffL >> (63 - n)))
    SafeLongLong(x << n)
  else
    SafeLongBigInt(BigInt(x) << n)

  def >>(n: Int): SafeLong = if (n < 0) <<(-n) else SafeLongLong(x >> n)

  override def equals(that: Any): Boolean = that match {
    case SafeLongLong(y) => x == y
    case SafeLongBigInt(y) => x == y
    case t: BigInt => if (t.bitLength > 63) false else t.toLong == x
    case that => unifiedPrimitiveEquals(that)
  }

  def gcd(that: SafeLong) = spire.math.gcd(x, that.fold(identity, n => (n % x).toLong))

  def doubleValue: Double = x.toDouble
  def floatValue: Float = x.toFloat
  def longValue: Long = x.toLong
  def intValue: Int = x.toInt
  
  def underlying: java.lang.Long = new java.lang.Long(x)

  override def toLong: Long = x
  def toBigInt: BigInt = BigInt(x)
  def toBigDecimal = BigDecimal(x)

  def fold[A,B <: A,C <: A](f: Long => B, g: BigInt => C): A = f(x)
}


case class SafeLongBigInt private[math] (x: BigInt) extends SafeLong {
  def +(y: Long): SafeLong = if ((x.signum ^ y) < 0) SafeLong(x + y) else SafeLongBigInt(x + y)
  def -(y: Long): SafeLong = if ((x.signum ^ y) < 0) SafeLongBigInt(x - y) else SafeLong(x - y)
  def *(y: Long): SafeLong = if (y == 0) SafeLongBigInt(0) else SafeLongBigInt(x * y)
  def /(y: Long): SafeLong = SafeLong(x / y)
  def %(y: Long): SafeLong = SafeLong(x % y)
  def /%(y: Long): (SafeLong, SafeLong) = {
    val (q, r) = x /% BigInt(y)
    (SafeLong(q), SafeLong(r))
  }

  def &(y: Long): SafeLong = SafeLongLong(x.toLong & y)
  def |(y: Long): SafeLong = SafeLongLong(x.toLong | y)
  def ^(y: Long): SafeLong = SafeLongLong(x.toLong ^ y)

  def +(y: BigInt): SafeLong = if ((x.signum ^ y.signum) < 0) SafeLong(x + y) else SafeLongBigInt(x + y)
  def -(y: BigInt): SafeLong = if ((x.signum ^ y.signum) < 0) SafeLongBigInt(x - y) else SafeLong(x - y)
  def *(y: BigInt): SafeLong = SafeLongBigInt(x * y)
  def /(y: BigInt): SafeLong = SafeLong(x / y)
  def %(y: BigInt): SafeLong = SafeLong(x % y)
  def /%(y: BigInt): (SafeLong, SafeLong) = {
    val (q, r) = x /% y
    (SafeLong(q), SafeLong(r))
  }

  def &(y: BigInt): SafeLong = SafeLong(x & y)
  def |(y: BigInt): SafeLong = SafeLong(x | y)
  def ^(y: BigInt): SafeLong = SafeLong(x ^ y)
  
  def unary_-(): SafeLong = SafeLong(-x)  // Covers the case where x == Long.MaxValue + 1

  def compare(that: SafeLong): Int = that match {
    case SafeLongLong(y) => if (x < y) -1 else if (x > y) 1 else 0
    case SafeLongBigInt(y) => x compare y
  }

  def <<(n: Int): SafeLong = SafeLong(x << n)
  def >>(n: Int): SafeLong = SafeLong(x >> n)

  override def equals(that: Any): Boolean = that match {
    case SafeLongLong(y) => x == y
    case SafeLongBigInt(y) => x == y
    case t: BigInt => x == t
    case that => unifiedPrimitiveEquals(that)
  }

  def gcd(that: SafeLong) = that match {
    case SafeLongLong(y) => spire.math.gcd((x % y).toLong, y)
    case SafeLongBigInt(y) => x.gcd(y)
  }

  def doubleValue: Double = x.toDouble
  def floatValue: Float = x.toFloat
  def longValue: Long = x.toLong
  def intValue: Int = x.toInt
  
  def underlying: BigInt = x

  override def toLong: Long = x.toLong
  def toBigInt: BigInt = x
  def toBigDecimal = BigDecimal(x)

  def fold[A,B <: A,C <: A](f: Long => B, g: BigInt => C): A = g(x)
}
