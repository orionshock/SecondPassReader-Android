export type SecondPassWellKnown = {
  server_name: string;
  server_description?: string;
  server_version?: string;
  server_release?: string;
  server_release_date?: string;
  api_base_url: string;
};

export type SecondPassDiscovery = {
  server_name: string;
  server_description?: string;
  server_version?: string;
  server_release?: string;
  server_release_date?: string;
  api_base_url: string;
  client_api: {
    discovery_version: string;
    login_request_endpoint: string;
    poll_endpoint_template: string;
    consume_endpoint_template: string;
    token_type: "Bearer" | string;
  };
};

export type ClientApiLoginRequestResponse = {
  id: string;
  code: string;
  authorizeUrl: string;
  pollUrl: string;
  consumeUrl: string;
  expiresAt: string;
  interval: number;
};

export type ClientApiPollResponse =
  | { status: "pending" }
  | { status: "approved" }
  | { status: "denied" }
  | { status: "expired" }
  | { status: "consumed" };

export type ClientApiConsumeResponse =
  | { status: "pending" }
  | { status: "denied" }
  | { status: "expired" }
  | { status: "consumed" }
  | {
      status: "consumed";
      accessToken: string;
      tokenType: "Bearer" | string;
      clientSession: {
        id: string;
        name: string;
        clientType: string;
      };
    };
