import type {
  AddShelfItemInput,
  CreateShelfInput,
  PaginatedShelfItemResponse,
  PaginatedShelfResponse,
  Shelf,
  ShelfItem,
  ShelfListParams,
  UpdateShelfInput,
  UpdateShelfItemInput,
} from "./schemas/Shelves.Types";
import type { AuthenticatedClientContext } from "./ClientContext.Policy";
import { authErrorMessages, requestJson, resolveUrl } from "./ApiHttp.Adapter";

const SHELVES_FORBIDDEN_403 = "Token is not allowed to access shelves (403).";

export async function listShelves(
  ctx: AuthenticatedClientContext,
  input?: ShelfListParams,
): Promise<PaginatedShelfResponse> {
  if (input?.scope === "personal" && input.ownerGroup !== undefined) {
    throw new TypeError("Shelf scope 'personal' cannot be combined with ownerGroup.");
  }

  const url = new URL(resolveUrl(ctx.apiBaseUrl, "/shelves/"));
  if (input?.scope) url.searchParams.set("scope", input.scope);
  if (input?.ownerGroup !== undefined) url.searchParams.set("owner_group", String(input.ownerGroup));
  if (input?.book !== undefined) url.searchParams.set("book", String(input.book));
  if (input?.page !== undefined) url.searchParams.set("page", String(input.page));
  if (input?.pageSize !== undefined) url.searchParams.set("page_size", String(input.pageSize));
  if (input?.includePreviewBooks === true) url.searchParams.set("include_preview_books", "true");
  if (input?.ordering) url.searchParams.set("ordering", input.ordering);

  return requestJson<PaginatedShelfResponse>({
    apiBaseUrl: ctx.apiBaseUrl,
    accessToken: ctx.accessToken,
    tokenType: ctx.tokenType,
    endpointOrUrl: url.toString(),
    options: {
      errorMessages: authErrorMessages({ forbidden: SHELVES_FORBIDDEN_403 }),
    },
  });
}

export async function getShelf(
  ctx: AuthenticatedClientContext,
  input: { shelfId: string; includePreviewBooks?: boolean },
): Promise<Shelf> {
  const url = new URL(resolveUrl(ctx.apiBaseUrl, `/shelves/${encodeURIComponent(input.shelfId)}/`));
  if (input.includePreviewBooks === true) url.searchParams.set("include_preview_books", "true");
  return requestJson<Shelf>({
    apiBaseUrl: ctx.apiBaseUrl,
    accessToken: ctx.accessToken,
    tokenType: ctx.tokenType,
    endpointOrUrl: url.toString(),
    options: {
      errorMessages: authErrorMessages({
        forbidden: SHELVES_FORBIDDEN_403,
        notFound: "Shelf not found or not accessible (404).",
      }),
    },
  });
}

export async function createShelf(ctx: AuthenticatedClientContext, input: CreateShelfInput): Promise<Shelf> {
  const url = resolveUrl(ctx.apiBaseUrl, "/shelves/");
  return requestJson<Shelf>({
    apiBaseUrl: ctx.apiBaseUrl,
    accessToken: ctx.accessToken,
    tokenType: ctx.tokenType,
    endpointOrUrl: url,
    options: {
      method: "POST",
      body: input,
      errorMessages: authErrorMessages({ forbidden: SHELVES_FORBIDDEN_403 }),
    },
  });
}

export async function updateShelf(
  ctx: AuthenticatedClientContext,
  input: { shelfId: string; update: UpdateShelfInput },
): Promise<Shelf> {
  const url = resolveUrl(ctx.apiBaseUrl, `/shelves/${encodeURIComponent(input.shelfId)}/`);
  return requestJson<Shelf>({
    apiBaseUrl: ctx.apiBaseUrl,
    accessToken: ctx.accessToken,
    tokenType: ctx.tokenType,
    endpointOrUrl: url,
    options: {
      method: "PATCH",
      body: input.update,
      errorMessages: authErrorMessages({
        forbidden: SHELVES_FORBIDDEN_403,
        notFound: "Shelf not found or not accessible (404).",
      }),
    },
  });
}

export async function deleteShelf(ctx: AuthenticatedClientContext, input: { shelfId: string }): Promise<void> {
  const url = resolveUrl(ctx.apiBaseUrl, `/shelves/${encodeURIComponent(input.shelfId)}/`);
  return requestJson<void>({
    apiBaseUrl: ctx.apiBaseUrl,
    accessToken: ctx.accessToken,
    tokenType: ctx.tokenType,
    endpointOrUrl: url,
    options: {
      method: "DELETE",
      errorMessages: authErrorMessages({
        forbidden: SHELVES_FORBIDDEN_403,
        notFound: "Shelf not found or not accessible (404).",
      }),
    },
  });
}

export async function listShelfItems(
  ctx: AuthenticatedClientContext,
  input: { shelfId: string; page?: number; pageSize?: number; ordering?: string },
): Promise<PaginatedShelfItemResponse> {
  const url = new URL(resolveUrl(ctx.apiBaseUrl, `/shelves/${encodeURIComponent(input.shelfId)}/items/`));
  if (typeof input.page === "number") url.searchParams.set("page", String(input.page));
  if (typeof input.pageSize === "number") url.searchParams.set("page_size", String(input.pageSize));
  if (input.ordering) url.searchParams.set("ordering", input.ordering);

  return requestJson<PaginatedShelfItemResponse>({
    apiBaseUrl: ctx.apiBaseUrl,
    accessToken: ctx.accessToken,
    tokenType: ctx.tokenType,
    endpointOrUrl: url.toString(),
    options: {
      errorMessages: authErrorMessages({
        forbidden: SHELVES_FORBIDDEN_403,
        notFound: "Shelf not found or not accessible (404).",
      }),
    },
  });
}

export async function addShelfItem(
  ctx: AuthenticatedClientContext,
  input: { shelfId: string; item: AddShelfItemInput },
): Promise<ShelfItem> {
  const url = resolveUrl(ctx.apiBaseUrl, `/shelves/${encodeURIComponent(input.shelfId)}/items/`);
  return requestJson<ShelfItem>({
    apiBaseUrl: ctx.apiBaseUrl,
    accessToken: ctx.accessToken,
    tokenType: ctx.tokenType,
    endpointOrUrl: url,
    options: {
      method: "POST",
      body: input.item,
      errorMessages: authErrorMessages({
        forbidden: SHELVES_FORBIDDEN_403,
        notFound: "Shelf not found or not accessible (404).",
      }),
    },
  });
}

export async function updateShelfItem(
  ctx: AuthenticatedClientContext,
  input: { shelfId: string; itemId: string; update: UpdateShelfItemInput },
): Promise<ShelfItem> {
  const url = resolveUrl(
    ctx.apiBaseUrl,
    `/shelves/${encodeURIComponent(input.shelfId)}/items/${encodeURIComponent(input.itemId)}/`,
  );
  return requestJson<ShelfItem>({
    apiBaseUrl: ctx.apiBaseUrl,
    accessToken: ctx.accessToken,
    tokenType: ctx.tokenType,
    endpointOrUrl: url,
    options: {
      method: "PATCH",
      body: input.update,
      errorMessages: authErrorMessages({
        forbidden: SHELVES_FORBIDDEN_403,
        notFound: "Shelf item not found or not accessible (404).",
      }),
    },
  });
}

export async function deleteShelfItem(
  ctx: AuthenticatedClientContext,
  input: { shelfId: string; itemId: string },
): Promise<void> {
  const url = resolveUrl(
    ctx.apiBaseUrl,
    `/shelves/${encodeURIComponent(input.shelfId)}/items/${encodeURIComponent(input.itemId)}/`,
  );
  return requestJson<void>({
    apiBaseUrl: ctx.apiBaseUrl,
    accessToken: ctx.accessToken,
    tokenType: ctx.tokenType,
    endpointOrUrl: url,
    options: {
      method: "DELETE",
      errorMessages: authErrorMessages({
        forbidden: SHELVES_FORBIDDEN_403,
        notFound: "Shelf item not found or not accessible (404).",
      }),
    },
  });
}
