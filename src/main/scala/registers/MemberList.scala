package registers

import org.ergoplatform.appkit.{ErgoType, ErgoValue}
import special.collection.Coll
import java.nio.charset.StandardCharsets

class MemberList(normalValue: Coll[(Coll[Byte], Coll[Byte])]) extends RegValue[(Coll[Byte], Coll[Byte]), (Array[Byte], String)] {
  import MemberList._

  override val nValue: Coll[(Coll[Byte], Coll[Byte])] = normalValue

  override val cValue: Array[(Array[Byte], String)] = normalValue.map(
    memVal => (memVal._1.toArray, new String(memVal._2.toArray, StandardCharsets.US_ASCII))
  ).toArray

  override val eValue: ErgoValue[Coll[(Coll[Byte], Coll[Byte])]] = ErgoValue.of(normalValue, eType)

  override def getNormalValue: Coll[(Coll[Byte], Coll[Byte])] = normalValue

  override def getConversionValue: Array[(Array[Byte], String)] = cValue

  override def getErgoValue: ErgoValue[Coll[(Coll[Byte], Coll[Byte])]] = eValue

  override def getErgoType: ErgoType[(Coll[Byte], Coll[Byte])] = eType


  override def append(otherNormalValues: Coll[(Coll[Byte], Coll[Byte])]): MemberList = {
    new MemberList(this.normalValue.append(otherNormalValues))
  }

  override def addValue(unitValue: (Coll[Byte], Coll[Byte])): MemberList = {
    new MemberList(this.normalValue.append(newColl(Array(unitValue), eType)))
  }
  override def addConversion(member: (Array[Byte], String)): MemberList = {
    addValue(defaultValue(member))
  }

  override def removeValue(member: (Coll[Byte], Coll[Byte])): MemberList = {
    new MemberList(this.normalValue.filter(memVal => memVal != member))
  }

  override def removeConversion(member: (Array[Byte], String)): MemberList = {
    removeValue(defaultValue(member))
  }

  override def getValue(idx: Int): (Coll[Byte], Coll[Byte]) = {
    normalValue.getOrElse(idx, null)
  }

  override def getConversion(idx: Int): (Array[Byte], String) = {
    convertValue(getValue(idx))
  }


}

object MemberList extends RegCompanion[(Coll[Byte], Coll[Byte]), (Array[Byte], String)] {
  override val eType: ErgoType[(Coll[Byte], Coll[Byte])] = ErgoType.pairType[Coll[Byte], Coll[Byte]](ErgoType.collType(ErgoType.byteType()), ErgoType.collType(ErgoType.byteType()))

  override def defaultValue(member: (Array[Byte], String)): (Coll[Byte], Coll[Byte]) = {
    (newColl(member._1, ErgoType.byteType()), newColl(member._2.getBytes(StandardCharsets.US_ASCII), ErgoType.byteType()))
  }

  override def convertValue(unitValue: (Coll[Byte], Coll[Byte])): (Array[Byte], String) = {
    (unitValue._1.toArray, new String(unitValue._2.toArray, StandardCharsets.US_ASCII))
  }


  override def fromNormalValues(normalValue: Coll[(Coll[Byte], Coll[Byte])]): MemberList = new MemberList(normalValue)

  override def convert(conversionValue: Array[(Array[Byte], String)] ): MemberList = {
    val propBytesConverted = conversionValue.map{
      (memVal: (Array[Byte], String)) =>
        defaultValue(memVal)
    }
    new MemberList(newColl(propBytesConverted, eType))
  }

  override def fromErgoValues(ergoValue: ErgoValue[Coll[(Coll[Byte], Coll[Byte])]]): MemberList = {
    val fromErgVal = ergoValue.getValue
    new MemberList(fromErgVal)
  }
}