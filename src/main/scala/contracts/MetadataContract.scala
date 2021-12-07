package contracts

import boxes.{MetadataBox, MetadataInputBox, MetadataOutBox, MetadataOutBoxBuilder, MetadataTemplateBuilder}
import org.ergoplatform.appkit._
import scorex.crypto.hash.Blake2b256
import special.collection.Coll
import special.sigma.SigmaProp
import contracts.SpType._
import values.{BytesColl, MemberList, PoolFees, PoolInfo, PoolOperators, ShareConsensus}

import java.nio.charset.StandardCharsets
import scala.math.BigInt

object MetadataContract {
  /**
   * This is the metadata contract, representing the metadata box associated with the stratum holding contract.
   *
   *
   * R4 Of the metadata box holds a modified version of the last share consensus for this subpool.
   * -- A share consensus is some Coll[(Coll[Byte], Long)] that maps propBytes to share numbers.
   * R5 Of the metadata box holds the list of members associated with this subpool.
   *  -- In the Stratum Smart Pool, A member is defined as some (Coll[Byte], Coll[Byte])
   *  ---- member._1 are the proposition bytes for some box. This may be a box protected by a subpool or a box protected.
   *  ---- member._2 represents the name of the member. This may be displayed
   *  -- This flexibility allows us to add any address to the smart pool, whether that be a normal P2PK or a P2S.
   *  -- we can therefore make subpools under the smart pool to allow multiple levels of distribution.
   *  -- this solution may be brought to subpools in the future to allow multiple levels of subpooling.
   *
   * R6 Is a collection of pool fees.
   * -- Each pool fee is some (Coll[Byte], Int) representing some boxes propBytes that receives the Integer value
   * -- divided by 1000 and multiplied by the total transaction value.
   *
   * R7 Is a Coll[Long] representing Pool Information.
   * -- Element 0 of the collection represents the current Pool Epoch. This value must increase in the spending Tx.
   * -- Element 1 of the collection represents the height that this Epoch started.
   * -- Element 2 of the collection represents the height the SmartPool was created.
   * -- Element 3 of the collection represents the byte of array of the creation box serialized as a long
   * -- Element 4+ are not looked at or verified. This information may be stored and parsed according to the smart pool owner.
   *
   * -- * R8 is a collection members representing the pool operators. Each pool operator may send commands
   * -- to the pool
   *
   * A metadata box is only considered valid if it holds proper values inside its registers. Invalid metadata boxes
   * may be spent by anybody.
   *
   * If a metadata box is valid, it may spent in a transaction to create a new metadata box. Only the metadata
   * box itself verifies that is is valid and that the transaction has a valid layout according to the smart pool
   * protocol. This is the metadata boxes' unique job during any consensus transaction. It verifies that
   * input 0 is SELF and has valid registers, input 1 is some command box belonging to a pool operator. The
   * metadata box does not attempt to verify that it is being spent with holding boxes, only that
   * it is being spent with a command box. This allows a new holding address to be used
   * for each metadata box.
   * The metadata box also verifies that a new metadata box with
   * proper registers is created in the outputs.
   *
   * The metadata box does not perform consensus and does not verify any outputs other than there being another
   * valid metadata box in the output. A new valid metadata box is created using information from the old metadata box
   * and information from the command box. The validity of the command box must therefore also be checked by the metadata
   * box.
   */
  val script: String =
    s"""
    {
      val selfValid = allOf(Coll(
        SELF.R4[Coll[(Coll[Byte], Long)]].isDefined,        // Last consensus
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
            INPUTS(1).R4[Coll[(Coll[Byte], Long)]].isDefined,       // New consensus
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
            OUTPUTS(0).R4[Coll[(Coll[Byte], Long)]].isDefined,
            OUTPUTS(0).R5[Coll[(Coll[Byte], Coll[Byte])]].isDefined,
            OUTPUTS(0).R6[Coll[(Coll[Byte], Int)]].isDefined,
            OUTPUTS(0).R7[Coll[Long]].isDefined,
            OUTPUTS(0).R8[Coll[(Coll[Byte], Coll[Byte])]].isDefined,
            OUTPUTS(0).value == SELF.value
          ))
        }else{
          false
        }
      // This boolean verifies that important metadata is preserved
      // during the creation of the new metadata box.
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

          // creation ID represents id of box with epoch 0
          // If not epoch 0, then new metadata uses creationID stored in current metadata box
          // If current metadata box is on epoch 0, then we store its id as a long in the new metadata box
          val creationBoxPreserved =
          if(currentPoolInfo(0) != 0L){
            newPoolInfo(3) == currentPoolInfo(3)
          }else{
            // newPoolInfo(3) == byteArrayToLong(SELF.id)
            true
          }
          epochIncremented && epochHeightStored && creationHeightPreserved && creationBoxPreserved
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
          val newConsensus = INPUTS(1).R4[Coll[(Coll[Byte], Long)]].get
          val newMembers = INPUTS(1).R5[Coll[(Coll[Byte], Coll[Byte])]].get
          val consPropBytes = newConsensus.map{(consVal: (Coll[Byte], Long)) => consVal._1}
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
            OUTPUTS(0).R4[Coll[(Coll[Byte], Long)]].get == INPUTS(1).R4[Coll[(Coll[Byte], Long)]].get,
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


  /**
   * Generates Metadata contract
   * @param ctx Blockchain context used to generate contract
   * @param poolCreator Pool creator who has ability to delete metadata box
   * @return Compiled ErgoContract of Metadata Contract
   */
  def generateMetadataContract(ctx: BlockchainContext, poolCreator: Address): ErgoContract = {
    val constantsBuilder = ConstantsBuilder.create().item("const_poolCreator", poolCreator.getPublicKey)
      .build()
    val compiledContract = ctx.compileContract(constantsBuilder, script)
    compiledContract
  }


  /**
   * Build a metadata box using a pool operator address, initial value
   * @param outBoxBuilder OutBox builder supplied by context
   * @param metadataContract Contract to use for output
   * @param poolOp Address of initial pool operator
   * @param initialValue initial value to keep in metadata box
   * @param currentHeight current height from blockchain context
   * @return OutBox representing new metadata box with initialized values.
   */
  def buildInitialMetadata(outBoxBuilder: OutBoxBuilder, metadataContract: ErgoContract, poolOp: Address, initialValue: Long, currentHeight: Int): MetadataOutBox = {
    val poolOpBytes = poolOp.getErgoAddress.script.bytes
    val initialConsensus: ShareConsensus = ShareConsensus.fromConversionValues(Array((poolOpBytes, 0L)))
    val initialMembers: MemberList = MemberList.fromConversionValues(Array((poolOpBytes, poolOp.toString)))
    val initialPoolFee: PoolFees = PoolFees.fromConversionValues(Array((poolOpBytes, 1)))
    val initialPoolOp: PoolOperators = PoolOperators.fromConversionValues(Array((poolOpBytes, poolOp.toString)))
    // The following info is stored: epoch 0, currentEpochHeight, creationEpochHeight,
    // and a filler value for the box id, since that info can only be obtained after the first spending tx.
    val initialPoolInfo: PoolInfo = PoolInfo.fromConversionValues(Array(0L, currentHeight.toLong, currentHeight.toLong, 0L))
    val mTB = new MetadataTemplateBuilder(outBoxBuilder)

    mTB
      .contract(metadataContract)
      .value(initialValue)
      .setConsensus(initialConsensus)
      .setMembers(initialMembers)
      .setPoolFees(initialPoolFee)
      .setPoolInfo(initialPoolInfo)
      .setPoolOps(initialPoolOp)
      .setMetadata()
      .build()
  }

  /**
   * Build a MetadataOutBox with the given Smart Pool registers
   * @param outBoxBuilder builder to wrap
   * @param metadataContract ErgoContract to make metadata template box under
   * @param initialValue Value to use for output box
   * @param consensus Share Consensus
   * @param members Member List
   * @param fees Pool Fees
   * @param info Pool Info
   * @param operators Pool Operators
   * @return New MetadataOutBox with given registers and parameters set
   */
  def buildMetadataBox(outBoxBuilder: OutBoxBuilder, metadataContract: ErgoContract, initialValue: Long,
                      consensus: ShareConsensus, members: MemberList, fees: PoolFees, info: PoolInfo,
                      operators: PoolOperators): MetadataOutBox = {
    val mTB = new MetadataTemplateBuilder(outBoxBuilder)
    mTB
      .contract(metadataContract)
      .value(initialValue)
      .setConsensus(consensus)
      .setMembers(members)
      .setPoolFees(fees)
      .setPoolInfo(info)
      .setPoolOps(operators)
      .setMetadata()
      .build()
  }

  /**
   * Copy the contents of a metadata-like input box into a new output box.
   * @param metadataTemplateBox template box to copy from
   * @param outBoxBuilder outBoxBuilder to build output from
   * @param metadataContract Contract to use for metadata-like output
   * @return New MetadataOutBox with same exact register values
   */
  def copyMetadataBox(metadataTemplateBox: MetadataInputBox, outBoxBuilder: OutBoxBuilder, metadataContract: ErgoContract): MetadataOutBox = {
    val mTB = new MetadataTemplateBuilder(outBoxBuilder)
    mTB
      .contract(metadataContract)
      .value(metadataTemplateBox.getValue)
      .setConsensus(metadataTemplateBox.getShareConsensus)
      .setMembers(metadataTemplateBox.getMemberList)
      .setPoolFees(metadataTemplateBox.getPoolFees)
      .setPoolInfo(metadataTemplateBox.getPoolInfo)
      .setPoolOps(metadataTemplateBox.getPoolOperators)
      .setMetadata()
      .build()
  }

  /**
   * Build a standard metadata output from a distribution transaction using data from a metadata like input box.
   * Only epoch and epoch height are updated.
   * Can also be used to make command boxes following the default transaction.
   * @param metadataTemplateBox metadata-like box to copy from
   * @param outBoxBuilder builder to use for metadata template
   * @param metadataContract metadata contract to use
   * @param currentHeight current blockheight from context
   * @return MetadataOutBox for the default distribution transaction
   */
  def buildNextMetadataOutput(metadataTemplateBox: MetadataInputBox, outBoxBuilder: OutBoxBuilder, metadataContract: ErgoContract, currentHeight: Int): MetadataOutBox = {
    val lastConsensus = metadataTemplateBox.getShareConsensus
    val memberList = metadataTemplateBox.getMemberList
    val poolFees = metadataTemplateBox.getPoolFees
    val poolInfo = metadataTemplateBox.getPoolInfo
    val poolOps = metadataTemplateBox.getPoolOperators

    val newEpoch = metadataTemplateBox.getCurrentEpoch + 1
    val newEpochHeight = currentHeight
    val updatedPoolInfo = poolInfo.setCurrentEpoch(newEpoch).setCurrentEpochHeight(newEpochHeight)

    val mTB = new MetadataTemplateBuilder(outBoxBuilder)

    mTB
      .value(metadataTemplateBox.getValue)
      .contract(metadataContract)
      .setConsensus(lastConsensus)
      .setMembers(memberList)
      .setPoolFees(poolFees)
      .setPoolInfo(updatedPoolInfo)
      .setPoolOps(poolOps)
      .setMetadata()
      .build()

  }


}
