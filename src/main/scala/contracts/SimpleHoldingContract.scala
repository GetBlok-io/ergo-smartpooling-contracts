package contracts

import boxes.{MetadataInputBox, MetadataOutBox}
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import scorex.crypto.hash.Blake2b256
import special.collection.Coll
import special.sigma.{GroupElement, SigmaProp}
import values.{BytesColl, ShareConsensus}

import scala.collection.mutable.{ArrayBuffer, ListBuffer}

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
   * The SmartPool holding script takes information from the metadata box and command box to distribute
   * rewards to members of the smart pool.
   *
   * SmartPool holding boxes may only be spent in transactions that have both a metadata box and a command box
   * in inputs 0 and 1 of the transaction. The holding box verifies these boxes to ensure that it is only spent
   * in a valid transaction. The holding boxes' main job is to verify the validity of the distributed outputs. The
   * holding box ensures that the output boxes of the transaction it is spent in follow the consensus supplied
   * by the command box.
   *
   * During consensus, pool fees are retrieved from the metadata box(Not the command box, so as to ensure pool fees
   * cannot change until the next epoch). Pool fees are represented by an integer. The minimum value of this
   * integer is expected to be 1 and this is checked by the metadata box. A value of 1 represents 1/1000 of the
   * total value of the consensus transaction. Therefore the minimum pool fee must be 0.1% or 0.001 * the total inputs value.
   *
   * TODO: Consider alternate spending path to destroy Smart Pool boxes in case pool shuts down or restarts due to update.
   * TODO: Consider ways to scan Smart Pool boxes for off chain portion of code to verify box id is correct.
   * TODO: Allow option to choose TxFee
   * TODO: Do not use total input value, use total value of command box, metadata box, and holding boxes only
   *       This should allow more freedom in what the command box can do and use from a consensus transaction.
   *       For example: Block Bounty
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

          // Get each miners owed payouts, only search for miners whose current owed value is less than their minimum payout
          val totalUnpaidPayouts = currentConsensus
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

          // Maps each propositionBytes stored in the consensus to a value stored in command box.
          val boxValueMap = currentConsensus.map{
            (consVal: (Coll[Byte], Coll[Long])) =>
              // If the stored payout value is greater than min payout, then payout is sent
              if(consVal._2(2) >= consVal._2(1)){
                (consVal._1, consVal._2(2))
              }else{
                (consVal._1, 0L)
              }
          }
          val lastConsensusPropBytes = lastConsensus.map{
            (consVal: (Coll[Byte], Coll[Long])) =>
              consVal._1
          }
          val lastConsensusValues = lastConsensus.map{
            (consVal: (Coll[Byte], Coll[Long])) =>
              consVal._2
          }
          // Ensure payments are stored or paid as the current share value + last stored share value
          val owedPaymentsStored = currentConsensus.forall{
            (consVal: (Coll[Byte], Coll[Long])) =>
              val valueFromShares = getValueFromShare(consVal._2(0))
              val indexInLastConsensus = lastConsensusPropBytes.indexOf(consVal._1, 0)
              if(indexInLastConsensus != -1){
                // Confirm that the new stored value is the current consensus value + last stored value
                if(lastConsensusValues(indexInLastConsensus)(2) < lastConsensusValues(indexInLastConsensus)(1)){
                  consVal._2(2) == valueFromShares + lastConsensusValues(indexInLastConsensus)(2)
                }
                else{
                  consVal._2(2) == valueFromShares
                }
              }else{
                // If this is a new member, stored value is just the current value from shares
                consVal._2(2) == valueFromShares
              }
          }
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
          } && owedPaymentsStored
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

    println("===========Generating Holding Address=============")
    println("const_metadataPropBytes: " + smartPoolIdBytes.nValue)
    println("const_smartPoolID: " + metadataPropBytes.nValue)

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
                          feeAddresses: Array[Address], holdingAddress: Address): Array[OutBox] = {
    val metadataBox = new MetadataInputBox(inputBoxes(0))
    val commandBox = new MetadataInputBox(inputBoxes(1))
    val holdingBytes = BytesColl.fromConversionValues(holdingAddress.getErgoAddress.script.bytes)
    val TOTAL_HOLDED_VALUE = inputBoxes.foldLeft(0L){
      (accum: Long, box: InputBox) =>
        val boxPropBytes = BytesColl.fromConversionValues(box.getErgoTree.bytes)
        if(boxPropBytes.getNormalValue == holdingBytes.getNormalValue){
          accum + box.getValue
        }else
          accum
    }
    val lastConsensus = metadataBox.getShareConsensus.getNormalValue
    val currentConsensus = commandBox.getShareConsensus.getNormalValue
    val currentPoolFees = metadataBox.getPoolFees.getNormalValue
    val currentTxFee = Parameters.MinFee * currentConsensus.length

    val totalOwedPayouts =
      currentConsensus.toArray.filter((consVal: (Coll[Byte], Coll[Long])) => consVal._2(2) < consVal._2(1))
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
        if(consVal._2(2) >= consVal._2(1))
          (consVal._1, consVal._2(2))
        else
          (consVal._1, 0L)
    }

    // This verifies that each member of the consensus has some output box
    // protected by their script and that the value of each box is the
    // value obtained from consensus.
    // This boolean value is returned and represents the main sigma proposition of the smartpool holding
    // contract.
    // This boolean value also verifies that poolFees are paid and go to the correct boxes.
    val TxB = ctx.newTxBuilder()
    val outB = TxB.outBoxBuilder()
    val outBoxBuffer = ArrayBuffer[OutBox]()
    val memberAddresses = metadataBox.getMemberList.cValue.map{(a: (Array[Byte], String)) => Address.create(a._2)}
    memberAddresses.foreach{
      (addr: Address) =>
        val addrBytes = BytesColl.fromConversionValues(addr.getErgoAddress.script.bytes)
        boxValueMap.foreach{consVal: (Coll[Byte], Long) => println(consVal._1); println(addrBytes.nValue)}

        val boxValue = boxValueMap.filter{consVal: (Coll[Byte], Long) => BytesColl.fromNormalValues(consVal._1).nValue == addrBytes.nValue}(0)
        if(boxValue._2 > 0) {
          val newOutBox = outB.value(boxValue._2).contract(new ErgoTreeContract(addr.getErgoAddress.script)).build()
          outBoxBuffer.append(newOutBox)
        }
    }
    feeAddresses.foreach{
      (addr: Address) =>
        val boxValue = feeList.filter{poolFeeVal: (Coll[Byte], Long) => poolFeeVal._1.toArray sameElements addr.getErgoAddress.script.bytes}(0)
        val newOutBox = outB.value(boxValue._2).contract(new ErgoTreeContract(addr.getErgoAddress.script)).build()
        outBoxBuffer.append(newOutBox)
    }
    outBoxBuffer.toArray
  }


  def getBoxValue(shareNum: Long, totalShares: Long, totalValueAfterFees: Long): Long = {
    ((totalValueAfterFees * shareNum)/totalShares)
  }

  /**
   * Pre-modify command box inputs to ensure min payouts are updated properly
   * @param outBoxBuilder outbox builder
   * @param commandBox command box to copy registers from
   * @param commandContract contract to set new command box
   * @param smartPoolId SmartPool NFT id
   * @param metadataBox Metadata box calculate pool fees
   * @param holdingBoxes Holding boxes to calculate total rewards
   * @return Returns a new command box with balances modified
   */
  def modifyBalances(outBoxBuilder: OutBoxBuilder, commandBox: MetadataOutBox, commandContract: ErgoContract,
                       smartPoolId: ErgoId, metadataBox: MetadataInputBox, holdingBoxes: List[InputBox]): MetadataOutBox = {
    val currentShareConsensus = commandBox.getShareConsensus
    val newTemplate = MetadataContract.copyMetadataOutBox(outBoxBuilder, commandBox, commandContract, smartPoolId)

    val holdingBoxValues = holdingBoxes.foldLeft(0L){
      (accum: Long, box: InputBox) =>
          accum + box.getValue
    }
    val currentConsensus = commandBox.getShareConsensus.getNormalValue
    val currentPoolFees = metadataBox.getPoolFees.getNormalValue
    val currentTxFee = Parameters.MinFee * currentConsensus.length

    val totalOwedPayouts =
      currentConsensus.toArray.filter((consVal: (Coll[Byte], Coll[Long])) => consVal._2(2) < consVal._2(1))
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
    val updatedConsensus = currentShareConsensus.getConversionValue.map{
      (consVal: (Array[Byte], Array[Long])) =>
        val shareNum = consVal._2(0)
        var minPayout = consVal._2(1)
        if(minPayout < (1*Parameters.OneErg)/10)
          minPayout = (1*Parameters.OneErg)/10
        val owedPayment = consVal._2(2) + getBoxValue(shareNum, totalShares, totalValAfterFees)
        val newConsensusInfo = Array(shareNum, minPayout, owedPayment)
        (consVal._1, newConsensusInfo)
    }
    val newShareConsensus = ShareConsensus.fromConversionValues(updatedConsensus)
    newTemplate.setConsensus(newShareConsensus)
    newTemplate.setMetadata()
    newTemplate.build()
  }


}
