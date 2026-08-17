import { authErrorMessages, requestJson, resolveUrl } from "./ApiHttp.Adapter";
import type { AuthenticatedClientContext } from "./ClientContext.Policy";
import type { PaginatedResponse } from "./schemas/Library.Types";
import type {
  BoundedSessionBook,
  MarginaliaAnnotation,
  MarginaliaAnnotationBatchOperation,
  MarginaliaAnnotationCollection,
  MarginaliaBookSessions,
  MarginaliaBookSummary,
  MarginaliaBootstrap,
  MarginaliaHighlightColor,
  MarginaliaProgress,
  MarginaliaProgressInput,
  MarginaliaRecentSessions,
  MarginaliaSession,
  MarginaliaSessionDetail,
  MarginaliaSessionFinalizeInput,
  MarginaliaSessionListItem,
  MarginaliaSessionMetadataInput,
  MarginaliaSessionSummary,
  MarginaliaSessionStatus,
} from "./schemas/Marginalia.Types";

type WireBook = { id: string; title: string; cover_url: string | null; can_open: boolean };
type WireProgress = { cfi: string; location_label: string; updated_at: string };
type WireSession = {
  id: string; name: string; notes: string; status: MarginaliaSessionStatus; started_at: string; closed_at: string | null;
  updated_at: string; last_activity_at: string; annotation_count: number; progress?: WireProgress | null; book?: WireBook;
};
type WireAnnotation = {
  id: string; client_id: string; kind: "highlight" | "bookmark";
  location: { cfi: string; location_label: string };
  body?: { text: string; prefix: string; suffix: string; color: MarginaliaHighlightColor; note: string };
  created_at: string; updated_at: string;
};
type WirePage<T> = { count: number; next: string | null; previous: string | null; results: T[] };
type WireBookSummary = WireBook & {
  authors: Array<{ id: string; name: string }>;
  series: { id: string; name: string; series_index: string | null } | null;
  session_count: number;
  active_session_count: number;
  last_activity_at: string | null;
};
type WireSessionDetail = { context: { book: WireBook }; session: WireSession };
type WireBootstrap = {
  created: boolean;
  context: { book: WireBook };
  session: WireSession | null;
  annotations: WireAnnotation[];
  closed_sessions: WirePage<WireSession>;
};

const forbidden = authErrorMessages({ forbidden: "Token is not allowed to access marginalia (403)." });
const url = (ctx: AuthenticatedClientContext, path: string) => resolveUrl(ctx.apiBaseUrl, `/marginalia${path}`);
const page = <T, R>(value: WirePage<T>, project: (item: T) => R): PaginatedResponse<R> => ({
  count: value.count, next: value.next, previous: value.previous, results: value.results.map(project),
});
const book = (value: WireBook): BoundedSessionBook => ({ id: String(value.id), title: value.title, coverUrl: value.cover_url, canOpen: value.can_open });
const progress = (value: WireProgress): MarginaliaProgress => ({ cfi: value.cfi, locationLabel: value.location_label, updatedAt: value.updated_at });
const sessionSummary = (value: WireSession): MarginaliaSessionSummary => ({
  id: String(value.id), name: value.name, notes: value.notes, status: value.status, startedAt: value.started_at,
  closedAt: value.closed_at, updatedAt: value.updated_at, lastActivityAt: value.last_activity_at, annotationCount: value.annotation_count,
});
const session = (value: WireSession): MarginaliaSession => ({ ...sessionSummary(value), progress: value.progress ? progress(value.progress) : null });
const annotation = (value: WireAnnotation): MarginaliaAnnotation => {
  const common = { id: String(value.id), clientId: value.client_id, location: { cfi: value.location.cfi, locationLabel: value.location.location_label }, createdAt: value.created_at, updatedAt: value.updated_at };
  if (value.kind === "bookmark") return { ...common, kind: "bookmark" };
  if (!value.body) throw new Error("Marginalia highlight response is missing its body.");
  return { ...common, kind: "highlight", body: { ...value.body } };
};
const projectBookSummary = (value: WireBookSummary): MarginaliaBookSummary => ({
  id: String(value.id),
  title: value.title,
  authors: value.authors.map((author) => ({ id: String(author.id), name: author.name })),
  series: value.series
    ? { id: String(value.series.id), name: value.series.name, seriesIndex: value.series.series_index }
    : null,
  coverUrl: value.cover_url,
  canOpen: value.can_open,
  sessionCount: value.session_count,
  activeSessionCount: value.active_session_count,
  lastActivityAt: value.last_activity_at,
});
const projectDetail = (value: WireSessionDetail): MarginaliaSessionDetail => ({ context: { book: book(value.context.book) }, session: session(value.session) });
const projectBootstrap = (value: WireBootstrap): MarginaliaBootstrap => ({
  created: value.created, context: { book: book(value.context.book) }, session: value.session ? session(value.session) : null,
  annotations: value.annotations.map(annotation), closedSessions: page(value.closed_sessions, sessionSummary),
});
const progressBody = (value: MarginaliaProgressInput) => ({ cfi: value.cfi, ...(value.locationLabel !== undefined ? { location_label: value.locationLabel } : {}) });
const finalizeBody = (value?: MarginaliaSessionFinalizeInput) => value ? ({ ...("name" in value ? { name: value.name } : {}), ...("notes" in value ? { notes: value.notes } : {}), ...(value.progress ? { progress: progressBody(value.progress) } : {}) }) : {};
const get = <T>(ctx: AuthenticatedClientContext, endpointOrUrl: string) => requestJson<T>({ apiBaseUrl: ctx.apiBaseUrl, accessToken: ctx.accessToken, tokenType: ctx.tokenType, endpointOrUrl, options: { errorMessages: forbidden } });
const send = <T>(ctx: AuthenticatedClientContext, endpointOrUrl: string, method: "POST" | "PUT" | "PATCH", bodyValue: unknown, headers?: Record<string, string>) => requestJson<T>({ apiBaseUrl: ctx.apiBaseUrl, accessToken: ctx.accessToken, tokenType: ctx.tokenType, endpointOrUrl, options: { method, body: bodyValue, headers, errorMessages: forbidden } });

export async function listMarginaliaBooks(ctx: AuthenticatedClientContext, input?: { page?: number; pageSize?: number }): Promise<PaginatedResponse<MarginaliaBookSummary>> {
  const target = new URL(url(ctx, "/books/")); if (input?.page !== undefined) target.searchParams.set("page", String(input.page)); if (input?.pageSize !== undefined) target.searchParams.set("page_size", String(input.pageSize));
  const wire = await get<WirePage<WireBookSummary>>(ctx, target.toString());
  return page(wire, projectBookSummary);
}
export async function getMarginaliaBook(ctx: AuthenticatedClientContext, bookId: string): Promise<MarginaliaBookSummary> { const result = await listMarginaliaBooksProjection(ctx, url(ctx, `/books/${encodeURIComponent(bookId)}/`)); return result; }
async function listMarginaliaBooksProjection(ctx: AuthenticatedClientContext, target: string): Promise<MarginaliaBookSummary> { return projectBookSummary(await get<WireBookSummary>(ctx, target)); }
export async function listBookSessions(ctx: AuthenticatedClientContext, bookId: string, input?: { status?: MarginaliaSessionStatus; q?: string; page?: number; pageSize?: number }): Promise<MarginaliaBookSessions> { const target = new URL(url(ctx, `/books/${encodeURIComponent(bookId)}/sessions/`)); setSessionQuery(target, input); const value = await get<WirePage<WireSession> & { context: { book: WireBook } }>(ctx, target.toString()); return { context: { book: book(value.context.book) }, ...page(value, sessionSummary) }; }
export async function openMarginaliaBook(ctx: AuthenticatedClientContext, bookId: string, input?: MarginaliaSessionMetadataInput): Promise<MarginaliaBootstrap> { return projectBootstrap(await send<WireBootstrap>(ctx, url(ctx, `/books/${encodeURIComponent(bookId)}/open/`), "POST", input ?? {})); }
export async function getActiveMarginaliaSession(ctx: AuthenticatedClientContext, bookId: string): Promise<MarginaliaBootstrap> { return projectBootstrap(await get<WireBootstrap>(ctx, url(ctx, `/books/${encodeURIComponent(bookId)}/active-session/`))); }
export async function startOverMarginaliaBook(ctx: AuthenticatedClientContext, bookId: string, input: MarginaliaSessionFinalizeInput | undefined, idempotencyKey: string): Promise<MarginaliaBootstrap> { const key = idempotencyKey.trim(); if (!key || key.length > 128 || /[\u0000-\u001f\u007f]/.test(key)) throw new Error("startOver requires a valid idempotencyKey."); return projectBootstrap(await send<WireBootstrap>(ctx, url(ctx, `/books/${encodeURIComponent(bookId)}/start-over/`), "POST", finalizeBody(input), { "Idempotency-Key": key })); }

function setSessionQuery(target: URL, input?: { status?: MarginaliaSessionStatus; q?: string; hasAnnotations?: boolean; page?: number; pageSize?: number }) { if (input?.status !== undefined) target.searchParams.set("status", input.status); if (input?.q !== undefined) target.searchParams.set("q", input.q); if (input?.hasAnnotations !== undefined) target.searchParams.set("has_annotations", input.hasAnnotations ? "true" : "false"); if (input?.page !== undefined) target.searchParams.set("page", String(input.page)); if (input?.pageSize !== undefined) target.searchParams.set("page_size", String(input.pageSize)); }
export async function listMarginaliaSessions(ctx: AuthenticatedClientContext, input?: { status?: MarginaliaSessionStatus; q?: string; hasAnnotations?: boolean; page?: number; pageSize?: number }): Promise<PaginatedResponse<MarginaliaSessionListItem>> { const target = new URL(url(ctx, "/sessions/")); setSessionQuery(target, input); const value = await get<WirePage<WireSession>>(ctx, target.toString()); return page(value, (row) => ({ ...sessionSummary(row), book: book(row.book!) })); }
export async function listRecentMarginaliaSessions(ctx: AuthenticatedClientContext, input?: { limit?: number; includeClosed?: boolean }): Promise<MarginaliaRecentSessions> { const target = new URL(url(ctx, "/sessions/recent/")); if (input?.limit !== undefined) target.searchParams.set("limit", String(input.limit)); if (input?.includeClosed !== undefined) target.searchParams.set("include_closed", input.includeClosed ? "true" : "false"); const value = await get<{ results: Array<Pick<WireSession, "id" | "name" | "status" | "last_activity_at" | "progress"> & { book: WireBook }> }>(ctx, target.toString()); return { results: value.results.map((row) => ({ id: String(row.id), name: row.name, status: row.status, lastActivityAt: row.last_activity_at, book: book(row.book), progress: row.progress ? progress(row.progress) : null })) }; }
export async function getMarginaliaSession(ctx: AuthenticatedClientContext, sessionId: string): Promise<MarginaliaSessionDetail> { return projectDetail(await get<WireSessionDetail>(ctx, url(ctx, `/sessions/${encodeURIComponent(sessionId)}/`))); }
export async function updateMarginaliaSession(ctx: AuthenticatedClientContext, sessionId: string, input: MarginaliaSessionMetadataInput): Promise<MarginaliaSessionDetail> { return projectDetail(await send<WireSessionDetail>(ctx, url(ctx, `/sessions/${encodeURIComponent(sessionId)}/`), "PATCH", input)); }
export async function closeMarginaliaSession(ctx: AuthenticatedClientContext, sessionId: string, input?: MarginaliaSessionFinalizeInput): Promise<MarginaliaSessionDetail> { return projectDetail(await send<WireSessionDetail>(ctx, url(ctx, `/sessions/${encodeURIComponent(sessionId)}/close/`), "POST", finalizeBody(input))); }
export async function getMarginaliaProgress(ctx: AuthenticatedClientContext, sessionId: string): Promise<{ progress: MarginaliaProgress | null }> { const value = await get<{ progress: WireProgress | null }>(ctx, url(ctx, `/sessions/${encodeURIComponent(sessionId)}/progress/`)); return { progress: value.progress ? progress(value.progress) : null }; }
export async function replaceMarginaliaProgress(ctx: AuthenticatedClientContext, sessionId: string, input: MarginaliaProgressInput): Promise<{ progress: MarginaliaProgress }> { const value = await send<{ progress: WireProgress }>(ctx, url(ctx, `/sessions/${encodeURIComponent(sessionId)}/progress/`), "PUT", progressBody(input)); return { progress: progress(value.progress) }; }
export async function getMarginaliaAnnotations(ctx: AuthenticatedClientContext, sessionId: string): Promise<MarginaliaAnnotationCollection> { const value = await get<{ annotations: WireAnnotation[] }>(ctx, url(ctx, `/sessions/${encodeURIComponent(sessionId)}/annotations/`)); return { annotations: value.annotations.map(annotation) }; }
export async function batchMarginaliaAnnotations(ctx: AuthenticatedClientContext, sessionId: string, operations: MarginaliaAnnotationBatchOperation[]): Promise<MarginaliaAnnotationCollection> { if (operations.length < 1 || operations.length > 100) throw new Error("Annotation batch requires 1-100 operations."); const mapped = operations.map((op) => op.action === "delete" ? { action: "delete", client_id: op.clientId } : { action: "upsert", annotation: { client_id: op.annotation.clientId, kind: op.annotation.kind, location: { cfi: op.annotation.location.cfi, ...(op.annotation.location.locationLabel !== undefined ? { location_label: op.annotation.location.locationLabel } : {}) }, ...(op.annotation.kind === "highlight" ? { body: op.annotation.body } : {}) } }); const value = await send<{ annotations: WireAnnotation[] }>(ctx, url(ctx, `/sessions/${encodeURIComponent(sessionId)}/annotations/batch/`), "POST", { operations: mapped }); return { annotations: value.annotations.map(annotation) }; }
