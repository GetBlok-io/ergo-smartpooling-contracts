package config.params;

import org.ergoplatform.appkit.Parameters;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class SmartPoolParameters {
    private String smartPoolId;

    private String poolId;
    private String[] poolOperators;
    private long initialTxFee;
    private String paymentType;
    private long PPLNS_NUM_SHARES;
    private Map<String, Double> fees;


    private MetadataConfig metaConf;
    private CommandConfig commandConf;
    private HoldingConfig holdingConf;
    private VotingConfig votingConf;


    public SmartPoolParameters(String spId, String pId, String[] poolOps){
        smartPoolId = spId;
        poolId = pId;
        poolOperators = poolOps;
        initialTxFee = Parameters.MinFee;
        paymentType = "PPLNS";


        PPLNS_NUM_SHARES = 50000;
        metaConf = new MetadataConfig("", "", "default", 0L);
        commandConf = new CommandConfig("", "", "default", 0L);
        holdingConf = new HoldingConfig("", "default", 0L);
        votingConf = new VotingConfig();
        fees = new HashMap<String, Double>();
        fees.put("address", 1.0);
    }

    public String getSmartPoolId() {
        return smartPoolId;
    }

    public void setSmartPoolId(String smartPoolId) {
        this.smartPoolId = smartPoolId;
    }



    public String[] getPoolOperators() {
        return poolOperators;
    }

    public void setPoolOperators(String[] poolOperators) {
        this.poolOperators = poolOperators;
    }


    public long getInitialTxFee() {
        return initialTxFee;
    }

    public void setInitialTxFee(long initialTxFee) {
        this.initialTxFee = initialTxFee;
    }


    public String getPoolId() {
        return poolId;
    }

    public void setPoolId(String poolId) {
        this.poolId = poolId;
    }



    public SmartPoolParameters copy(){
        SmartPoolParameters sp = new SmartPoolParameters(this.smartPoolId, this.poolId, this.poolOperators);
        sp.setInitialTxFee(this.initialTxFee);
        sp.setPPLNS_NUM_SHARES(this.PPLNS_NUM_SHARES);
        sp.setPaymentType(this.paymentType);
        sp.setMetaConf(this.metaConf);
        sp.setCommandConf(this.commandConf);
        sp.setHoldingConf(this.holdingConf);
        sp.setVotingConf(this.votingConf);
        sp.setFees(this.fees);
        return sp;
    }

    public long getPPLNS_NUM_SHARES() {
        return PPLNS_NUM_SHARES;
    }

    public void setPPLNS_NUM_SHARES(long PPLNS_NUM_SHARES) {
        this.PPLNS_NUM_SHARES = PPLNS_NUM_SHARES;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }

    public HoldingConfig getHoldingConf() {
        return holdingConf;
    }

    public void setHoldingConf(HoldingConfig holdingConf) {
        this.holdingConf = holdingConf;
    }

    public CommandConfig getCommandConf() {
        return commandConf;
    }

    public void setCommandConf(CommandConfig commandConf) {
        this.commandConf = commandConf;
    }

    public MetadataConfig getMetaConf() {
        return metaConf;
    }

    public void setMetaConf(MetadataConfig metaConf) {
        this.metaConf = metaConf;
    }

    public VotingConfig getVotingConf() {
        return votingConf;
    }

    public void setVotingConf(VotingConfig votingConf) {
        this.votingConf = votingConf;
    }


    public Map<String, Double> getFees() {
        return fees;
    }

    public void setFees(Map<String, Double> fees) {
        this.fees = fees;
    }

}
