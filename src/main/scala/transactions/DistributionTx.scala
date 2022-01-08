package transactions

import boxes.{BoxHelpers, CommandInputBox, CommandOutBox, MetadataInputBox, MetadataOutBox}
import boxes.builders.{HoldingOutputBuilder, MetadataOutputBuilder}
import contracts.MetadataContract
import contracts.command.{CommandContract, PKContract}
import contracts.holding.{HoldingContract, SimpleHoldingContract}
import logging.LoggingHandler
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.{BlockchainContext, ErgoToken, InputBox, NetworkType, OutBox, OutBoxBuilder, Parameters, PreHeader, SignedTransaction, UnsignedTransaction, UnsignedTransactionBuilder}
import org.slf4j.{Logger, LoggerFactory}
import spire.compat.numeric
import transactions.models.{MetadataTxTemplate, TransactionTemplate}

import java.util
import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, seqAsJavaListConverter}

class DistributionTx(unsignedTxBuilder: UnsignedTransactionBuilder) extends MetadataTxTemplate(unsignedTxBuilder) {
  val logger: Logger = LoggerFactory.getLogger(LoggingHandler.loggers.LOG_DIST_TX)
  private var hOB: HoldingOutputBuilder = _
  private[this] var _mainHoldingContract: HoldingContract = _
  private[this] var _otherCommandContracts: List[CommandContract] = List[CommandContract]()
  private[this] var _holdingInputs: List[InputBox] = List[InputBox]()


  private def otherCommandContracts: List[CommandContract] = _otherCommandContracts

  private def otherCommandContracts(contracts: List[CommandContract]): Unit = {
    _otherCommandContracts = contracts

  }

  def holdingContract: HoldingContract = _mainHoldingContract

  def holdingContract(holdingContract: HoldingContract): DistributionTx = {
    _mainHoldingContract = holdingContract
    this
  }

  def holdingInputs: List[InputBox] = _holdingInputs

  def holdingInputs(holdingBoxes: List[InputBox]): DistributionTx = {
    _holdingInputs = holdingBoxes
    this
  }

  def withCommandContracts(commandContracts: CommandContract*): DistributionTx = {
    otherCommandContracts(commandContracts.toList)
    this
  }

  def metadataInput(value: MetadataInputBox): DistributionTx = {
    this._metadataInputBox = value
    this
  }

  def commandInput(value: CommandInputBox): DistributionTx = {
    this._commandInputBox = value
    this
  }

  def metadataOutput(value: MetadataOutBox): DistributionTx = {
    this._metadataOutBox = value
    this
  }

  override def buildMetadataTx(): UnsignedTransaction = {
    val commandContract = commandInputBox.contract
    val holdingAddress = holdingContract.getAddress


    logger.info(s"Total Holding Box Value: ${BoxHelpers.sumBoxes(holdingInputs)}")

    val holdingBoxes = holdingInputs

    val metadataContract = metadataInputBox.getContract

    val initBoxes = List(metadataInputBox.asInput, commandInputBox.asInput)
    val inputBoxes = initBoxes++holdingBoxes

    metadataOutput(MetadataContract.buildFromCommandBox(mOB, commandInputBox, metadataContract, metadataInputBox.getValue, metadataInputBox.getSmartPoolId))

    hOB = holdingContract
      .generateInitialOutputs(ctx, this, holdingBoxes)
      .applyCommandContract(commandContract)

    otherCommandContracts.foreach(c => hOB.applyCommandContract(c))

    val holdingOutputs = hOB.build()

    val txFee = commandInputBox.getValue + (commandInputBox.getShareConsensus.nValue.size * Parameters.MinFee)
    val outputBoxes = List(metadataOutBox.asOutBox)++(holdingOutputs.map(h => h.asOutBox))
    logger.info("Distribution Tx built")
    logger.info("Total Input Value: "+ (inputBoxes.map(x => x.getValue.toLong).sum))
    logger.info("Total Output Value: "+ outputBoxes.map(x => x.getValue.toLong).sum)

      this.asUnsignedTxB
      .boxesToSpend(inputBoxes.asJava)
      .outputs(outputBoxes:_*)
      .fee(txFee)
      .sendChangeTo(holdingAddress.getErgoAddress)
      .build()

  }


}
