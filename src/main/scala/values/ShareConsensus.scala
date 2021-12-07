package values

import org.ergoplatform.appkit.{ErgoType, ErgoValue}
import special.collection.Coll

import java.nio.charset.StandardCharsets

class ShareConsensus(normalValue: Coll[(Coll[Byte], Long)]) extends RegValue[(Coll[Byte], Long), (Array[Byte], Long)] {
  import ShareConsensus._

  override val nValue: Coll[(Coll[Byte], Long)] = normalValue
  override val cValue: Array[(Array[Byte], Long)] = normalValue.map(consVal => (consVal._1.toArray, consVal._2)).toArray
  override val eValue: ErgoValue[Coll[(Coll[Byte], Long)]] = ErgoValue.of(normalValue, eType)



  override def getNormalValue: Coll[(Coll[Byte], Long)] = normalValue

  override def getConversionValue: Array[(Array[Byte], Long)] = cValue

  override def getErgoValue: ErgoValue[Coll[(Coll[Byte], Long)]] = eValue

  override def getErgoType: ErgoType[(Coll[Byte], Long)] = eType



  override def append(otherNormalValues: Coll[(Coll[Byte], Long)]): ShareConsensus = {
    new ShareConsensus(this.normalValue.append(otherNormalValues))
  }

  override def addValue(unitValue: (Coll[Byte], Long)): ShareConsensus = {
    new ShareConsensus(this.normalValue.append(newColl(Array(unitValue), eType)))
  }
  override def addConversion(unitValue: (Array[Byte], Long)): ShareConsensus = {
    addValue(defaultValue(unitValue))
  }

  override def removeValue(unitValue: (Coll[Byte], Long)): ShareConsensus = {
    new ShareConsensus(this.normalValue.filter(consVal => consVal != unitValue))
  }

  override def removeConversion(unitValue: (Array[Byte], Long)): ShareConsensus = {
    removeValue(defaultValue(unitValue))
  }

  override def getValue(idx: Int): (Coll[Byte], Long) = {
    normalValue.getOrElse(idx, null)
  }

  override def getConversion(idx: Int): (Array[Byte], Long) = {
    convertValue(getValue(idx))
  }



}

object ShareConsensus extends RegCompanion[(Coll[Byte], Long), (Array[Byte], Long)] {

  override val eType: ErgoType[(Coll[Byte], Long)] = ErgoType.pairType[Coll[Byte], Long]
  (ErgoType.collType(ErgoType.byteType()), ErgoType.longType())

  override def defaultValue(unitValue: (Array[Byte], Long)): (Coll[Byte], Long) = {
    (newColl(unitValue._1, ErgoType.byteType()),unitValue._2)
  }

  override def convertValue(unitValue: (Coll[Byte], Long)): (Array[Byte], Long) = {
    (unitValue._1.toArray, unitValue._2)
  }

  override def fromNormalValues(normalValue: Coll[(Coll[Byte], Long)]): ShareConsensus = new ShareConsensus(normalValue)

  override def fromConversionValues(conversionValue: Array[(Array[Byte], Long)] ): ShareConsensus = {
    val propBytesConverted = conversionValue.map(c => defaultValue(c))
    new ShareConsensus(newColl(propBytesConverted, eType))
  }

  override def fromErgoValues(ergoValue: ErgoValue[Coll[(Coll[Byte], Long)]]): ShareConsensus = {
    val fromErgVal = ergoValue.getValue
    new ShareConsensus(fromErgVal)
  }

}