export type PaginatedResponse<T> = {
  count: number;
  next: string | null;
  previous: string | null;
  results: T[];
};

export type PreviewBook = {
  id: string;
  title: string;
  coverUrl: string | null;
};

export type AuthorSummary = {
  id: string;
  name: string;
};

export type SeriesSummary = {
  id: string;
  name: string;
  sortName: string;
  seriesIndex: string | null;
};

export type CatalogTagSummary = {
  id: string;
  name: string;
  slug: string;
};

export type CatalogTag = CatalogTagSummary & {
  bookCount: number;
};

export type BookIdentifierScheme =
  | "isbn_10" | "isbn_13" | "asin" | "doi" | "oclc" | "lccn"
  | "openlibrary" | "calibre" | "epub_uid" | "publisher" | "uri"
  | "uuid" | "other";

export type BookIdentifier = {
  id: string;
  scheme: BookIdentifierScheme;
  value: string;
};

export type BookFile = {
  format: string;
  fileSize: number;
  checksum: string;
  downloadUrl: string;
};

export type BookGroup = {
  id: string;
  name: string;
  description: string;
  isPublicGroup: boolean;
};

export type CompactBook = {
  id: string;
  title: string;
  sortTitle: string;
  subtitle: string;
  authors: AuthorSummary[];
  series: SeriesSummary | null;
  catalogTags: CatalogTagSummary[];
  language: string | null;
  publisher: string | null;
  publishedYear: number | null;
  publishedMonth: number | null;
  publishedDay: number | null;
  publishedDatePrecision: "" | "year" | "month" | "day";
  coverUrl: string | null;
  fileFormat: string;
};

export type BookDetail = Omit<CompactBook, "fileFormat"> & {
  description: string;
  identifiers: BookIdentifier[];
  file: BookFile | null;
  groups: BookGroup[];
};

export type Series = {
  id: string;
  name: string;
  sortName: string;
  summary: string;
  bookCount: number;
  previewBooks: PreviewBook[];
};

export type Author = {
  id: string;
  name: string;
  sortName: string;
  biography: string;
  bookCount: number;
  previewBooks: PreviewBook[];
};

export type LibraryGroup = {
  id: string;
  name: string;
  description: string;
  isPublicGroup: boolean;
  previewBooks: PreviewBook[];
};

export type BookFileDownloadResult = {
  blob: Blob;
  contentType?: string;
  contentLength?: number;
  contentDisposition?: string;
  filename?: string;
};
