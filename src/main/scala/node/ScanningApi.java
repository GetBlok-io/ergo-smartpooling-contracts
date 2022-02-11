package node;//retrofit2

import org.ergoplatform.restapi.client.*;
import org.ergoplatform.restapi.client.CollectionFormats.*;

import org.ergoplatform.restapi.client.Scan;
import retrofit2.Call;
import retrofit2.http.*;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.http.Body;


public interface ScanningApi {
    /**
     * Adds a box to scans, writes box to database if it is not there. You can use scan number 10 to add a box to the wallet.
     *
     * @param body  (required)
     * @return Call&lt;String&gt;
     */
    @Headers({
            "Content-Type:application/json"
    })
    @POST("scan/addBox")
    Call<String> addBox(
            @retrofit2.http.Body ScanIdsBox body
    );

    /**
     * Stop tracking and deregister scan
     *
     * @param body  (required)
     * @return Call&lt;ScanId&gt;
     */
    @Headers({
            "Content-Type:application/json"
    })
    @POST("scan/deregister")
    Call<ScanId> deregisterScan(
            @retrofit2.http.Body ScanId body
    );

    /**
     * List all the registered scans
     *
     * @return Call&lt;java.util.List&lt;Scan&gt;&gt;
     */
    @GET("scan/listAll")
    Call<java.util.List<node.Scan>> listAllScans();


    /**
     * List boxes which are not spent.
     *
     * @param scanId identifier of a scan (required)
     * @param minConfirmations Minimal number of confirmations (optional, default to 0)
     * @param minInclusionHeight Minimal box inclusion height (optional, default to 0)
     * @return Call&lt;java.util.List&lt;WalletBox&gt;&gt;
     */
    @GET("scan/unspentBoxes/{scanId}")
    Call<java.util.List<WalletBox>> listUnspentScans(
            @retrofit2.http.Path("scanId") Integer scanId            ,     @retrofit2.http.Query("minConfirmations") Integer minConfirmations                ,     @retrofit2.http.Query("minInclusionHeight") Integer minInclusionHeight
    );

    /**
     * Register a scan
     *
     * @param body  (required)
     * @return Call&lt;ScanId&gt;
     */
    @Headers({
            "Content-Type:application/json"
    })
    @POST("scan/register")
    Call<ScanId> registerScan(
            @retrofit2.http.Body node.Scan body
    );

    /**
     * Stop scan-related box tracking
     *
     * @param body  (required)
     * @return Call&lt;ScanIdBoxId&gt;
     */
    @Headers({
            "Content-Type:application/json"
    })
    @POST("scan/stopTracking")
    Call<ScanIdBoxId> scanStopTracking(
            @retrofit2.http.Body ScanIdBoxId body
    );

    @Headers({
            "Content-Type:application/json"
    })
    @POST("scan/register")
    Call<ScanId> registerScan(
            @retrofit2.http.Body ScanRequest body
    );

    @GET("utxo/byId/{boxId}")
    Call<ErgoTransactionOutput> getBoxById(
            @retrofit2.http.Path("boxId") String boxId
    );

}
