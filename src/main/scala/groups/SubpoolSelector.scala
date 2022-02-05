package groups

import boxes.MetadataInputBox
import logging.LoggingHandler
import org.ergoplatform.appkit.{Address, Parameters}
import org.slf4j.{Logger, LoggerFactory}
import registers.{MemberList, ShareConsensus}

class SubpoolSelector() {

  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_SUB_SEL)
  final val MIN_PAYMENT_THRESHOLD = Parameters.OneErg / 10 // 0.1 ERG Min Payment
  final val SHARE_CONSENSUS_LIMIT = 10
  final val EPOCH_LEFT_LIMIT = 5L
  private var boxToShare: Map[MetadataInputBox, ShareConsensus] = Map.empty[MetadataInputBox, ShareConsensus]
  private var boxToMember: Map[MetadataInputBox, MemberList] = Map.empty[MetadataInputBox, MemberList]

  def shareMap: Map[MetadataInputBox, ShareConsensus] = boxToShare
  def memberMap: Map[MetadataInputBox, MemberList] = boxToMember
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
            if(sc._2.length <= 3) {
              sc = (sc._1, sc._2++Array(0L))
            }
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
            if(storedPayout > MIN_PAYMENT_THRESHOLD / 10 && oldEpochLeft <= EPOCH_LEFT_LIMIT) {
              val memberVal = oldMemberList.cValue.filter(m => m._1 sameElements oldSc._1).head

              var sc = (oldSc._1, Array(0, oldSc._2(1), oldSc._2(2), oldEpochLeft))
              if (oldEpochLeft == EPOCH_LEFT_LIMIT) {
                logger.info("Miner last connected 5 epochs ago, flushing out payments.")
                sc = (oldSc._1, Array(0, MIN_PAYMENT_THRESHOLD / 10, oldSc._2(2), oldEpochLeft))
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

    var openSubpools = boxToShare.keys.filter(m => boxToShare(m).cValue.length < SHARE_CONSENSUS_LIMIT).toArray.sortBy(m => m.getSubpoolId)

//    openSubpools = metadataInputs

    var currentSubpool = 0
    logger.info(s"There are a total of ${openSubpools.length} open subpools")

    for(sc <- consensusLeft){
      val metadataBox = openSubpools(currentSubpool)
      logger.info("Current open subpool to add to: " + metadataBox.getSubpoolId)
      val memberVal = memberList.cValue.filter(m => m._1 sameElements sc._1).head
      logger.info("Member being added to open subpool: " + memberVal._2)

      addMemberToSubpool(metadataBox, sc, memberVal)

      if(boxToShare(metadataBox).cValue.length == SHARE_CONSENSUS_LIMIT){
        currentSubpool = currentSubpool + 1
      }
      consensusAdded = consensusAdded ++ Array(sc)
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

}
