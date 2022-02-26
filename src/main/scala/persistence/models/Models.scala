package persistence.models

import boxes.MetadataInputBox
import org.ergoplatform.appkit.{BlockchainContext, ErgoId, InputBox}

import scala.util.Try

object Models {
  case class BoxEntry(poolId: String, boxId: String, txId: String,
                      epoch: Long, status: String, smartPoolNft: String,
                      subpoolId: Int, blocks: Array[Long], holdingId: String,
                      holdingVal: Long, storedId: String, storedVal: Long){
    def idTag: String = this.subpoolId.toString

    def grabFromContext(ctx: BlockchainContext): Try[MetadataInputBox] = Try{new MetadataInputBox(ctx.getBoxesById(boxId).head, ErgoId.create(smartPoolNft))}
    def holdingFromContext(ctx: BlockchainContext): Try[InputBox] = Try{ctx.getBoxesById(holdingId).head}
    def storageFromContext(ctx: BlockchainContext): Try[InputBox] = Try{ctx.getBoxesById(storedId).head}
  }
  object BoxEntry{
    val EMPTY = "none"
    val DIST_TX = "distTx"
    val CMD_TX = "cmdTx"
    val EMPTY_LONG = 0L
    val EMPTY_INT = 0
  }
}
