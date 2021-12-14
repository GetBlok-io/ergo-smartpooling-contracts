import org.ergoplatform.appkit.NetworkType

package object app {

  object ExitCodes {
    final val SUCCESS = 0

    final val CONFIG_NOT_FOUND = 100
    final val INVALID_CONFIG = 101
    final val NO_SMARTPOOL_ID_IN_CONFIG = 102
    final val NO_CONSENSUS_PATH_IN_CONFIG = 103
    final val NO_WALLET = 104
    final val INVALID_NODE = 105

  }

  object AppParameters {
    var networkType: NetworkType = NetworkType.TESTNET
  }
}
