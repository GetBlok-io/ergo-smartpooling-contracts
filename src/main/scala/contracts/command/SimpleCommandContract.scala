package contracts.command

import boxes.builders.{CommandOutputBuilder, HoldingOutputBuilder}
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract

/**
 * Default command contract that ensures payments are stored
 * @param p2pkAddress P2PK used to extract public key
 */
class SimpleCommandContract(p2pkAddress: Address) extends CommandContract(new ErgoTreeContract(p2pkAddress.getErgoAddress.script)) {

  override def getAddress: Address = p2pkAddress
  val script: String =
    """
      |{
      | val validInputs = INPUTS.size >= 2 && SELF.id == INPUTS(1).id
      |
      | val metadataExists =
      |   if(validInputs){
      |     INPUTS(0).propositionBytes == const_metadataPropBytes
      |   }else{
      |     false
      |   }
      | val owedPayoutsStored =
      |   if(metadataExists){
      |
      |    val lastConsensus = INPUTS(0).R4[Coll[(Coll[Byte], Coll[Long])]].get // old consensus grabbed from metadata
      |    val currentConsensus = INPUTS(1).R4[Coll[(Coll[Byte], Coll[Long])]].get // New consensus grabbed from current command
      |
      |    val lastConsensusPropBytes = lastConsensus.map{
      |      (consVal: (Coll[Byte], Coll[Long])) =>
      |        consVal._1
      |    }
      |    val lastConsensusValues = lastConsensus.map{
      |      (consVal: (Coll[Byte], Coll[Long])) =>
      |        consVal._2
      |    }
      |
      |    // Returns some value that is a percentage of the total rewards after the fees.
      |    // The percentage used is the proportion of the share number passed in over the total number of shares
      |    def getValueFromShare(shareNum: Long) = {
      |      val newBoxValue = (((totalValAfterFees) * (shareNum)) / (totalShares)).toLong
      |      newBoxValue
      |    }
      |
      |   }else{
      |     false
      |   }
      |
      |}
      |""".stripMargin
  /**
   * To apply this command contract to the outputted command box, we must ensure that payouts are stored.
   **/
  def applyToCommand(commandOutputBuilder: CommandOutputBuilder): CommandOutputBuilder = commandOutputBuilder

  /**
   * This contract performs no specific changes to the holding outputs.
   */
  override def applyToHolding(holdingOutputBuilder: HoldingOutputBuilder): HoldingOutputBuilder = holdingOutputBuilder
}


