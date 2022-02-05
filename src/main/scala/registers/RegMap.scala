package registers

import org.ergoplatform.appkit.{ErgoType, ErgoValue}
import special.collection.Coll

/**
 * Each Register Value represents a register in the smartpool that takes a certain format of data.
 * @tparam N Normal unit value type that is used to create a RegValue
 * @tparam C Conversion unit value type that the RegValue uses to interact with user data
 */
abstract class RegMap[M, m](map: Map[M, m]){

  type keyC
  type valC
  type keyA
  type valA
  var asMap: Map[M, m] = map
  def asColl: Coll[(keyC, valC)]
  def asArray: Array[(keyA, valA)]
  def asErgoVal: ErgoValue[Coll[(keyC, valC)]]

  def +=(mapVal: (M, m)): RegMap[M, m]
  def ++(mapVal: (M, m)): RegMap[M, m]
  def -=(mapVal: (M, m)): RegMap[M, m]
  def --(mapVal: (M, m)): RegMap[M, m]

  def +=(mapVal: Seq[(M, m)]): RegMap[M, m]
  def ++(mapVal: Seq[(M, m)]): RegMap[M, m]
  def -=(mapVal: Seq[(M, m)]): RegMap[M, m]
  def --(mapVal: Seq[(M, m)]): RegMap[M, m]

  def addCVal(cVals: (keyC, valC)*): RegMap[M, m]
  def addColl(cVals: Coll[(keyC, valC)]): RegMap[M, m]
  def addAVal(aVals: (keyA, valA)*): RegMap[M, m]

  def remCVal(cVals: (keyC, valC)*): RegMap[M, m]
  def remColl(cVals: Coll[(keyC, valC)]): RegMap[M, m]
  def remAVal(aVals: (keyA, valA)*): RegMap[M, m]

}


abstract class RegMapCompanion[M, m] {
  type keyColl
  type valColl
  type keyArr
  type valArr

  val eType: ErgoType[(keyColl, valColl)]

  def ofColl(collMap: Coll[(keyColl, valColl)]): RegMap[M, m]
  def ofArray(arrMap: Array[(keyArr, valArr)]): RegMap[M, m]
  def ofErgo(ergMap: ErgoValue[Coll[(keyColl, valColl)]]): RegMap[M, m]

}
