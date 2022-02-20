package explorer;

import org.ergoplatform.explorer.client.CollectionFormats.*;

import retrofit2.Call;
import retrofit2.http.*;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import org.ergoplatform.explorer.client.model.BadRequest;
import org.ergoplatform.explorer.client.model.Balance;
import org.ergoplatform.explorer.client.model.BlockSummary;
import org.ergoplatform.explorer.client.model.BoxQuery;
import org.ergoplatform.explorer.client.model.EpochParameters;
import org.ergoplatform.explorer.client.model.ItemsA;
import org.ergoplatform.explorer.client.model.ListOutputInfo;
import org.ergoplatform.explorer.client.model.NotFound;
import org.ergoplatform.explorer.client.model.OutputInfo;
import org.ergoplatform.explorer.client.model.TokenInfo;
import org.ergoplatform.explorer.client.model.TotalBalance;
import org.ergoplatform.explorer.client.model.TransactionInfo;
import org.ergoplatform.explorer.client.model.UnknownErr;

import java.util.List;

/**
 * Custom explorer api to get around crashes caused by additional registers field in TransactionInfo
 */
public interface CustomExplorerApi {


    /**
     *
     *
     * @param p1  (required)
     * @return Call&lt;TransactionInfo&gt;
     */
    @GET("api/v1/transactions/{p1}")
    Call<ResponseBody> getTransactionById(
            @retrofit2.http.Path("p1") String p1
    );

    @GET("api/v1/transactions/{p1}")
    Call<TransactionInfo> getFullTxById(
            @retrofit2.http.Path("p1") String p1
    );

    @GET("api/v1/addresses/{p1}/transactions")
    Call<ItemsB> getTxsByAddress(
            @retrofit2.http.Path("p1") String p1            ,     @retrofit2.http.Query("offset") Integer offset                ,     @retrofit2.http.Query("limit") Integer limit
    );


}
