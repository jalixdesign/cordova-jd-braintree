package net.justincredible;

import android.util.Log;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.braintreepayments.api.DropInListener;
import com.braintreepayments.api.GooglePayRequest;
import com.braintreepayments.api.UserCanceledException;
import com.braintreepayments.api.PayPalCheckoutRequest;

import com.braintreepayments.api.DropInClient;
import com.braintreepayments.api.DropInRequest;
import com.braintreepayments.api.DropInResult;
import com.google.android.gms.wallet.TransactionInfo;
import com.google.android.gms.wallet.WalletConstants;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class BraintreePlugin extends CordovaPlugin implements DropInListener {

    private static final String TAG = "BraintreePlugin";
    private static final String TOKEN_PREFERENCES = "BraintreeTokenPreferences";
    private static final String TOKEN_KEY = "BraintreeTokenKey";
    private DropInClient dropInClient = null;
    private CallbackContext _callbackContext = null;

    String savedToken = null;

    @Override
    protected void pluginInitialize() {
        Log.d(TAG, "BraintreePlugin -> pluginInitialize");

        // Prüfe, ob der Token lokal gespeichert ist
        savedToken = getSavedToken();
        if (savedToken != null) {
            Log.d(TAG, "Token found, initializing Braintree client with saved token: " + savedToken);
            if (savedToken != null && !"null".equals(savedToken) && !savedToken.isEmpty()) {
                initializeBraintreeClient(savedToken);
            } else {
                Log.d(TAG, "Token is either null or empty, skipping initialization.");
            }
            fetchTokenFromAPI(false);
        } else {
            Log.d(TAG, "No saved token found, fetching token from API.");
            // Token nicht gefunden, hole den Token von der API
            fetchTokenFromAPI(true);
        }
    }

    // Methode zum Abrufen des gespeicherten Tokens
    private String getSavedToken() {
        SharedPreferences preferences = cordova.getActivity().getSharedPreferences(TOKEN_PREFERENCES, android.content.Context.MODE_PRIVATE);
        return preferences.getString(TOKEN_KEY, null);  // Gibt null zurück, wenn kein Token vorhanden ist
    }

    // Methode zum Speichern des Tokens
    private void saveToken(String token) {
        Log.d(TAG, "Braintree saveToken");
        SharedPreferences.Editor editor = cordova.getActivity().getSharedPreferences(TOKEN_PREFERENCES, android.content.Context.MODE_PRIVATE).edit();
        editor.putString(TOKEN_KEY, token);
        editor.commit();
    }

    // Methode, um den Token von der API zu holen
    private void fetchTokenFromAPI(Boolean withRestart) {
        Log.d(TAG, "Fetching token from API.");

        String bundleId = cordova.getActivity().getPackageName();

        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("apiKey", "8a72264a15e492ea287c5cbd9fd7e93f29b66fde4e60b8a9w8er7awer8asd564")
                .addFormDataPart("bundleId", bundleId);

        RequestBody requestBody = builder.build();

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://dev-apiv2.tennis-club.net/v2/braintreeTVA/getToken")
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Error fetching Braintree token: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                Log.d(TAG, "Full response: " + responseBody);

                if (!response.isSuccessful()) {
                    Log.e(TAG, "Error fetching token: Invalid response code " + response.code());
                    return;
                }

                try {
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    JSONObject meta = jsonResponse.getJSONObject("meta");
                    int code = meta.getInt("code");

                    if (code == 100) {
                        JSONObject data = jsonResponse.getJSONObject("data");
                        String token = data.getString("token");
                        Log.d(TAG, "Braintree token received: " + token);

                        // Token speichern
                        saveToken(token);

                        if ( withRestart || !token.equals(savedToken) ) {
                            restartApp();
                        }
                    } else {
                        Log.e(TAG, "Error fetching token: Invalid response code");
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing JSON response: " + e.getMessage());
                }
            }
        });
    }

    // Methode zum Initialisieren des Braintree-Clients
    private void initializeBraintreeClient(String token) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dropInClient = new DropInClient(cordova.getActivity(), token);
                dropInClient.setListener(BraintreePlugin.this);
                Log.d(TAG, "Braintree Client initialized with token.");
            }
        });
    }

    // Methode zum Neustarten der App
    private void restartApp() {
        Log.d(TAG, "Braintree restartApp");
        // Intent zum Starten der App erstellen
        Intent intent = cordova.getActivity().getBaseContext().getPackageManager()
                .getLaunchIntentForPackage(cordova.getActivity().getBaseContext().getPackageName());

        if (intent != null) {
            // Stelle sicher, dass die App richtig neu gestartet wird
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            // App in einem neuen Thread neu starten, um Verzögerungen zu vermeiden
            cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // Startet die App neu
                    cordova.getActivity().startActivity(intent);

                    // Beende die aktuelle Activity, damit die App vollständig neu gestartet wird
                    cordova.getActivity().finish();

                    // Gib der App etwas Zeit, bevor sie neu startet
                    System.exit(0);
                }
            });
        } else {
            Log.e(TAG, "Unable to restart the app: Launch intent is null.");
        }
    }


    @Override
    public synchronized boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {

        if (action == null) {
            Log.e(TAG, "execute ==> exiting for bad action");
            return false;
        }

        Log.w(TAG, "execute ==> " + action + " === " + args);

        _callbackContext = callbackContext;

        try {
            Log.d(TAG, "execute: debug2");
            if (action.equals("initialize")) {
                this.initializeBT(args);
            }
            else if (action.equals("presentDropInPaymentUI")) {
                Log.d(TAG, "execute: debug3");
                this.presentDropInPaymentUI(args);
            }
            else if (action.equals("paypalProcess")) {
                this.paypalProcess(args);
            }
            else {
                // The given action was not handled above.
                return false;
            }
        } catch (Exception exception) {
            callbackContext.error("BraintreePlugin uncaught exception: " + exception.getMessage());
        }

        return true;
    }

    // Initialize the Braintree client
    private synchronized void initializeBT(final JSONArray args) throws Exception {
        String token = args.getString(0);

        // DropInClient can also be instantiated with a tokenization key

        _callbackContext.success();
    }

    private synchronized void presentDropInPaymentUI(final JSONArray args) throws JSONException {

        Log.d(TAG, "presentDropInPaymentUI: debug0");

        dropInClient.setListener(this);

        String betrag = args.getString(0);

        Log.d(TAG, "presentDropInPaymentUI: debug1");

        DropInRequest dropInRequest = new DropInRequest();

        Log.d(TAG, "presentDropInPaymentUI: debug1.1");

        //paypal
        PayPalCheckoutRequest payPalRequest = new PayPalCheckoutRequest(betrag);
        payPalRequest.setCurrencyCode("EUR");
        dropInRequest.setPayPalRequest(payPalRequest);

        //google pay
        GooglePayRequest googlePayRequest = new GooglePayRequest();
        googlePayRequest.setTransactionInfo(TransactionInfo.newBuilder()
                .setTotalPrice(betrag)
                .setTotalPriceStatus(WalletConstants.TOTAL_PRICE_STATUS_FINAL)
                .setCurrencyCode("EUR")
                .build());
        googlePayRequest.setBillingAddressRequired(true);

        dropInRequest.setGooglePayRequest(googlePayRequest);

        Log.d(TAG, "presentDropInPaymentUI: debug2");
        dropInClient.launchDropIn(dropInRequest);
        Log.d(TAG, "presentDropInPaymentUI: debug3");
    }

    private synchronized void paypalProcess(final JSONArray args) throws Exception {
        /*PayPalClient payPalClient = new PayPalClient(braintreeClient);

        PayPalCheckoutRequest request = new PayPalCheckoutRequest(args.getString(0));  // Specify the amount
        request.currencyCode(args.getString(1));  // Specify the currency

        payPalClient.tokenizePayPalAccount(this.cordova.getActivity(), request, (payPalAccountNonce, error) -> {
            if (error != null) {
                _callbackContext.error("PayPal Process Error: " + error.getMessage());
            } else if (payPalAccountNonce != null) {
                Map<String, Object> resultMap = getPaymentUINonceResult(payPalAccountNonce);
                _callbackContext.success(new JSONObject(resultMap));
            }
        });*/
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (_callbackContext == null) {
            Log.e(TAG, "onActivityResult exiting ==> callbackContext is invalid");
            return;
        }

    }

    @Override
    public void onDropInSuccess(@NonNull DropInResult dropInResult) {
        Log.d(TAG, "onDropInSuccess:" + dropInResult.toString());

        String paymentMethodNonce = dropInResult.getPaymentMethodNonce().getString();
        String deviceData = dropInResult.getDeviceData();

        try {
            JSONObject resultObj = new JSONObject();
            resultObj.put("error", "false");
            resultObj.put("nonce", paymentMethodNonce);
            resultObj.put("deviceData", deviceData);

            _callbackContext.success(resultObj);
        } catch (JSONException e) {
            _callbackContext.error("JSON Exception: " + e.getMessage());
        }
    }

    @Override
    public void onDropInFailure(@NonNull Exception error) {
        Log.d(TAG, "onDropInFailure:" + error.getMessage());

        if (error instanceof UserCanceledException) {
            try {
                JSONObject resultObj = new JSONObject();
                resultObj.put("error", "true");
                resultObj.put("errorMessage", "userCancelled");
                resultObj.put("userCancelled", true);



                _callbackContext.success(resultObj);
            } catch (JSONException e) {
                _callbackContext.error("JSON Exception: " + e.getMessage());
            }
        } else {
            try {
                JSONObject errorObj = new JSONObject();
                errorObj.put("error", "true");
                errorObj.put("errorMessage", error.getMessage());
                errorObj.put("errorType", error.getClass().getSimpleName());

                _callbackContext.success(errorObj);
            } catch (JSONException e) {
                _callbackContext.error("JSON Exception: " + e.getMessage());
            }
        }
    }
}
