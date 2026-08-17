import type { PaginatedResponse } from "./Library.Types";

export type MarginaliaAuthor = { id: string; name: string };

export type MarginaliaBookSummary = {
  id: string;
  title: string;
  authors: MarginaliaAuthor[];
  series: { id: string; name: string; seriesIndex: string | null } | null;
  coverUrl: string | null;
  canOpen: boolean;
  sessionCount: number;
  activeSessionCount: number;
  lastActivityAt: string | null;
};

export type BoundedSessionBook = {
  id: string;
  title: string;
  coverUrl: string | null;
  canOpen: boolean;
};

export type MarginaliaProgress = {
  cfi: string;
  locationLabel: string;
  updatedAt: string;
};

export type MarginaliaSessionStatus = "active" | "closed";

export type MarginaliaSessionSummary = {
  id: string;
  name: string;
  notes: string;
  status: MarginaliaSessionStatus;
  startedAt: string;
  closedAt: string | null;
  updatedAt: string;
  lastActivityAt: string;
  annotationCount: number;
};

export type MarginaliaSession = MarginaliaSessionSummary & {
  progress: MarginaliaProgress | null;
};

export type MarginaliaSessionListItem = MarginaliaSessionSummary & {
  book: BoundedSessionBook;
};

export type MarginaliaSessionDetail = {
  context: { book: BoundedSessionBook };
  session: MarginaliaSession;
};

export type MarginaliaBookSessions = PaginatedResponse<MarginaliaSessionSummary> & {
  context: { book: BoundedSessionBook };
};

export type MarginaliaRecentSession = {
  id: string;
  name: string;
  status: MarginaliaSessionStatus;
  lastActivityAt: string;
  book: BoundedSessionBook;
  progress: MarginaliaProgress | null;
};

export type MarginaliaRecentSessions = { results: MarginaliaRecentSession[] };

export type MarginaliaAnnotationLocation = { cfi: string; locationLabel: string };
export type MarginaliaHighlightColor = "yellow" | "green" | "blue" | "pink" | "purple" | "orange";

export type MarginaliaHighlight = {
  id: string;
  clientId: string;
  kind: "highlight";
  location: MarginaliaAnnotationLocation;
  body: {
    text: string;
    prefix: string;
    suffix: string;
    color: MarginaliaHighlightColor;
    note: string;
  };
  createdAt: string;
  updatedAt: string;
};

export type MarginaliaBookmark = {
  id: string;
  clientId: string;
  kind: "bookmark";
  location: MarginaliaAnnotationLocation;
  createdAt: string;
  updatedAt: string;
};

export type MarginaliaAnnotation = MarginaliaHighlight | MarginaliaBookmark;
export type MarginaliaAnnotationCollection = { annotations: MarginaliaAnnotation[] };

export type MarginaliaBootstrap = {
  created: boolean;
  context: { book: BoundedSessionBook };
  session: MarginaliaSession | null;
  annotations: MarginaliaAnnotation[];
  closedSessions: PaginatedResponse<MarginaliaSessionSummary>;
};

export type MarginaliaProgressInput = { cfi: string; locationLabel?: string };
export type MarginaliaSessionMetadataInput = { name?: string; notes?: string };
export type MarginaliaSessionFinalizeInput = MarginaliaSessionMetadataInput & { progress?: MarginaliaProgressInput };

export type MarginaliaHighlightUpsert = {
  clientId: string;
  kind: "highlight";
  location: { cfi: string; locationLabel?: string };
  body: {
    text: string;
    prefix?: string;
    suffix?: string;
    color?: MarginaliaHighlightColor;
    note?: string;
  };
};

export type MarginaliaBookmarkUpsert = {
  clientId: string;
  kind: "bookmark";
  location: { cfi: string; locationLabel?: string };
};

export type MarginaliaAnnotationBatchOperation =
  | { action: "upsert"; annotation: MarginaliaHighlightUpsert | MarginaliaBookmarkUpsert }
  | { action: "delete"; clientId: string };
