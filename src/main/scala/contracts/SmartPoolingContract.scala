package contracts

import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import scorex.crypto.hash.Blake2b256
import special.collection.Coll
import special.sigma.{GroupElement, SigmaProp}

import scala.collection.mutable.{ArrayBuffer, ListBuffer}

object SmartPoolingContract {

  def getSmartPoolingScript: String = {
    /**
     * SmartPool Holding script
     * - This script requires 3 constants:
     * - Metadata Box Proposition Bytes
     *    -- Used to verify input 0 as a metadata box
     * - Metadata Box id
     *    -- Used to identify what box a given set of holding boxes is linked to.
     *    -- We use the id to ensure that each holding address is uniquely linked to
     *    -- a valid metadata box. This ensures that fake metadata boxes cannot be created
     *    -- to spend the holding boxes.
     *    -- Every valid metadata box will have a list of pool operators in R8 that ensures that
     *    -- the holding boxes may only be spent if certain conditions are met.
     *    -- We must to create a new holding contract everytime we enter a new epoch to ensure
     *    -- that funds are not lost. (Maybe add backup spending path in case of mistake?)
     * - MinTxFee for use in calculation
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
        val TOTAL_HOLDED_VALUE = INPUTS.fold(0L, {(accum: Long, box:Box) =>
          if(box.propositionBytes == SELF.propositionBytes)
            accum + box.value
          else
            accum
        })
        val MIN_TXFEE = 1000L * 1000L


        val metadataExists =
          if(VALID_INPUTS_SIZE){
            INPUTS(0).propositionBytes == const_metadataPropBytes
            && INPUTS(0).id == const_metadataID
          }else{
            false
          }
        val metadataValid =
          if(metadataExists){
            allOf(Coll(
              INPUTS(0).R4[Coll[(Coll[Byte], Long)]].isDefined,       // Last consensus
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
              (op: (Coll[Byte], Coll[Byte]) =>
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
              INPUTS(1).R4[Coll[(Coll[Byte], Long)]].isDefined,       // New consensus
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
            val currentConsensus = INPUTS(1).R4[Coll[(Coll[Byte], Long)]].get // New consensus grabbed from current command
            val currentPoolFees = INPUTS(0).R6[Coll[(Coll[Byte], Int)]].get // Pool fees grabbed from current metadata
            val currentTxFee = MIN_TXFEE * currentConsensus.size()
            val feeList: Coll[(Coll[Byte], Long)] = currentPoolFees.map{
              // Pool fee is defined as x/1000 of total inputs value.
              (poolFee: (Coll[Byte], Int)) => ( poolFee._1 , ((poolFee._2 * TOTAL_HOLDED_VALUE)/1000) )
            }

            // Total amount in holding after pool fees and tx fees.
            // This is the total amount of ERG to be distributed to pool members
            val totalValAfterFees = ((feeList.fold(TOTAL_HOLDED_VALUE, {
              (accum: Long, poolFeeVal: (Coll[Byte], Long)) => accum - poolFeeVal._2
            })) - currentTxFee)

            val totalShares = currentConsensus.fold(0L, {(accum: Long, consVal: (Coll[Byte], Long)) => accum + consVal._2})

            // Returns some value that is a percentage of the total rewards after the fees.
            // The percentage used is the proportion of the share number passed in over the total number of shares.
            def getValueFromShare(shareNum: Long) = {
              val newBoxValue = (((totalValAfterFees) * (shareNum)) / (totalShares))
              newBoxValue
            }

            // Maps each propositionBytes stored in the consensus to a value obtained from the shares.
            val boxValueMap = currentConsensus.map{
              (consVal: (Coll[Byte], Long)) =>
                (consVal._1, getValueFromShare(consVal._2)
            }

            // This verifies that each member of the consensus has some output box
            // protected by their script and that the value of each box is the
            // value obtained from consensus.
            // This boolean value is returned and represents the main sigma proposition of the smartpool holding
            // contract.
            // This boolean value also verifies that poolFees are paid and go to the correct boxes.
            boxValueMap.forall{
              (boxVal: (Coll[Byte], Long)) =>
                OUTPUTS.exists{
                  (box: Box) => box.propositionBytes == boxVal._1 && box.value == boxVal._2
                }
            } && feeList.forall{
              (poolFeeVal: (Coll[Byte], Long)) =>
                OUTPUTS.exists{
                  (box: Box) => box.propositionBytes == poolFeeVal._1 && box.value == poolFeeVal._2
                }
            }
          }else{
            false
          }
        sigmaProp(consensusValid)
      }
      """.stripMargin
    script
  }

  /**
   * Generates Holding Contract with given constants
   * @param ctx Blockchain context used to generate contract
   * @param metadataAddress address of metadata
   * @return Compiled ErgoContract of Holding Smart Contract
   */
  def generateHoldingContract(ctx: BlockchainContext, metadataAddress: Address, metadataBox: InputBox): ErgoContract = {
    val metadataPropBytes: Array[Byte] = metadataAddress.getErgoAddress.script.bytes
    val metadataID: Array[Byte] = metadataBox.getId().getBytes
    val constantsBuilder = ConstantsBuilder.create()
    println("===========Generating Holding Address=============")
    println("const_metadataPropBytes: " + newColl(metadataPropBytes, ErgoType.byteType()))
    println("const_metadataID: " + newColl(metadataID, ErgoType.byteType()))

    val compiledContract = ctx.compileContract(constantsBuilder
      .item("const_metadataPropBytes", newColl(metadataPropBytes, ErgoType.byteType()))
      .item("const_metadataID", newColl(metadataID, ErgoType.byteType()))
      .build(), getSmartPoolingScript)
    compiledContract
  }

  /**
   * Generates a list of output boxes that follow a consensus. Metadata and Command boxes are assumed
   * to be inputs 0 and 1.
   * @param ctx Blockchain context
   * @return Returns list of output boxes to use in transaction
   */
  def generateOutputBoxes(ctx: BlockchainContext, inputBoxes: Array[InputBox], memberAddresses: Array[Address],
                          feeAddresses: Array[Address], holdingAddress: Address): Array[OutBox] = {
    val metadataBox = inputBoxes(0)
    val commandBox = inputBoxes(1)
    val TOTAL_HOLDED_VALUE = inputBoxes.foldLeft(0L){
      (accum: Long, box: InputBox) =>
        if(box.getErgoTree.bytes sameElements holdingAddress.getErgoAddress.script.bytes){
          accum + box.getValue
        }else
          accum
    }
    val currentConsensus = commandBox.getRegisters.get(0).getValue.asInstanceOf[Coll[(Coll[Byte], Long)]].toArray
    val currentPoolFees = metadataBox.getRegisters.get(2).getValue.asInstanceOf[Coll[(Coll[Byte], Int)]].toArray
    val currentTxFee = Parameters.MinFee * currentConsensus.length

    val feeList: Array[(Coll[Byte], Long)] = currentPoolFees.map{
      // Pool fee is defined as x/1000 of total inputs value.
      (poolFee: (Coll[Byte], Int)) => ( poolFee._1 , ((poolFee._2 * TOTAL_HOLDED_VALUE)/1000) )
    }

    // Total amount in holding after pool fees and tx fees.
    // This is the total amount of ERG to be distributed to pool members
    val totalValAfterFees = (feeList.foldLeft(TOTAL_HOLDED_VALUE){
      (accum: Long, poolFeeVal: (Coll[Byte], Long)) => accum - poolFeeVal._2
    })- currentTxFee

    val totalShares = currentConsensus.foldLeft(0L){(accum: Long, consVal: (Coll[Byte], Long)) => accum + consVal._2}

    // Returns some value that is a percentage of the total rewards after the fees.
    // The percentage used is the proportion of the share number passed in over the total number of shares.
    def getValueFromShare(shareNum: Long) = {
      val newBoxValue = (((totalValAfterFees) * (shareNum)) / (totalShares))
      newBoxValue
    }

    // Maps each propositionBytes stored in the consensus to a value obtained from the shares.
    val boxValueMap = currentConsensus.map{
      (consVal: (Coll[Byte], Long)) =>
        (consVal._1, getValueFromShare(consVal._2))
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
    memberAddresses.foreach{
      (addr: Address) =>
        val boxValue = boxValueMap.filter{consVal: (Coll[Byte], Long) => consVal._1.toArray sameElements addr.getErgoAddress.script.bytes}(0)
        val newOutBox = outB.value(boxValue._2).contract(new ErgoTreeContract(addr.getErgoAddress.script)).build()
        outBoxBuffer.append(newOutBox)
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


}
