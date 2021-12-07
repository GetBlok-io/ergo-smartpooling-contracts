import org.ergoplatform.{ErgoAddressEncoder, Pay2SHAddress}
import org.ergoplatform.appkit.{Address, ErgoContract, ErgoType, ErgoValue, NetworkType}
import scalan.RType
import sigmastate.basics.DLogProtocol.ProveDlog
import sigmastate.eval.CostingSigmaDslBuilder.proveDlog
import sigmastate.eval.SigmaDsl
import special.collection.Coll
import special.sigma.{GroupElement, SigmaProp}

package object contracts {

  def genSigProp(addr: Address): SigmaProp = {
    proveDlog(addr.getPublicKeyGE)
  }

  def genDlog(addr: Address): ProveDlog = {
    addr.getPublicKey
  }


  def generateContractAddress(contract: ErgoContract, networkType: NetworkType): Address = {
    Address.fromErgoTree(contract.getErgoTree, networkType)
  }
  /**
   * Represents custom ErgoTypes to be used in SmartPooling contracts
   */
  object SpType {
    final val PROPBYTES_TYPE = ErgoType.collType(ErgoType.byteType())
    final val STRING_TYPE = ErgoType.collType(ErgoType.byteType())

    final val CONSENSUS_VAL_TYPE = ErgoType.pairType[Coll[Byte], Long](PROPBYTES_TYPE, ErgoType.longType())
    final val CONSENSUS_TYPE = ErgoType.collType(CONSENSUS_VAL_TYPE)

    final val MEMBER_TYPE = ErgoType.pairType[Coll[Byte], Coll[Byte]](PROPBYTES_TYPE, STRING_TYPE)
    final val MEMBER_LIST_TYPE = ErgoType.collType(MEMBER_TYPE)

    final val POOL_FEE_TYPE = ErgoType.pairType[Coll[Byte], Int](PROPBYTES_TYPE, ErgoType.integerType())
    final val POOL_FEES_LIST_TYPE = ErgoType.collType(POOL_FEE_TYPE)

    final val POOL_INFO_TYPE = ErgoType.collType(ErgoType.longType())

    final val POOL_OPERATORS_TYPE = ErgoType.collType(MEMBER_TYPE)

  }
  // SpType has three values, a normal value (used in offchain code), a conversion value(Used to convert from more traditional types)
  // and the ergo type value is typically used to reference it.
  // For types with parameters(like coll) we use the type parameter as the ergo type since this is how it is referenced in most methods
  case class SpValue[A](nValue: A, cValue: _, eValue: ErgoType[_])

  final val PROPBYTES_TYPE = new SpType[Coll[Byte]]()

  final val PROPBYTES
  trait SpContract {
    val contractScript: String
    def buildContract: ErgoContract

  }





}
