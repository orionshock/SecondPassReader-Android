export { ApiError, type ApiErrorKind } from "./ApiHttp.Adapter";
export { createSecondPassClient } from "./SecondPassClient.Factory";
export type {
  SecondPassClient,
  SecondPassClientConfig,
  LibraryBookListParams,
  LibrarySearchParams,
  LibraryEntityListParams,
  LibraryTagListParams,
  LibraryGroupListParams,
  LibraryPreviewParams,
  LibraryGroupBookListParams,
  LibraryGroupEntityListParams,
} from "./SecondPassClient.Types";

export type {
  ClientApiConsumeResponse,
  ClientApiLoginRequestResponse,
  ClientApiPollResponse,
  SecondPassDiscovery,
} from "./schemas/ClientApiAuth.Types";
export type { CurrentUser, CurrentUserGroup, MePayload } from "./schemas/Account.Types";
export type { ServerInfo, ServerPublicGroup } from "./schemas/Server.Types";

export type { BookDetail, BookIdentifierScheme, CatalogTag, CatalogTagSummary, CompactBook, Author, AuthorSummary, BookFile, BookGroup, BookIdentifier, LibraryGroup, Series, SeriesSummary, PaginatedResponse, PreviewBook } from "./schemas/Library.Types";

export type {
  BoundedSessionBook,
  MarginaliaAnnotation,
  MarginaliaAnnotationBatchOperation,
  MarginaliaAnnotationCollection,
  MarginaliaAnnotationLocation,
  MarginaliaBookSessions,
  MarginaliaBookSummary,
  MarginaliaBookmark,
  MarginaliaBookmarkUpsert,
  MarginaliaBootstrap,
  MarginaliaHighlight,
  MarginaliaHighlightColor,
  MarginaliaHighlightUpsert,
  MarginaliaProgress,
  MarginaliaProgressInput,
  MarginaliaRecentSession,
  MarginaliaRecentSessions,
  MarginaliaSession,
  MarginaliaSessionDetail,
  MarginaliaSessionFinalizeInput,
  MarginaliaSessionListItem,
  MarginaliaSessionMetadataInput,
  MarginaliaSessionSummary,
  MarginaliaSessionStatus,
} from "./schemas/Marginalia.Types";

export type {
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
