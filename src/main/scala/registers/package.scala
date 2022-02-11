import org.ergoplatform.appkit.ErgoType
import sigmastate.eval.CostingSigmaDslBuilder.Colls
import special.collection.Coll

package object registers {
  /**
   * This registers holds classes used to represent various types and registers referenced throughout the rest
   * of the project
   */
  /**
   * Returns new collection of type Coll[T] where T must have some corresponding ErgoType ErgoType[T]
   */
  def newColl[T](list: List[T], ergoType: ErgoType[T]): Coll[T] = {
    special.collection.Builder.DefaultCollBuilder.fromItems(list:_*)(ergoType.getRType)
  }
  def newColl[T](arr: Array[T], ergoType: ErgoType[T]): Coll[T] = {
    special.collection.Builder.DefaultCollBuilder.fromArray(arr)(ergoType.getRType)
  }



}
