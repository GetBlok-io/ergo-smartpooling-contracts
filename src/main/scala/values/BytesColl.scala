package values

import org.ergoplatform.appkit.{ErgoType, ErgoValue}
import special.collection.Coll

import java.nio.charset.StandardCharsets
import scala.math.BigInt

class BytesColl(normalValue: Coll[Byte]) extends RegValue[Byte, Byte] {
  import BytesColl._

  override val nValue: Coll[Byte] = normalValue
  override val cValue: Array[Byte] = normalValue.toArray

  override val eValue: ErgoValue[Coll[Byte]] = ErgoValue.of(normalValue, eType)

  override def getNormalValue: Coll[Byte] = normalValue

  override def getConversionValue: Array[Byte] = cValue

  override def getErgoValue: ErgoValue[Coll[Byte]] = eValue

  override def getErgoType: ErgoType[Byte] = eType

  override def append(otherNormalValues: Coll[Byte]): BytesColl = {
    new BytesColl(this.normalValue.append(otherNormalValues))
  }
  override def addValue(unitValue: Byte): BytesColl = {
    new BytesColl(this.normalValue.append(newColl(Array(unitValue), eType)))
  }
  override def addConversion(unitValue: Byte): BytesColl = {
    addValue(defaultValue(unitValue))
  }

  override def removeValue(unitValue: Byte): BytesColl = {
    new BytesColl(this.normalValue.filter(feeVal => feeVal != unitValue))
  }

  override def removeConversion(unitValue: Byte): BytesColl = {
    removeValue(defaultValue(unitValue))
  }

  override def getValue(idx: Int): Byte = {
    normalValue.getOrElse(idx, 0)
  }

  override def getConversion(idx: Int): Byte = {
    convertValue(getValue(idx))
  }


}

object BytesColl extends RegCompanion[Byte, Byte] {

  override val eType: ErgoType[Byte] = ErgoType.byteType()

  override def defaultValue(unitValue: Byte): Byte = {
    unitValue
  }
  override def convertValue(unitValue: Byte): Byte = {
    unitValue
  }

  override def fromNormalValues(normalValue: Coll[Byte]): BytesColl = new BytesColl(normalValue)

  override def fromConversionValues(conversionValue: Array[Byte] ): BytesColl = {
    new BytesColl(newColl(conversionValue, eType))
  }

  override def fromErgoValues(ergoValue: ErgoValue[Coll[Byte]]): BytesColl = {
    val fromErgVal = ergoValue.getValue
    new BytesColl(fromErgVal)
  }
}
