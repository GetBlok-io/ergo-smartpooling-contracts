package groups

import app.{ExitCodes, exit}
import boxes.{BoxHelpers, CommandInputBox, CommandOutBox, MetadataInputBox}
import configs.SmartPoolConfig
import contracts.command.{CommandContract, PKContract}
import contracts.holding.HoldingContract
import groups.chains.{CommandChain, DistributionChain, HoldingChain, HoldingChainException}
import groups.exceptions.{ExactTokenBoxNotFoundException, StoredPaymentNotFoundException}
import logging.LoggingHandler
import org.ergoplatform.appkit.impl.{ErgoTreeContract, InputBoxImpl}
import org.ergoplatform.appkit._
import org.slf4j.{Logger, LoggerFactory}
import persistence.{BoxIndex, BoxStatus}
import registers.{MemberList, PoolFees, PoolOperators, ShareConsensus}
import sigmastate.Values.ErgoTree
import transactions.{CreateCommandTx, DistributionTx}

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.util.Try


class DistributionGroup2(ctx: BlockchainContext, boxIndex: BoxIndex, prover: ErgoProver, address: Address,
                         holdingContract: HoldingContract, commandContract: CommandContract, config: SmartPoolConfig,
                         shareConsensus: ShareConsensus, memberList: MemberList, poolFees: PoolFees, isFailureAttempt: Boolean) extends TransactionGroup[Map[MetadataInputBox, String]]{

  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_DIST_GRP)
  private[this] var _completed = Map[MetadataInputBox, String]()
  private[this] var _failed = Map[MetadataInputBox, String]()
  private[this] var _txs = Map[MetadataInputBox, SignedTransaction]()


  private val cmdConf = config.getParameters.getCommandConf
  private val metaConf = config.getParameters.getMetaConf
  private val holdConf = config.getParameters.getHoldingConf

  private var metadataInputs: Array[MetadataInputBox] = Array()

  private var boxToHolding = Map.empty[MetadataInputBox, InputBox]
  private var boxToStorage = Map.empty[MetadataInputBox, InputBox]

  private var boxToShare = Map.empty[MetadataInputBox, ShareConsensus]
  private var boxToMember = Map.empty[MetadataInputBox, MemberList]


  private var boxToFees = Map.empty[MetadataInputBox, InputBox]
  private var boxToCommand = Map.empty[MetadataInputBox, CommandInputBox]

  final val SHARE_CONSENSUS_LIMIT = 10
  final val STANDARD_FEE = Parameters.MinFee * 5

  var tokenAssigned: String = ""

  override def buildGroup: TransactionGroup[Map[MetadataInputBox, String]] = {
    logger.info("Now building DistributionGroup")
    if(!isFailureAttempt) {
      require(boxIndex.boxes.forall(b => b._2.status == BoxStatus.INITIATED), "Not all boxes were initiated!")
      logger.info("Now grabbing inputs, holding, and storage boxes from context")
      metadataInputs = boxIndex.grabFromContext(ctx)
      boxToHolding = boxIndex.getHoldingBoxes(ctx)
      boxToStorage = boxIndex.getStorageBoxes(ctx)
    }else{
      metadataInputs = boxIndex.getUsed.grabFromContext(ctx)
      val tryGetHolding = Try{ boxIndex.getUsed.getSuccessful.getHoldingBoxes(ctx) ++ boxIndex.getUsed.getFailed.getHoldingBoxes(ctx) }
      boxToStorage = boxIndex.getUsed.getSuccessful.getStorageBoxes(ctx) ++ boxIndex.getUsed.getFailed.getStorageBoxes(ctx)
      if(tryGetHolding.isSuccess){
        boxToHolding = tryGetHolding.get
      }else{
        logger.warn("The holding box could not be found, this is okay if this is the first distribution run for this block.")
        logger.warn("Now exiting...")
        exit(logger, ExitCodes.HOLDING_NOT_COVERED)
      }
    }

    logger.info(s"Using ${metadataInputs.length} metadata boxes, with ${shareConsensus.cValue.length} consensus vals")
    val subpoolSelector = new SubpoolSelector
    val membersLeft = subpoolSelector.selectDefaultSubpools(metadataInputs, shareConsensus, memberList)._2
    boxToShare = subpoolSelector.shareMap
    boxToMember = subpoolSelector.memberMap

    if(isFailureAttempt){
      for(box <- boxToShare.keys){
        if(boxIndex(box.getSubpoolId.toInt).status == BoxStatus.CONFIRMED){
          removeFromMaps(box)
        }
      }
    }

    for(boxSh <- boxToShare){
      logger.info(s"Subpool ${boxSh._1.getSubpoolId} has ${boxSh._2.cValue.length} members")
      if(boxSh._2.cValue.length > SHARE_CONSENSUS_LIMIT){
        logger.error(s"There was an error assigning a share consensus to subpool ${boxSh._1.getSubpoolId}")
        logger.error(s"Current share consensus length: ${boxSh._2.cValue.length}")
        app.exit(logger, ExitCodes.SUBPOOL_TX_FAILED)
      }
//      if(boxSh._2.cValue.length == 0 || boxSh._2.cValue.map(c => c._2(0)).sum == 0){
//        logger.info(s"Subpool ${boxSh._1.getSubpoolId} removed from maps due to having 0 new members or shares!")
//        removeFromMaps(boxSh._1)
//      }

      logger.info(s"==== Subpool ${boxSh._1.getSubpoolId} ====\n")
      val memberStrings = boxToMember(boxSh._1).cValue.map(m => m._2)
      logger.info(memberStrings.mkString("\n"))
    }

    if(membersLeft.length > 0){
      logger.warn("There are still members left after adding to existing subpools. Now exiting...")
      logger.warn("Members to add: " + membersLeft.map(m => m._2).mkString("\n"))

      app.exit(logger, ExitCodes.SUBPOOL_TX_FAILED)
    }

    if(isFailureAttempt){
      logger.info("Is failure attempt!")
      boxToMember.foreach(m => logger.info(m.toString()))
      boxToMember.foreach(m => logger.info(m._2.cValue.mkString("Array(", ", ", ")")))
    }
    logger.info(s"boxToShare: ${boxToShare.size} boxToMember: ${boxToMember.size}")

    boxToFees = collectFeeBoxes

    logger.info("BoxToHolding Length: " + boxToHolding.size)
    boxToHolding.values.foreach(i => logger.info("BoxToHolding Value: " + i.getValue + " Id " + i.getId))

    this
  }

  override def executeGroup: TransactionGroup[Map[MetadataInputBox, String]] = {

    val commandChain = new CommandChain(ctx, boxToFees, boxToHolding, boxToStorage, boxToShare, boxToMember,
                                        prover, address, STANDARD_FEE, holdingContract, commandContract,
                                        poolFees, config)
    boxToCommand = commandChain.executeChain.result
    commandChain.failed.foreach(m => removeFromMaps(m._1))

    val distributionChain = new DistributionChain(ctx, boxToCommand, boxToHolding, boxToStorage, boxToShare,
                                                  prover, address, holdingContract, config)
    _txs = distributionChain.executeChain.result
    _completed = distributionChain.completed
    _failed = distributionChain.failed

    this
  }

  override def completed: Map[MetadataInputBox, String] = _completed

  override def failed: Map[MetadataInputBox, String] = _failed

  def successfulTxs: Map[MetadataInputBox, SignedTransaction] = _txs

  /**
   * Creates tx to collect fee boxes from node wallet and use them in subsequent transactions
   */
  def collectFeeBoxes: Map[MetadataInputBox, InputBox] = {

    val txB = ctx.newTxBuilder()
    var totalFees = 0L
    var boxToOutputs = Map.empty[MetadataInputBox, Array[OutBox]]
    var boxToFeeInputs = Map.empty[MetadataInputBox, InputBox]
    for(boxSh <- boxToShare){
      val commandFeeOutput = txB.outBoxBuilder().value(cmdConf.getCommandValue + STANDARD_FEE).contract(new ErgoTreeContract(address.getErgoAddress.script, address.getNetworkType))
      if(tokenAssigned != ""){
        // If vote token id exists, lets send vote tokens equal to total amount in holding contracts
        if(boxSh._1.getPoolOperators.cValue.exists(o => o._1 sameElements commandContract.getErgoTree.bytes)) {
          val voteTokenId = ErgoId.create(tokenAssigned)
          var totalTokenAmnt = boxToHolding(boxSh._1).getValue
          if(boxToStorage.contains(boxSh._1)){
            totalTokenAmnt = totalTokenAmnt + boxToStorage(boxSh._1).getValue
          }
          commandFeeOutput.tokens(new ErgoToken(voteTokenId, totalTokenAmnt))
          logger.info(s"Added $totalTokenAmnt tokens to commandFeeOutput box for subpool ${boxSh._1.getSubpoolId}")
        }
      }
      val commandFee = commandFeeOutput.build()

      totalFees = totalFees + cmdConf.getCommandValue + STANDARD_FEE

      // Elem 0 is distribution out box
      boxToOutputs = boxToOutputs ++ Map((boxSh._1, Array(commandFee)))
    }


    val feeTxOutputs = boxToOutputs.values.flatten.toArray
    var feeInputBoxes = ctx.getWallet.getUnspentBoxes(totalFees + STANDARD_FEE).get()
//    val tryInputs = feeInputBoxes.asScala.map(b => Try{ctx.getBoxesById(b.getId.toString)}).filter(t => t.isSuccess).flatMap(t => t.get).toList
//    feeInputBoxes = tryInputs.asJava

    if(tokenAssigned != ""){
      logger.info("Now checking if enough tokens are in current boxes")
      if(boxToShare.exists(m => m._1.getPoolOperators.cValue.exists(op => op._1 sameElements commandContract.getErgoTree.bytes))) {
        val holdingWithOp = boxToHolding.filter(m => m._1.getPoolOperators.cValue.exists(op => op._1 sameElements commandContract.getErgoTree.bytes))
        val storageWithOp = boxToStorage.filter(m => m._1.getPoolOperators.cValue.exists(op => op._1 sameElements commandContract.getErgoTree.bytes))

        val voteTokenId = ErgoId.create(tokenAssigned)
        val totalTokens = holdingWithOp.values.map(v => v.getValue.toLong).sum + storageWithOp.values.map(v => v.getValue.toLong).sum
        val currentTokens = feeInputBoxes.asScala
          .filter(ib => ib.getTokens.size() > 0)
          .filter(ib => ib.getTokens.get(0).getId.toString == voteTokenId.toString)
          .map(ib => ib.getTokens.get(0).getValue)
          .sum

        logger.info("Total tokens needed: " + totalTokens)
        logger.info("Current tokens in boxes: " + currentTokens)

        if (currentTokens < totalTokens) {
          logger.info("Not enough tokens found in current boxes, now searching for exact token box.")

          val exactTokenBox = BoxHelpers.findExactTokenBox(ctx, address, voteTokenId, totalTokens - currentTokens)
          if (exactTokenBox.isDefined) {
            logger.info("Exact token box found, now adding to fee input boxes.")
            feeInputBoxes.add(exactTokenBox.get)

            logger.info(s"Token Box: \n " + exactTokenBox.get.toJson(true))
          } else {
            logger.error("No token box could be found!")

            throw new ExactTokenBoxNotFoundException
          }
        }
      }else{
        logger.info("No tokens needed for current boxes")
      }
    }

    logger.info("Total Fees: " + totalFees)
    logger.info("Total Fee Output Size: " + feeTxOutputs.size)
    logger.info("Total Fee Tx Input Size: " + feeInputBoxes.size)
    logger.info("Total Fee Tx Input Val: " + BoxHelpers.sumBoxes(feeInputBoxes.asScala.toList))
    logger.info("UnsignedTx Now building")

    val unsignedTx = txB.boxesToSpend(feeInputBoxes).fee(STANDARD_FEE).outputs(feeTxOutputs:_*).sendChangeTo(address.getErgoAddress).build()
    val signedTx = prover.sign(unsignedTx)

    logger.info("Fee Tx signed")
    val txId = ctx.sendTransaction(signedTx)
    logger.info(s"Tx sent with fee: $txId and cost: ${signedTx.getCost}")

    var inputsAdded = Array[InputBox]()
    val feeInputs = signedTx.getOutputsToSpend

    for(box <- boxToShare.keys){
      val outBoxes = boxToOutputs(box)
      val commandFeeVal = outBoxes(0).getValue
      val commandFeeBox = feeInputs.asScala.filter(fb => fb.getValue == commandFeeVal && !inputsAdded.contains(fb)).head
      inputsAdded = inputsAdded++Array(commandFeeBox)

      logger.info("CommandFeeBoxId: " + commandFeeBox.getId)

      if(commandFeeBox.getTokens.size() > 0){
        logger.info(s"CommandFeeBox Tokens - id: ${commandFeeBox.getTokens.get(0).getId} amnt: ${commandFeeBox.getTokens.get(0).getValue}")
      }

      logger.info("Inputs Added: " + inputsAdded.length)
      boxToFeeInputs = boxToFeeInputs ++ Map((box, commandFeeBox))
    }

    logger.info("Fee Box Inputs: " + boxToFeeInputs.size)
    Thread.sleep(500)
    boxToFeeInputs
  }

  def removeFromMaps(metadataInputBox: MetadataInputBox): Unit = {
    boxToShare = boxToShare--List(metadataInputBox)
    boxToMember = boxToMember--List(metadataInputBox)
    boxToHolding = boxToHolding--List(metadataInputBox)
    boxToStorage = boxToStorage--List(metadataInputBox)
    boxToCommand = boxToCommand--List(metadataInputBox)
    boxToFees = boxToFees--List(metadataInputBox)
  }

}