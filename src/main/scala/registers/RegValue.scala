package registers

import org.ergoplatform.appkit.{ErgoType, ErgoValue}
import special.collection.Coll

/**
 * Each Register Value represents a register in the smartpool that takes a certain format of data.
 * @tparam N Normal unit value type that is used to create a RegValue
 * @tparam C Conversion unit value type that the RegValue uses to interact with user data
 */
trait RegValue[N, C] {

  val nValue: Coll[N]
  val cValue: Array[C]

  val eValue: ErgoValue[Coll[N]]


  def getNormalValue: Coll[N]
  def getConversionValue: Array[C]
  def getErgoValue: ErgoValue[Coll[N]]
  def getErgoType: ErgoType[N]

  def append(otherNormalValues: Coll[N]): RegValue[N, C]

  def addValue(unitValue: N): RegValue[N, C]
  def addConversion(unitValue: C): RegValue[N, C]

  def removeValue(unitValue: N): RegValue[N, C]
  def removeConversion(unitValue: C): RegValue[N, C]

  def getValue(idx: Int): N
  def getConversion(idx: Int): C



}

/**
 * Static methods and fields for reg value
 * @tparam N Normal type of RegValue
 * @tparam C Conversion type of RegValue
 */
trait RegCompanion[N, C] {
  val eType: ErgoType[N]

  def fromNormalValues(normalValue: Coll[N]): RegValue[N, C]
  def fromConversionValues(conversionValue: Array[C]): RegValue[N, C]
  def fromErgoValues(ergoValue: ErgoValue[Coll[N]]): RegValue[N, C]

  def defaultValue(unitValue: C): N
  def convertValue(unitValue: N): C
}
