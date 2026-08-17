import { beforeEach, describe, expect, it, vi } from "vitest";
import { createSecondPassClient } from "../index";

const json = (body: unknown) => new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" } });
const page = { count: 0, next: null, previous: null, results: [] };

describe("library group client", () => {
  beforeEach(() => vi.stubGlobal("fetch", vi.fn()));

  it("maps group preview controls on list and detail", async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValueOnce(json(page)).mockResolvedValueOnce(json({ id: "group-1", name: "Group", description: "", is_public_group: false, preview_books: [] }));
    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "token" });
    await spl.library.groups.list({ q: "club", book: "book-1", ordering: "-name", includePreviewBooks: true, previewLimit: 8, pageSize: 25 });
    await spl.library.groups.get("group-1", { includePreviewBooks: true, previewLimit: 4 });
    const listUrl = new URL(String(fetchMock.mock.calls[0]![0]));
    expect(listUrl.searchParams.get("preview_limit")).toBe("8");
    expect(listUrl.searchParams.get("book")).toBe("book-1");
    const detailUrl = new URL(String(fetchMock.mock.calls[1]![0]));
    expect(detailUrl.searchParams.get("preview_limit")).toBe("4");
  });

  it("maps group-scoped book and entity controls without tag previews", async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockImplementation(async () => json(page));
    const spl = createSecondPassClient({ apiBaseUrl: "https://api.example", accessToken: "token" });
    await spl.library.groups.books("group-1", { q: "broad", excludeShelf: "shelf-1", series: "series-1", ordering: "series_index" });
    await spl.library.groups.authors("group-1", { q: "author", includePreviewBooks: true, previewLimit: 6 });
    await spl.library.groups.series("group-1", { tag: "classic", previewLimit: 5 });
    await spl.library.groups.tags("group-1", { q: "tag", ordering: "book_count" });
    const booksUrl = new URL(String(fetchMock.mock.calls[0]![0]));
    expect(booksUrl.searchParams.get("exclude_shelf")).toBe("shelf-1");
    expect(booksUrl.searchParams.get("ordering")).toBe("series_index");
    expect(new URL(String(fetchMock.mock.calls[1]![0])).searchParams.get("preview_limit")).toBe("6");
    expect(new URL(String(fetchMock.mock.calls[2]![0])).searchParams.get("preview_limit")).toBe("5");
    const tagsUrl = new URL(String(fetchMock.mock.calls[3]![0]));
    expect(tagsUrl.searchParams.has("include_preview_books")).toBe(false);
    expect(tagsUrl.searchParams.has("preview_limit")).toBe(false);
  });
});
