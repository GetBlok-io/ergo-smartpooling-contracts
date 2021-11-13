package contracts

import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import scorex.crypto.hash.Blake2b256
import special.collection.Coll
import special.sigma.{GroupElement, SigmaProp}

import scala.collection.mutable.ListBuffer

object SmartPoolingContract {

  def getSmartPoolingScript: String = {
    /**
     * SmartPool Holding script
     * - This script requires 1 constant:
     * - Metadata Box Proposition Bytes
     *    -- Used to verify input 0 as metadata box
     *    -- Also used to verify input 1 is a box protected by a pool operator(who are stored in R8 of the metadata)
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
     */
    val script: String =
      s"""
      {
        val VALID_INPUTS_SIZE = INPUTS.size > 2
        val TOTAL_INPUTS_VALUE = INPUTS.fold(0L, {(accum: Long, box:Box) => accum + box.value})

        val metadataExists =
          if(VALID_INPUTS_SIZE){
            INPUTS(0).propositionBytes == const_metadataPropBytes
          }else{
            false
          }
        val metadataValid =
          if(metadataExists){
            allOf(Coll(
              INPUTS(0).R4[Coll[(Coll[Byte], Long)]].isDefined,       // Last consensus
              INPUTS(0).R5[Coll[(Coll[Byte], Coll[Byte])]].isDefined, // Current members
              INPUTS(0).R6[Coll[(Coll[Byte], Int)]].isDefined,        // Pool fees
              INPUTS(0).R7[Coll[Int]].isDefined,                      // Pool Information
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
              INPUTS(1).R7[Coll[Int]].isDefined,                      // New Pool Information
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

            val feeList: Coll[(Coll[Byte], Long)] = currentPoolFees.map{
              // Pool fee is defined as x/1000 of total inputs value.
              (poolFee: (Coll[Byte], Int)) => ( poolFee._1 , ((poolFee._2 * TOTAL_INPUTS_VALUE)/1000) )
            }

            // Total amount in holding after pool fees, minTxFee, Metadata Box value and command box value.
            // Freedom of command box ensures that it can be used for anything in the transaction, so long
            // as the boxes it makes are separate from the consensus outputs and that the box itself is spent
            // according to whatever script is protecting it.
            val totalValAfterFees = ((feeList.fold(TOTAL_INPUTS_VALUE, {
              (accum: Long, poolFeeVal: (Coll[Byte], Long)) => accum - poolFeeVal._2
            })) - const_MinTxFee) - INPUTS(0).value - INPUTS(1).value

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
  def generateHoldingContract(ctx: BlockchainContext, metadataAddress: Address): ErgoContract = {
    val metadataPropBytes: Array[Byte] = metadataAddress.getErgoAddress.script.bytes
    val constantsBuilder = ConstantsBuilder.create()
    println("===========Generating Holding Address=============")
    println("const_metadataPropBytesHashed: " + newColl(metadataPropBytes, ErgoType.byteType()))

    val compiledContract = ctx.compileContract(constantsBuilder
      .item("const_MinTxFee", Parameters.MinFee)
      .item("const_metadataPropBytesHashed", newColl(metadataPropBytes, ErgoType.byteType()))
      .build(), getSmartPoolingScript)
    compiledContract
  }

  /**
   * Generates a list of output boxes that follow a consensus from some command box.
   * @param ctx Blockchain context
   * @return Returns list of output boxes to use in transaction
   */
  def generateOutputBoxes(ctx: BlockchainContext) = {

  }


  def getBoxValue(shareNum: Long, totalShares: Long, totalValueAfterFees: Long): Long = {
    ((totalValueAfterFees * shareNum)/totalShares)
  }


}
