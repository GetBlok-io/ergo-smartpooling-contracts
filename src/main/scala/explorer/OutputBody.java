package explorer;

public class OutputBody {
    private String boxId;
    private long value;
    private String address;

    public OutputBody(String boxId, long value, String address) {
        this.boxId = boxId;
        this.value = value;
        this.address = address;
    }

    public String getBoxId() {
        return boxId;
    }

    public void setBoxId(String boxId) {
        this.boxId = boxId;
    }

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
