package config;

import org.ergoplatform.appkit.ErgoId;

public class SmartPoolParameters {
    private String smartPoolId;
    private String consensusPath;
    private String holdingAddress;
    private String metadataAddress;
    private String[] poolOperators;
    private String metadataId;
    private String[] holdingIds;
    private String commandId;

    private double minimumPayout;

    public SmartPoolParameters(String spId, String consPath, String holdingAddr, String metadataAddr, String[] poolOps, double minPay){
        smartPoolId = spId;
        consensusPath = consPath;
        holdingAddress = holdingAddr;
        metadataAddress = metadataAddr;
        poolOperators = poolOps;
        minimumPayout = minPay;
        metadataId = null;
        holdingIds = null;
        commandId = null;
    }

    public String getSmartPoolId() {
        return smartPoolId;
    }

    public void setSmartPoolId(String smartPoolId) {
        this.smartPoolId = smartPoolId;
    }

    public String getHoldingAddress() {
        return holdingAddress;
    }

    public void setHoldingAddress(String holdingAddress) {
        this.holdingAddress = holdingAddress;
    }

    public String getMetadataAddress() {
        return metadataAddress;
    }

    public void setMetadataAddress(String metadataAddress) {
        this.metadataAddress = metadataAddress;
    }

    public String[] getPoolOperators() {
        return poolOperators;
    }

    public void setPoolOperators(String[] poolOperators) {
        this.poolOperators = poolOperators;
    }

    public double getMinimumPayout() {
        return minimumPayout;
    }

    public void setMinimumPayout(double minimumPayout) {
        this.minimumPayout = minimumPayout;
    }

    public String getConsensusPath() {
        return consensusPath;
    }

    public void setConsensusPath(String consensusPath) {
        this.consensusPath = consensusPath;
    }

    public String getMetadataId() {
        return metadataId;
    }

    public void setMetadataId(String metadataId) {
        this.metadataId = metadataId;
    }

    public String[] getHoldingIds() {
        return holdingIds;
    }

    public void setHoldingIds(String[] holdingIds) {
        this.holdingIds = holdingIds;
    }

    public String getCommandId() {
        return commandId;
    }

    public void setCommandId(String commandId) {
        this.commandId = commandId;
    }
}
