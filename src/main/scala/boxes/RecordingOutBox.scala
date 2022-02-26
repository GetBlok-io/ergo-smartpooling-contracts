package boxes

import boxes.models.OutputTemplate
import org.ergoplatform.appkit._
import registers._
import sigmastate.Values
import sigmastate.serialization.ErgoTreeSerializer
import special.collection.Coll

import java.util

/**
 * Wrapper class that wraps output boxes as metadata boxes / command boxes
 *
 * @param outBox Out box to wrap as metadata box / command box
 */
class RecordingOutBox(outBox: OutBox, yesVoteReg: VoteRegister, noVoteReg: VoteRegister, recordingNFT: ErgoId)
                     extends OutBox {
  val asOutBox: OutBox = outBox
  val yesVotes: VoteRegister = yesVoteReg
  val noVotes: VoteRegister = noVoteReg
  def getRecordingId: ErgoId = this.recordingNFT

  override def toString: String = {
    val asString = s"""
    Recording Box Info:
    - Value: ${this.getValue.toDouble / Parameters.OneErg.toDouble} ERG
    - Recording NFT: ${this.getRecordingId}
    - Yes Votes: ${this.yesVotes.nValue.toDouble / Parameters.OneErg.toDouble}
    - No Votes: ${this.noVotes.nValue.toDouble / Parameters.OneErg.toDouble}
    """
    asString
  }

  override def getValue: Long = asOutBox.getValue

  override def convertToInputWith(txId: String, boxIdx: Short): InputBox = asOutBox.convertToInputWith(txId, boxIdx)

  override def getCreationHeight: Int = asOutBox.getCreationHeight

  override def getTokens: java.util.List[ErgoToken] = asOutBox.getTokens

  override def getRegisters: util.List[ErgoValue[_]] = asOutBox.getRegisters

  override def getErgoTree: Values.ErgoTree = asOutBox.getErgoTree

  override def getBytesWithNoRef: Array[Byte] = asOutBox.getBytesWithNoRef
}
