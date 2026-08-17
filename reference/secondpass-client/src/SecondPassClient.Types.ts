import type {
  ClientApiConsumeResponse,
  ClientApiLoginRequestResponse,
  ClientApiPollResponse,
  SecondPassDiscovery,
} from "./schemas/ClientApiAuth.Types";
import type { CurrentUser } from "./schemas/Account.Types";
import type { ServerInfo } from "./schemas/Server.Types";
import type {
  Author,
  BookDetail,
  CatalogTag,
  CompactBook,
  LibraryGroup,
  PaginatedResponse,
  Series,
} from "./schemas/Library.Types";
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
import type {
  MarginaliaAnnotationBatchOperation,
  MarginaliaAnnotationCollection,
  MarginaliaBookSessions,
  MarginaliaBookSummary,
  MarginaliaBootstrap,
  MarginaliaProgress,
  MarginaliaProgressInput,
  MarginaliaRecentSessions,
  MarginaliaSessionDetail,
  MarginaliaSessionFinalizeInput,
  MarginaliaSessionListItem,
  MarginaliaSessionMetadataInput,
  MarginaliaSessionStatus,
} from "./schemas/Marginalia.Types";

export type SecondPassClientConfig = {
  apiBaseUrl: string;
  accessToken?: string;
  tokenType?: string;
};

export type LibraryBookListParams = {
  q?: string;
  series?: string;
  author?: string;
  tag?: string;
  publisher?: string;
  ordering?: "title" | "-title" | "author" | "-author" | "series" | "-series" | "series_index" | "-series_index" | "publisher" | "-publisher";
  page?: number;
  pageSize?: number;
  excludeGroup?: string;
};

export type LibrarySearchParams = {
  q?: string;
  ordering?: "title" | "-title" | "author" | "-author" | "series" | "-series";
  excludeShelf?: string;
  excludeGroup?: string;
  page?: number;
  pageSize?: number;
};

export type LibraryEntityListParams = {
  q?: string;
  tag?: string;
  page?: number;
  pageSize?: number;
  includePreviewBooks?: boolean;
  previewLimit?: number;
  excludeId?: string;
  ordering?: "name" | "-name" | "book_count" | "-book_count";
};

export type LibraryTagListParams = {
  q?: string;
  ordering?: "name" | "-name" | "book_count" | "-book_count";
  page?: number;
  pageSize?: number;
};

export type LibraryGroupListParams = {
  q?: string;
  ordering?: "name" | "-name";
  page?: number;
  pageSize?: number;
  includePreviewBooks?: boolean;
  previewLimit?: number;
  book?: string;
};

export type LibraryPreviewParams = { includePreviewBooks?: boolean; previewLimit?: number };
export type LibraryGroupBookListParams = LibraryBookListParams & { excludeShelf?: string };
export type LibraryGroupEntityListParams = Omit<LibraryEntityListParams, "excludeId">;

export type SecondPassClient = {
  readonly config: Readonly<SecondPassClientConfig>;

  server: {
    discover(serverBaseUrl: string): Promise<SecondPassDiscovery>;
    info(): Promise<ServerInfo>;
    createLoginRequest(
      discovery: SecondPassDiscovery,
      input?: { clientName?: string; clientType?: string },
    ): Promise<ClientApiLoginRequestResponse>;
    pollLoginRequest(pollUrl: string): Promise<ClientApiPollResponse>;
    consumeLoginRequest(consumeUrl: string): Promise<ClientApiConsumeResponse>;
  };

  account: {
    getCurrentUser(): Promise<CurrentUser>;
  };

  library: {
    search(params?: LibrarySearchParams): Promise<PaginatedResponse<CompactBook>>;
    books: {
      list(params?: LibraryBookListParams): Promise<PaginatedResponse<CompactBook>>;
      get(bookId: string): Promise<BookDetail>;
      /**
       * Returns a server-provided download URL for deliberate URL workflows.
       *
       * Normal app flows should prefer `download()` to avoid passing URLs around.
       */
      getDownloadUrl(book: CompactBook | BookDetail | string | number): Promise<string>;
      /**
       * Download the backing book file bytes (format-neutral).
       *
       * Server convention: 1 book === 1 file.
       */
      download(book: CompactBook | BookDetail | string | number): Promise<Blob>;
    };
    series: {
      list(params?: LibraryEntityListParams): Promise<PaginatedResponse<Series>>;
      get(seriesId: string, params?: LibraryPreviewParams): Promise<Series>;
    };
    authors: {
      list(params?: LibraryEntityListParams): Promise<PaginatedResponse<Author>>;
      get(authorId: string, params?: LibraryPreviewParams): Promise<Author>;
    };
    tags: {
      list(params?: LibraryTagListParams): Promise<PaginatedResponse<CatalogTag>>;
      get(tagId: string): Promise<CatalogTag>;
    };
    groups: {
      list(params?: LibraryGroupListParams): Promise<PaginatedResponse<LibraryGroup>>;
      get(groupId: string, params?: LibraryPreviewParams): Promise<LibraryGroup>;
      books(groupId: string, params?: LibraryGroupBookListParams): Promise<PaginatedResponse<CompactBook>>;
      authors(groupId: string, params?: LibraryGroupEntityListParams): Promise<PaginatedResponse<Author>>;
      series(groupId: string, params?: LibraryGroupEntityListParams): Promise<PaginatedResponse<Series>>;
      tags(groupId: string, params?: LibraryTagListParams): Promise<PaginatedResponse<CatalogTag>>;
    };
  };

  shelves: {
    list(params?: ShelfListParams): Promise<PaginatedShelfResponse>;
    create(input: CreateShelfInput): Promise<Shelf>;
    get(shelfId: string, params?: { includePreviewBooks?: boolean }): Promise<Shelf>;
    update(shelfId: string, input: UpdateShelfInput): Promise<Shelf>;
    remove(shelfId: string): Promise<void>;
    items(shelfId: string, params?: { page?: number; pageSize?: number; ordering?: string }): Promise<PaginatedShelfItemResponse>;
    addItem(shelfId: string, input: AddShelfItemInput): Promise<ShelfItem>;
    updateItem(shelfId: string, itemId: string, input: UpdateShelfItemInput): Promise<ShelfItem>;
    removeItem(shelfId: string, itemId: string): Promise<void>;
  };

  marginalia: {
    books: {
      list(params?: { page?: number; pageSize?: number }): Promise<PaginatedResponse<MarginaliaBookSummary>>;
      get(bookId: string): Promise<MarginaliaBookSummary>;
      sessions(bookId: string, params?: { status?: MarginaliaSessionStatus; q?: string; page?: number; pageSize?: number }): Promise<MarginaliaBookSessions>;
      open(bookId: string, input?: MarginaliaSessionMetadataInput): Promise<MarginaliaBootstrap>;
      getActiveSession(bookId: string): Promise<MarginaliaBootstrap>;
      startOver(bookId: string, input: MarginaliaSessionFinalizeInput | undefined, options: { idempotencyKey: string }): Promise<MarginaliaBootstrap>;
    };
    sessions: {
      list(params?: { status?: MarginaliaSessionStatus; q?: string; hasAnnotations?: boolean; page?: number; pageSize?: number }): Promise<PaginatedResponse<MarginaliaSessionListItem>>;
      recent(params?: { limit?: number; includeClosed?: boolean }): Promise<MarginaliaRecentSessions>;
      get(sessionId: string): Promise<MarginaliaSessionDetail>;
      update(sessionId: string, input: MarginaliaSessionMetadataInput): Promise<MarginaliaSessionDetail>;
      close(sessionId: string, input?: MarginaliaSessionFinalizeInput): Promise<MarginaliaSessionDetail>;
      getProgress(sessionId: string): Promise<{ progress: MarginaliaProgress | null }>;
      replaceProgress(sessionId: string, input: MarginaliaProgressInput): Promise<{ progress: MarginaliaProgress }>;
      getAnnotations(sessionId: string): Promise<MarginaliaAnnotationCollection>;
      batchAnnotations(sessionId: string, operations: MarginaliaAnnotationBatchOperation[]): Promise<MarginaliaAnnotationCollection>;
    };
  };
};
