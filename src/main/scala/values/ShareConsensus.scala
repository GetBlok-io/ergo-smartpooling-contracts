package values

import org.ergoplatform.appkit.{ErgoType, ErgoValue}
import special.collection.Coll

import java.nio.charset.StandardCharsets

class ShareConsensus(normalValue: Coll[(Coll[Byte], Coll[Long])]) extends RegValue[(Coll[Byte], Coll[Long]), (Array[Byte], Array[Long])] {
  import ShareConsensus._

  override val nValue: Coll[(Coll[Byte], Coll[Long])] = normalValue
  override val cValue: Array[(Array[Byte], Array[Long])] = normalValue.map(consVal => (consVal._1.toArray, consVal._2.toArray)).toArray
  override val eValue: ErgoValue[Coll[(Coll[Byte], Coll[Long])]] = ErgoValue.of(normalValue, eType)



  override def getNormalValue: Coll[(Coll[Byte], Coll[Long])] = normalValue

  override def getConversionValue: Array[(Array[Byte], Array[Long])] = cValue

  override def getErgoValue: ErgoValue[Coll[(Coll[Byte], Coll[Long])]] = eValue

  override def getErgoType: ErgoType[(Coll[Byte], Coll[Long])] = eType



  override def append(otherNormalValues: Coll[(Coll[Byte], Coll[Long])]): ShareConsensus = {
    new ShareConsensus(this.normalValue.append(otherNormalValues))
  }

  override def addValue(unitValue: (Coll[Byte], Coll[Long])): ShareConsensus = {
    new ShareConsensus(this.normalValue.append(newColl(Array(unitValue), eType)))
  }
  override def addConversion(unitValue: (Array[Byte], Array[Long])): ShareConsensus = {
    addValue(defaultValue(unitValue))
  }

  override def removeValue(unitValue: (Coll[Byte], Coll[Long])): ShareConsensus = {
    new ShareConsensus(this.normalValue.filter(consVal => consVal != unitValue))
  }

  override def removeConversion(unitValue: (Array[Byte], Array[Long])): ShareConsensus = {
    removeValue(defaultValue(unitValue))
  }

  override def getValue(idx: Int): (Coll[Byte], Coll[Long]) = {
    normalValue.getOrElse(idx, null)
  }

  override def getConversion(idx: Int): (Array[Byte], Array[Long]) = {
    convertValue(getValue(idx))
  }



}

object ShareConsensus extends RegCompanion[(Coll[Byte], Coll[Long]), (Array[Byte], Array[Long])] {

  override val eType: ErgoType[(Coll[Byte], Coll[Long])] =
    ErgoType.pairType[Coll[Byte], Coll[Long]](ErgoType.collType(ErgoType.byteType()), ErgoType.collType(ErgoType.longType()))

  override def defaultValue(unitValue: (Array[Byte], Array[Long])): (Coll[Byte], Coll[Long]) = {
    (newColl(unitValue._1, ErgoType.byteType()), newColl(unitValue._2, ErgoType.longType()))
  }

  override def convertValue(unitValue: (Coll[Byte], Coll[Long])): (Array[Byte], Array[Long]) = {
    (unitValue._1.toArray, unitValue._2.toArray)
  }

  override def fromNormalValues(normalValue: Coll[(Coll[Byte], Coll[Long])]): ShareConsensus = new ShareConsensus(normalValue)

  override def fromConversionValues(conversionValue: Array[(Array[Byte], Array[Long])] ): ShareConsensus = {
    val propBytesConverted = conversionValue.map(c => defaultValue(c))
    new ShareConsensus(newColl(propBytesConverted, eType))
  }

  override def fromErgoValues(ergoValue: ErgoValue[Coll[(Coll[Byte], Coll[Long])]]): ShareConsensus = {
    val fromErgVal = ergoValue.getValue
    new ShareConsensus(fromErgVal)
  }

}