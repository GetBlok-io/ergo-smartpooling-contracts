package config;

import org.ergoplatform.appkit.ErgoId;

public class SmartPoolParameters {
    private String smartPoolId;
    private String holdingAddress;
    private String metadataAddress;
    private String[] poolOperators;
    private String metadataId;
    private String[] holdingIds;
    private String commandId;


    private long metadataValue;



    private long initialTxFee;

    public SmartPoolParameters(String spId, String holdingAddr, String metadataAddr, String[] poolOps){
        smartPoolId = spId;
        holdingAddress = holdingAddr;
        metadataAddress = metadataAddr;
        poolOperators = poolOps;
        metadataId = null;
        holdingIds = null;
        commandId = null;
        metadataValue = 0;

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


    public long getMetadataValue() {
        return metadataValue;
    }

    public void setMetadataValue(long metadataValue) {
        this.metadataValue = metadataValue;
    }

    public long getInitialTxFee() {
        return initialTxFee;
    }

    public void setInitialTxFee(long initialTxFee) {
        this.initialTxFee = initialTxFee;
    }


    public SmartPoolParameters copy(){
        SmartPoolParameters sp = new SmartPoolParameters(this.smartPoolId, this.holdingAddress, this.metadataAddress, this.poolOperators);
        sp.setMetadataId(this.metadataId);
        sp.setCommandId(this.commandId);
        sp.setHoldingIds(this.holdingIds);
        sp.setMetadataValue(this.initialTxFee);
        sp.setMetadataValue(this.metadataValue);
        return sp;
    }
}
