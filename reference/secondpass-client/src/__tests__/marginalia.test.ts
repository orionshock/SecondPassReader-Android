import { beforeEach, describe, expect, it, vi } from "vitest";
import { createSecondPassClient } from "../index";

const json = (body: unknown) => new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" } });
const fetchMock = () => vi.mocked(globalThis.fetch);
const wireBook = { id: "book-1", title: "Book", cover_url: null, can_open: true };
const wireProgress = { cfi: "epubcfi(/6/2)", location_label: "Chapter 08 - 42%", updated_at: "2026-08-02T10:00:00Z" };
const wireSession = { id: "session-1", name: "Morning", notes: "Notes", status: "active", started_at: "2026-08-01T10:00:00Z", closed_at: null, updated_at: "2026-08-02T10:00:00Z", last_activity_at: "2026-08-02T10:00:00Z", annotation_count: 2, progress: wireProgress };
const highlight = { id: "ann-h", client_id: "client-h", kind: "highlight", location: { cfi: "epubcfi(/6/4,/2,/8)", location_label: "Chapter 09 - 50%" }, body: { text: "Quote", prefix: "Before", suffix: "After", color: "purple", note: "Note" }, created_at: "2026-08-01", updated_at: "2026-08-02" };
const bookmark = { id: "ann-b", client_id: "client-b", kind: "bookmark", location: { cfi: "epubcfi(/6/8)", location_label: "Location 08 - 42%" }, created_at: "2026-08-01", updated_at: "2026-08-02" };
const emptyPage = { count: 0, next: null, previous: null, results: [] };

describe("marginalia client", () => {
  beforeEach(() => { vi.stubGlobal("fetch", vi.fn()); });
  const client = () => createSecondPassClient({ apiBaseUrl: "https://api.example/api/v1", accessToken: "token" });

  it("projects book summaries, bounded books, recent sessions, and null activity", async () => {
    fetchMock()
      .mockResolvedValueOnce(json({ count: 1, next: null, previous: null, results: [{ ...wireBook, authors: [{ id: "author-1", name: "Author" }], series: { id: "series-1", name: "Series", series_index: "1.25" }, session_count: 3, active_session_count: 1, last_activity_at: null }] }))
      .mockResolvedValueOnce(json({ results: [
        { id: "session-1", name: "Morning", status: "active", last_activity_at: "2026-08-02", book: wireBook, progress: wireProgress },
        { id: "session-2", name: "New", status: "active", last_activity_at: "2026-08-01", book: wireBook, progress: null },
      ] }));
    const spl = client();
    const books = await spl.marginalia.books.list({ page: 2, pageSize: 50 });
    const recent = await spl.marginalia.sessions.recent({ limit: 10, includeClosed: true });
    expect(books.results[0]).toEqual({ id: "book-1", title: "Book", authors: [{ id: "author-1", name: "Author" }], series: { id: "series-1", name: "Series", seriesIndex: "1.25" }, coverUrl: null, canOpen: true, sessionCount: 3, activeSessionCount: 1, lastActivityAt: null });
    expect(recent.results[0].book).toEqual({ id: "book-1", title: "Book", coverUrl: null, canOpen: true });
    expect(recent.results[0].progress).toEqual({ cfi: wireProgress.cfi, locationLabel: wireProgress.location_label, updatedAt: wireProgress.updated_at });
    expect(recent.results[1].progress).toBeNull();
    expect(String(fetchMock().mock.calls[0]![0])).toContain("page_size=50");
    expect(String(fetchMock().mock.calls[1]![0])).toBe("https://api.example/api/v1/marginalia/sessions/recent/?limit=10&include_closed=true");
  });

  it("maps global and book session filters and projects list/detail progress", async () => {
    fetchMock()
      .mockResolvedValueOnce(json({ count: 1, next: null, previous: null, results: [{ ...wireSession, book: wireBook }] }))
      .mockResolvedValueOnce(json({ context: { book: wireBook }, count: 1, next: null, previous: null, results: [wireSession] }))
      .mockResolvedValueOnce(json({ context: { book: wireBook }, session: wireSession }));
    const spl = client();
    const global = await spl.marginalia.sessions.list({ status: "closed", q: "notes", hasAnnotations: true, page: 3, pageSize: 25 });
    const scoped = await spl.marginalia.books.sessions("book-1", { status: "active", q: "morning", pageSize: 10 });
    const detail = await spl.marginalia.sessions.get("session-1");
    expect(global.results[0].book.coverUrl).toBeNull();
    expect(scoped.context.book.canOpen).toBe(true);
    expect(detail.session.progress).toEqual({ cfi: wireProgress.cfi, locationLabel: wireProgress.location_label, updatedAt: wireProgress.updated_at });
    expect(String(fetchMock().mock.calls[0]![0])).toContain("has_annotations=true");
    expect(String(fetchMock().mock.calls[1]![0])).toContain("/marginalia/books/book-1/sessions/");
  });

  it("gets marginalia book detail and updates session metadata", async () => {
    const summary = { ...wireBook, authors: [], series: null, session_count: 1, active_session_count: 1, last_activity_at: null };
    fetchMock()
      .mockResolvedValueOnce(json(summary))
      .mockResolvedValueOnce(json({ context: { book: wireBook }, session: { ...wireSession, name: "Updated" } }));
    const spl = client();
    await expect(spl.marginalia.books.get("book 1")).resolves.toMatchObject({ id: "book-1", lastActivityAt: null });
    await expect(spl.marginalia.sessions.update("session-1", { name: "Updated", notes: "Notes" })).resolves.toMatchObject({ session: { name: "Updated" } });
    expect(String(fetchMock().mock.calls[0]![0])).toBe("https://api.example/api/v1/marginalia/books/book%201/");
    expect(fetchMock().mock.calls[1]![1]?.method).toBe("PATCH");
  });

  it("gets and replaces the complete progress tuple", async () => {
    fetchMock().mockResolvedValueOnce(json({ progress: null })).mockResolvedValueOnce(json({ progress: wireProgress }));
    const spl = client();
    await expect(spl.marginalia.sessions.getProgress("session-1")).resolves.toEqual({ progress: null });
    await expect(spl.marginalia.sessions.replaceProgress("session-1", { cfi: wireProgress.cfi, locationLabel: wireProgress.location_label })).resolves.toEqual({ progress: { cfi: wireProgress.cfi, locationLabel: wireProgress.location_label, updatedAt: wireProgress.updated_at } });
    expect(JSON.parse(String(fetchMock().mock.calls[1]![1]?.body))).toEqual({ cfi: wireProgress.cfi, location_label: wireProgress.location_label });
    expect(fetchMock().mock.calls[1]![1]?.method).toBe("PUT");
  });

  it("projects open/active bootstrap and sends start-over idempotency with final progress", async () => {
    const bootstrap = { created: true, context: { book: wireBook }, session: wireSession, annotations: [highlight, bookmark], closed_sessions: emptyPage };
    fetchMock().mockResolvedValueOnce(json(bootstrap)).mockResolvedValueOnce(json({ ...bootstrap, created: false, session: null, annotations: [] })).mockResolvedValueOnce(json(bootstrap));
    const spl = client();
    const opened = await spl.marginalia.books.open("book-1", { name: "Morning" });
    const active = await spl.marginalia.books.getActiveSession("book-1");
    await spl.marginalia.books.startOver("book-1", { name: "Done", progress: { cfi: "epubcfi(/6/10)", locationLabel: "Chapter 10 - 75%" } }, { idempotencyKey: "start-over-action-1" });
    expect(opened.annotations[1]).not.toHaveProperty("body");
    expect(active.session).toBeNull();
    const startInit = fetchMock().mock.calls[2]![1]!;
    expect((startInit.headers as Record<string, string>)["Idempotency-Key"]).toBe("start-over-action-1");
    expect(JSON.parse(String(startInit.body))).toEqual({ name: "Done", progress: { cfi: "epubcfi(/6/10)", location_label: "Chapter 10 - 75%" } });
  });

  it("maps close progress and authoritative annotation collections and batches", async () => {
    fetchMock()
      .mockResolvedValueOnce(json({ context: { book: wireBook }, session: { ...wireSession, status: "closed" } }))
      .mockResolvedValueOnce(json({ annotations: [highlight, bookmark] }))
      .mockResolvedValueOnce(json({ annotations: [highlight, bookmark] }));
    const spl = client();
    await spl.marginalia.sessions.close("session-1", { progress: { cfi: "epubcfi(/6/12)", locationLabel: "Chapter 12 - 90%" } });
    const collection = await spl.marginalia.sessions.getAnnotations("session-1");
    await spl.marginalia.sessions.batchAnnotations("session-1", [
      { action: "upsert", annotation: { clientId: "client-h", kind: "highlight", location: { cfi: "range", locationLabel: "Chapter 1 - 10%" }, body: { text: "Quote", color: "yellow" } } },
      { action: "upsert", annotation: { clientId: "client-b", kind: "bookmark", location: { cfi: "point", locationLabel: "Location 1 - 10%" } } },
      { action: "delete", clientId: "client-old" },
    ]);
    expect(collection.annotations[0]).toMatchObject({ clientId: "client-h", location: { locationLabel: "Chapter 09 - 50%" } });
    expect(collection.annotations[1]).not.toHaveProperty("body");
    const body = JSON.parse(String(fetchMock().mock.calls[2]![1]?.body));
    expect(body.operations[0].annotation.body.text).toBe("Quote");
    expect(body.operations[1].annotation).not.toHaveProperty("body");
    expect(body.operations[2]).toEqual({ action: "delete", client_id: "client-old" });
  });

  it("does not expose archive or legacy reading APIs", () => {
    const spl = client() as any;
    expect(spl.reading).toBeUndefined();
    expect(spl.marginalia.export).toBeUndefined();
    expect(spl.marginalia.import).toBeUndefined();
  });

  it("rejects invalid start-over keys and empty annotation batches before sending", async () => {
    const spl = client();
    await expect(spl.marginalia.books.startOver("book-1", undefined, { idempotencyKey: "   " })).rejects.toThrow(/idempotencyKey/);
    await expect(spl.marginalia.sessions.batchAnnotations("session-1", [])).rejects.toThrow(/1-100 operations/);
    expect(fetchMock()).not.toHaveBeenCalled();
  });
});
