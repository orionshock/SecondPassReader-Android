import type {
  BookDetail, BookFileDownloadResult, CatalogTag, CompactBook, Author,
  LibraryGroup, Series, PaginatedResponse, PreviewBook,
} from "./schemas/Library.Types";
import type { AuthenticatedClientContext } from "./ClientContext.Policy";
import { authErrorMessages, requestBlob, requestJson, resolveUrl, tryParseFilename } from "./ApiHttp.Adapter";

const LIBRARY_FORBIDDEN_403 = "Token is not allowed to access the library (403).";
const LIBRARY_FILE_DOWNLOAD_FORBIDDEN_403 = "Token is not allowed to download files (403).";

type WirePreviewBook = { id: string; title: string; cover_url: string | null };
type WireAuthorSummary = { id: string; name: string };
type WireSeriesSummary = { id: string; name: string; sort_name: string; series_index: string | null };
type WireTagSummary = { id: string; name: string; slug: string };
type WireCompactBook = {
  id: string; title: string; sort_title: string; subtitle: string; authors: WireAuthorSummary[];
  series: WireSeriesSummary | null; catalog_tags: WireTagSummary[]; language: string | null;
  publisher: string | null; published_year: number | null; published_month: number | null;
  published_day: number | null; published_date_precision: "" | "year" | "month" | "day";
  cover_url: string | null; file_format: string;
};
type WireBookDetail = Omit<WireCompactBook, "file_format"> & {
  description: string;
  identifiers: Array<{ id: string; scheme: BookDetail["identifiers"][number]["scheme"]; value: string }>;
  file: { format: string; file_size: number; checksum: string; download_url: string } | null;
  groups: Array<{ id: string; name: string; description: string; is_public_group: boolean }>;
};
type WireAuthor = { id: string; name: string; sort_name: string; biography: string; book_count: number; preview_books?: WirePreviewBook[] };
type WireSeries = { id: string; name: string; sort_name: string; summary: string; book_count: number; preview_books?: WirePreviewBook[] };
type WireTag = WireTagSummary & { book_count: number };
type WireGroup = { id: string; name: string; description: string; is_public_group: boolean; preview_books?: WirePreviewBook[] };
type WirePage<T> = { count: number; next: string | null; previous: string | null; results: T[] };

export type BookListParams = {
  page?: number; pageSize?: number; q?: string; author?: string; series?: string; tag?: string;
  publisher?: string; ordering?: string; excludeGroup?: string;
};
export type SearchParams = {
  q?: string; ordering?: string; excludeShelf?: string; excludeGroup?: string; page?: number; pageSize?: number;
};
export type EntityListParams = {
  q?: string; tag?: string; ordering?: string; page?: number; pageSize?: number;
  excludeId?: string; includePreviewBooks?: boolean; previewLimit?: number;
};
export type EntityPreviewParams = { includePreviewBooks?: boolean; previewLimit?: number };
export type TagListParams = { q?: string; ordering?: string; page?: number; pageSize?: number };
export type GroupListParams = {
  q?: string; book?: string; ordering?: string; page?: number; pageSize?: number;
  includePreviewBooks?: boolean; previewLimit?: number;
};

const preview = (wire: WirePreviewBook): PreviewBook => ({ id: String(wire.id), title: wire.title, coverUrl: wire.cover_url ?? null });
const compactBook = (wire: WireCompactBook): CompactBook => ({
  id: String(wire.id), title: wire.title, sortTitle: wire.sort_title, subtitle: wire.subtitle,
  authors: (wire.authors ?? []).map((author) => ({ id: String(author.id), name: author.name })),
  series: wire.series ? { id: String(wire.series.id), name: wire.series.name, sortName: wire.series.sort_name, seriesIndex: wire.series.series_index ?? null } : null,
  catalogTags: (wire.catalog_tags ?? []).map((tag) => ({ id: String(tag.id), name: tag.name, slug: tag.slug })),
  language: wire.language ?? null, publisher: wire.publisher ?? null, publishedYear: wire.published_year ?? null,
  publishedMonth: wire.published_month ?? null, publishedDay: wire.published_day ?? null,
  publishedDatePrecision: wire.published_date_precision ?? "", coverUrl: wire.cover_url ?? null,
  fileFormat: wire.file_format,
});
function bookDetail(wire: WireBookDetail): BookDetail {
  const { fileFormat, ...book } = compactBook({ ...wire, file_format: "" });
  void fileFormat;
  return {
    ...book,
    description: wire.description,
    identifiers: (wire.identifiers ?? []).map((item) => ({ id: String(item.id), scheme: item.scheme, value: item.value })),
    file: wire.file ? { format: wire.file.format, fileSize: wire.file.file_size, checksum: wire.file.checksum, downloadUrl: wire.file.download_url } : null,
    groups: (wire.groups ?? []).map((group) => ({ id: String(group.id), name: group.name, description: group.description, isPublicGroup: group.is_public_group === true })),
  };
}
const author = (wire: WireAuthor): Author => ({ id: String(wire.id), name: wire.name, sortName: wire.sort_name, biography: wire.biography, bookCount: wire.book_count, previewBooks: (wire.preview_books ?? []).map(preview) });
const series = (wire: WireSeries): Series => ({ id: String(wire.id), name: wire.name, sortName: wire.sort_name, summary: wire.summary, bookCount: wire.book_count, previewBooks: (wire.preview_books ?? []).map(preview) });
const tag = (wire: WireTag): CatalogTag => ({ id: String(wire.id), name: wire.name, slug: wire.slug, bookCount: wire.book_count });
const group = (wire: WireGroup): LibraryGroup => ({ id: String(wire.id), name: wire.name, description: wire.description, isPublicGroup: wire.is_public_group === true, previewBooks: (wire.preview_books ?? []).map(preview) });
const page = <W, T>(wire: WirePage<W>, project: (value: W) => T): PaginatedResponse<T> => ({ ...wire, results: (wire.results ?? []).map(project) });

function addPageParams(url: URL, params: { page?: number; pageSize?: number }): void {
  if (params.page !== undefined) url.searchParams.set("page", String(params.page));
  if (params.pageSize !== undefined) url.searchParams.set("page_size", String(params.pageSize));
}
function addBookParams(url: URL, params: BookListParams): void {
  if (params.q !== undefined) url.searchParams.set("q", params.q);
  if (params.author !== undefined) url.searchParams.set("author", params.author);
  if (params.series !== undefined) url.searchParams.set("series", params.series);
  if (params.tag !== undefined) url.searchParams.set("tag", params.tag);
  if (params.publisher !== undefined) url.searchParams.set("publisher", params.publisher);
  if (params.ordering !== undefined) url.searchParams.set("ordering", params.ordering);
  if (params.excludeGroup !== undefined) url.searchParams.set("exclude_group", params.excludeGroup);
  addPageParams(url, params);
}
function addSearchParams(url: URL, params: SearchParams): void {
  if (params.q !== undefined) url.searchParams.set("q", params.q);
  if (params.ordering !== undefined) url.searchParams.set("ordering", params.ordering);
  if (params.excludeShelf !== undefined) url.searchParams.set("exclude_shelf", params.excludeShelf);
  if (params.excludeGroup !== undefined) url.searchParams.set("exclude_group", params.excludeGroup);
  addPageParams(url, params);
}
function addPreviewParams(url: URL, params: EntityPreviewParams): void {
  if (params.includePreviewBooks !== undefined) url.searchParams.set("include_preview_books", String(params.includePreviewBooks));
  if (params.previewLimit !== undefined) url.searchParams.set("preview_limit", String(params.previewLimit));
}
function addEntityParams(url: URL, params: EntityListParams, allowExcludeId: boolean): void {
  if (params.q !== undefined) url.searchParams.set("q", params.q);
  if (params.tag !== undefined) url.searchParams.set("tag", params.tag);
  if (params.ordering !== undefined) url.searchParams.set("ordering", params.ordering);
  if (allowExcludeId && params.excludeId !== undefined) url.searchParams.set("exclude_id", params.excludeId);
  addPreviewParams(url, params);
  addPageParams(url, params);
}
function addTagParams(url: URL, params: TagListParams): void {
  if (params.q !== undefined) url.searchParams.set("q", params.q);
  if (params.ordering !== undefined) url.searchParams.set("ordering", params.ordering);
  addPageParams(url, params);
}
function addGroupParams(url: URL, params: GroupListParams): void {
  if (params.q !== undefined) url.searchParams.set("q", params.q);
  if (params.book !== undefined) url.searchParams.set("book", params.book);
  if (params.ordering !== undefined) url.searchParams.set("ordering", params.ordering);
  addPreviewParams(url, params);
  addPageParams(url, params);
}

async function list<W, T>(ctx: AuthenticatedClientContext, path: string, configure: (url: URL) => void, project: (wire: W) => T): Promise<PaginatedResponse<T>> {
  const url = new URL(resolveUrl(ctx.apiBaseUrl, path));
  configure(url);
  const wire = await requestJson<WirePage<W>>({ apiBaseUrl: ctx.apiBaseUrl, accessToken: ctx.accessToken, tokenType: ctx.tokenType, endpointOrUrl: url.toString(), options: { errorMessages: authErrorMessages({ forbidden: LIBRARY_FORBIDDEN_403 }) } });
  return page(wire, project);
}
async function get<W, T>(ctx: AuthenticatedClientContext, path: string, notFound: string, project: (wire: W) => T, configure?: (url: URL) => void): Promise<T> {
  const url = new URL(resolveUrl(ctx.apiBaseUrl, path));
  configure?.(url);
  const wire = await requestJson<W>({ apiBaseUrl: ctx.apiBaseUrl, accessToken: ctx.accessToken, tokenType: ctx.tokenType, endpointOrUrl: url.toString(), options: { errorMessages: authErrorMessages({ forbidden: LIBRARY_FORBIDDEN_403, notFound }) } });
  return project(wire);
}

export const listBooks = (ctx: AuthenticatedClientContext, params: BookListParams = {}) => list(ctx, "/library/books/", (url) => addBookParams(url, params), compactBook);
export const searchBooks = (ctx: AuthenticatedClientContext, params: SearchParams = {}) => list(ctx, "/library/search", (url) => addSearchParams(url, params), compactBook);
export const getBook = (ctx: AuthenticatedClientContext, bookId: string) => get(ctx, `/library/books/${encodeURIComponent(bookId)}/`, "Book not found or not accessible (404).", bookDetail);
export const listSeries = (ctx: AuthenticatedClientContext, params: EntityListParams = {}) => list(ctx, "/library/series/", (url) => addEntityParams(url, params, true), series);
export const getSeries = (ctx: AuthenticatedClientContext, seriesId: string, params: EntityPreviewParams = {}) => get(ctx, `/library/series/${encodeURIComponent(seriesId)}/`, "Series not found or not accessible (404).", series, (url) => addPreviewParams(url, params));
export const listAuthors = (ctx: AuthenticatedClientContext, params: EntityListParams = {}) => list(ctx, "/library/authors/", (url) => addEntityParams(url, params, true), author);
export const getAuthor = (ctx: AuthenticatedClientContext, authorId: string, params: EntityPreviewParams = {}) => get(ctx, `/library/authors/${encodeURIComponent(authorId)}/`, "Author not found or not accessible (404).", author, (url) => addPreviewParams(url, params));
export const listTags = (ctx: AuthenticatedClientContext, params: TagListParams = {}) => list(ctx, "/library/tags/", (url) => addTagParams(url, params), tag);
export const getTag = (ctx: AuthenticatedClientContext, tagId: string) => get(ctx, `/library/tags/${encodeURIComponent(tagId)}/`, "Catalog tag not found or not accessible (404).", tag);
export const listGroups = (ctx: AuthenticatedClientContext, params: GroupListParams = {}) => list(ctx, "/library/groups/", (url) => addGroupParams(url, params), group);
export const getGroup = (ctx: AuthenticatedClientContext, groupId: string, params: EntityPreviewParams = {}) => get(ctx, `/library/groups/${encodeURIComponent(groupId)}/`, "Library group not found or not accessible (404).", group, (url) => addPreviewParams(url, params));
export const listGroupBooks = (ctx: AuthenticatedClientContext, groupId: string, params: BookListParams & { excludeShelf?: string } = {}) => list(ctx, `/library/groups/${encodeURIComponent(groupId)}/books/`, (url) => { addBookParams(url, params); if (params.excludeShelf !== undefined) url.searchParams.set("exclude_shelf", params.excludeShelf); }, compactBook);
export const listGroupAuthors = (ctx: AuthenticatedClientContext, groupId: string, params: Omit<EntityListParams, "excludeId"> = {}) => list(ctx, `/library/groups/${encodeURIComponent(groupId)}/authors/`, (url) => addEntityParams(url, params, false), author);
export const listGroupSeries = (ctx: AuthenticatedClientContext, groupId: string, params: Omit<EntityListParams, "excludeId"> = {}) => list(ctx, `/library/groups/${encodeURIComponent(groupId)}/series/`, (url) => addEntityParams(url, params, false), series);
export const listGroupTags = (ctx: AuthenticatedClientContext, groupId: string, params: TagListParams = {}) => list(ctx, `/library/groups/${encodeURIComponent(groupId)}/tags/`, (url) => addTagParams(url, params), tag);

export async function downloadBookFile(ctx: AuthenticatedClientContext, bookId: string): Promise<BookFileDownloadResult> {
  const { blob, response } = await requestBlob({ apiBaseUrl: ctx.apiBaseUrl, accessToken: ctx.accessToken, tokenType: ctx.tokenType, endpointOrUrl: resolveUrl(ctx.apiBaseUrl, `/library/books/${encodeURIComponent(bookId)}/download/`), options: { accept: "application/epub+zip, application/octet-stream, */*", errorMessages: authErrorMessages({ forbidden: LIBRARY_FILE_DOWNLOAD_FORBIDDEN_403 }) } });
  const contentType = response.headers.get("content-type") ?? undefined;
  const contentDisposition = response.headers.get("content-disposition") ?? undefined;
  const contentLengthRaw = response.headers.get("content-length");
  const contentLength = contentLengthRaw ? Number(contentLengthRaw) : undefined;
  return { blob, contentType, contentLength: Number.isFinite(contentLength) ? contentLength : undefined, contentDisposition, filename: contentDisposition ? tryParseFilename(contentDisposition) : undefined };
}
