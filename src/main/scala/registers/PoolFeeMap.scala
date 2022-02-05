//package registers
//
//import app.AppParameters
//import org.ergoplatform.appkit.{Address, ErgoType, ErgoValue}
//import sigmastate.serialization.ErgoTreeSerializer
//import special.collection.Coll
//
//import scala.collection.GenTraversableOnce
//import scala.collection.generic.GenTraversableFactory
//
//class PoolFeeMap(map: Map[Address, Int]) extends RegMap[Address, Int](map){
//  import PoolFeeMap._
//  override type keyC = keyColl
//  override type valC = valColl
//  override type keyA = keyArr
//  override type valA = valColl
//  val serializer = new ErgoTreeSerializer()
//
//  override def asColl: Coll[(Coll[Byte], Int)] = {
//    val bytesConverted = asMap.map(f => (newColl(f._1.getErgoAddress.script.bytes, ErgoType.byteType()), f._2)).toArray
//    newColl(bytesConverted, eType)
//  }
//
//  override def asArray: Array[(Array[Byte], Int)] = {
//    asMap.map(f => (f._1.getErgoAddress.script.bytes, f._2)).toArray
//  }
//
//  override def asErgoVal: ErgoValue[Coll[(Coll[Byte], Int)]] = ErgoValue.of(asColl, eType)
//
//  def +=(mapVal: (Address, Int)): PoolFeeMap = {
//    asMap = asMap ++ Array(mapVal)
//    this
//  }
//  def +=(mapVal: Seq[(Address, Int)]): PoolFeeMap = {
//    asMap = asMap ++ mapVal
//    this
//  }
//
//  def ++(mapVal: (Address, Int)): PoolFeeMap = {
//    new PoolFeeMap(asMap ++ Array(mapVal))
//  }
//
//  def ++(mapVal: Seq[(Address, Int)]): PoolFeeMap = {
//    new PoolFeeMap(asMap ++ mapVal)
//  }
//
//  def -=(mapVal: (Address, Int)): PoolFeeMap = {
//    asMap = asMap -- Array(mapVal)
//    this
//  }
//  def -=(mapVal: Seq[(Address, Int)]): PoolFeeMap = {
//    asMap = asMap -- Array(mapVal)
//    this
//  }
//
//  def --(mapVal: (Address, Int)): PoolFeeMap = {
//    new PoolFeeMap(asMap -- Array(mapVal))
//  }
//
//  def --(mapVal: Seq[(Address, Int)]): PoolFeeMap = {
//    new PoolFeeMap((asMap -- mapVal.toArray))
//  }
//
//
//  override def addCVal(mapVal: (Coll[Byte], Int)*): PoolFeeMap = {
//    val collMap = newColl(mapVal.toArray, eType)
//    asMap = asMap ++ ofColl(collMap)
//    this
//  }
//
//  override def addColl(cVals: Coll[(Coll[Byte], Int)]): RegMap[Address, Int] = {
//    asMap = asMap ++ ofColl(cVals)
//    this
//  }
//
//  override def addAVal(aVals: (Array[Byte], Int)*): RegMap[Address, Int] = {
//    asMap = asMap ++ ofArray(aVals.toArray)
//    this
//  }
//
//  override def remCVal(cVals: (Coll[Byte], Int)*): RegMap[Address, Int] = {
//    val collMap = newColl(cVals.toArray, eType)
//    asMap = asMap -- ofColl(collMap)
//    this
//  }
//
//  override def remAVal(aVals: (Array[Byte], Int)*): RegMap[Address, Int] = {
//    asMap = asMap -- ofArray(aVals.toArray)
//    this
//  }
//  override def remColl(cVals: Coll[(Coll[Byte], Int)]): RegMap[Address, Int] = {
//    asMap = asMap -- ofColl(cVals)
//    this
//  }
//
//}
//
//object PoolFeeMap extends RegMapCompanion[Address, Int] {
//  override type keyColl = Coll[Byte]
//  override type valColl = Int
//  override type keyArr = Array[Byte]
//  override type valArr = Int
//  val serializer = new ErgoTreeSerializer()
//  override val eType: ErgoType[(Coll[Byte], Int)] = ErgoType.pairType[Coll[Byte], Int](ErgoType.collType(ErgoType.byteType()), ErgoType.integerType())
//
//  override def ofColl(collMap: Coll[(Coll[Byte], Int)]): RegMap[Address, Int] = {
//    new PoolFeeMap(collMap.toArray.map{
//      c =>
//        (Address.fromErgoTree(serializer.deserializeErgoTree(c._1.toArray), AppParameters.networkType), c._2)
//    }.toMap)
//  }
//
//  override def ofArray(arrMap: Array[(Array[Byte], Int)]): RegMap[Address, Int] = {
//    new PoolFeeMap(arrMap.map{
//      c =>
//        (Address.fromErgoTree(serializer.deserializeErgoTree(c._1), AppParameters.networkType), c._2)
//    }.toMap)
//  }
//
//  override def ofErgo(ergoValue: ErgoValue[Coll[(Coll[Byte], Int)]]): RegMap[Address, Int] = ofColl(ergoValue.getValue)
//
//}
