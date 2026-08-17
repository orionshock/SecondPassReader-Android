import { ApiError } from "./ApiHttp.Adapter";

export type ClientContext = {
  apiBaseUrl: string;
  /**
   * Optional: `server.*` discovery/linking calls may be unauthenticated.
   *
   * Most other namespaces require auth; use `requireAuth(ctx)`.
   */
  accessToken?: string;
  tokenType: string;
};

export type AuthenticatedClientContext = {
  apiBaseUrl: string;
  accessToken: string;
  tokenType: string;
};

export function createClientContext(input: {
  apiBaseUrl: string;
  accessToken?: string;
  tokenType?: string;
}): ClientContext {
  if (!input.apiBaseUrl) throw new Error("SecondPassClient config.apiBaseUrl is required.");
  return {
    apiBaseUrl: input.apiBaseUrl,
    accessToken: input.accessToken,
    tokenType: input.tokenType ?? "Bearer",
  };
}

export function requireAuth(ctx: ClientContext): AuthenticatedClientContext {
  if (!ctx.accessToken) {
    throw new ApiError({ kind: "unauthorized", status: 401, message: "Access token is missing." });
  }
  return { apiBaseUrl: ctx.apiBaseUrl, accessToken: ctx.accessToken, tokenType: ctx.tokenType };
}

