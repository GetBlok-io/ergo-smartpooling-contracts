package groups

/**
 * Group of transaction chains to build and execute
 */
abstract class TransactionGroup[T] {

  def buildGroup: TransactionGroup[T]

  def executeGroup: TransactionGroup[T]

  def completed: T

  def failed: T


}
