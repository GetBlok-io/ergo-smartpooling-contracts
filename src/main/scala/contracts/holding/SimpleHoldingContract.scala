package contracts.holding

import app.AppParameters
import boxes.builders.CommandOutputBuilder
import boxes.{CommandInputBox, MetadataInputBox}
import contracts.command.CommandContract
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import registers.{BytesColl, ShareConsensus}
import sigmastate.serialization.ErgoTreeSerializer
import special.collection.Coll

import scala.collection.mutable.ArrayBuffer

/**
 * This is a simple holding contract that distributes PPS and saves minimum payouts that are then applied to next
 * command box output
 * @param holdingContract ErgoContract to build SimpleHoldingContract from.
 */
class SimpleHoldingContract(holdingContract: ErgoContract) extends HoldingContract(holdingContract) {
  import SimpleHoldingContract._

  private[this] var _holdingBoxes: List[InputBox] = List[InputBox]()
  private[this] var _metadataBox: MetadataInputBox = _

  def metadataBox: MetadataInputBox = _metadataBox

  def metadataBox_=(value: MetadataInputBox): Unit = {
    _metadataBox = value
  }

  def holdingBoxes: List[InputBox] = _holdingBoxes

  def holdingBoxes_=(value: List[InputBox]): Unit = {
    _holdingBoxes = value
  }

  override def applyToCommand(cOB: CommandOutputBuilder): CommandOutputBuilder = {
    val currentShareConsensus = cOB.metadataRegisters.shareConsensus
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
      (poolFee: (Coll[Byte], Int)) => ( poolFee._1 , ((poolFee._2 * totalRewards)/1000) )
    }
    // Total amount in holding after pool fees and tx fees.
    // This is the total amount of ERG to be distributed to pool members
    val totalValAfterFees = (feeList.toArray.foldLeft(totalRewards){
      (accum: Long, poolFeeVal: (Coll[Byte], Long)) => accum - poolFeeVal._2
    })- currentTxFee
    val totalShares = currentConsensus.toArray.foldLeft(0L){(accum: Long, consVal: (Coll[Byte], Coll[Long])) => accum + consVal._2(0)}
    val updatedConsensus = currentConsensus.toArray.map{
      (consVal: (Coll[Byte], Coll[Long])) =>
        val shareNum = consVal._2(0)
        var currentMinPayout = consVal._2(1)

        val valueFromShares = getBoxValue(shareNum, totalShares, totalValAfterFees)
        if(currentMinPayout < (1*Parameters.OneErg)/10)
          currentMinPayout = (1*Parameters.OneErg)/10

        val owedPayment =
          if(lastShareConsensus.nValue.toArray.exists(sc => consVal._1 == sc._1)){
            val lastConsValues = lastShareConsensus.nValue.toArray.filter(sc => consVal._1 == sc._1 ).head._2
            val lastStoredPayout = lastConsValues(2)
            println("Last Stored Payout: " + lastStoredPayout)
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
        println(s"Owed for ${consVal._1}: ${owedPayment}")
        println(
          s"""Parameters - ShareNum: ${shareNum} - CurrentMinPayout: ${currentMinPayout} - ValueFromShares: ${valueFromShares}
             |shareValueGreater ${valueFromShares >= currentMinPayout} - """.stripMargin)
        val newConsensusInfo = Array(shareNum, currentMinPayout, owedPayment)
        (consVal._1.toArray, newConsensusInfo)
    }
    val newShareConsensus = ShareConsensus.fromConversionValues(updatedConsensus)
    val newMetadataRegisters = cOB.metadataRegisters.copy
    newMetadataRegisters.shareConsensus = newShareConsensus

    cOB
      .setMetadata(newMetadataRegisters)
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
   * TODO: CHECK MIN PAYMENT CODE CAREFULLY - this code could be very finnicky and it stores an important piece of info
   */
  val script: String =
    s"""
    {
      val VALID_INPUTS_SIZE = INPUTS.size > 2
      val TOTAL_HOLDED_VALUE: Long = INPUTS.fold(0L, {(accum: Long, box:Box) =>
        if(box.propositionBytes == SELF.propositionBytes)
          accum + box.value
        else
          accum
      })
      val MIN_TXFEE: Long = 1000L * 1000L


      val metadataExists =
        if(VALID_INPUTS_SIZE){
          INPUTS(0).propositionBytes == const_metadataPropBytes && INPUTS(0).tokens(0)._1 == const_smartPoolNFT
        }else{
          false
        }
      val metadataValid =
        if(metadataExists){
          allOf(Coll(
            INPUTS(0).R4[Coll[(Coll[Byte], Coll[Long])]].isDefined,       // Last consensus
            INPUTS(0).R5[Coll[(Coll[Byte], Coll[Byte])]].isDefined, // Current members
            INPUTS(0).R6[Coll[(Coll[Byte], Int)]].isDefined,        // Pool fees
            INPUTS(0).R7[Coll[Long]].isDefined,                     // Pool Information
            INPUTS(0).R8[Coll[(Coll[Byte], Coll[Byte])]].isDefined  // Pool operators
          ))
        }else{
          false
        }
      // Command box is defined as some box protected by a script present in the pool operators register.
      val commandExists =
        if(metadataValid){
          val POOL_OPERATORS = INPUTS(0).R8[Coll[(Coll[Byte], Coll[Byte])]].get
          val COMMAND_BYTES = INPUTS(1).propositionBytes
          // Verifies that the command boxes proposition bytes exists in the pool operators collection
          val commandOwnedByOperators = POOL_OPERATORS.exists{
            (op: (Coll[Byte], Coll[Byte])) =>
              op._1 == COMMAND_BYTES
          }
          commandOwnedByOperators
        }else{
          false
        }
      // Command box is essentially a new metadata box, so has same registers.
      val commandValid =
        if(commandExists){
          allOf(Coll(
            INPUTS(1).R4[Coll[(Coll[Byte], Coll[Long])]].isDefined,       // New consensus
            INPUTS(1).R5[Coll[(Coll[Byte], Coll[Byte])]].isDefined, // New members list
            INPUTS(1).R6[Coll[(Coll[Byte], Int)]].isDefined,        // New Pool fees
            INPUTS(1).R7[Coll[Long]].isDefined,                     // New Pool Information
            INPUTS(1).R8[Coll[(Coll[Byte], Coll[Byte])]].isDefined  // New Pool operators
          ))
        }else{
          false
        }
      // Check if consensus is valid. This is verified by performing consensus on-chain, that means
      // the amount of erg each box gets is proportional to the amount of shares assigned to them by
      // the pool.
      val consensusValid =
        if(commandValid){
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
            (poolFee: (Coll[Byte], Int)) => ( poolFee._1 , ((poolFee._2.toLong * totalRewards)/1000) )
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
            val newBoxValue = (((totalValAfterFees) * (shareNum)) / (totalShares))
            newBoxValue
          }

          val lastConsensusPropBytes = lastConsensus.map{
            (consVal: (Coll[Byte], Coll[Long])) =>
              consVal._1
          }
          val lastConsensusValues = lastConsensus.map{
            (consVal: (Coll[Byte], Coll[Long])) =>
              consVal._2
          }


          // Maps each propositionBytes stored in the consensus to a value stored in command box.
          val boxValueMap = currentConsensus.map{
            (consVal: (Coll[Byte], Coll[Long])) =>

              // If the last stored payout value + current payout(from shares) is >= min payout, then set outbox value
              // equal to stored payout + current payout

              val currentShareNumber = consVal._2(0)
              val currentMinPayout = consVal._2(1)
              val valueFromShares = getValueFromShare(currentShareNumber)
              val indexInLastConsensus = lastConsensusPropBytes.indexOf(consVal._1, 0)

              if(indexInLastConsensus != -1){
                val lastStoredPayout = lastConsensusValues(indexInLastConsensus)(2)

                if(lastStoredPayout + valueFromShares >= currentMinPayout){
                  (consVal._1, lastStoredPayout + valueFromShares)
                }else{
                  (consVal._1, 0L)
                }
              }else{
                // If the last consensus doesn't exist, we can say the last payment was 0 and just use val from shares
                if(valueFromShares >= currentMinPayout){
                  (consVal._1, valueFromShares)
                }else{
                  (consVal._1, 0L)
                }
              }
          }

          // Ensure payments are stored or paid as the current share value + last stored share value
          val owedPaymentsStored = currentConsensus.forall{
            (consVal: (Coll[Byte], Coll[Long])) =>

              val currentShareNumber = consVal._2(0)
              val currentMinPayout = consVal._2(1)
              val currentStoredPayout = consVal._2(2)
              val valueFromShares = getValueFromShare(currentShareNumber)
              val indexInLastConsensus = lastConsensusPropBytes.indexOf(consVal._1, 0)

              if(indexInLastConsensus != -1){

                val lastStoredPayout = lastConsensusValues(indexInLastConsensus)(2)

                // If the last stored payout + valueFromShares is greater than current min payout, then
                // we know the payout was paid in this consensus and we can verify that the current stored payout is 0
                if(lastStoredPayout + valueFromShares >= currentMinPayout){
                 currentStoredPayout == 0L
                }else{
                  // If its less than the currentMinPayout, we can ensure that the value is stored in the current payout
                  currentStoredPayout == (lastStoredPayout + valueFromShares)
                }
              }else{
                // If the last consensus doesn't exist, we can say the last payment was 0 and just use val from shares
                if(valueFromShares >= currentMinPayout){
                  currentStoredPayout == 0L
                }else{
                  currentStoredPayout == valueFromShares
                }
              }
          }

          // Value that is to be sent back to holding box as change
          val totalChange = currentConsensus
            .filter{(consVal:(Coll[Byte], Coll[Long])) => consVal._2(2) < consVal._2(1)}
            .fold(0L, {(accum: Long, consVal: (Coll[Byte], Coll[Long])) => accum + consVal._2(2)})

          // Ensure that change is stored as an outbox with holding prop bytes
          val changeInOutputs = OUTPUTS.exists{(box: Box) => box.value == totalChange && box.propositionBytes == SELF.propositionBytes}

          val outputPropBytes = OUTPUTS.map{
            (box: Box) => box.propositionBytes
          }
          val outputValues = OUTPUTS.map{
            (box: Box) => box.value
          }
          // This verifies that each member of the consensus has some output box
          // protected by their script and that the value of each box is the
          // value obtained from consensus.
          // This boolean value is returned and represents the main sigma proposition of the smartpool holding
          // contract.
          // This boolean value also verifies that poolFees are paid and go to the correct boxes.
          boxValueMap.forall{
            (boxVal: (Coll[Byte], Long)) =>
              if(boxVal._2 > 0){
                val propBytesIndex = outputPropBytes.indexOf(boxVal._1, 0)
                if(propBytesIndex != -1){
                  OUTPUTS(propBytesIndex).value == boxVal._2
                }else{
                  false
                }
              }else{
                true
              }
          } && feeList.forall{
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
          } && owedPaymentsStored && changeInOutputs
        }else{
          false
        }
      sigmaProp(consensusValid)
    }
    """.stripMargin


  /**
   * Generates Holding Contract with given constants
   * @param ctx Blockchain context used to generate contract
   * @param metadataAddress address of metadata
   * @return Compiled ErgoContract of Holding Smart Contract
   */
  def generateHoldingContract(ctx: BlockchainContext, metadataAddress: Address, smartPoolId: ErgoId): ErgoContract = {
    val metadataPropBytes: BytesColl = BytesColl.fromConversionValues(metadataAddress.getErgoAddress.script.bytes)
    val smartPoolIdBytes: BytesColl = BytesColl.fromConversionValues(smartPoolId.getBytes)
    val constantsBuilder = ConstantsBuilder.create()

    val compiledContract = ctx.compileContract(constantsBuilder
      .item("const_metadataPropBytes", metadataPropBytes.nValue)
      .item("const_smartPoolNFT", smartPoolIdBytes.nValue)
      .build(), script)
    compiledContract
  }

  /**
   * Generates a list of output boxes that follow a consensus. Metadata and Command boxes are assumed
   * to be inputs 0 and 1.
   * @param ctx Blockchain context
   * @return Returns list of output boxes to use in transaction
   */
  def generateOutputBoxes(ctx: BlockchainContext, inputBoxes: Array[InputBox],
                          feeAddresses: Array[Address], holdingAddress: Address,
                          smartPoolId: ErgoId, commandContract: CommandContract): Array[OutBox] = {

    val metadataBox = new MetadataInputBox(inputBoxes(0), smartPoolId)
    val commandBox = new CommandInputBox(inputBoxes(1), commandContract)
    val holdingBytes = BytesColl.fromConversionValues(holdingAddress.getErgoAddress.script.bytes)
    val TOTAL_HOLDED_VALUE = inputBoxes.foldLeft(0L){
      (accum: Long, box: InputBox) =>
        val boxPropBytes = BytesColl.fromConversionValues(box.getErgoTree.bytes)
        if(boxPropBytes.getNormalValue == holdingBytes.getNormalValue){
          accum + box.getValue
        }else
          accum
    }
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
      (poolFee: (Coll[Byte], Int)) => ( poolFee._1 , ((poolFee._2 * totalRewards)/1000) )
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
      val newBoxValue = (((totalValAfterFees) * (shareNum)) / (totalShares))
      newBoxValue
    }


    // Maps each propositionBytes stored in the consensus to a value obtained from the shares.
    val boxValueMap = currentConsensus.toArray.map{
      (consVal: (Coll[Byte], Coll[Long])) =>

        val shareNum = consVal._2(0)
        var currentMinPayout = consVal._2(1)
        val valueFromShares = getValueFromShare(shareNum)
        println(consVal)
        if(lastConsensus.toArray.exists(sc => consVal._1 == sc._1)){
          val lastConsValues = lastConsensus.toArray.filter(sc => consVal._1 == sc._1).head._2
          val lastStoredPayout = lastConsValues(2)

          if(lastStoredPayout + valueFromShares >= currentMinPayout) {
            println("This value was higher than min payout")
            (consVal._1, lastStoredPayout + valueFromShares)
          } else{
            println("This value was lower than min payout")
            (consVal._1, 0L)
          }
        }else{
          if(valueFromShares >= currentMinPayout) {
            println("This new value was higher than min payout" + valueFromShares + " | " + currentMinPayout)
            (consVal._1, valueFromShares)
          } else{
            println("This new value was lower than min payout: " + valueFromShares + " | " + currentMinPayout)

            (consVal._1, 0L)
          }
        }
    }
    val changeValue =
      currentConsensus.toArray.filter((consVal: (Coll[Byte], Coll[Long])) => consVal._2(2) < consVal._2(1))
        .foldLeft(0L){(accum: Long, consVal: (Coll[Byte], Coll[Long])) => accum + consVal._2(2)}
    // This verifies that each member of the consensus has some output box
    // protected by their script and that the value of each box is the
    // value obtained from consensus.
    // This boolean value is returned and represents the main sigma proposition of the smartpool holding
    // contract.
    // This boolean value also verifies that poolFees are paid and go to the correct boxes.
    val TxB = ctx.newTxBuilder()
    val outB = TxB.outBoxBuilder()
    val outBoxBuffer = ArrayBuffer[OutBox]()
    val memberAddresses = commandBox.getMemberList.cValue.map{(a: (Array[Byte], String)) => Address.create(a._2)}
    val serializer = new ErgoTreeSerializer()
    boxValueMap.foreach{consVal: (Coll[Byte], Long) => println(Address.fromErgoTree(serializer.deserializeErgoTree(consVal._1.toArray), AppParameters.networkType))}
    memberAddresses.foreach{
      (addr: Address) =>
        val addrBytes = BytesColl.fromConversionValues(addr.getErgoAddress.script.bytes)

        // This should (theoretically) never fail since members list and consensus map to each other properly
        val boxValue = boxValueMap.filter{consVal: (Coll[Byte], Long) => BytesColl.fromNormalValues(consVal._1).nValue == addrBytes.nValue}(0)
        println(s" Value from shares for address ${addr}: ${boxValue._2}")
        if(boxValue._2 > 0) {
          val newOutBox = outB.value(boxValue._2).contract(new ErgoTreeContract(addr.getErgoAddress.script)).build()
          outBoxBuffer.append(newOutBox)
        }
    }
    feeAddresses.foreach{
      (addr: Address) =>

        val addrBytes = BytesColl.fromConversionValues(addr.getErgoAddress.script.bytes)
        val boxValue = feeList.filter{poolFeeVal: (Coll[Byte], Long) => poolFeeVal._1 == addrBytes.nValue}(0)
        println(s"Fee Value for address ${addr}: ${boxValue._2}")
        val newOutBox = outB.value(boxValue._2).contract(new ErgoTreeContract(addr.getErgoAddress.script)).build()
        outBoxBuffer.append(newOutBox)
    }

    if(changeValue > 0) {
      val newOutBox = outB.value(changeValue).contract(new ErgoTreeContract(holdingAddress.getErgoAddress.script)).build()
      outBoxBuffer.append(newOutBox)
    }
    outBoxBuffer.toArray
  }


  def getBoxValue(shareNum: Long, totalShares: Long, totalValueAfterFees: Long): Long = {
    if(totalShares != 0)
      ((totalValueAfterFees * shareNum)/totalShares)
    else
      0L
  }



}
