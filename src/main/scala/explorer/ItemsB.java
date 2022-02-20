package explorer;

import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;
import org.ergoplatform.explorer.client.model.ItemsA;
import org.ergoplatform.explorer.client.model.OutputInfo;
import org.ergoplatform.explorer.client.model.TransactionInfo;

import java.util.List;
import java.util.Objects;

public class ItemsB {
    @SerializedName("items")
    private java.util.List<TransactionInfo> items = null;

    @SerializedName("total")
    private Integer total = null;

    public ItemsB items(java.util.List<TransactionInfo> items) {
        this.items = items;
        return this;
    }

    public ItemsB addItemsItem(TransactionInfo itemsItem) {
        if (this.items == null) {
            this.items = new java.util.ArrayList<TransactionInfo>();
        }
        this.items.add(itemsItem);
        return this;
    }

    /**
     * Get items
     * @return items
     **/
    @Schema(description = "")
    public java.util.List<TransactionInfo> getItems() {
        return items;
    }

    public void setItems(java.util.List<TransactionInfo> items) {
        this.items = items;
    }

    public ItemsB total(Integer total) {
        this.total = total;
        return this;
    }

    /**
     * Get total
     * @return total
     **/
    @Schema(required = true, description = "")
    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }


    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ItemsB itemsB = (ItemsB) o;
        return Objects.equals(this.items, itemsB.items) &&
                Objects.equals(this.total, itemsB.total);
    }

    @Override
    public int hashCode() {
        return Objects.hash(items, total);
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class ItemsA {\n");

        sb.append("    items: ").append(toIndentedString(items)).append("\n");
        sb.append("    total: ").append(toIndentedString(total)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(java.lang.Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }

}