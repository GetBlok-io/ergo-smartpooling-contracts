package boxes.builders

import boxes.{CommandOutBox, HoldingOutBox}
import contracts.command.CommandContract
import contracts.holding.HoldingContract
import org.ergoplatform.appkit._
import registers._

/**
 * Outbox Builder wrapper that builds holding outputs. Each long value represents the value of the holding output,
 * while the boolean indicates whether the output is for a miner, or is a fee or change box from the tx.
 */
class HoldingOutputBuilder(builders: Map[OutBoxBuilder, (Long, Boolean)]) {

  private[this] var _outBoxBuilders: Map[OutBoxBuilder, (Long, Boolean)] = builders

  def getOutBoxBuilders: Map[OutBoxBuilder, (Long, Boolean)] = _outBoxBuilders

  def setOutBoxBuilders(newBuilders: Map[OutBoxBuilder, (Long, Boolean)]): HoldingOutputBuilder ={
    _outBoxBuilders = newBuilders
    this
  }

  def applyCommandContract(commandContract: CommandContract): HoldingOutputBuilder = {
   commandContract.applyToHolding(this)
   this
 }


  def build(): List[HoldingOutBox] = {
   val holdingOutputsBuilt = this._outBoxBuilders.map(oBB => new HoldingOutBox(oBB._1.build())).toList
    holdingOutputsBuilt
  }

}
