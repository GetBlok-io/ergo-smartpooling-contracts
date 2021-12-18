package boxes

import org.ergoplatform.appkit._

/**
 * Wrapper class that wraps output boxes as command boxes
 *
 * @param outBox Out box to wrap as command box
 */
class HoldingOutBox(outBox: OutBox)
                    extends OutBox{
  val asOutBox: OutBox = outBox
  override def toString: String = {
    val asString = s"""
    Holding Output Info:
    - Value: ${this.getValue.toDouble / Parameters.OneErg.toDouble} ERG
    """
    asString
  }

  override def getValue: Long = asOutBox.getValue

  override def getCreationHeight: Int = asOutBox.getCreationHeight

  override def token(id: ErgoId): ErgoToken = asOutBox.token(id)

  override def convertToInputWith(txId: String, outputIndex: Short): InputBox = asOutBox.convertToInputWith(txId, outputIndex)
}
