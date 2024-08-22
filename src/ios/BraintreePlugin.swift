import Foundation
import BraintreeDropIn
import Braintree

@objc(BraintreePlugin)
class BraintreePlugin: CDVPlugin {

    // Definiere die braintreeClient-Instanz
    var braintreeClient: BTAPIClient?

    override func pluginInitialize() {
        // Token von der API laden und Braintree-Client initialisieren
        fetchBraintreeToken { [weak self] token in
            guard let token = token else {
                print("Error: Token could not be retrieved")
                return
            }
            
            // Initialisiere den Braintree-Client mit dem erhaltenen Token
            self?.braintreeClient = BTAPIClient(authorization: token)
            print("Braintree Plugin initialized with token: \(token)")
        }
    }

    // API-Anfrage zum Abrufen des Braintree-Tokens
    private func fetchBraintreeToken(completion: @escaping (String?) -> Void) {
        let url = URL(string: "https://dev-apiv2.tennis-club.net/v2/braintreeTVA/getToken")!
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        
        // Setze die POST-Parameter
        let params = "apiKey=8a72264a15e492ea287c5cbd9fd7e93f29b66fde4e60b8a9w8er7awer8asd564&bundleId=\(Bundle.main.bundleIdentifier ?? "")"
        request.httpBody = params.data(using: .utf8)
        
        // Setze den Content-Type auf application/x-www-form-urlencoded
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        
        // Führe die Anfrage aus
        let task = URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                print("Error fetching token: \(error.localizedDescription)")
                completion(nil)
                return
            }
            
            guard let data = data else {
                print("Error: No data received")
                completion(nil)
                return
            }
            
            do {
                // JSON-Antwort parsen
                if let json = try JSONSerialization.jsonObject(with: data, options: []) as? [String: Any],
                   let meta = json["meta"] as? [String: Any],
                   let code = meta["code"] as? Int, code == 100,
                   let data = json["data"] as? [String: Any],
                   let token = data["token"] as? String {
                    print("Braintree token received: \(token)")
                    completion(token)
                } else {
                    print("Error parsing JSON or invalid response code")
                    completion(nil)
                }
            } catch {
                print("JSON error: \(error.localizedDescription)")
                completion(nil)
            }
        }
        
        task.resume() // Starte den Netzwerkaufruf
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
