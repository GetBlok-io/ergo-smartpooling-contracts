package node;

import java.util.Arrays;

public class TrackingRule {
    public String predicate;
    public String assetId;
    public String value;
    public TrackingRule[] args;

    public TrackingRule(String pred, String arg){
        predicate = pred;
        value = arg;
    }
    public TrackingRule(String pred, TrackingRule[] rules){
        predicate = pred;
        args = rules;
    }

    public String getPredicate() {
        return predicate;
    }

    public void setPredicate(String predicate) {
        this.predicate = predicate;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public TrackingRule[] getArgs() {
        return args;
    }

    public void setArgs(TrackingRule[] args) {
        this.args = args;
    }

    @Override
    public String toString() {
        return "TrackingRule{" +
                "predicate='" + predicate + '\'' +
                " assetId='" + assetId + '\'' +
                " value='" + value + '\'' +
                " args=" + Arrays.toString(args) +
                '}';
    }
}
