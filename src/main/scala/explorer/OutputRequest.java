package explorer;

public class OutputRequest {


    private OutputBody[] outputs;

    public OutputRequest(OutputBody[] outs) {
        outputs = outs;
    }

    public OutputBody[] getOutputs() {
        return outputs;
    }

    public void setOutputs(OutputBody[] outputs) {
        this.outputs = outputs;
    }
}
