package node;

import org.ergoplatform.restapi.client.ScanId;

public class Scan {

    public ScanId scanId;
    public String scanName;
    public String walletInteraction;
    public boolean removeOffchain;
    public TrackingRule trackingRule;

    public Scan(ScanId id, String name, String interaction, TrackingRule rule, boolean offchain){
        scanId = id;
        scanName = name;
        walletInteraction = interaction;
        trackingRule = rule;
        removeOffchain = offchain;
    }
    public Scan(String name, String interaction, TrackingRule rule, boolean offchain){
        scanName = name;
        walletInteraction = interaction;
        trackingRule = rule;
        removeOffchain = offchain;
    }

    public Scan(String name, TrackingRule rule){
        scanName = name;
        walletInteraction = "off";
        trackingRule = rule;
        removeOffchain = true;
    }

    public String getScanName() {
        return scanName;
    }

    public void setScanName(String scanName) {
        this.scanName = scanName;
    }

    public String getWalletInteraction() {
        return walletInteraction;
    }

    public void setWalletInteraction(String walletInteraction) {
        this.walletInteraction = walletInteraction;
    }

    public TrackingRule getTrackingRule() {
        return trackingRule;
    }

    public void setTrackingRule(TrackingRule trackingRule) {
        this.trackingRule = trackingRule;
    }

    public boolean isRemoveOffchain() {
        return removeOffchain;
    }

    public void setRemoveOffchain(boolean removeOffchain) {
        this.removeOffchain = removeOffchain;
    }

    public ScanId getScanId() {
        return scanId;
    }

    public void setScanId(ScanId scanId) {
        this.scanId = scanId;
    }

    @Override
    public String toString() {
        return "Scan{" +
                "scanName='" + scanName + '\'' +
                " walletInteraction='" + walletInteraction + '\'' +
                " trackingRule=" + trackingRule +
                " removeOffchain=" + removeOffchain +
                '}';
    }

}
