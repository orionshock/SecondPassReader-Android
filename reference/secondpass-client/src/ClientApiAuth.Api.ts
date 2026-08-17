import type {
  ClientApiConsumeResponse,
  ClientApiLoginRequestResponse,
  ClientApiPollResponse,
  SecondPassDiscovery,
  SecondPassWellKnown,
} from "./schemas/ClientApiAuth.Types";
import { requestJsonUrl, resolveUrl } from "./ApiHttp.Adapter";

type JsonRecord = Record<string, unknown>;
const CLIENT_API_DISCOVERY_ENDPOINT = "/client-api/discovery/";

function record(value: unknown, context: string): JsonRecord {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error(`Invalid ${context} response.`);
  return value as JsonRecord;
}

function requiredString(value: unknown, context: string): string {
  if (typeof value !== "string" || !value.trim()) throw new Error(`Invalid ${context} response.`);
  return value;
}

function optionalString(value: unknown, context: string): string | undefined {
  if (value === undefined || value === null) return undefined;
  return requiredString(value, context);
}

function parseWellKnown(value: unknown): SecondPassWellKnown {
  const wire = record(value, "SecondPass discovery");
  return {
    server_name: requiredString(wire.server_name, "SecondPass discovery"),
    server_description: optionalString(wire.server_description, "SecondPass discovery"),
    server_version: optionalString(wire.server_version, "SecondPass discovery"),
    server_release: optionalString(wire.server_release, "SecondPass discovery"),
    server_release_date: optionalString(wire.server_release_date, "SecondPass discovery"),
    api_base_url: requiredString(wire.api_base_url, "SecondPass discovery"),
  };
}

function parseClientApiDiscovery(value: unknown, wellKnown: SecondPassWellKnown): SecondPassDiscovery {
  const wire = record(value, "client API discovery");
  return {
    server_name: wellKnown.server_name,
    server_description: wellKnown.server_description,
    server_version: wellKnown.server_version,
    server_release: wellKnown.server_release,
    server_release_date: wellKnown.server_release_date,
    api_base_url: wellKnown.api_base_url,
    client_api: {
      discovery_version: requiredString(wire.discovery_version, "client API discovery"),
      login_request_endpoint: requiredString(wire.login_request_endpoint, "client API discovery"),
      poll_endpoint_template: requiredString(wire.poll_endpoint_template, "client API discovery"),
      consume_endpoint_template: requiredString(wire.consume_endpoint_template, "client API discovery"),
      token_type: requiredString(wire.token_type, "client API discovery"),
    },
  };
}

function parseLoginRequest(value: unknown): ClientApiLoginRequestResponse {
  const wire = record(value, "login request");
  if (typeof wire.interval !== "number" || !Number.isFinite(wire.interval) || wire.interval <= 0) {
    throw new Error("Invalid login request response.");
  }
  return {
    id: requiredString(wire.id, "login request"),
    code: requiredString(wire.code, "login request"),
    authorizeUrl: requiredString(wire.authorize_url, "login request"),
    pollUrl: requiredString(wire.poll_url, "login request"),
    consumeUrl: requiredString(wire.consume_url, "login request"),
    expiresAt: requiredString(wire.expires_at, "login request"),
    interval: wire.interval,
  };
}

const POLL_STATUSES = new Set(["pending", "approved", "denied", "expired", "consumed"]);

function parsePollResponse(value: unknown): ClientApiPollResponse {
  const wire = record(value, "login request poll");
  if (typeof wire.status !== "string" || !POLL_STATUSES.has(wire.status)) {
    throw new Error("Invalid login request poll response.");
  }
  if (Object.keys(wire).some((key) => key !== "status")) throw new Error("Invalid login request poll response.");
  return { status: wire.status } as ClientApiPollResponse;
}

function parseConsumeResponse(value: unknown): ClientApiConsumeResponse {
  const wire = record(value, "login request consume");
  if (wire.status === "pending" || wire.status === "denied" || wire.status === "expired") return { status: wire.status };
  if (wire.status !== "consumed") throw new Error("Invalid login request consume response.");
  if (!("access_token" in wire)) return { status: "consumed" };
  if (typeof wire.access_token !== "string") throw new Error("Invalid login request consume response.");
  if (!wire.access_token.trim()) return { status: "consumed" };

  const clientSession = record(wire.client_session, "login request consume");
  return {
    status: "consumed",
    accessToken: wire.access_token,
    tokenType: requiredString(wire.token_type, "login request consume"),
    clientSession: {
      id: requiredString(clientSession.id, "login request consume"),
      name: requiredString(clientSession.name, "login request consume"),
      clientType: requiredString(clientSession.client_type, "login request consume"),
    },
  };
}

export async function discoverSecondPass(serverBaseUrl: string): Promise<SecondPassDiscovery> {
  const wellKnownUrl = resolveUrl(serverBaseUrl, "/.well-known/secondpass");
  const wellKnown = parseWellKnown(await requestJsonUrl<unknown>({ url: wellKnownUrl, method: "GET", credentials: "omit" }));
  const discoveryUrl = resolveUrl(wellKnown.api_base_url, CLIENT_API_DISCOVERY_ENDPOINT);
  const clientApi = await requestJsonUrl<unknown>({ url: discoveryUrl, method: "GET", credentials: "omit" });
  return parseClientApiDiscovery(clientApi, wellKnown);
}

export async function createLoginRequest(
  discovery: SecondPassDiscovery,
  input?: { clientName?: string; clientType?: string },
): Promise<ClientApiLoginRequestResponse> {
  const endpoint = discovery.client_api?.login_request_endpoint;
  if (!endpoint) throw new Error("Invalid client API discovery response.");
  const url = resolveUrl(discovery.api_base_url, endpoint);
  const wire = await requestJsonUrl<unknown>({
    url,
    method: "POST",
    body: {
      client_name: input?.clientName ?? "Second Pass Reader",
      client_type: input?.clientType ?? "reader",
    },
    credentials: "omit",
  });
  return parseLoginRequest(wire);
}

export async function pollLoginRequest(pollUrl: string): Promise<ClientApiPollResponse> {
  const wire = await requestJsonUrl<unknown>({ url: pollUrl, method: "GET", credentials: "omit" });
  return parsePollResponse(wire);
}

export async function consumeLoginRequest(consumeUrl: string): Promise<ClientApiConsumeResponse> {
  const wire = await requestJsonUrl<unknown>({ url: consumeUrl, method: "POST", credentials: "omit" });
  return parseConsumeResponse(wire);
}
