package values

import org.ergoplatform.appkit.{ErgoId, ErgoType, ErgoValue, InputBox}
import special.collection.Coll

import java.nio.charset.StandardCharsets
import scala.math.BigInt

class PoolInfo(normalValue: Coll[Long]) extends RegValue[Long, Long] {
  import PoolInfo._

  override val nValue: Coll[Long] = normalValue
  override val cValue: Array[Long] = normalValue.toArray

  override val eValue: ErgoValue[Coll[Long]] = ErgoValue.of(normalValue, eType)

  override def getNormalValue: Coll[Long] = normalValue

  override def getConversionValue: Array[Long] = cValue

  override def getErgoValue: ErgoValue[Coll[Long]] = eValue

  override def getErgoType: ErgoType[Long] = eType

  override def append(otherNormalValues: Coll[Long]): PoolInfo = {
    new PoolInfo(this.normalValue.append(otherNormalValues))
  }
  override def addValue(unitValue: Long): PoolInfo = {
    new PoolInfo(this.normalValue.append(newColl(Array(unitValue), eType)))
  }
  override def addConversion(unitValue: Long): PoolInfo = {
    addValue(defaultValue(unitValue))
  }

  override def removeValue(unitValue: Long): PoolInfo = {
    new PoolInfo(this.normalValue.filter(infoVal => infoVal != unitValue))
  }

  override def removeConversion(unitValue: Long): PoolInfo = {
    removeValue(defaultValue(unitValue))
  }

  override def getValue(idx: Int): Long = {
    normalValue.getOrElse(idx, null)
  }

  override def getConversion(idx: Int): Long = {
    convertValue(getValue(idx))
  }

  def setCurrentEpoch(epoch: Long): PoolInfo = {
    new PoolInfo(this.normalValue.updated(0, epoch))
  }
  def getCurrentEpoch: Long = {
    getValue(0)
  }

  def setCurrentEpochHeight(epochHeight: Long): PoolInfo = {
    new PoolInfo(this.normalValue.updated(1, epochHeight))
  }
  def getCurrentEpochHeight: Long = {
    getValue(1)
  }

  def setCreationHeight(epochHeight: Long): PoolInfo = {
    new PoolInfo(this.normalValue.updated(2, epochHeight))
  }
  def getCreationHeight: Long = {
    getValue(2)
  }

  def setCreationBox(byteArray: Array[Byte]): PoolInfo = {
    val creationBoxId = BigInt(byteArray).toLong
    new PoolInfo(this.normalValue.updated(3, creationBoxId))
  }
  def getCreationBox: String = {
    val creationBox = getValue(3)
    val creationIdArray = BigInt.long2bigInt(creationBox).toByteArray
    (new ErgoId(creationIdArray)).toString
  }
}

object PoolInfo extends RegCompanion[Long, Long]{
  override val eType: ErgoType[Long] = ErgoType.longType()

  def defaultValue(unitValue: Long): Long = {
    unitValue
  }
  def convertValue(unitValue: Long): Long = {
    unitValue
  }
  def fromNormalValues(normalValue: Coll[Long]): PoolInfo = new PoolInfo(normalValue)

  def fromConversionValues(conversionValue: Array[Long] ): PoolInfo = {
    new PoolInfo(newColl(conversionValue, eType))
  }
  def fromErgoValues(ergoValue: ErgoValue[Coll[Long]]): PoolInfo = {
    val fromErgVal = ergoValue.getValue
    new PoolInfo(fromErgVal)
  }
}
