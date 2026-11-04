import { sendApiRequest } from "../components/send-api-request/SendApiRequest";
import { KsefCredentials } from "../models/ksef_invoice";

class KsefCredentialsService {

  static async getCredentials(): Promise<KsefCredentials> {
    return await sendApiRequest<KsefCredentials>("ksef/credentials", {
      method: "get",
      errorMessage: "Error retrieving KSeF credentials.",
    });
  }

  static async saveCredentials(dto: KsefCredentials): Promise<KsefCredentials> {
    return await sendApiRequest<KsefCredentials>("ksef/credentials", {
      method: "put",
      body: dto,
      errorMessage: "Error saving KSeF credentials.",
    });
  }
}

export default KsefCredentialsService;
