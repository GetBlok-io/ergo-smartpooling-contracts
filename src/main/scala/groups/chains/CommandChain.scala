package groups.chains

import boxes.{CommandInputBox, CommandOutBox, MetadataInputBox}
import contracts.command.{CommandContract, PKContract}
import contracts.holding.HoldingContract
import logging.LoggingHandler
import org.ergoplatform.appkit._
import configs.SmartPoolConfig
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.slf4j.{Logger, LoggerFactory}
import persistence.models.Models.BoxEntry
import registers.{MemberList, PoolFees, PoolOperators, ShareConsensus}
import transactions.CreateCommandTx

import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}
import scala.util.Try

class CommandChain(ctx: BlockchainContext, boxToFees: Map[MetadataInputBox, InputBox], boxToHolding: Map[MetadataInputBox, InputBox],
                   boxToStorage: Map[MetadataInputBox, InputBox], boxToShare: Map[MetadataInputBox, ShareConsensus],
                   boxToMember: Map[MetadataInputBox, MemberList], prover: ErgoProver, address: Address,
                   feeValue: Long, holdingContract: HoldingContract, commandContract: CommandContract,
                   poolFees: PoolFees, config: SmartPoolConfig){

  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_CMD_CHAIN)
  private[this] var _completed = Map.empty[MetadataInputBox, String]
  private[this] var _failed = Map.empty[MetadataInputBox, String]
  private val cmdConf = config.getParameters.getCommandConf
  private val metaConf = config.getParameters.getMetaConf
  private val holdConf = config.getParameters.getHoldingConf
  private var commandContractToUse: CommandContract = _

  private var boxToCommand = Map.empty[MetadataInputBox, CommandInputBox]

  var tokenAssigned: String = ""

  def executeChain: CommandChain = {
    logger.info(boxToHolding.map(h => ("Subpool " + h._1.getSubpoolId + s" with id ${h._1.getId}", "Holding: " + h._2.getId.toString)).toString())
    logger.info(boxToStorage.map(h => ("Subpool " + h._1.getSubpoolId + s" with id ${h._1.getId}", "Storage: " + h._2.getId.toString)).toString())
    logger.info("Now executing CommandChain")
    var boxToCmdOutput = Map.empty[MetadataInputBox, CommandOutBox]
    for (metadataBox <- boxToShare.keys) {
      val commandChain = Try {
        logger.info(s"Creating tx for subpool ${metadataBox.getSubpoolId}")
        val commandTx = new CreateCommandTx(ctx.newTxBuilder())
        logger.info(s"Current holding size: ${boxToHolding.size} Current Storage size: ${boxToStorage.size}")
        val inputBoxes = List(boxToFees(metadataBox))
        // TODO: Take from config instead
        logger.info("Fee boxes inserted")
        val newPoolOperators = PoolOperators.convert(Array(
          (address.getErgoAddress.script.bytes, address.toString),
        ))
        logger.info("New pool ops created")
        var holdingInputs = List(boxToHolding(metadataBox))
        logger.info("Holding box added")
        if (boxToStorage.contains(metadataBox)) {
          holdingInputs = holdingInputs ++ List(boxToStorage(metadataBox))
          logger.info("Storage box added")
        }
        logger.info("Holding inputs created")
        commandContractToUse = new PKContract(address)

        if(metadataBox.getPoolOperators.cValue.exists(op => op._1 sameElements commandContract.getErgoTree.bytes)){
          logger.info("MetadataBox has custom command contract in pool operators, now prioritizing custom command contract.")
          commandContractToUse = commandContract
        }
        logger.info(s"Adding new command box to map for box ${metadataBox.getId}")
        val unbuiltCommandTx =
          commandTx
            .metadataToCopy(metadataBox)
            .withCommandContract(commandContractToUse)
            .commandValue(cmdConf.getCommandValue)
            .inputBoxes(inputBoxes: _*)
            .withHolding(holdingContract, holdingInputs)
            .setConsensus(boxToShare(metadataBox))
            .setMembers(boxToMember(metadataBox))
            .setPoolFees(poolFees)
            .setPoolOps(newPoolOperators)
        if(tokenAssigned != "" && metadataBox.getPoolOperators.cValue.exists(op => op._1 sameElements commandContract.getErgoTree.bytes)) {
          logger.info("Custom token id set, adding tokens to command output")
          unbuiltCommandTx.cOB.tokens(boxToFees(metadataBox).getTokens.get(0))
        }
        val unsignedCommandTx = unbuiltCommandTx.buildCommandTx()
        logger.info(s"Command box built!")
        boxToCmdOutput = boxToCmdOutput ++ Map((metadataBox, commandTx.commandOutBox))
        logger.info("Command box added to outputs")
      }
      if(commandChain.isFailure) {
        logger.warn(s"Exception caught for metadata box ${metadataBox.getId.toString} during command chain execution!")
        logger.warn(commandChain.failed.get.getMessage)

        logger.warn("Now adding metadata box to failure list")
        _failed = _failed++Map((metadataBox, BoxEntry.CMD_TX))
        removeFromMaps(metadataBox)
      }
    }

    val commandInputs = boxToFees.values.toList
    logger.info(s"New cmd tx with ${commandInputs.length} command inputs and ${boxToCmdOutput.size} command outputs")


    val unsignedCommandTx = ctx.newTxBuilder()
      .boxesToSpend(commandInputs.asJava)
      .fee(feeValue * boxToCmdOutput.size)
      .outputs(boxToCmdOutput.values.map(c => c.asOutBox).toList:_*)
      .sendChangeTo(address.getErgoAddress)
      .build()
    val signedCmdTx = prover.sign(unsignedCommandTx)
    logger.info(s"Signed Tx Num Bytes Cost: ${signedCmdTx.toBytes.length}")
    logger.info(s"Signed Tx Cost: ${signedCmdTx.getCost}")
    val cmdOutputsToSpend = signedCmdTx.getOutputsToSpend.asScala
    for(ib <- cmdOutputsToSpend) {
      val asErgoBox = ib.asInstanceOf[InputBoxImpl].getErgoBox
      logger.info("Total ErgoBox Bytes: " + asErgoBox.bytes.length)
    }

    logger.info("Command Tx successfully signed")
    val cmdTxId = ctx.sendTransaction(signedCmdTx)
    logger.info(s"Tx was successfully sent with id: $cmdTxId and cost: ${signedCmdTx.getCost}")


    Thread.sleep(500)
    logger.info("Command Input Boxes: " + cmdOutputsToSpend)

    val pkContract = new PKContract(address)

    val commandBoxes = cmdOutputsToSpend
      .filter(ib => ib.getValue == cmdConf.getCommandValue)
      .filter(ib =>
        ((ib.getErgoTree.bytes sameElements commandContract.getErgoTree.bytes) || (ib.getErgoTree.bytes sameElements pkContract.getErgoTree.bytes))).map{
      o =>
        commandContractToUse = new PKContract(address)
        if(o.getErgoTree.bytes sameElements commandContract.getErgoTree.bytes) {
          commandContractToUse = commandContract
        }
        new CommandInputBox(o, commandContractToUse)
    }

    for(metadataBox <- boxToShare.keys){
      val commandBox = commandBoxes.filter(i => i.getSubpoolId == metadataBox.getSubpoolId).head
      if(commandBox.contract.getErgoTree.bytes sameElements commandContract.getErgoTree.bytes) {
        logger.info(s"Subpool ${commandBox.getSubpoolId} has custom command contract")
        if (tokenAssigned != "") {
          logger.info("Tokens in current command box: ")
          logger.info(s"id: ${commandBox.getTokens.get(0).getId}")
          logger.info(s"amnt: ${commandBox.getTokens.get(0).getValue}")
        }
      }else{
        logger.info(s"Subpool ${commandBox.getSubpoolId} has default command contract.")
      }
      logger.info(s"Adding command box with id ${commandBox.getSubpoolId} to metadata box with id ${metadataBox.getSubpoolId}")
      boxToCommand = boxToCommand++Map((metadataBox, commandBox))
    }
    logger.info(s"Total of ${boxToCommand.size} command boxes in map")
    this
  }

  def result: Map[MetadataInputBox, CommandInputBox] = boxToCommand
  def completed: Map[MetadataInputBox, String] = _completed
  def failed: Map[MetadataInputBox, String] = _failed

  def removeFromMaps(metadataInputBox: MetadataInputBox): Unit = {
    boxToCommand = boxToCommand--List(metadataInputBox)
  }



}