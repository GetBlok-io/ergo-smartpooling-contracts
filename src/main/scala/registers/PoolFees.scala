package registers

import org.ergoplatform.appkit.{ErgoType, ErgoValue}
import special.collection.Coll

class PoolFees(normalValue: Coll[(Coll[Byte], Int)]) extends RegValue[(Coll[Byte], Int), (Array[Byte], Int)] {
  import PoolFees._

  override val nValue: Coll[(Coll[Byte], Int)] = normalValue
  override val cValue: Array[(Array[Byte], Int)] = normalValue.map(feeVal => (feeVal._1.toArray, feeVal._2)).toArray
  override val eValue: ErgoValue[Coll[(Coll[Byte], Int)]] = ErgoValue.of(normalValue, eType)

  override def getNormalValue: Coll[(Coll[Byte], Int)] = normalValue

  override def getConversionValue: Array[(Array[Byte], Int)] = cValue

  override def getErgoValue: ErgoValue[Coll[(Coll[Byte], Int)]] = eValue

  override def getErgoType: ErgoType[(Coll[Byte], Int)] = eType


  override def append(otherNormalValues: Coll[(Coll[Byte], Int)]): PoolFees = {
    new PoolFees(this.normalValue.append(otherNormalValues))
  }
  override def addValue(unitValue: (Coll[Byte], Int)): PoolFees = {
    new PoolFees(this.normalValue.append(newColl(Array(unitValue), eType)))
  }
  override def addConversion(unitValue: (Array[Byte], Int)): PoolFees = {
    addValue(defaultValue(unitValue))
  }

  override def removeValue(unitValue: (Coll[Byte], Int)): PoolFees = {
    new PoolFees(this.normalValue.filter(feeVal => feeVal != unitValue))
  }

  override def removeConversion(unitValue: (Array[Byte], Int)): PoolFees = {
    removeValue(defaultValue(unitValue))
  }

  override def getValue(idx: Int): (Coll[Byte], Int) = {
    normalValue.getOrElse(idx, null)
  }

  override def getConversion(idx: Int): (Array[Byte], Int) = {
    convertValue(getValue(idx))
  }

}

object PoolFees extends RegCompanion[(Coll[Byte], Int), (Array[Byte], Int)] {
  override val eType: ErgoType[(Coll[Byte], Int)] = ErgoType.pairType[Coll[Byte], Int](ErgoType.collType(ErgoType.byteType()), ErgoType.integerType())

  override def defaultValue(unitValue: (Array[Byte], Int)): (Coll[Byte], Int) = {
    (newColl(unitValue._1, ErgoType.byteType()), unitValue._2)
  }

  override def convertValue(unitValue: (Coll[Byte], Int)): (Array[Byte], Int) = {
    (unitValue._1.toArray, unitValue._2)
  }

  override def fromNormalValues(normalValue: Coll[(Coll[Byte], Int)]): PoolFees = new PoolFees(normalValue)

  override def fromConversionValues(conversionValue: Array[(Array[Byte], Int)] ): PoolFees = {
    val propBytesConverted = conversionValue.map(c => defaultValue(c))
    new PoolFees(newColl(propBytesConverted, eType))
  }

  override def fromErgoValues(ergoValue: ErgoValue[Coll[(Coll[Byte], Int)]]): PoolFees = {
    val fromErgVal = ergoValue.getValue
    new PoolFees(fromErgVal)
  }
}