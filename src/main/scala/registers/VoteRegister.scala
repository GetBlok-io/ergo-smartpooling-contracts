package registers

import org.ergoplatform.appkit.{ErgoType, ErgoValue}
import special.collection.Coll

class VoteRegister(normalValue: Long) {

  val nValue: Long = normalValue
  val eValue: ErgoValue[Long] = ErgoValue.of(normalValue)

  def +(add: Long): VoteRegister = {
    new VoteRegister(nValue + add)
  }

}


