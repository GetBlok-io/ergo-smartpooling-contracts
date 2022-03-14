package groups

import boxes.MetadataInputBox
import configs.SmartPoolConfig
import logging.LoggingHandler
import org.ergoplatform.appkit.{Address, Parameters}
import org.slf4j.{Logger, LoggerFactory}
import persistence.models.Models.BoxTag
import registers.{MemberList, PoolFees, PoolInfo, ShareConsensus}

import scala.collection.JavaConverters.mapAsScalaMapConverter

class SubpoolSelector(config: SmartPoolConfig) {

  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_SUB_SEL)
  final val MIN_PAYMENT_THRESHOLD = Parameters.OneErg / 10 // 0.1 ERG Min Payment
  final val SHARE_CONSENSUS_LIMIT = 10
  final val EPOCH_LEFT_LIMIT = 5L
  private var boxToShare: Map[MetadataInputBox, ShareConsensus] = Map.empty[MetadataInputBox, ShareConsensus]
  private var boxToMember: Map[MetadataInputBox, MemberList]    = Map.empty[MetadataInputBox, MemberList]
  private var boxToInfo: Map[MetadataInputBox, PoolInfo]        = Map.empty[MetadataInputBox, PoolInfo]
  private var boxToPoolFees: Map[MetadataInputBox, PoolFees]        = Map.empty[MetadataInputBox, PoolFees]
  def shareMap: Map[MetadataInputBox, ShareConsensus]   = boxToShare
  def memberMap: Map[MetadataInputBox, MemberList]      = boxToMember
  def infoMap: Map[MetadataInputBox, PoolInfo]          = boxToInfo
  def poolFeeMap: Map[MetadataInputBox, PoolFees]       = boxToPoolFees
  /**
   * Selects default subpools for members based on last epoch placement.
   * Returns consensus and members that could not be placed
   * */
  def selectDefaultSubpools(metadataInputs: Array[MetadataInputBox], shareConsensus: ShareConsensus, memberList: MemberList): (Array[(Array[Byte], Array[Long])], Array[(Array[Byte], String)]) = {
    logger.info("Beginning subpool selection and placement...")
    var consensusAdded = Array[(Array[Byte], Array[Long])]()
    var membersAdded = Array[(Array[Byte], String)]()

    for(metadataBox <- metadataInputs){
      if(metadataBox.getCurrentEpoch > 0) {
        for (oldSc <- metadataBox.getShareConsensus.cValue) {
          logger.info(s"Placing members into share consensus for subpool ${metadataBox.getSubpoolId}")
          if (shareConsensus.cValue.exists(newSc => newSc._1 sameElements oldSc._1)) {
            var sc = shareConsensus.cValue.find(newSc => newSc._1 sameElements oldSc._1).get
            if(sc._2.length == 3)
              sc = (sc._1, sc._2++Array(0L, 0L))
            if(sc._2.length == 4)
              sc = (sc._1, sc._2++Array(0L))
            if(oldSc._2.length == 4)
              sc = (sc._1, sc._2.updated(3, oldSc._2(3)))
            if(oldSc._2.length == 5)
              sc = (sc._1, sc._2.updated(3, oldSc._2(3)).updated(4, oldSc._2(4)))
            logger.info("Miner address: " + memberList.cValue.filter(m => Address.create(m._2).getErgoAddress.script.bytes sameElements sc._1).head._2)
            logger.info("Subpool to be placed in: " + metadataBox.getSubpoolId)
            logger.info("Consensus values: " + sc._2.mkString("Array(", ", ", ")"))
            val memberVal = memberList.cValue.filter(m => m._1 sameElements sc._1).head
            addMemberToSubpool(metadataBox, sc, memberVal)
            consensusAdded = consensusAdded ++ Array(sc)
            membersAdded = membersAdded ++ Array(memberVal)
          }else{

            val storedPayout = oldSc._2(2)
            val oldMemberList = metadataBox.getMemberList
            val oldEpochLeft = if (oldSc._2.length > 3) oldSc._2(3) else 0L
            val oldMinerTag = if (oldSc._2.length > 4) oldSc._2(4) else 0L
            if(storedPayout > MIN_PAYMENT_THRESHOLD / 10 && oldEpochLeft <= EPOCH_LEFT_LIMIT) {
              val memberVal = oldMemberList.cValue.filter(m => m._1 sameElements oldSc._1).head

              var sc = (oldSc._1, Array(0, oldSc._2(1), oldSc._2(2), oldEpochLeft, oldMinerTag))
              if (oldEpochLeft == EPOCH_LEFT_LIMIT) {
                logger.info("Miner last connected 5 epochs ago, flushing out payments.")
                sc = (oldSc._1, Array(0, MIN_PAYMENT_THRESHOLD / 10, oldSc._2(2), oldEpochLeft, oldMinerTag))
              }
              logger.info(s"${memberVal._2} is not in current consensus.")
              logger.info(s"Assigning share value of 0 with oldEpochLeft = $oldEpochLeft}")
              logger.info("Miner address: " + memberVal._2)
              logger.info("Subpool to be placed in: " + metadataBox.getSubpoolId)
              logger.info("Consensus values: " + sc._2.mkString("Array(", ", ", ")"))
              logger.info("Epoch Left after holding outputs are generated: " + (oldEpochLeft + 1))
              addMemberToSubpool(metadataBox, sc, memberVal)
              consensusAdded = consensusAdded ++ Array(sc)
              membersAdded = membersAdded ++ Array(memberVal)
            }else{
              val memberVal = oldMemberList.cValue.filter(m => m._1 sameElements oldSc._1).head
              logger.info(s"Member ${memberVal._2} is being kicked from a subpool")
              logger.info("Miner address: " + memberVal._2)
              logger.info("Subpool to be removed from: " + metadataBox.getSubpoolId)
              logger.info("Value in holding: " + oldSc._2(2))
              logger.info("Epochs Left: " + oldEpochLeft)
            }

          }
        }
        if(boxToMember.contains(metadataBox)) {
          val membersStrings = boxToMember(metadataBox).cValue.map(m => m._2)
          logger.info(s"Subpool ${metadataBox.getSubpoolId} members list from consensus: " + membersStrings.mkString("\n", "\n", ""))
        }
      }
    }
    logger.info("Members from old consensus: " + consensusAdded.length)
    logger.info("Members from new consensus: " + shareConsensus.cValue.length)
    var membersLeft = memberList.cValue.filter(m => !membersAdded.exists(om => om._2 == m._2))
    var consensusLeft = shareConsensus.cValue.filter(m => !consensusAdded.exists(om => om._1 sameElements m._1))

    logger.info("Total members left: " + membersLeft.length)
    logger.info("Total consensus left: " + consensusLeft.length)

    logger.info("Members Left: " + membersLeft.map(m => m._2).mkString("\n"))



    logger.info("Now adding remaining members to existing subpools.")

    var openSubpools = metadataInputs
      .filter(m => boxToShare.contains(m))
      .filter(m => boxToShare(m).cValue.length < SHARE_CONSENSUS_LIMIT)
      .sortBy(m => m.getSubpoolId)
    if(openSubpools.length == 0)
      openSubpools = metadataInputs

    var currentSubpool = 0
    logger.info(s"There are a total of ${openSubpools.length} open subpools")

    for(sc <- consensusLeft){
      val metadataBox = openSubpools(currentSubpool)
      logger.info("Current open subpool to add to: " + metadataBox.getSubpoolId)
      val memberVal = memberList.cValue.filter(m => m._1 sameElements sc._1).head
      logger.info("Member being added to open subpool: " + memberVal._2)
      var newConsensusVal = sc

      if(sc._2.length == 3)
        newConsensusVal = (sc._1, sc._2++Array(0L, 0L))
      if(sc._2.length == 4)
        newConsensusVal = (sc._1, sc._2++Array(0L))
      addMemberToSubpool(metadataBox, newConsensusVal, memberVal)

      if(boxToShare(metadataBox).cValue.length == SHARE_CONSENSUS_LIMIT){
        currentSubpool = currentSubpool + 1
      }
      consensusAdded = consensusAdded ++ Array(newConsensusVal)
      membersAdded = membersAdded ++ Array(memberVal)
    }

    var metadataLeft = metadataInputs.filter(m => !boxToShare.keys.exists(ms => ms.getSubpoolId == m.getSubpoolId)).sortBy(m => m.getSubpoolId)
    membersLeft = memberList.cValue.filter(m => !membersAdded.exists(om => om._2 == m._2))
    consensusLeft = shareConsensus.cValue.filter(m => !consensusAdded.exists(om => om._1 sameElements m._1))
    logger.info("Members left: " + membersLeft.length)
    logger.info("Subpool selection and placement complete.")
    (consensusLeft, membersLeft)
  }

  def addMemberToSubpool(metadataBox: MetadataInputBox, sc: (Array[Byte], Array[Long]), memberVal: (Array[Byte], String)): Unit = {
    val newShareConsensus = ShareConsensus.convert(Array(sc))
    val newMemberList = MemberList.convert(Array(memberVal))
    if (boxToShare.contains(metadataBox)) {
      val updatedShareConsensus = ShareConsensus.convert(boxToShare(metadataBox).cValue ++ Array(sc))
      val updatedMemberList = MemberList.convert(boxToMember(metadataBox).cValue ++ Array(memberVal))
      boxToShare = boxToShare.updated(metadataBox, updatedShareConsensus)
      boxToMember = boxToMember.updated(metadataBox, updatedMemberList)
    } else {
      boxToShare = boxToShare ++ Map((metadataBox, newShareConsensus))
      boxToMember = boxToMember ++ Map((metadataBox, newMemberList))
    }
  }

  def selectWithEventPools(normalPools: Array[MetadataInputBox], eventPools: Array[MetadataInputBox], shareConsensus: ShareConsensus, memberList: MemberList): (Array[(Array[Byte], Array[Long])], Array[(Array[Byte], String)]) = {
    var normalConsensus = ShareConsensus.convert(shareConsensus.cValue.filter(c => c._2(4) == 0))
    var eventConsensus = ShareConsensus.convert(shareConsensus.cValue.filter(c => c._2(4) != 0))
    for(sc <- eventConsensus.cValue){
      val oldPool = normalPools.find(m => m.getShareConsensus.cValue.exists(cons => cons._1 sameElements sc._1))
      if(oldPool.isDefined){
        val oldCons = oldPool.get.getShareConsensus.cValue.find(cons => cons._1 sameElements sc._1)
        if(oldCons.get._2(2) > 0){
          val memberEntry = memberList.cValue.filter(mem => mem._1 sameElements oldCons.get._1).head
          logger.info(s"Removing ${memberEntry._2} from eventConsensus due to having stored payout. Now forcing min payout to be 0.01")
          val newCons = (oldCons.get._1, oldCons.get._2.updated(1, MIN_PAYMENT_THRESHOLD / 10).updated(4, 0L))
          eventConsensus = ShareConsensus.convert(eventConsensus.cValue.filter(eventCons => !(eventCons._1 sameElements newCons._1)))
          normalConsensus = ShareConsensus.convert(normalConsensus.cValue ++ Array(newCons))
        }
      }else{
        val memIndex = eventConsensus.cValue.indexOf(sc)
        eventConsensus = ShareConsensus.convert(eventConsensus.cValue.updated(memIndex, (sc._1, sc._2.updated(1, MIN_PAYMENT_THRESHOLD / 10))))
      }
    }
    val eventPoolsList = eventPools.sortBy(e => e.getSubpoolId)
    val normalMembers = MemberList.convert(memberList.cValue.filter(m => normalConsensus.cValue.exists(c => c._1 sameElements m._1)))
    val eventMembers = MemberList.convert(memberList.cValue.filter(m => eventConsensus.cValue.exists(c => c._1 sameElements m._1)))
    var minersLeft = selectDefaultSubpools(normalPools, normalConsensus, normalMembers)
    var slicedEventPools = Array(eventPoolsList.slice(0, 3), eventPoolsList.slice(3, 6), eventPoolsList.slice(6, 10))
    val ngoFees: Array[PoolFees] = config.getParameters.getEventFees.asScala.map{
      f =>
        val address = Address.create(f._1)
        PoolFees.convert(Array((address.getErgoAddress.script.bytes, (f._2 * 10).toInt)))
    }.toArray
    if(eventPoolsList.forall(m => m.getPoolInfo.getTag.isEmpty)){
      for(pool <- slicedEventPools(0)) {
        boxToInfo = boxToInfo + (pool -> pool.getPoolInfo.setTag(Some(BoxTag.NGO_1.longTag)))
        boxToPoolFees = boxToPoolFees + (pool -> ngoFees(0))
      }
      for(pool <- slicedEventPools(1)){
        boxToInfo = boxToInfo + (pool -> pool.getPoolInfo.setTag(Some(BoxTag.NGO_2.longTag)))
        boxToPoolFees = boxToPoolFees + (pool -> ngoFees(1))
      }
      for(pool <- slicedEventPools(2)){
        boxToInfo = boxToInfo + (pool -> pool.getPoolInfo.setTag(Some(BoxTag.NGO_3.longTag)))
        boxToPoolFees = boxToPoolFees + (pool -> ngoFees(2))
      }
    }else{
      for(pool <- eventPoolsList){
        boxToInfo = boxToInfo + (pool -> pool.getPoolInfo)
        boxToPoolFees = boxToPoolFees + (pool -> pool.getPoolFees)
      }
      slicedEventPools = Array(
        boxToInfo.filter(b => BoxTag.NGO_1.longTag == b._2.getTag.get).keys.toArray,
        boxToInfo.filter(b => BoxTag.NGO_2.longTag == b._2.getTag.get).keys.toArray,
        boxToInfo.filter(b => BoxTag.NGO_3.longTag == b._2.getTag.get).keys.toArray
      )
    }
    logger.info(s"Current boxToInfo for eventPools: ${boxToInfo.values.toArray.mkString("Array(", ", ", ")")}")
    for (pools <- slicedEventPools) {
      if(pools.length > 0) {
        logger.info(s"Selecting for event pools ${pools.map(p => p.getSubpoolId).mkString("Array(", ", ", ")")}")
        val filteredConsensus = ShareConsensus.convert(eventConsensus.cValue.filter(c => c._2(4) == boxToInfo(pools.head).getTag.get))
        val filteredMembers = MemberList.convert(eventMembers.cValue.filter(m => filteredConsensus.cValue.exists(c => c._1 sameElements m._1)))
        val newMinersLeft = selectDefaultSubpools(pools, filteredConsensus, filteredMembers)
        minersLeft = (minersLeft._1 ++ newMinersLeft._1, minersLeft._2 ++ newMinersLeft._2)
      }
    }
    minersLeft
  }

}
