export type RequestUrlOptions = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  accessToken?: string | null;
  defaultAccessToken?: string | null;
  body?: unknown;
  headers?: Record<string, string>;
  credentials?: RequestCredentials;
  url: string;
  /**
   * Optional override messages for specific status codes.
   *
   * Useful when you want a clearer product-facing error while keeping the
   * underlying wire contract unchanged.
   */
  errorMessages?: Partial<Record<number, string>>;
};

export type ApiErrorKind = "unauthorized" | "forbidden" | "http_error";

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly status: number;
  readonly statusText: string;

  constructor(input: { kind: ApiErrorKind; status: number; statusText?: string; message: string }) {
    super(input.message);
    this.kind = input.kind;
    this.status = input.status;
    this.statusText = input.statusText ?? "";
  }
}

function containsHtml(value: string): boolean {
  return /<!doctype\s+html\b|<html(?:\s|>)/i.test(value);
}

function createApiError(res: Response, responseBody: string, overrideMessage?: string): ApiError {
  const statusDescription = [res.status, res.statusText].filter(Boolean).join(" ");
  const conciseMessage = `Request failed: ${statusDescription}`;
  const contentType = res.headers.get("content-type") ?? "";
  const bodyIsHtml = /(?:text\/html|application\/xhtml\+xml)/i.test(contentType) || containsHtml(responseBody);
  const safeOverride = overrideMessage && !containsHtml(overrideMessage) ? overrideMessage : undefined;
  const message = safeOverride ?? `${conciseMessage}${responseBody && !bodyIsHtml ? ` - ${responseBody}` : ""}`;
  const kind: ApiErrorKind =
    res.status === 401 ? "unauthorized" : res.status === 403 ? "forbidden" : "http_error";
  return new ApiError({ kind, status: res.status, statusText: res.statusText, message });
}

/**
 * Common auth-related messages.
 *
 * Most authenticated endpoints use the same semantics for 401/403.
 * Callers can override 403/404 when a more specific message is valuable.
 */
export const INVALID_OR_REVOKED_TOKEN_401 = "Token is invalid or revoked (401).";

export function authErrorMessages(input?: {
  forbidden?: string;
  notFound?: string;
}): Partial<Record<number, string>> {
  return {
    401: INVALID_OR_REVOKED_TOKEN_401,
    ...(input?.forbidden ? { 403: input.forbidden } : {}),
    ...(input?.notFound ? { 404: input.notFound } : {}),
  };
}

export function resolveUrl(baseUrl: string, endpointOrUrl: string): string {
  if (/^https?:\/\//i.test(endpointOrUrl)) return endpointOrUrl;
  const base = baseUrl.replace(/\/+$/, "");
  const path = endpointOrUrl.startsWith("/") ? endpointOrUrl : `/${endpointOrUrl}`;
  return `${base}${path}`;
}

export async function requestJsonUrl<T>(options: RequestUrlOptions): Promise<T> {
  const headers: Record<string, string> = {
    Accept: "application/json",
    ...options.headers,
  };

  const token = options.accessToken ?? options.defaultAccessToken;
  if (token) headers.Authorization = `Bearer ${token}`;

  let body: BodyInit | undefined;
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(options.body);
  }

  const res = await fetch(options.url, {
    method: options.method ?? "GET",
    headers,
    body,
    credentials: options.credentials,
  });

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw createApiError(res, text, options.errorMessages?.[res.status]);
  }

  return (await res.json()) as T;
}

export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

export type RequestOptions = {
  method?: HttpMethod;
  headers?: Record<string, string>;
  body?: unknown;
  errorMessages?: Partial<Record<number, string>>;
};

export function buildAuthHeaders(input: { accessToken?: string; tokenType?: string }): Record<string, string> {
  if (!input.accessToken) return {};
  const tokenType = input.tokenType ?? "Bearer";
  return { Authorization: `${tokenType} ${input.accessToken}` };
}

export async function requestJson<T>(input: {
  apiBaseUrl: string;
  accessToken?: string;
  tokenType?: string;
  endpointOrUrl: string;
  options?: RequestOptions;
}): Promise<T> {
  const url = resolveUrl(input.apiBaseUrl, input.endpointOrUrl);
  const headers: Record<string, string> = {
    Accept: "application/json",
    ...buildAuthHeaders({ accessToken: input.accessToken, tokenType: input.tokenType }),
    ...input.options?.headers,
  };

  let body: BodyInit | undefined;
  if (input.options?.body !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(input.options.body);
  }

  const res = await fetch(url, {
    method: input.options?.method ?? "GET",
    headers,
    body,
  });

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw createApiError(res, text, input.options?.errorMessages?.[res.status]);
  }

  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export async function requestBlob(input: {
  apiBaseUrl: string;
  accessToken?: string;
  tokenType?: string;
  endpointOrUrl: string;
  options?: Omit<RequestOptions, "body"> & { accept?: string };
}): Promise<{ blob: Blob; response: Response }> {
  const url = resolveUrl(input.apiBaseUrl, input.endpointOrUrl);
  const headers: Record<string, string> = {
    Accept: input.options?.accept ?? "application/octet-stream, */*",
    ...buildAuthHeaders({ accessToken: input.accessToken, tokenType: input.tokenType }),
    ...input.options?.headers,
  };

  const res = await fetch(url, {
    method: input.options?.method ?? "GET",
    headers,
  });

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw createApiError(res, text, input.options?.errorMessages?.[res.status]);
  }

  const blob = await res.blob();
  return { blob, response: res };
}

export function tryParseFilename(contentDisposition: string): string | undefined {
  // RFC 6266 basics: filename="<name>" or filename*=UTF-8''<urlencoded>
  const filenameStar = /filename\*\s*=\s*([^']*)''([^;]+)/i.exec(contentDisposition);
  if (filenameStar) {
    const encoded = filenameStar[2].trim();
    try {
      return decodeURIComponent(encoded);
    } catch {
      return encoded;
    }
  }

  const filename = /filename\s*=\s*\"?([^\";]+)\"?/i.exec(contentDisposition);
  if (filename) return filename[1].trim();
  return undefined;
}
