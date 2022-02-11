package contracts.voting

import app.AppParameters
import boxes.{RecordingInputBox, RecordingOutBox}
import org.ergoplatform.appkit.{Address, BlockchainContext, Constants, ConstantsBuilder, ErgoContract, ErgoId, ErgoToken, InputBox}
import registers.{BytesColl, VoteRegister}
import scorex.crypto.hash.Blake2b256
import sigmastate.Values

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

class RecordingContract(ergoContract: ErgoContract) extends ErgoContract {
  override def getConstants: Constants = ergoContract.getConstants

  override def getErgoScript: String = ergoContract.getErgoScript

  override def substConstant(name: String, value: Any): ErgoContract = ergoContract.substConstant(name, value)

  override def getErgoTree: Values.ErgoTree = ergoContract.getErgoTree

  def getAddress: Address = Address.fromErgoTree(this.getErgoTree, AppParameters.networkType)

  def asErgoContract: ErgoContract = ergoContract
}

object RecordingContract {
  // TODO: Add vote ending height
  // TODO: Add second vote token
  val script: String =
  s"""
    {
       val inputsValid = INPUTS.forall{
        (box: Box) =>
          if(box.propositionBytes == SELF.propositionBytes || box.propositionBytes == const_voteYesBytes || box.propositionBytes == const_voteNoBytes){
            true
          }else{
            false
          }
       }

       val heightValid = HEIGHT < const_voteEndHeight

       val registersUpdated =
        if(inputsValid){
          val newYesVotes = INPUTS.fold(0L, {
            (accum: Long, box: Box) =>
              if(box.propositionBytes == const_voteYesBytes){
                if(box.tokens.size > 0){
                  if(box.tokens(0)._1 == const_voteTokenId || box.tokens(0)._1 == const_otrTokenId){
                    accum + box.tokens(0)._2
                  }else{
                    accum
                  }
                }else{
                  accum
                }
              }else{
                accum
              }
          })

          val newNoVotes = INPUTS.fold(0L, {
            (accum: Long, box: Box) =>
              if(box.propositionBytes == const_voteNoBytes){
                if(box.tokens.size > 0){
                  if(box.tokens(0)._1 == const_voteTokenId || box.tokens(0)._1 == const_otrTokenId){
                    if(box.tokens.size > 1){
                      if(box.tokens(1)._1 == const_voteTokenId || box.tokens(1)._1 == const_otrTokenId){
                        accum + box.tokens(0)._2 + box.tokens(1)._2
                      }else{
                        accum + box.tokens(0)._2
                      }
                    }else{
                      accum + box.tokens(0)._2
                    }
                  }else{
                    accum
                  }
                }else{
                  accum
                }
              }else{
                accum
              }
          })

          val currentYesVotes = SELF.R4[Long].get
          val currentNoVotes = SELF.R5[Long].get

          val yesVotesUpdated = OUTPUTS(0).R4[Long].get == currentYesVotes + newYesVotes
          val noVotesUpdated = OUTPUTS(0).R5[Long].get == currentNoVotes + newNoVotes
          val recordingOutputted = OUTPUTS(0).propositionBytes == SELF.propositionBytes && OUTPUTS(0).value == SELF.value
          val outputsToCheck = OUTPUTS.slice(1, OUTPUTS.size)
          val tokensBurned = outputsToCheck.forall{(box: Box) =>
            box.tokens.size == 0
          }

          val nftPreserved = OUTPUTS(0).tokens.size == 1 && OUTPUTS(0).tokens(0)._1 == SELF.tokens(0)._1

          recordingOutputted && yesVotesUpdated && noVotesUpdated && nftPreserved && tokensBurned
        }else{
          false
        }

       sigmaProp(registersUpdated) && sigmaProp(heightValid)
    }
      """.stripMargin

  /**
   * To simplify voting process, we only allow one P2PK address to reclaim lost funds above minimum amount required.
   * @param ctx
   * @param voteTokenId
   * @param voteYes
   * @param changeAddress
   * @return
   */
  def generateContract(ctx: BlockchainContext, voteTokenId: ErgoId, voteYes: Address, voteNo: Address, voteEndHeight: Int): RecordingContract = {
    val voteYesBytes = BytesColl.convert(voteYes.getErgoAddress.script.bytes)
    val voteNoBytes = BytesColl.convert(voteNo.getErgoAddress.script.bytes)
    val voteTokenBytes = BytesColl.convert(voteTokenId.getBytes)
    val otherTokenBytes = BytesColl.convert(ErgoId.create("a243398b94d8638f1d7e81e411851adec8c2735ca813c3e3d4bc8443fdc434e0").getBytes)

    val constants = ConstantsBuilder.create()
      .item("const_voteTokenId", voteTokenBytes.nValue)
      .item("const_otrTokenId", otherTokenBytes.nValue)
      .item("const_voteYesBytes", voteYesBytes.nValue)
      .item("const_voteNoBytes", voteNoBytes.nValue)
      .item("const_voteEndHeight", voteEndHeight)
      .build()
    val contract: ErgoContract = ctx.compileContract(constants, script)
    new RecordingContract(contract)
  }

  def buildNewRecordingBox(ctx: BlockchainContext, recordingId: ErgoId, recordingContract: RecordingContract, recordingValue: Long): RecordingOutBox = {
    val outB = ctx.newTxBuilder().outBoxBuilder()
    val recordingToken = new ErgoToken(recordingId, 1L)
    val zeroedVotes = new VoteRegister(0L)
    val asOutBox = outB
      .contract(recordingContract.asErgoContract)
      .value(recordingValue)
      .registers(zeroedVotes.eValue, zeroedVotes.eValue)
      .tokens(recordingToken)
      .build()
    new RecordingOutBox(asOutBox, zeroedVotes, zeroedVotes, recordingId)
  }

  def buildNextRecordingBox(ctx: BlockchainContext, recordingBox: RecordingInputBox, recordingContract: RecordingContract,
                            yesBoxes: Array[InputBox], noBoxes:Array[InputBox], voteTokenId: ErgoId): RecordingOutBox = {
    val outB = ctx.newTxBuilder().outBoxBuilder()
    val recordingToken = new ErgoToken(recordingBox.recordingNFT, 1L)
    val otherTokenId = ErgoId.create("a243398b94d8638f1d7e81e411851adec8c2735ca813c3e3d4bc8443fdc434e0")
    val newYesVotes = yesBoxes
      .filter(ib => ib.getTokens.size() > 0)
      .filter(ib => ib.getTokens.get(0).getId == voteTokenId)
      .map(ib => ib.getTokens.get(0).getValue)
      .sum

    val newNoVotes = noBoxes
      .filter(ib => ib.getTokens.size() > 0)
      .filter(ib => ib.getTokens.asScala.exists(t => t.getId == voteTokenId))
      .map(ib => ib.getTokens.asScala.find(t => t.getId == voteTokenId).get.getValue)
      .sum
    val otrYesVotes = yesBoxes
      .filter(ib => ib.getTokens.size() > 0)
      .filter(ib => ib.getTokens.get(0).getId == otherTokenId)
      .map(ib => ib.getTokens.get(0).getValue)
      .sum

    val otrNoVotes = noBoxes
      .filter(ib => ib.getTokens.size() > 0)
      .filter(ib => ib.getTokens.asScala.exists(t => t.getId == otherTokenId))
      .map(ib => ib.getTokens.asScala.find(t => t.getId == otherTokenId).get.getValue)
      .sum


    val updatedYesVotes = recordingBox.yesVotes + newYesVotes + otrYesVotes
    val updatedNoVotes = recordingBox.noVotes + newNoVotes + otrNoVotes


    val asOutBox = outB
      .contract(recordingContract.asErgoContract)
      .value(recordingBox.getValue)
      .registers(updatedYesVotes.eValue, updatedNoVotes.eValue)
      .tokens(recordingToken)
      .build()
    new RecordingOutBox(asOutBox, updatedYesVotes, updatedNoVotes, recordingBox.recordingNFT)
  }

}
