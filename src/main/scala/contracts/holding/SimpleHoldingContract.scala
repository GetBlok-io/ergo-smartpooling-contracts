package contracts.holding

import app.AppParameters
import boxes.builders.{CommandOutputBuilder, HoldingOutputBuilder}
import boxes.{BoxHelpers, CommandInputBox, MetadataInputBox}
import contracts.command.CommandContract
import logging.LoggingHandler
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.slf4j.{Logger, LoggerFactory}
import registers.{BytesColl, MemberList, ShareConsensus}
import sigmastate.serialization.ErgoTreeSerializer
import special.collection.Coll
import transactions.{CreateCommandTx, DistributionTx}

import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.collection.mutable.ArrayBuffer
// TODO: Clean up offchain code to look nicer and possibly be more efficient
/**
 * This is a simple holding contract that distributes PPS and saves minimum payouts that are then applied to next
 * command box output
 * @param holdingContract ErgoContract to build SimpleHoldingContract from.
 */
class SimpleHoldingContract(holdingContract: ErgoContract) extends HoldingContract(holdingContract) {
  import SimpleHoldingContract._
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_HOLD_CONTRACT)
  final val MIN_PAYMENT_THRESHOLD = Parameters.OneErg / 10L // TODO: Make this an AppParameter
  override def applyToCommand(commandTx: CreateCommandTx): CommandOutputBuilder = {
    val metadataBox = commandTx.metadataInputBox
    val storedPayouts = metadataBox.getShareConsensus.cValue.map(c => c._2(2)).sum

    val holdingBoxes = commandTx.holdingInputs

    val currentShareConsensus = commandTx.cOB.metadataRegisters.shareConsensus
    val lastShareConsensus = metadataBox.shareConsensus

    val holdingBoxValues = holdingBoxes.foldLeft(0L){
      (accum: Long, box: InputBox) =>
        accum + box.getValue
    }

    val lastConsensus = lastShareConsensus.getNormalValue
    val currentConsensus = currentShareConsensus.getNormalValue
    val currentPoolFees = metadataBox.getPoolFees.getNormalValue
    val currentTxFee = Parameters.MinFee * currentConsensus.length

    val totalOwedPayouts =
      lastConsensus.toArray.filter((consVal: (Coll[Byte], Coll[Long])) => consVal._2(2) < consVal._2(1))
        .foldLeft(0L){(accum: Long, consVal: (Coll[Byte], Coll[Long])) => accum + consVal._2(2)}
    val totalRewards = holdingBoxValues - totalOwedPayouts
    val feeList: Coll[(Coll[Byte], Long)] = currentPoolFees.map{
      // Pool fee is defined as x/1000 of total inputs value.
      (poolFee: (Coll[Byte], Int)) =>
        val feeAmount: Long = (poolFee._2.toLong * totalRewards)/1000L
        val feeNoDust: Long = BoxHelpers.removeDust(feeAmount)
        (poolFee._1 , feeNoDust)

    }
    // Total amount in holding after pool fees and tx fees.
    // This is the total amount of ERG to be distributed to pool members
    val totalValAfterFees = (feeList.toArray.foldLeft(totalRewards){
      (accum: Long, poolFeeVal: (Coll[Byte], Long)) => accum - poolFeeVal._2
    })- currentTxFee
    val totalShares = currentConsensus.toArray.foldLeft(0L){(accum: Long, consVal: (Coll[Byte], Coll[Long])) => accum + consVal._2(0)}

//    println("Total Shares:" + totalShares)
//    println("Owed Payouts: " + totalOwedPayouts)
//    println("Val after fees: " + totalValAfterFees)
    var shareScoreLeft = 0L
    val updatedConsensus = currentConsensus.toArray.map{
      (consVal: (Coll[Byte], Coll[Long])) =>
        val shareNum = consVal._2(0)
        var currentMinPayout = consVal._2(1)
        println(shareNum)
        var valueFromShares = ((totalValAfterFees * BigDecimal(shareNum)) / BigDecimal(totalShares)).toLong

        valueFromShares = BoxHelpers.removeDust(valueFromShares)

        // Custom epoch value being set to keep track of members whose payout must be flushed
        var epochLeft = 0L
        var minerTag = 0L
        if(consVal._2.length > 3){
          epochLeft = consVal._2(3)
        }
        if(consVal._2.length > 4){
          minerTag = consVal._2(4)
        }
        if(shareNum == 0){
          epochLeft = epochLeft + 1
        }else if(shareNum > 0){
          epochLeft = 0
        }

        val member = MemberList.fromNormalValues( commandTx.memberList.nValue.filter(m => m._1 == consVal._1))
        logger.info("Member: " + member.cValue(0)._2)
        logger.info("Value from shares: " + valueFromShares)

        logger.info("Current Min Payout: " + currentMinPayout)
//        println("Share Num: " + shareNum)
//        println("Total Shares: " + totalShares)
//        println("Val After Fees: " + totalValAfterFees)
//        println("Box Value " + valueFromShares)

        if(currentMinPayout < MIN_PAYMENT_THRESHOLD) {
          if(epochLeft <= 5)
            currentMinPayout = MIN_PAYMENT_THRESHOLD
        }


        val owedPayment =
          if(lastShareConsensus.nValue.toArray.exists(sc => consVal._1 == sc._1)){
            val lastConsValues = lastShareConsensus.nValue.toArray.filter(sc => consVal._1 == sc._1 ).head._2
            val lastStoredPayout = lastConsValues(2)
//            println("Last Stored Payout: " + lastStoredPayout)
            if(lastStoredPayout + valueFromShares >= currentMinPayout)
              0L
            else{
              lastStoredPayout + valueFromShares
            }
          }else{
            if(valueFromShares >= currentMinPayout)
              0L
            else{
              valueFromShares
            }
          }
//        println(s"Owed for ${consVal._1}: ${owedPayment}")
//        println(
//          s"""Parameters - ShareNum: ${shareNum} - CurrentMinPayout: ${currentMinPayout} - ValueFromShares: ${valueFromShares}
//             |shareValueGreater ${valueFromShares >= currentMinPayout} - """.stripMargin)
        val newConsensusInfo = Array(shareNum, currentMinPayout, owedPayment, epochLeft, minerTag)
        (consVal._1.toArray, newConsensusInfo)
    }
    val newShareConsensus = ShareConsensus.convert(updatedConsensus)
    val newMetadataRegisters = commandTx.cOB.metadataRegisters.copy
    newMetadataRegisters.shareConsensus = newShareConsensus

    commandTx.cOB
      .setMetadata(newMetadataRegisters)
  }

  /**
   * Generates a HoldingOutputBuilder that follows consensus.
   * @param ctx Blockchain context
   * @return Returns HoldingOutputBuilder to use in transaction
   */
  override def generateInitialOutputs(ctx: BlockchainContext, distributionTx: DistributionTx, holdingBoxes: List[InputBox]): HoldingOutputBuilder = {

    val metadataBox = distributionTx.metadataInputBox
    val commandBox = distributionTx.commandInputBox
    val holdingAddress = this.getAddress
    val initBoxes: List[InputBox] = List(metadataBox.asInput, commandBox.asInput)
    val inputList = initBoxes++holdingBoxes
    val inputBoxes: Array[InputBox] = inputList.toArray
    val serializer = new ErgoTreeSerializer()
    val feeAddresses = metadataBox.getPoolFees.cValue.map(c => Address.fromErgoTree(serializer.deserializeErgoTree(c._1), ctx.getNetworkType))

    val holdingBytes = BytesColl.convert(holdingAddress.getErgoAddress.script.bytes)
    val TOTAL_HOLDED_VALUE = inputBoxes.foldLeft(0L){
      (accum: Long, box: InputBox) =>
        val boxPropBytes = BytesColl.convert(box.getErgoTree.bytes)
        if(boxPropBytes.getNormalValue == holdingBytes.getNormalValue){
          accum + box.getValue
        }else
          accum
    }
    println("Total Value Held: " + TOTAL_HOLDED_VALUE)
    val lastShareConsensus = metadataBox.shareConsensus
    val lastConsensus = metadataBox.getShareConsensus.getNormalValue
    val currentConsensus = commandBox.getShareConsensus.getNormalValue
    val currentPoolFees = metadataBox.getPoolFees.getNormalValue
    val currentTxFee = Parameters.MinFee * currentConsensus.length

    val totalOwedPayouts =
      lastConsensus.toArray.filter((consVal: (Coll[Byte], Coll[Long])) => consVal._2(2) < consVal._2(1))
        .foldLeft(0L){(accum: Long, consVal: (Coll[Byte], Coll[Long])) => accum + consVal._2(2)}
    val totalRewards = TOTAL_HOLDED_VALUE - totalOwedPayouts
    val feeList: Coll[(Coll[Byte], Long)] = currentPoolFees.map{
      // Pool fee is defined as x/1000 of total inputs value.
      (poolFee: (Coll[Byte], Int)) =>
        val feeAmount = (poolFee._2 * totalRewards)/1000L
        val dustRemoved = BoxHelpers.removeDust(feeAmount)
        ( poolFee._1 , dustRemoved )
    }

    // Total amount in holding after pool fees and tx fees.
    // This is the total amount of ERG to be distributed to pool members
    val totalValAfterFees = (feeList.toArray.foldLeft(totalRewards){
      (accum: Long, poolFeeVal: (Coll[Byte], Long)) => accum - poolFeeVal._2
    })- currentTxFee

    val totalShares = currentConsensus.toArray.foldLeft(0L){(accum: Long, consVal: (Coll[Byte], Coll[Long])) => accum + consVal._2(0)}

    // Returns some value that is a percentage of the total rewards after the fees.
    // The percentage used is the proportion of the share number passed in over the total number of shares.
    def getValueFromShare(shareNum: Long) = {
      if(totalShares != 0) {
        val newBoxValue = ((totalValAfterFees * BigDecimal(shareNum)) / BigDecimal(totalShares)).toLong
        val dustRemoved = BoxHelpers.removeDust(newBoxValue)
        dustRemoved
      }else
        0L
    }


    // Maps each propositionBytes stored in the consensus to a value obtained from the shares.
    val boxValueMap = currentConsensus.toArray.map{
      (consVal: (Coll[Byte], Coll[Long])) =>

        val shareNum = consVal._2(0)
        var currentMinPayout = consVal._2(1)
        val valueFromShares = getValueFromShare(shareNum)
        //println("Value From Shares: " + valueFromShares)
        if(lastConsensus.toArray.exists(sc => consVal._1 == sc._1)){
          val lastConsValues = lastConsensus.toArray.filter(sc => consVal._1 == sc._1).head._2
          val lastStoredPayout = lastConsValues(2)

          if(lastStoredPayout + valueFromShares >= currentMinPayout) {

            (consVal._1, lastStoredPayout + valueFromShares)
          } else{

            (consVal._1, 0L)
          }
        }else{
          if(valueFromShares >= currentMinPayout) {
            //println("This new value was higher than min payout" + valueFromShares + " | " + currentMinPayout)
            (consVal._1, valueFromShares)
          } else{
            //println("This new value was lower than min payout: " + valueFromShares + " | " + currentMinPayout)

            (consVal._1, 0L)
          }
        }
    }
    val changeValue =
      currentConsensus.toArray.filter((consVal: (Coll[Byte], Coll[Long])) => consVal._2(2) < consVal._2(1))
        .foldLeft(0L){(accum: Long, consVal: (Coll[Byte], Coll[Long])) => accum + consVal._2(2)}

    var outBoxMap = Map[OutBoxBuilder, (Long, Boolean)]()


    val memberAddresses = commandBox.getMemberList.cValue.map(m => Address.create(m._2))
    boxValueMap.foreach{consVal: (Coll[Byte], Long) => logger.info("Address " + Address.fromErgoTree(serializer.deserializeErgoTree(consVal._1.toArray), AppParameters.networkType))}
    boxValueMap.foreach{
      (consVal: (Coll[Byte], Long)) =>
        val addr = Address.fromErgoTree(serializer.deserializeErgoTree(consVal._1.toArray), AppParameters.networkType)
        val addrBytes = BytesColl.convert(addr.getErgoAddress.script.bytes)

        // This should (theoretically) never fail since members list and consensus map to each other properly
        val boxValue = boxValueMap.filter{consVal: (Coll[Byte], Long) => BytesColl.fromNormalValues(consVal._1).nValue == addrBytes.nValue}(0)
        logger.info(s" Value from shares for address ${addr}: ${boxValue._2}")
        if(boxValue._2 > 0) {
          val outB = distributionTx.asUnsignedTxB.outBoxBuilder()
          val newOutBox = outB.value(boxValue._2).contract(new ErgoTreeContract(addr.getErgoAddress.script, addr.getNetworkType))
          outBoxMap = outBoxMap++Map((newOutBox, (boxValue._2, true)))
        }
    }
    feeAddresses.foreach{
      (addr: Address) =>
        val outB = distributionTx.asUnsignedTxB.outBoxBuilder()
        val addrBytes = BytesColl.convert(addr.getErgoAddress.script.bytes)
        val boxValue = feeList.filter{poolFeeVal: (Coll[Byte], Long) => poolFeeVal._1 == addrBytes.nValue}(0)
        if(boxValue._2 > 0) {
          println(s"Fee Value for address ${addr}: ${boxValue._2}")
          val newOutBox = outB.value(boxValue._2).contract(new ErgoTreeContract(addr.getErgoAddress.script, addr.getNetworkType))
          outBoxMap = outBoxMap++Map((newOutBox, (boxValue._2, false)))
        }
    }

    if(changeValue > 0) {
      val outB = distributionTx.asUnsignedTxB.outBoxBuilder()
      val newOutBox = outB.value(changeValue).contract(new ErgoTreeContract(holdingAddress.getErgoAddress.script, holdingAddress.getNetworkType))
      outBoxMap = outBoxMap++Map((newOutBox, (changeValue, false)))
    }
    new HoldingOutputBuilder(outBoxMap)
  }



}

object SimpleHoldingContract {

  /**
   * Simple Holding script
   * - This script requires 2 constants:
   * - Metadata Box Proposition Bytes
   *    -- Used to verify input 0 as a metadata box
   * - Smart Pool id
   *    -- Used to identify what smart pool a given set of holding boxes is linked to.
   *    -- We use the id to ensure that each holding address is uniquely linked to
   *    -- a valid metadata box. This ensures that fake metadata boxes cannot be created
   *       to spend the holding boxes.
   *    -- The smart pool id will either be the id of the metadata box(if the epoch is 0)
   *       or it will be the id of the smart pool nft that is generated after epoch 0
   *
   * TODO: Consider alternate spending path to destroy Smart Pool boxes in case pool shuts down or restarts due to update.
   * TODO: Allow option to choose TxFee
   */
  val script: String =
    s"""
    {

      val holdingBoxes = INPUTS.filter{(box: Box) => box.propositionBytes == SELF.propositionBytes}

      // Lets ensure that only one holding box performs the consensus calculations to reduce tx cost
      val doesCalculations =
        if(holdingBoxes.size > 1 && holdingBoxes(0).id == SELF.id){
          true
        }else{
          if(holdingBoxes.size == 1){
            true
          }else{
            false
          }
        }
      val metadataExists = INPUTS(0).propositionBytes == const_metadataPropBytes



      // Alternate spending path that allows holding boxes to be regrouped so long as Total Value Held stays
      // the same. This ensures exact holding boxes no matter the scenario.
      //      def regroupTx(mExists: Boolean): Boolean =
      //        if(!mExists){
      //          val TOTAL_HOLDED_VALUE: Long = holdingBoxes.fold(0L, {(accum: Long, box:Box) =>
      //            accum + box.value
      //          })
      //          val TOTAL_HOLDED_OUTPUTS: Long = OUTPUTS.fold(0L, {(accum: Long, box:Box) =>
      //            if(box.propositionBytes == SELF.propositionBytes)
      //              accum + box.value
      //            else
      //              accum
      //          })
      //
      //          TOTAL_HOLDED_VALUE == TOTAL_HOLDED_OUTPUTS
      //        }else{
      //          false
      //        }
      val MIN_TXFEE: Long = 1000L * 1000L



      // Check if consensus is valid. This is verified by performing consensus on-chain, that means
      // the amount of erg each box gets is proportional to the amount of shares assigned to them by
      // the pool.
      def consensusValid(mExists: Boolean): Boolean =
          if(mExists){
            val TOTAL_HOLDED_VALUE: Long = holdingBoxes.fold(0L, {(accum: Long, box:Box) =>
              accum + box.value
            })
            val poolInfo = INPUTS(0).R7[Coll[Long]].get
            val lastEpoch = poolInfo(0)

            // Let's confirm that we are working with
            // with the right metadata box by ensuring the smartPool NFT is present.
            // If it's epoch 0, let's simply ensure that the box id is equal to the smart pool NFT id
            val smartPoolNFT =
              if(lastEpoch != 0){
                INPUTS(0).tokens(0)._1 == const_smartPoolNFT
              }else{
                if(INPUTS(0).tokens.size > 0){
                  INPUTS(0).tokens(0)._1 == const_smartPoolNFT
                }else{
                  INPUTS(0).id == const_smartPoolNFT
                }
              }

            val lastConsensus = INPUTS(0).R4[Coll[(Coll[Byte], Coll[Long])]].get // old consensus grabbed from metadata
            val currentConsensus = INPUTS(1).R4[Coll[(Coll[Byte], Coll[Long])]].get // New consensus grabbed from current command
            val currentPoolFees = INPUTS(0).R6[Coll[(Coll[Byte], Int)]].get // Pool fees grabbed from current metadata
            val currentTxFee = MIN_TXFEE * currentConsensus.size

            // Get each miners owed payouts from the last consensus
            val totalUnpaidPayouts = lastConsensus
              .filter{(consVal:(Coll[Byte], Coll[Long])) => consVal._2(2) < consVal._2(1)}
              .fold(0L, {(accum: Long, consVal: (Coll[Byte], Coll[Long])) => accum + consVal._2(2)})
            // Subtract unpaid payments from holded value, gives us the value to calculate fees and rewards from
            val totalRewards = TOTAL_HOLDED_VALUE - totalUnpaidPayouts


            val feeList: Coll[(Coll[Byte], Long)] = currentPoolFees.map{
              // Pool fee is defined as x/1000 of total inputs value.
              (poolFee: (Coll[Byte], Int)) =>
                val feeAmount: Long = (poolFee._2.toLong * totalRewards)/1000L
                val feeNoDust: Long = feeAmount - (feeAmount % MIN_TXFEE)
                (poolFee._1 , feeNoDust)
            }

            // Total amount in holding after pool fees and tx fees.
            // This is the total amount of ERG to be distributed to pool members
            val totalValAfterFees = ((feeList.fold(totalRewards, {
              (accum: Long, poolFeeVal: (Coll[Byte], Long)) => accum - poolFeeVal._2
            })) - currentTxFee)

            val totalShares = currentConsensus.fold(0L, {(accum: Long, consVal: (Coll[Byte], Coll[Long])) => accum + consVal._2(0)})

            // Returns some value that is a percentage of the total rewards after the fees.
            // The percentage used is the proportion of the share number passed in over the total number of shares.
            def getValueFromShare(shareNum: Long) = {
              val newBoxValue = (((totalValAfterFees) * (shareNum)) / (totalShares)).toLong
              val dustRemoved = newBoxValue - (newBoxValue % MIN_TXFEE)
              dustRemoved
            }

            val lastConsensusPropBytes = lastConsensus.map{
              (consVal: (Coll[Byte], Coll[Long])) =>
                consVal._1
            }
            val lastConsensusValues = lastConsensus.map{
              (consVal: (Coll[Byte], Coll[Long])) =>
                consVal._2
            }

            val outputPropBytes = OUTPUTS.map{
              (box: Box) => box.propositionBytes
            }
            val outputValues = OUTPUTS.map{
              (box: Box) => box.value
            }

            // Ensures there exists output boxes for each consensus value
            // And that owed payments are stored
            val consensusPaid = currentConsensus.forall{
              (consVal: (Coll[Byte], Coll[Long])) =>

                // If the last stored payout value + current payout(from shares) is >= min payout, then set outbox value
                // equal to stored payout + current payout

                val currentShareNumber = consVal._2(0)
                val currentMinPayout = consVal._2(1)
                val currentStoredPayout = consVal._2(2)
                val valueFromShares = getValueFromShare(currentShareNumber)
                val indexInLastConsensus = lastConsensusPropBytes.indexOf(consVal._1, 0)
                val indexInOutputs = outputPropBytes.indexOf(consVal._1, 0)

                if(indexInLastConsensus != -1){
                  val lastStoredPayout = lastConsensusValues(indexInLastConsensus)(2)

                  if(lastStoredPayout + valueFromShares >= currentMinPayout){
                    if(indexInOutputs != -1){
                      (outputValues(indexInOutputs) == lastStoredPayout + valueFromShares) && (currentStoredPayout == 0L)
                    }else{
                      false
                    }
                  }else{
                    (indexInOutputs == -1) && (currentStoredPayout == (lastStoredPayout + valueFromShares))
                  }
                }else{
                  // If the last consensus doesn't exist, we can say the last payment was 0 and just use val from shares
                  if(valueFromShares >= currentMinPayout){
                    if(indexInOutputs != -1){
                      (outputValues(indexInOutputs) == valueFromShares) && (currentStoredPayout == 0L)
                    }else{
                      false
                    }
                  }else{
                    (indexInOutputs == -1) && (currentStoredPayout == valueFromShares)
                  }
                }
            }

            // Value that is to be sent back to holding box as change
            val totalChange = currentConsensus
              .filter{(consVal:(Coll[Byte], Coll[Long])) => consVal._2(2) < consVal._2(1)}
              .fold(0L, {(accum: Long, consVal: (Coll[Byte], Coll[Long])) => accum + consVal._2(2)})

            // Ensure that change is stored as an outbox with holding prop bytes
            val changeInOutputs =
              if(totalChange > 0){
                OUTPUTS.exists{(box: Box) => box.value == totalChange && box.propositionBytes == SELF.propositionBytes}
              }
              else{
                true
              }


            // This verifies that each member of the consensus has some output box
            // protected by their script and that the value of each box is the
            // value obtained from consensus.
            // This boolean value is returned and represents the main sigma proposition of the smartpool holding
            // contract.
            // This boolean value also verifies that poolFees are paid and go to the correct boxes.
              consensusPaid && feeList.forall{
                (poolFeeVal: (Coll[Byte], Long)) =>
                if(poolFeeVal._2 > 0){
                  val propBytesIndex = outputPropBytes.indexOf(poolFeeVal._1, 0)
                  if(propBytesIndex != -1){
                    OUTPUTS(propBytesIndex).value == poolFeeVal._2
                  }else{
                    false
                  }
                }else{
                  true
                }
            } && changeInOutputs && smartPoolNFT
          }else{
            false
          }

      if(!doesCalculations){
        sigmaProp(consensusValid(metadataExists))
      }else{
        sigmaProp(true)
      }
    }
    """.stripMargin


  /**
   * Generates Holding Contract with given constants
   * @param ctx Blockchain context used to generate contract
   * @param metadataAddress address of metadata
   * @return Compiled ErgoContract of Holding Smart Contract
   */
  def generateHoldingContract(ctx: BlockchainContext, metadataAddress: Address, smartPoolId: ErgoId): ErgoContract = {
    val metadataPropBytes: BytesColl = BytesColl.convert(metadataAddress.getErgoAddress.script.bytes)
    val smartPoolIdBytes: BytesColl = BytesColl.convert(smartPoolId.getBytes)
    val constantsBuilder = ConstantsBuilder.create()

    val compiledContract = ctx.compileContract(constantsBuilder
      .item("const_metadataPropBytes", metadataPropBytes.nValue)
      .item("const_smartPoolNFT", smartPoolIdBytes.nValue)
      .build(), script)
    compiledContract
  }




  def getBoxValue(shareNum: Long, totalShares: Long, totalValueAfterFees: Long): Long = {
    if(totalShares != 0) {
      val boxValue = ((totalValueAfterFees * shareNum)/totalShares)
      val dustRemoved = BoxHelpers.removeDust(boxValue)
      dustRemoved
    } else
      0L
  }



}
