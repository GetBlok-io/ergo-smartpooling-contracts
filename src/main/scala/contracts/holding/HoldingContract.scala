package contracts.holding

import app.AppParameters
import boxes.builders.{CommandOutputBuilder, HoldingOutputBuilder}
import org.ergoplatform.appkit._
import sigmastate.Values
import transactions.{CreateCommandTx, DistributionTx}
import transactions.models.CommandTxTemplate

abstract class HoldingContract(holdingContract: ErgoContract) extends ErgoContract {
  override def getConstants: Constants = holdingContract.getConstants

  override def getErgoScript: String = holdingContract.getErgoScript

  override def substConstant(name: String, value: Any): ErgoContract = holdingContract.substConstant(name, value)

  override def getErgoTree: Values.ErgoTree = holdingContract.getErgoTree

  def getAddress: Address = Address.fromErgoTree(this.getErgoTree, AppParameters.networkType)

  /**
   * Apply this holding contract's effects to an unbuilt command output. This is necessary to ensure
   * that holding box effects are reflected in the next command box to be used during the distribution
   * transaction.
   * @return Command output builder with desired holding effects on the metadata.
   */
  def applyToCommand(createCommandTx: CreateCommandTx): CommandOutputBuilder


  def generateInitialOutputs(ctx: BlockchainContext, distributionTx: DistributionTx, holdingBoxes: List[InputBox]): HoldingOutputBuilder
}


