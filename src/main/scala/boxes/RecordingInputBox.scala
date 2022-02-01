package boxes

import app.AppParameters
import boxes.models.InputTemplate
import org.ergoplatform.appkit._
import registers.VoteRegister
import sigmastate.Values
import sigmastate.serialization.ErgoTreeSerializer

import java.{lang, util}


class RecordingInputBox(inputBox: InputBox, rercordingNFTId: ErgoId) extends InputBox {
  val recordingNFT: ErgoId = rercordingNFTId
  val asInput: InputBox = inputBox
  val yesVotes: VoteRegister = new VoteRegister(inputBox.getRegisters.get(0).getValue.asInstanceOf[Long])
  val noVotes: VoteRegister = new VoteRegister(inputBox.getRegisters.get(1).getValue.asInstanceOf[Long])

  override def toString: String = {
    val serializer = new ErgoTreeSerializer()
    //val shareConsensusDeserialized = shareConsensus.cValue.map{(sc) => (serializer.deserializeErgoTree(sc._1), sc._2)}
    //val shareConsensusWithAddress = shareConsensusDeserialized.map{(sc) => (Address.fromErgoTree(sc._1, AppParameters.networkType), sc._2)}

    val asString = s"""
    Recording Box Info:
    - Id: ${this.getId.toString}
    - Value: ${this.getValue.toDouble / Parameters.OneErg.toDouble} ERG
    - Recording NFT: ${this.getRecordingId}
    - Yes Votes: ${this.yesVotes.nValue.toDouble / Parameters.OneErg.toDouble}
    - No Votes: ${this.noVotes.nValue.toDouble / Parameters.OneErg.toDouble}
    """
    asString
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case box: RecordingInputBox => this.getId.equals(box.getId)
      case _ => false
    }
  }

  def getRecordingId: ErgoId = this.recordingNFT

  def getValue: lang.Long = asInput.getValue

  def getTokens: util.List[ErgoToken] = asInput.getTokens

  def getRegisters: util.List[ErgoValue[_]] = asInput.getRegisters

  def getErgoTree: Values.ErgoTree = asInput.getErgoTree

  def withContextVars(variables: ContextVar*): InputBox = asInput.withContextVars(variables:_*)

  def toJson(prettyPrint: Boolean): String = asInput.toJson(prettyPrint)

  def toJson(prettyPrint: Boolean, formatJson: Boolean): String = asInput.toJson(prettyPrint, formatJson)

  override def getCreationHeight: Int = asInput.getCreationHeight

  override def getId: ErgoId = asInput.getId

}
