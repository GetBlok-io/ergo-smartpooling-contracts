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
}
