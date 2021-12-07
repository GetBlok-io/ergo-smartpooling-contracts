package values

import org.ergoplatform.appkit.{ErgoType, ErgoValue}
import special.collection.Coll

/**
 * Each Register Value represents a register in the subpool that takes a certain format of data.
 * @tparam A Normal unit value type that is used to create a RegValue
 * @tparam B Conversion unit value type that the RegValue uses to interact with user data
 */
trait RegValue[A, B] {

  val nValue: Coll[A]
  val cValue: Array[B]

  val eValue: ErgoValue[Coll[A]]


  def getNormalValue: Coll[A]
  def getConversionValue: Array[B]
  def getErgoValue: ErgoValue[Coll[A]]
  def getErgoType: ErgoType[A]

  def append(otherNormalValues: Coll[A]): _ >: RegValue[A, B]

  def addValue(unitValue: A): _ >: RegValue[A, B]
  def addConversion(unitValue: B): _ >: RegValue[A, B]

  def removeValue(unitValue: A): _ >: RegValue[A, B]
  def removeConversion(unitValue: B): _ >: RegValue[A, B]

  def getValue(idx: Int): A
  def getConversion(idx: Int): B



}

/**
 * Static methods and fields for reg value
 * @tparam A Normal type of RegValue
 * @tparam B Conversion type of RegValue
 */
trait RegCompanion[A, B] {
  val eType: ErgoType[A]

  def fromNormalValues(normalValue: Coll[A]): RegValue[A, B]
  def fromConversionValues(conversionValue: Array[B]): RegValue[A, B]
  def fromErgoValues(ergoValue: ErgoValue[Coll[A]]): RegValue[A, B]

  def defaultValue(unitValue: B): A
  def convertValue(unitValue: A): B
}
