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
    def boxStatus: BoxStatus = BoxStatus(status)
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

  case class BoxTag(longTag: Long, strTag: String){

    override def equals(obj: Any): Boolean = {
      obj match {
        case asTag: BoxTag =>
          asTag.longTag == this.longTag
        case asLong: Long =>
          asLong == this.longTag
        case asString: String =>
          asString == this.strTag
        case _ =>
          false
      }
    }
  }
  object BoxTag {
    val NGO_1: BoxTag = BoxTag(100L, "NGO_1")
    val NGO_2: BoxTag = BoxTag(200L, "NGO_2")
    val NGO_3: BoxTag = BoxTag(300L, "NGO_3")

    val NGO_TAGS: Array[BoxTag] = Array(NGO_1, NGO_2, NGO_3)
  }

  case class BoxStatus(status: String){
    override def toString: String = this.status

    override def equals(obj: Any): Boolean = {
      obj match {
        case asString: String =>
          asString == this.status
        case asStatus: BoxStatus =>
          asStatus.status == this.status
        case _ =>
          false
        }
      }

    }

  object BoxStatus {
    final val FAILURE   = BoxStatus("failure")
    final val SUCCESS   = BoxStatus("success")
    final val CONFIRMED = BoxStatus("confirmed")
    final val INITIATED = BoxStatus("initiated")
    final val RESERVED  = BoxStatus("reserved")
  }


}
