package boxes.builders

import boxes.{CommandOutBox, HoldingOutBox}
import contracts.command.CommandContract
import contracts.holding.HoldingContract
import org.ergoplatform.appkit._
import registers._

/**
 * Outbox Builder wrapper that treats outboxes like metadata/command boxes
 */
class HoldingOutputBuilder(builders: List[OutBoxBuilder]) {

  private[this] var _outBoxBuilders: List[OutBoxBuilder] = builders

  def applyCommandContract(commandContract: CommandContract): HoldingOutputBuilder = {
   commandContract.applyToHolding(this)
   this
 }


  def build(): List[HoldingOutBox] = {
   val holdingOutputsBuilt = this._outBoxBuilders.map(oBB => new HoldingOutBox(oBB.build()))
    holdingOutputsBuilt
  }

}
