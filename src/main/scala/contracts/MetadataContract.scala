package contracts

import app.AppParameters
import boxes.builders.MetadataOutputBuilder
import boxes.{CommandInputBox, MetadataInputBox, MetadataOutBox}
import org.ergoplatform.appkit._
import scorex.crypto.hash.Blake2b256
import special.collection.Coll
import special.sigma.SigmaProp
import registers.{BytesColl, MemberList, MetadataRegisters, PoolFees, PoolInfo, PoolOperators, ShareConsensus}
import sigmastate.Values

import java.nio.charset.StandardCharsets
import scala.math.BigInt



object MetadataContract {
  val script: String =
    s"""
    {
      val selfValid = allOf(Coll(
        SELF.R4[Coll[(Coll[Byte], Coll[Long])]].isDefined,        // Last consensus
        SELF.R5[Coll[(Coll[Byte], Coll[Byte])]].isDefined,  // Current members
        SELF.R6[Coll[(Coll[Byte], Int)]].isDefined,         // Pool fees
        SELF.R7[Coll[Long]].isDefined,                       // Pool Information
        SELF.R8[Coll[(Coll[Byte], Coll[Byte])]].isDefined,  // Pool operators
        INPUTS(0) == SELF
      ))
      val commandExists =
        if(selfValid){
          val POOL_OPERATORS = SELF.R8[Coll[(Coll[Byte], Coll[Byte])]].get
          val COMMAND_BYTES = INPUTS(1).propositionBytes
          // Verifies that the command boxes proposition bytes exists in the pool operators
          val commandOwnedByOperators = POOL_OPERATORS.exists{
            (op: (Coll[Byte], Coll[Byte])) =>
              op._1 == COMMAND_BYTES
          }
          commandOwnedByOperators
        }else{
          false
        }
      val commandValid =
        if(commandExists){
          allOf(Coll(
            INPUTS(1).R4[Coll[(Coll[Byte], Coll[Long])]].isDefined,       // New consensus
            INPUTS(1).R5[Coll[(Coll[Byte], Coll[Byte])]].isDefined, // New members list
            INPUTS(1).R6[Coll[(Coll[Byte], Int)]].isDefined,        // New Pool fees
            INPUTS(1).R7[Coll[Long]].isDefined,                      // New Pool Information
            INPUTS(1).R8[Coll[(Coll[Byte], Coll[Byte])]].isDefined  // New Pool operators
          ))
        }else{
          false
        }

      val newMetadataExists = OUTPUTS(0).propositionBytes == SELF.propositionBytes
      val newMetadataValid =
        if(newMetadataExists){
          allOf(Coll(
            OUTPUTS(0).R4[Coll[(Coll[Byte], Coll[Long])]].isDefined,
            OUTPUTS(0).R5[Coll[(Coll[Byte], Coll[Byte])]].isDefined,
            OUTPUTS(0).R6[Coll[(Coll[Byte], Int)]].isDefined,
            OUTPUTS(0).R7[Coll[Long]].isDefined,
            OUTPUTS(0).R8[Coll[(Coll[Byte], Coll[Byte])]].isDefined,
            OUTPUTS(0).value == SELF.value,
            OUTPUTS(0).tokens.size == 1
          ))
        }else{
          false
        }
      // This boolean verifies that important metadata is preserved
      // during the creation of the new metadata box.
      // It also verifies that the smart pool nft is preserved or generated if the epoch is 0
      val metadataIsPreserved =
        if(newMetadataValid){
          val currentPoolInfo = SELF.R7[Coll[Long]].get
          val newPoolInfo = OUTPUTS(0).R7[Coll[Long]].get

          // verifies that epoch is increased by 1
          val epochIncremented = newPoolInfo(0) == currentPoolInfo(0) + 1L

          // New epoch height is stored and is greater than last height
          val epochHeightStored = newPoolInfo(1) <= HEIGHT && newPoolInfo(1) > currentPoolInfo(1)

          // creation epoch height stays same between spending tx
          val creationHeightPreserved = newPoolInfo(2) == currentPoolInfo(2)

          val smartPoolNFTPreserved =
          if(currentPoolInfo(0) != 0L){
            SELF.tokens(0)._1 == OUTPUTS(0).tokens(0)._1
          }else{
            OUTPUTS(0).tokens(0)._1 == SELF.id
          }

          epochIncremented && epochHeightStored && creationHeightPreserved && smartPoolNFTPreserved
        }else{
          false
        }

      // This boolean verifies that the new member list
      // is consistent with consensus. That is, no new members are added
      // unless they also exist in the consensus that occurred during this epoch.
      // This ensures that every member of the subpool has a verifiable amount of shares they
      // received a payout for. Even if that verifiable amount is 0.
      val membersInConsensus =
        if(commandValid){
          val newConsensus = INPUTS(1).R4[Coll[(Coll[Byte], Coll[Long])]].get
          val newMembers = INPUTS(1).R5[Coll[(Coll[Byte], Coll[Byte])]].get
          val consPropBytes = newConsensus.map{(consVal: (Coll[Byte], Coll[Long])) => consVal._1}
          newMembers.forall{
            (member: (Coll[Byte], Coll[Byte])) =>
              consPropBytes.indexOf(member._1, 0) != -1
          }
        }else{
          false
        }

      // Verify that the registers in the command box are stored in the new metadata box
      val newMetadataFromCommand =
        if(membersInConsensus && metadataIsPreserved){
          allOf(Coll(
            OUTPUTS(0).R4[Coll[(Coll[Byte], Coll[Long])]].get == INPUTS(1).R4[Coll[(Coll[Byte], Coll[Long])]].get,
            OUTPUTS(0).R5[Coll[(Coll[Byte], Coll[Byte])]].get == INPUTS(1).R5[Coll[(Coll[Byte], Coll[Byte])]].get,
            OUTPUTS(0).R6[Coll[(Coll[Byte], Int)]].get == INPUTS(1).R6[Coll[(Coll[Byte], Int)]].get,
            OUTPUTS(0).R7[Coll[Long]].get == INPUTS(1).R7[Coll[Long]].get,
            OUTPUTS(0).R8[Coll[(Coll[Byte], Coll[Byte])]].get == INPUTS(1).R8[Coll[(Coll[Byte], Coll[Byte])]].get
          ))
        }else{
          false
        }

      if(selfValid){
        // We verify that the metadata box follows the proper consensus
        // Currently no way to destroy metadata box
        sigmaProp(newMetadataFromCommand)
      }else{
        sigmaProp(true)
      }
    }
      """.stripMargin

  private val constants = ConstantsBuilder.create().build()
  def generateMetadataContract(ctx: BlockchainContext): ErgoContract = {
    val contract: ErgoContract = ctx.compileContract(constants, script)
    contract
  }

  /**
   * Builds genesis box for SmartPool
   *
   * @param mOB    OutBox builder supplied by context
   * @param metadataContract Contract to use for output
   * @param poolOp           Address of initial pool operator
   * @param initialValue     initial value to keep in metadata box
   * @param currentHeight    current height from blockchain context
   * @return OutBox representing new metadata box with initialized registers.
   */
  def buildGenesisBox(mOB: MetadataOutputBuilder, metadataContract: ErgoContract, poolOp: Address, initialValue: Long, currentHeight: Int): OutBox = {
    val poolOpBytes = poolOp.getErgoAddress.script.bytes
    val initialConsensus: ShareConsensus = ShareConsensus.fromConversionValues(Array((poolOpBytes, Array(1L, (1 * Parameters.OneErg)/10 , 0L))))
    val initialMembers: MemberList = MemberList.fromConversionValues(Array((poolOpBytes, poolOp.toString)))
    val initialPoolFee: PoolFees = PoolFees.fromConversionValues(Array((poolOpBytes, 1)))
    val initialPoolOp: PoolOperators = PoolOperators.fromConversionValues(Array((poolOpBytes, poolOp.toString)))
    // The following info is stored: epoch 0, currentEpochHeight, creationEpochHeight,
    // and a filler value for the box id, since that info can only be obtained after the first spending tx.
    val initialPoolInfo: PoolInfo = PoolInfo.fromConversionValues(Array(0L, currentHeight.toLong, currentHeight.toLong, 0L))
    val initialMetadata = new MetadataRegisters(initialConsensus, initialMembers, initialPoolFee, initialPoolInfo, initialPoolOp)
    mOB
      .contract(metadataContract)
      .value(initialValue)
      .creationHeight(currentHeight)
      .setMetadata(initialMetadata)
      .buildInitial()
  }

  /**
   * Build a MetadataOutBox with the given Smart Pool registers and Smart Pool ID
   *
   * @param outBoxBuilder    builder to wrap
   * @param metadataContract ErgoContract to make metadata template box under
   * @param initialValue     Value to use for output box
   * @param consensus        Share Consensus
   * @param members          Member List
   * @param fees             Pool Fees
   * @param info             Pool Info
   * @param operators        Pool Operators
   * @return New MetadataOutBox with given registers and parameters set
   */
  def buildMetadataBox(mOB: MetadataOutputBuilder, metadataContract: ErgoContract, initialValue: Long,
                       metadataRegisters: MetadataRegisters, smartPoolId: ErgoId): MetadataOutBox = {
    mOB
      .contract(metadataContract)
      .value(initialValue)
      .setSmartPoolId(smartPoolId)
      .setMetadata(metadataRegisters)
      .build()
  }


  /**
   * Build a MetadataOutBox with the given Smart Pool registers and Smart Pool ID
   *
   * @param metadataContract ErgoContract to make metadata template box under
   * @return New MetadataOutBox with given registers and parameters set
   */
  def buildFromCommandBox(mOB: MetadataOutputBuilder, commandBox: CommandInputBox, metadataContract: ErgoContract,
                          value: Long, smartPoolId: ErgoId): MetadataOutBox = {
    mOB
      .contract(metadataContract)
      .value(value)
      .setSmartPoolId(smartPoolId)
      .setMetadata(commandBox.getMetadataRegisters)
      .build()
  }

}

