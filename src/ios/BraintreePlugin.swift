import Foundation
import BraintreeDropIn
import Braintree

@objc(BraintreePlugin)
class BraintreePlugin: CDVPlugin {

    // Definiere die braintreeClient-Instanz
    var braintreeClient: BTAPIClient?

    // Diese Methode wird automatisch aufgerufen, wenn das Plugin geladen wird
    override func pluginInitialize() {
        // Beispiel-Token (Du solltest einen echten Token vom Server holen)
        let token = "sandbox_tvsw733g_mdxf3sf6ggqsgktg"
        
        // Initialisiere den Braintree-Client mit dem Token
        self.braintreeClient = BTAPIClient(authorization: token)
        
        print("Braintree Plugin initialized with token: \(token)")
    }

    var dropInUIcallbackId: String?

    @objc(presentDropInPaymentUI:)
    func presentDropInPaymentUI(command: CDVInvokedUrlCommand) {
        
        // Speichere die callbackId, um später das Ergebnis zurückzusenden
        self.dropInUIcallbackId = command.callbackId
        
        // Initialisiere die Braintree DropIn-Anfrage
        let request = BTDropInRequest()
        
        // Beispiel-Token (Du solltest einen echten Token vom Server holen)
        let authorizationToken = "sandbox_tvsw733g_mdxf3sf6ggqsgktg"
        
        let dropIn = BTDropInController(authorization: authorizationToken, request: request) { (controller, result, error) in
            
            if let error = error {
                // Fehlerfall: Sende den Fehler an die Cordova-Ebene
                var resultData: [String: Any] = [
                    "error": true,
                    "errorMessage": error.localizedDescription,
                ]
                
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: resultData)
                self.commandDelegate.send(pluginResult, callbackId: self.dropInUIcallbackId)
            } else if result?.isCanceled == true {
                // Fall: Benutzer hat abgebrochen
                var resultData: [String: Any] = [
                    "error": true,
                    "errorMessage": "userCancelled",
                    "userCancelled": true
                ]
                
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: resultData)
                self.commandDelegate.send(pluginResult, callbackId: self.dropInUIcallbackId)
            } else if let result = result {
                let paymentMethodNonce = result.paymentMethod?.nonce
                let paymentMethodType = result.paymentMethodType.rawValue

                var resultData: [String: Any] = [
                    "nonce": paymentMethodNonce ?? "",
                    "type": paymentMethodType
                ]
                
                // Device Data Collector für iOS
                self.collectDeviceData { deviceData in
                    resultData["deviceData"] = deviceData
                    let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: resultData)
                    self.commandDelegate.send(pluginResult, callbackId: self.dropInUIcallbackId)
                }
            }
            
            // Schließe den DropIn-Controller
            controller.dismiss(animated: true, completion: nil)
        }
        
        // Präsentiere das DropIn UI
        self.viewController.present(dropIn!, animated: true, completion: nil)
    }
   
    // Funktion zum Sammeln der deviceData
    func collectDeviceData(completion: @escaping (String?) -> Void) {
        guard let braintreeClient = self.braintreeClient else {
            completion(nil)
            return
        }

        let dataCollector = BTDataCollector(apiClient: braintreeClient)
        dataCollector.collectDeviceData { deviceData in
            completion(deviceData)
        }
    }

    @objc(getPaymentUINonceResult:)
    func getPaymentUINonceResult(_ paymentMethodNonce: Any) -> [String: Any] {
        // Return an empty dictionary for now
        return [:]
    }

    @objc(formatCardNetwork:)
    func formatCardNetwork(_ cardNetwork: Int) -> String {
        // Return an empty string for now
        return ""
    }
}
