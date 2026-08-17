import { beforeEach, describe, expect, it, vi } from "vitest";
import { consumeLoginRequest, createLoginRequest, pollLoginRequest } from "../ClientApiAuth.Api";
import type { SecondPassDiscovery } from "../schemas/ClientApiAuth.Types";

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" } });
}

const discovery: SecondPassDiscovery = {
  server_name: "Library",
  api_base_url: "https://api.example/api/v1",
  client_api: {
    discovery_version: "1",
    login_request_endpoint: "/api/v1/client-api/login-requests/",
    poll_endpoint_template: "/api/v1/client-api/login-requests/%7Bid%7D/poll/",
    consume_endpoint_template: "/api/v1/client-api/login-requests/%7Bid%7D/consume/",
    token_type: "Bearer",
  },
};

describe("client API auth contract", () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as unknown as typeof fetch;
  });

  it("projects all required login request fields including consume_url", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(loginRequestWire()));

    await expect(createLoginRequest(discovery)).resolves.toEqual({
      id: "request-1",
      code: "ABCD",
      authorizeUrl: "https://library.example/authorize/request-1",
      pollUrl: "https://api.example/poll/request-1",
      consumeUrl: "https://api.example/consume/request-1",
      expiresAt: "2026-08-04T12:00:00Z",
      interval: 3,
    });
  });

  it("rejects a create response missing a required concrete URL", async () => {
    const wire = loginRequestWire();
    delete (wire as Partial<typeof wire>).consume_url;
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(wire));
    await expect(createLoginRequest(discovery)).rejects.toThrow("Invalid login request response.");
  });

  it.each(["pending", "approved", "denied", "expired", "consumed"] as const)(
    "accepts lifecycle-only poll status %s",
    async (status) => {
      vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ status }));
      await expect(pollLoginRequest("https://api.example/concrete-poll")).resolves.toEqual({ status });
    },
  );

  it("rejects token-bearing poll responses", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ status: "approved", access_token: "wrong-place" }));
    await expect(pollLoginRequest("https://api.example/concrete-poll")).rejects.toThrow(
      "Invalid login request poll response.",
    );
  });

  it("POSTs the concrete consume URL without body or credentials and maps credentials", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(consumedWire()));

    await expect(consumeLoginRequest("https://api.example/concrete-consume")).resolves.toEqual({
      status: "consumed",
      accessToken: "secret-token",
      tokenType: "Bearer",
      clientSession: { id: "session-1", name: "Browser", clientType: "reader" },
    });
    expect(fetch).toHaveBeenCalledWith("https://api.example/concrete-consume", expect.objectContaining({
      method: "POST",
      body: undefined,
      credentials: "omit",
    }));
  });

  it("accepts consumed without a token as a terminal no-token response", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ status: "consumed" }));
    await expect(consumeLoginRequest("https://api.example/concrete-consume")).resolves.toEqual({ status: "consumed" });
  });

  it("rejects token-bearing consume responses missing client_session.id", async () => {
    const wire = consumedWire();
    wire.client_session.id = "";
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(wire));
    await expect(consumeLoginRequest("https://api.example/concrete-consume")).rejects.toThrow(
      "Invalid login request consume response.",
    );
  });
});

function loginRequestWire() {
  return {
    id: "request-1",
    code: "ABCD",
    authorize_url: "https://library.example/authorize/request-1",
    poll_url: "https://api.example/poll/request-1",
    consume_url: "https://api.example/consume/request-1",
    expires_at: "2026-08-04T12:00:00Z",
    interval: 3,
  };
}

function consumedWire() {
  return {
    status: "consumed",
    access_token: "secret-token",
    token_type: "Bearer",
    client_session: { id: "session-1", name: "Browser", client_type: "reader" },
  };
}
