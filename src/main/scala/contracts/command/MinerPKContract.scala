package contracts.command

import app.AppParameters
import boxes.builders.{CommandOutputBuilder, HoldingOutputBuilder}
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import special.sigma.GroupElement

/**
 * Contract that is automatically sent when block is mined by miner(or pool) with public key minerPK in
 * the block chain context's preheaders
 * @param ergoContract
 */
class MinerPKContract(ergoContract: ErgoContract) extends CommandContract(ergoContract) {
  val asErgoContract: ErgoContract = ergoContract
  override def getAddress: Address = Address.fromErgoTree(ergoContract.getErgoTree, AppParameters.networkType)

  /**
   * A simple P2PK Command Contract performs no specifically coded changes to the command box. Therefore,
   * we may simply return the command output builder that was passed in.
   */
  def applyToCommand(commandOutputBuilder: CommandOutputBuilder): CommandOutputBuilder = commandOutputBuilder

  /**
   * A simple P2PK Command Contract performs no specifically coded changes to the holding outputs. Therefore,
   * we may simply return the holding output builder that was passed in.
   */
  override def applyToHolding(holdingOutputBuilder: HoldingOutputBuilder): HoldingOutputBuilder = holdingOutputBuilder
}

object MinerPKContract {
  private val script =
    """{
       | val commandProp = CONTEXT.preHeader.minerPk == const_GE
       | sigmaProp(commandProp) && const_PK
       | }""".stripMargin

  def generateContract(ctx: BlockchainContext, minerAddress: Address, nodeGE: GroupElement): MinerPKContract = {
    val constantsBuilder = ConstantsBuilder.create()

    val compiledContract = ctx.compileContract(constantsBuilder
      .item("const_PK", minerAddress.getPublicKey)
      .item("const_GE", nodeGE)
      .build(), script)
    new MinerPKContract(compiledContract)
  }
}


