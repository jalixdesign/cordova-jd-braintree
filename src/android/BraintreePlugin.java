package net.justincredible;

import android.util.Log;
import android.app.Activity;
import android.content.Intent;

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

public final class BraintreePlugin extends CordovaPlugin implements DropInListener {

    private static final String TAG = "BraintreePlugin";


    private DropInClient dropInClient = null;
    private CallbackContext _callbackContext = null;

    @Override
    protected void pluginInitialize() {
        Log.d(TAG, "BraintreePlugin -> pluginInitialize");
        // Hier wird sichergestellt, dass die Initialisierung im Hauptthread in der onCreate-Zeit erfolgt
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "pluginInitialize: Called during Activity creation");
                initializeBraintreeClient();
            }
        });
    }

    private void initializeBraintreeClient(){
        Log.d(TAG, "initializeBraintreeClient: debug1");

        dropInClient = new DropInClient(this.cordova.getActivity(), "sandbox_tvsw733g_mdxf3sf6ggqsgktg");

        Log.d(TAG, "initializeBraintreeClient: debug2");

        // Make sure to register listener in onCreate
        dropInClient.setListener(this);
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
