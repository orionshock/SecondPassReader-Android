import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError, createSecondPassClient } from "../index";
import type { CompactBook } from "../index";
import { buildAuthHeaders, requestBlob, requestJsonUrl, resolveUrl, tryParseFilename } from "../ApiHttp.Adapter";

function jsonResponse(body: unknown, init?: { status?: number; headers?: Record<string, string> }) {
  return new Response(JSON.stringify(body), {
    status: init?.status ?? 200,
    headers: { "content-type": "application/json", ...(init?.headers ?? {}) },
  });
}

function emptyResponse(init?: { status?: number; headers?: Record<string, string> }) {
  return new Response(null, {
    status: init?.status ?? 204,
    headers: init?.headers,
  });
}

function blobResponse(blob: Blob, init?: { status?: number; headers?: Record<string, string> }) {
  return new Response(blob, {
    status: init?.status ?? 200,
    headers: init?.headers,
  });
}

function asMockFetch() {
  const f = globalThis.fetch;
  if (!f || typeof f !== "function") throw new Error("global fetch is not available in this test environment.");
  return vi.mocked(globalThis.fetch);
}

function clientApiDiscoveryResponse() {
  return {
    discovery_version: "1",
    login_request_endpoint: "/client-api/login-requests/",
    poll_endpoint_template: "/client-api/login-requests/%7Bid%7D/poll/",
    consume_endpoint_template: "/client-api/login-requests/%7Bid%7D/consume/",
    token_type: "Bearer",
  };
}

function expectNoStaleLiveAnnotationFields(payload: unknown) {
  const text = JSON.stringify(payload);
  expect(text).not.toContain("profile_version");
  expect(text).not.toContain("motivation");
  expect(text).not.toContain("target");
  expect(text).not.toContain("body");
  expect(text).not.toContain("TextualBody");
  expect(text).not.toContain("FragmentSelector");
  expect(text).not.toContain("TextQuoteSelector");
}

describe("@secondpass/client high-level workflows", () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as unknown as typeof fetch;
  });

  it("api HTTP helpers use bearer auth and never synthesize Basic auth", async () => {
    const fetchMock = asMockFetch();
    fetchMock.mockResolvedValueOnce(jsonResponse({ ok: true }));

    expect(buildAuthHeaders({ accessToken: undefined })).toEqual({});
    expect(buildAuthHeaders({ accessToken: "token" })).toEqual({ Authorization: "Bearer token" });
    expect(resolveUrl("https://api.example/root/", "relative/path")).toBe("https://api.example/root/relative/path");
    expect(resolveUrl("https://api.example/root/", "https://files.example/book.epub")).toBe("https://files.example/book.epub");

    await requestJsonUrl({
      url: "https://api.example/direct/",
      method: "POST",
      defaultAccessToken: "token",
      body: { hello: "world" },
    });

    const [, init] = fetchMock.mock.calls[0]!;
    const headers = init?.headers as Record<string, string>;
    expect(headers.Authorization).toBe("Bearer token");
    expect(headers.Authorization).not.toMatch(/^Basic /i);
    expect(headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(String(init?.body))).toEqual({ hello: "world" });
  });

  it("api HTTP helpers surface specific error kinds and parse download filenames", async () => {
    const fetchMock = asMockFetch();
    fetchMock
      .mockResolvedValueOnce(new Response("missing", { status: 404, statusText: "Not Found" }))
      .mockResolvedValueOnce(new Response("nope", { status: 403, statusText: "Forbidden" }));

    await expect(
      requestJsonUrl({ url: "https://api.example/missing/", errorMessages: { 404: "Custom not found." } }),
    ).rejects.toMatchObject({ kind: "http_error", status: 404, message: "Custom not found." });

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "token" });
    await expect(spl.library.books.download({ id: "1", title: "T" } as unknown as CompactBook)).rejects.toMatchObject({
      kind: "forbidden",
      status: 403,
    });

    expect(tryParseFilename("attachment; filename*=UTF-8''My%20Book.epub")).toBe("My Book.epub");
    expect(tryParseFilename("attachment; filename*=UTF-8''bad%ZZ.epub")).toBe("bad%ZZ.epub");
    expect(tryParseFilename("attachment; filename=\"plain.epub\"")).toBe("plain.epub");
    expect(tryParseFilename("attachment")).toBeUndefined();
  });

  it("API errors suppress HTML response bodies but retain a concise status", async () => {
    const fetchMock = asMockFetch();
    fetchMock
      .mockResolvedValueOnce(new Response("<!DOCTYPE html><html><body>Django 404</body></html>", {
        status: 404,
        statusText: "Not Found",
      }))
      .mockResolvedValueOnce(new Response("<html><body>Proxy failure</body></html>", {
        status: 502,
        statusText: "Bad Gateway",
        headers: { "content-type": "text/html" },
      }))
      .mockResolvedValueOnce(new Response("<!DOCTYPE html><html><body>Missing recent endpoint</body></html>", {
        status: 404,
        statusText: "Not Found",
      }));

    await expect(requestJsonUrl({ url: "https://api.example/missing/" })).rejects.toMatchObject({
      status: 404,
      statusText: "Not Found",
      message: "Request failed: 404 Not Found",
    });
    await expect(requestBlob({
      apiBaseUrl: "https://api.example",
      endpointOrUrl: "/broken-download/",
    })).rejects.toMatchObject({
      status: 502,
      statusText: "Bad Gateway",
      message: "Request failed: 502 Bad Gateway",
    });
    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "token" });
    await expect(spl.marginalia.sessions.recent()).rejects.toMatchObject({
      status: 404,
      statusText: "Not Found",
      message: "Request failed: 404 Not Found",
    });
  });

  it("API errors preserve non-HTML response detail", async () => {
    asMockFetch().mockResolvedValueOnce(new Response("Service unavailable", {
      status: 503,
      statusText: "Service Unavailable",
    }));

    await expect(requestJsonUrl({ url: "https://api.example/unavailable/" })).rejects.toMatchObject({
      message: "Request failed: 503 Service Unavailable - Service unavailable",
    });
  });

  it("client config requires apiBaseUrl and requestBlob uses the default accept header", async () => {
    const fetchMock = asMockFetch();
    expect(() => createSecondPassClient({ apiBaseUrl: "" })).toThrowError(/apiBaseUrl is required/i);

    fetchMock.mockResolvedValueOnce(blobResponse(new Blob(["x"])));
    const result = await requestBlob({
      apiBaseUrl: "https://api.example",
      accessToken: "token",
      endpointOrUrl: "/files/book.epub",
    });

    expect(result.blob.size).toBe(1);
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(String(url)).toBe("https://api.example/files/book.epub");
    const headers = init?.headers as Record<string, string>;
    expect(headers.Accept).toBe("application/octet-stream, */*");
    expect(headers.Authorization).toBe("Bearer token");
  });


  it("shelves.list sends scoped and existing shelf filters", async () => {
    const fetchMock = asMockFetch();
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ count: 0, next: null, previous: null, results: [] }))
      .mockResolvedValueOnce(jsonResponse({ count: 0, next: null, previous: null, results: [] }));

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "t" });
    await spl.shelves.list({
      scope: "shared",
      ownerGroup: 42,
      book: "book-1",
      page: 2,
      pageSize: 25,
      includePreviewBooks: true,
      ordering: "-item_count",
    });
    await spl.shelves.list({ scope: "personal", book: 7, includePreviewBooks: false });

    const sharedUrl = new URL(String(fetchMock.mock.calls[0]![0]));
    expect(sharedUrl.origin + sharedUrl.pathname).toBe("https://api.example/shelves/");
    expect(sharedUrl.searchParams.get("scope")).toBe("shared");
    expect(sharedUrl.searchParams.get("owner_group")).toBe("42");
    expect(sharedUrl.searchParams.get("book")).toBe("book-1");
    expect(sharedUrl.searchParams.get("page")).toBe("2");
    expect(sharedUrl.searchParams.get("page_size")).toBe("25");
    expect(sharedUrl.searchParams.get("include_preview_books")).toBe("true");
    expect(sharedUrl.searchParams.get("ordering")).toBe("-item_count");

    const personalUrl = new URL(String(fetchMock.mock.calls[1]![0]));
    expect(personalUrl.searchParams.get("scope")).toBe("personal");
    expect(personalUrl.searchParams.get("book")).toBe("7");
    expect(personalUrl.searchParams.has("owner_group")).toBe(false);
    expect(personalUrl.searchParams.has("include_preview_books")).toBe(false);
  });

  it("library books sends rebuilt catalog filters and server ordering", async () => {
    const fetchMock = asMockFetch();
    fetchMock.mockResolvedValueOnce(jsonResponse({ count: 0, next: null, previous: null, results: [] }));

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "t" });
    await spl.library.books.list({
      author: "a1",
      series: "s1",
      q: "space opera",
      tag: "award-winner",
      publisher: "Exact Press",
      ordering: "series_index",
      excludeGroup: "g1",
      page: 2,
      pageSize: 20,
    });

    const url = new URL(String(fetchMock.mock.calls[0]![0]));
    expect(url.origin + url.pathname).toBe("https://api.example/library/books/");
    expect(url.searchParams.get("author")).toBe("a1");
    expect(url.searchParams.get("series")).toBe("s1");
    expect(url.searchParams.get("q")).toBe("space opera");
    expect(url.searchParams.get("tag")).toBe("award-winner");
    expect(url.searchParams.get("publisher")).toBe("Exact Press");
    expect(url.searchParams.get("ordering")).toBe("series_index");
    expect(url.searchParams.get("exclude_group")).toBe("g1");
    expect(url.searchParams.get("page")).toBe("2");
    expect(url.searchParams.get("page_size")).toBe("20");
  });

  it("library.books.get fetches an encoded book detail URL", async () => {
    const fetchMock = asMockFetch();
    fetchMock.mockResolvedValueOnce(jsonResponse({ id: "book 1", title: "T", sort_title: "T", subtitle: "", authors: [], series: null, language: null, publisher: null, published_year: null, published_month: null, published_day: null, published_date_precision: "", cover_url: null, description: "", identifiers: [], catalog_tags: [], groups: [], file: { format: "epub", file_size: 4, checksum: "abc", download_url: "https://files.example/book.epub" } }));

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "t" });
    const book = await spl.library.books.get("book 1");

    expect(book.id).toBe("book 1");
    expect(book.file?.downloadUrl).toBe("https://files.example/book.epub");
    expect(book.groups).toEqual([]);
    expect(String(fetchMock.mock.calls[0]![0])).toBe("https://api.example/library/books/book%201/");
  });

  it("library book list rows do not require groups or file data", async () => {
    const row = {
      id: "b1", title: "Book", sort_title: "Book", subtitle: "", authors: [], series: null, catalog_tags: [],
      language: "en", publisher: null, published_year: null, published_month: null, published_day: null,
      published_date_precision: "", cover_url: null, file_format: "epub",
    };
    const fetchMock = asMockFetch();
    fetchMock.mockResolvedValueOnce(jsonResponse({ count: 1, next: null, previous: null, results: [row] }));

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "t" });
    const result = await spl.library.books.list();
    expect(result.results[0]).not.toHaveProperty("groups");
    expect(result.results[0]).not.toHaveProperty("file");
  });

  it("projects the complete compact book contract", async () => {
    const fetchMock = asMockFetch();
    fetchMock.mockResolvedValueOnce(jsonResponse({
      count: 1,
      next: "https://api.example/library/books/?page=2",
      previous: null,
      results: [{
        id: "b1",
        title: "Book",
        sort_title: "Book, The",
        subtitle: "A subtitle",
        authors: [{ id: "a1", name: "Author" }],
        series: { id: "s1", name: "Series", sort_name: "Series", series_index: "1.25" },
        catalog_tags: [{ id: "t1", name: "Science Fiction", slug: "science-fiction" }],
        language: "en",
        publisher: "Publisher",
        published_year: 2026,
        published_month: 8,
        published_day: 2,
        published_date_precision: "day",
        cover_url: "https://api.example/media/cover.jpg",
        file_format: "epub",
      }],
    }));

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "t" });
    const result = await spl.library.books.list();

    expect(result.results[0]).toEqual({
      id: "b1",
      title: "Book",
      sortTitle: "Book, The",
      subtitle: "A subtitle",
      authors: [{ id: "a1", name: "Author" }],
      series: { id: "s1", name: "Series", sortName: "Series", seriesIndex: "1.25" },
      catalogTags: [{ id: "t1", name: "Science Fiction", slug: "science-fiction" }],
      language: "en",
      publisher: "Publisher",
      publishedYear: 2026,
      publishedMonth: 8,
      publishedDay: 2,
      publishedDatePrecision: "day",
      coverUrl: "https://api.example/media/cover.jpg",
      fileFormat: "epub",
    });
  });

  it("projects book detail file, identifiers, groups, and omits compact fileFormat", async () => {
    const fetchMock = asMockFetch();
    fetchMock.mockResolvedValueOnce(jsonResponse({
      id: "b1", title: "Book", sort_title: "Book", subtitle: "", authors: [], series: null,
      language: null, publisher: null, published_year: null, published_month: null, published_day: null,
      published_date_precision: "", cover_url: null, description: "Description",
      identifiers: [{ id: "i1", scheme: "isbn_13", value: "9780000000000" }],
      catalog_tags: [],
      file: { format: "epub", file_size: 123456, checksum: "checksum", download_url: "https://api.example/library/books/b1/download/" },
      groups: [{ id: "g1", name: "Public", description: "", is_public_group: true }],
    }));

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "t" });
    const book = await spl.library.books.get("b1");

    expect(book.file).toEqual({ format: "epub", fileSize: 123456, checksum: "checksum", downloadUrl: "https://api.example/library/books/b1/download/" });
    expect(book.identifiers).toEqual([{ id: "i1", scheme: "isbn_13", value: "9780000000000" }]);
    expect(book.groups).toEqual([{ id: "g1", name: "Public", description: "", isPublicGroup: true }]);
    expect(book).not.toHaveProperty("fileFormat");
  });

  it("library.search uses the slashless route and maps broad-search parameters", async () => {
    const fetchMock = asMockFetch();
    fetchMock.mockResolvedValueOnce(jsonResponse({ count: 0, next: null, previous: null, results: [] }));
    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "t" });

    await spl.library.search({ q: "", ordering: "-series", excludeShelf: "s1", excludeGroup: "g1", page: 2, pageSize: 40 });

    const url = new URL(String(fetchMock.mock.calls[0]![0]));
    expect(url.origin + url.pathname).toBe("https://api.example/library/search");
    expect(url.searchParams.get("q")).toBe("");
    expect(url.searchParams.get("ordering")).toBe("-series");
    expect(url.searchParams.get("exclude_shelf")).toBe("s1");
    expect(url.searchParams.get("exclude_group")).toBe("g1");
    expect(url.searchParams.get("page")).toBe("2");
    expect(url.searchParams.get("page_size")).toBe("40");
  });

  it("library tags list/get use catalog tag routes and query params", async () => {
    const fetchMock = asMockFetch();
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ count: 0, next: null, previous: null, results: [] }))
      .mockResolvedValueOnce(jsonResponse({ id: "t 1", name: "Classic", slug: "classic", book_count: 3 }));
    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "t" });
    await spl.library.tags.list({ q: "class", ordering: "-book_count", page: 2, pageSize: 10 });
    await spl.library.tags.get("t 1");

    const listUrl = new URL(String(fetchMock.mock.calls[0]![0]));
    expect(listUrl.origin + listUrl.pathname).toBe("https://api.example/library/tags/");
    expect(listUrl.searchParams.get("q")).toBe("class");
    expect(listUrl.searchParams.get("ordering")).toBe("-book_count");
    expect(listUrl.searchParams.get("page")).toBe("2");
    expect(listUrl.searchParams.get("page_size")).toBe("10");
    expect(listUrl.searchParams.has("include_preview_books")).toBe(false);
    expect(listUrl.searchParams.has("preview_limit")).toBe(false);
    expect(String(fetchMock.mock.calls[1]![0])).toBe("https://api.example/library/tags/t%201/");
  });

  it("library authors and series optionally request preview books and ordering", async () => {
    const preview = { id: "b1", title: "Book", cover_url: null };
    const fetchMock = asMockFetch();
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ count: 0, next: null, previous: null, results: [] }))
      .mockResolvedValueOnce(jsonResponse({ count: 0, next: null, previous: null, results: [] }))
      .mockResolvedValueOnce(jsonResponse({ id: "s1", name: "Series", preview_books: [preview] }))
      .mockResolvedValueOnce(jsonResponse({ id: "a1", name: "Author" }));

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "t" });
    await spl.library.series.list({ q: "cycle", tag: "classic", page: 3, pageSize: 25, includePreviewBooks: true, previewLimit: 8, excludeId: "s0", ordering: "-book_count" });
    await spl.library.authors.list({ q: "le guin", tag: "classic", page: 4, pageSize: 10, includePreviewBooks: false, excludeId: "a0", ordering: "name" });
    const projectedSeries = await spl.library.series.get("s1", { includePreviewBooks: true, previewLimit: 4 });
    await spl.library.authors.get("a1");

    const seriesListUrl = new URL(String(fetchMock.mock.calls[0]![0]));
    expect(seriesListUrl.origin + seriesListUrl.pathname).toBe("https://api.example/library/series/");
    expect(seriesListUrl.searchParams.get("page")).toBe("3");
    expect(seriesListUrl.searchParams.get("include_preview_books")).toBe("true");
    expect(seriesListUrl.searchParams.get("preview_limit")).toBe("8");
    expect(seriesListUrl.searchParams.get("exclude_id")).toBe("s0");
    expect(seriesListUrl.searchParams.get("ordering")).toBe("-book_count");
    expect(seriesListUrl.searchParams.get("q")).toBe("cycle");
    expect(seriesListUrl.searchParams.get("tag")).toBe("classic");
    expect(seriesListUrl.searchParams.get("page_size")).toBe("25");

    const authorsListUrl = new URL(String(fetchMock.mock.calls[1]![0]));
    expect(authorsListUrl.origin + authorsListUrl.pathname).toBe("https://api.example/library/authors/");
    expect(authorsListUrl.searchParams.get("include_preview_books")).toBe("false");
    expect(authorsListUrl.searchParams.get("exclude_id")).toBe("a0");

    expect(projectedSeries.previewBooks).toEqual([{ id: "b1", title: "Book", coverUrl: null }]);
    const seriesDetailUrl = new URL(String(fetchMock.mock.calls[2]![0]));
    expect(seriesDetailUrl.searchParams.get("preview_limit")).toBe("4");
  });
  it("library download URL helper rejects books without a file download URL", async () => {
    const fetchMock = asMockFetch();
    fetchMock.mockResolvedValueOnce(jsonResponse({ id: 42, title: "No File" }));

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "t" });
    await expect(spl.library.books.getDownloadUrl(42)).rejects.toThrowError(/without a file download URL/i);
  });

  it("shelf items list sends server ordering", async () => {
    const fetchMock = asMockFetch();
    fetchMock.mockResolvedValueOnce(jsonResponse({ count: 0, next: null, previous: null, results: [] }));

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "t" });
    await spl.shelves.items("shelf-1", { page: 2, pageSize: 50, ordering: "author" });

    const url = new URL(String(fetchMock.mock.calls[0]![0]));
    expect(url.origin + url.pathname).toBe("https://api.example/shelves/shelf-1/items/");
    expect(url.searchParams.get("page")).toBe("2");
    expect(url.searchParams.get("page_size")).toBe("50");
    expect(url.searchParams.get("ordering")).toBe("author");
  });

  it("shelves.get fetches shelf detail with optional preview books", async () => {
    const fetchMock = asMockFetch();
    fetchMock.mockResolvedValueOnce(jsonResponse({ id: "shelf 1", name: "Shelf" }));

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "t" });
    await spl.shelves.get("shelf 1", { includePreviewBooks: true });

    const url = new URL(String(fetchMock.mock.calls[0]![0]));
    expect(url.origin + url.pathname).toBe("https://api.example/shelves/shelf%201/");
    expect(url.searchParams.get("include_preview_books")).toBe("true");
  });

  it("shelf items list supports position, title, and author ordering without mutating shelf positions", async () => {
    const fetchMock = asMockFetch();
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ count: 0, next: null, previous: null, results: [] }))
      .mockResolvedValueOnce(jsonResponse({ count: 0, next: null, previous: null, results: [] }))
      .mockResolvedValueOnce(jsonResponse({ count: 0, next: null, previous: null, results: [] }));

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "t" });
    await spl.shelves.items("shelf-1", { ordering: "position" });
    await spl.shelves.items("shelf-1", { ordering: "title" });
    await spl.shelves.items("shelf-1", { ordering: "author" });

    const orderings = fetchMock.mock.calls.map(([url]) => new URL(String(url)).searchParams.get("ordering"));
    expect(orderings).toEqual(["position", "title", "author"]);
    for (const [, init] of fetchMock.mock.calls) {
      expect(init?.method ?? "GET").toBe("GET");
      expect(init?.body).toBeUndefined();
    }
  });

  it("shelves.list preserves unscoped behavior and rejects personal owner-group filters", async () => {
    const fetchMock = asMockFetch();
    fetchMock.mockResolvedValueOnce(jsonResponse({ count: 0, next: null, previous: null, results: [] }));

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "t" });
    await spl.shelves.list({ ownerGroup: "group-1" });

    const unscopedUrl = new URL(String(fetchMock.mock.calls[0]![0]));
    expect(unscopedUrl.searchParams.has("scope")).toBe(false);
    expect(unscopedUrl.searchParams.get("owner_group")).toBe("group-1");

    await expect(
      spl.shelves.list({ scope: "personal", ownerGroup: "group-1" } as never),
    ).rejects.toThrowError(/cannot be combined/i);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("shelf create/update/delete and item mutations use reader-safe payloads", async () => {
    const fetchMock = asMockFetch();
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ id: "shelf-1", name: "Later", owner_type: "user" }))
      .mockResolvedValueOnce(jsonResponse({ id: "shelf-1", name: "Renamed" }))
      .mockResolvedValueOnce(jsonResponse({ id: "item-1", book: 10, position: 0 }))
      .mockResolvedValueOnce(jsonResponse({ id: "item-1", position: 2 }))
      .mockResolvedValueOnce(emptyResponse())
      .mockResolvedValueOnce(emptyResponse());

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "t" });
    await spl.shelves.create({ name: "Later", description: "To read", visibility: "private", owner_type: "user" });
    await spl.shelves.update("shelf-1", { name: "Renamed", description: "Updated" });
    await spl.shelves.addItem("shelf-1", { book: "10" });
    await spl.shelves.updateItem("shelf-1", "item-1", { position: 2 });
    await spl.shelves.removeItem("shelf-1", "item-1");
    await spl.shelves.remove("shelf-1");

    const createPayload = JSON.parse(String(fetchMock.mock.calls[0]![1]?.body));
    expect(String(fetchMock.mock.calls[0]![0])).toBe("https://api.example/shelves/");
    expect(fetchMock.mock.calls[0]![1]?.method).toBe("POST");
    expect(createPayload).toEqual({
      name: "Later",
      description: "To read",
      visibility: "private",
      owner_type: "user",
    });
    expect(createPayload).not.toHaveProperty("owner_group");
    expect(createPayload).not.toHaveProperty("group");

    expect(String(fetchMock.mock.calls[1]![0])).toBe("https://api.example/shelves/shelf-1/");
    expect(fetchMock.mock.calls[1]![1]?.method).toBe("PATCH");
    expect(JSON.parse(String(fetchMock.mock.calls[1]![1]?.body))).toEqual({ name: "Renamed", description: "Updated" });

    expect(String(fetchMock.mock.calls[2]![0])).toBe("https://api.example/shelves/shelf-1/items/");
    expect(fetchMock.mock.calls[2]![1]?.method).toBe("POST");
    expect(JSON.parse(String(fetchMock.mock.calls[2]![1]?.body))).toEqual({ book: "10" });

    expect(String(fetchMock.mock.calls[3]![0])).toBe("https://api.example/shelves/shelf-1/items/item-1/");
    expect(fetchMock.mock.calls[3]![1]?.method).toBe("PATCH");
    expect(JSON.parse(String(fetchMock.mock.calls[3]![1]?.body))).toEqual({ position: 2 });

    expect(String(fetchMock.mock.calls[4]![0])).toBe("https://api.example/shelves/shelf-1/items/item-1/");
    expect(fetchMock.mock.calls[4]![1]?.method).toBe("DELETE");
    expect(String(fetchMock.mock.calls[5]![0])).toBe("https://api.example/shelves/shelf-1/");
    expect(fetchMock.mock.calls[5]![1]?.method).toBe("DELETE");
  });

  it("library.books.download(book) downloads blob via internal download_url without app passing raw URL", async () => {
    const fetchMock = asMockFetch();
    const book = {
      id: 1,
      title: "T",
    } as unknown as CompactBook;

    fetchMock.mockResolvedValueOnce(
      blobResponse(new Blob(["epub"], { type: "application/epub+zip" }), {
        headers: { "content-type": "application/epub+zip", "content-disposition": "attachment; filename=\"book.epub\"" },
      }),
    );

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "t" });
    const blob = await spl.library.books.download(book);
    expect(blob.size).toBeGreaterThan(0);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(String(url)).toBe("https://api.example/library/books/1/download/");
    const headers = init?.headers as Record<string, string>;
    expect(headers.Authorization).toBe("Bearer t");
  });

  it("library.books.download(bookId) uses the dedicated download endpoint", async () => {
    const fetchMock = asMockFetch();
    fetchMock.mockResolvedValueOnce(blobResponse(new Blob(["epub"], { type: "application/epub+zip" })));

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "t" });
    const blob = await spl.library.books.download(1);
    expect(blob.size).toBeGreaterThan(0);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0]![0])).toBe("https://api.example/library/books/1/download/");
  });

  it("server.discover works without access token, while auth-required namespaces throw without access token", async () => {
    const fetchMock = asMockFetch();
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        server_name: "S",
        server_description: "D",
        server_version: "1.2.3",
        server_release: "r1",
        server_release_date: "2026-07-06",
        api_base_url: "https://api.example",
      }),
    );
    fetchMock.mockResolvedValueOnce(jsonResponse(clientApiDiscoveryResponse()));

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example" });
    const discovery = await spl.server.discover("https://server.example");
    expect(discovery.api_base_url).toBe("https://api.example");
    expect(discovery.server_release_date).toBe("2026-07-06");

    expect(() => spl.server.info()).toThrowError(ApiError);
    expect(() => spl.library.books.list()).toThrowError(ApiError);
  });

  it("server discovery and pairing use discovered endpoints without credentials", async () => {
    const fetchMock = asMockFetch();
    fetchMock
      .mockResolvedValueOnce(
        jsonResponse({
          server_name: "S",
          server_description: "D",
          server_version: "1.2.3",
          server_release: "r1",
          server_release_date: "2026-07-06",
          api_base_url: "https://api.example",
        }),
      )
      .mockResolvedValueOnce(jsonResponse(clientApiDiscoveryResponse()))
      .mockResolvedValueOnce(
        jsonResponse({
          id: "request-1",
          code: "ABCD",
          authorize_url: "https://server.example/authorize",
          poll_url: "https://api.example/client-api/login-requests/request-1/poll/",
          consume_url: "https://api.example/client-api/login-requests/request-1/consume/",
          expires_at: "2026-06-21T12:00:00Z",
          interval: 3,
        }),
      )
      .mockResolvedValueOnce(jsonResponse({ status: "approved" }))
      .mockResolvedValueOnce(jsonResponse({
        status: "consumed",
        access_token: "new-token",
        token_type: "Bearer",
        client_session: { id: "session-1", name: "Browser", client_type: "reader" },
      }));

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "pairing-token" });
    const discovery = await spl.server.discover("https://server.example/");
    expect(discovery).toEqual({
      server_name: "S",
      server_description: "D",
      server_version: "1.2.3",
      server_release: "r1",
      server_release_date: "2026-07-06",
      api_base_url: "https://api.example",
      client_api: {
        ...clientApiDiscoveryResponse(),
      },
    });

    const loginRequest = await spl.server.createLoginRequest(discovery);
    expect(loginRequest.consumeUrl).toBe("https://api.example/client-api/login-requests/request-1/consume/");
    await spl.server.pollLoginRequest("https://api.example/client-api/login-requests/request-1/poll/");
    await spl.server.consumeLoginRequest("https://api.example/client-api/login-requests/request-1/consume/");

    const discoverHeaders = fetchMock.mock.calls[0]![1]?.headers as Record<string, string>;
    expect(String(fetchMock.mock.calls[0]![0])).toBe("https://server.example/.well-known/secondpass");
    expect(discoverHeaders.Authorization).toBeUndefined();

    expect(String(fetchMock.mock.calls[1]![0])).toBe("https://api.example/client-api/discovery/");

    const createHeaders = fetchMock.mock.calls[2]![1]?.headers as Record<string, string>;
    expect(String(fetchMock.mock.calls[2]![0])).toBe("https://api.example/client-api/login-requests/");
    expect(createHeaders.Authorization).toBeUndefined();
    expect(JSON.parse(String(fetchMock.mock.calls[2]![1]?.body))).toEqual({
      client_name: "Second Pass Reader",
      client_type: "reader",
    });

    const pollHeaders = fetchMock.mock.calls[3]![1]?.headers as Record<string, string>;
    expect(pollHeaders.Authorization).toBeUndefined();
    const consumeInit = fetchMock.mock.calls[4]![1];
    expect(consumeInit?.method).toBe("POST");
    expect(consumeInit?.body).toBeUndefined();
    expect(consumeInit?.credentials).toBe("omit");
  });

  it("server.createLoginRequest submits the caller-provided editable client name", async () => {
    const fetchMock = asMockFetch();
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        id: "request-1",
        code: "ABCD",
        authorize_url: "https://server.example/authorize",
        poll_url: "https://api.example/client-api/login-requests/request-1/poll/",
        consume_url: "https://api.example/client-api/login-requests/request-1/consume/",
        expires_at: "2026-06-21T12:00:00Z",
        interval: 3,
      }),
    );

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example" });
    await spl.server.createLoginRequest(
      {
        server_name: "Library Server",
        api_base_url: "https://api.example",
        client_api: {
          ...clientApiDiscoveryResponse(),
        },
      },
      {
        clientName: "SecondPass Reader \u00b7 Firefox on Linux",
        clientType: "reader",
      },
    );

    const [url, init] = fetchMock.mock.calls[0]!;
    expect(String(url)).toBe("https://api.example/client-api/login-requests/");
    expect(JSON.parse(String(init?.body))).toEqual({
      client_name: "SecondPass Reader \u00b7 Firefox on Linux",
      client_type: "reader",
    });
  });

  it("account.getCurrentUser projects identity, role flags, and groups", async () => {
    const fetchMock = asMockFetch();
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        username: "ada",
        email: "ada@example.test",
        first_name: "Ada",
        last_name: "Lovelace",
        profile_id: "profile-1",
        role: "reader",
        is_owner: true,
        can_access_django_admin: true,
        groups: [
          { id: "public", name: "Common Room", is_public_group: true },
          { id: "club", name: "Fantasy Club", is_public_group: false, is_curator: true },
        ],
      }),
    );

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "t" });
    const me = await spl.account.getCurrentUser();

    expect(me.username).toBe("ada");
    expect(me.email).toBe("ada@example.test");
    expect(me.firstName).toBe("Ada");
    expect(me.lastName).toBe("Lovelace");
    expect(me.profileId).toBe("profile-1");
    expect(me.role).toBe("reader");
    expect(me.mustChangePassword).toBe(false);
    expect(me.isOwner).toBe(true);
    expect(me.isManager).toBe(false);
    expect(me.isLibrarian).toBe(false);
    expect(me.isReader).toBe(false);
    expect(me.canAccessDjangoAdmin).toBe(true);
    expect(me.groups).toEqual([
      { id: "public", name: "Common Room", isPublicGroup: true, isCurator: false },
      { id: "club", name: "Fantasy Club", isPublicGroup: false, isCurator: true },
    ]);
    expect(me).not.toHaveProperty("advancedLibraryGroupsEnabled");
    expect(me).not.toHaveProperty("bannerText");

    const [url, init] = fetchMock.mock.calls[0]!;
    expect(String(url)).toBe("https://api.example/accounts/me/");
    expect((init?.headers as Record<string, string>).Authorization).toBe("Bearer t");
  });

  it("account.getCurrentUser derives manager, librarian, and reader flags from role", async () => {
    const fetchMock = asMockFetch();
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ username: "manager", role: "manager" }))
      .mockResolvedValueOnce(jsonResponse({ username: "librarian", role: "librarian" }))
      .mockResolvedValueOnce(jsonResponse({ username: "reader", role: "reader" }));

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "t" });
    await expect(spl.account.getCurrentUser()).resolves.toMatchObject({
      isOwner: false,
      isManager: true,
      isLibrarian: false,
      isReader: false,
      mustChangePassword: false,
      canAccessDjangoAdmin: false,
      groups: [],
    });
    await expect(spl.account.getCurrentUser()).resolves.toMatchObject({
      isOwner: false,
      isManager: false,
      isLibrarian: true,
      isReader: false,
    });
    await expect(spl.account.getCurrentUser()).resolves.toMatchObject({
      isOwner: false,
      isManager: false,
      isLibrarian: false,
      isReader: true,
    });
  });

  it("server.info projects authenticated server display and configuration context", async () => {
    const fetchMock = asMockFetch();
    fetchMock.mockResolvedValueOnce(jsonResponse({
      server_name: "Athena Library",
      server_description: "Private reading server",
      server_banner_message: "Maintenance tonight",
      advanced_library_groups_enabled: true,
      reading_client_base_url: "https://reader.example",
      marginalia_profile_uri: "https://example.test/profiles/marginalia",
      public_group: {
        id: "public-1",
        name: "Common Room",
        description: "Public catalog",
      },
      server_version: "2.4.0",
      server_release_date: "2026-08-01",
    }));

    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "t" });
    await expect(spl.server.info()).resolves.toEqual({
      name: "Athena Library",
      description: "Private reading server",
      bannerText: "Maintenance tonight",
      advancedLibraryGroupsEnabled: true,
      readingClientBaseUrl: "https://reader.example",
      marginaliaProfileUri: "https://example.test/profiles/marginalia",
      publicGroup: {
        id: "public-1",
        name: "Common Room",
        description: "Public catalog",
      },
      version: "2.4.0",
      releaseDate: "2026-08-01",
    });
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(String(url)).toBe("https://api.example/server/info/");
    expect((init?.headers as Record<string, string>).Authorization).toBe("Bearer t");
  });

  it("401 marginalia responses surface as ApiError(kind=unauthorized)", async () => {
    const fetchMock = asMockFetch();
    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "t" });
    fetchMock.mockResolvedValueOnce(new Response("nope", { status: 401 }));
    await expect(spl.marginalia.sessions.list()).rejects.toMatchObject({ kind: "unauthorized", status: 401 });
  });
});
