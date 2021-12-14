package registers

class MetadataRegisters(consensus: ShareConsensus, members: MemberList, fees: PoolFees, info: PoolInfo, ops: PoolOperators) {
  private[this] var _shareConsensus: ShareConsensus = consensus

  def shareConsensus: ShareConsensus = _shareConsensus

  def shareConsensus_=(value: ShareConsensus): Unit = {
    _shareConsensus = value
  }

  private[this] var _memberList: MemberList = members

  def memberList: MemberList = _memberList

  def memberList_=(value: MemberList): Unit = {
    _memberList = value
  }

  private[this] var _poolFees: PoolFees = fees

  def poolFees: PoolFees = _poolFees

  def poolFees_=(value: PoolFees): Unit = {
    _poolFees = value
  }

  private[this] var _poolInfo: PoolInfo = info

  def poolInfo: PoolInfo = _poolInfo

  def poolInfo_=(value: PoolInfo): Unit = {
    _poolInfo = value
  }

  private[this] var _poolOps: PoolOperators = ops

  def poolOps: PoolOperators = _poolOps

  def poolOps_=(value: PoolOperators): Unit = {
    _poolOps = value
  }

  def copy: MetadataRegisters = {
    new MetadataRegisters(this.shareConsensus, this.memberList, this.poolFees, this.poolInfo, this.poolOps)
  }
}
