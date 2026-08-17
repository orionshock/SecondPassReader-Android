import type { PaginatedResponse } from "./Library.Types";
type ShelfPreviewBook = { id: string; title: string; cover_url: string | null };
type ShelfAuthorSummary = { id: string | number; name: string };
type ShelfSeriesSummary = { id: string | number; name: string; sort_name?: string; series_index?: string | number | null };

export type ShelfOwnerType = "user" | "group" | string;
export type ShelfVisibility = "private" | "listed" | string;

export type ShelfOwnerUserSummary = {
  profile_id: string;
  username?: string | null;
  first_name?: string | null;
  last_name?: string | null;
  [k: string]: unknown;
};

export type ShelfOwnerGroupSummary = {
  id: string | number;
  name: string;
  is_public_group?: boolean;
  [k: string]: unknown;
};

export type Shelf = {
  id: string;
  name: string;
  description?: string | null;
  owner_type: ShelfOwnerType;
  owner_user?: ShelfOwnerUserSummary | null;
  owner_group?: ShelfOwnerGroupSummary | null;
  visibility?: ShelfVisibility | null;
  item_count?: number | null;
  matched_item_id?: string | null;
  can_edit?: boolean;
  preview_books?: ShelfPreviewBook[];
  created_by?: ShelfOwnerUserSummary | null;
  created_at?: string;
  updated_at?: string;
  [k: string]: unknown;
};

export type ShelfBookSummary = {
  id: string | number;
  title: string;
  authors?: ShelfAuthorSummary[] | null;
  series?: ShelfSeriesSummary | null;
  has_file?: boolean;
  cover_url?: string | null;
  [k: string]: unknown;
};

export type ShelfItem = {
  id: string;
  shelf: string;
  book: ShelfBookSummary;
  position?: number | null;
  added_by?: ShelfOwnerUserSummary | null;
  created_at?: string;
  updated_at?: string;
  [k: string]: unknown;
};

export type PaginatedShelfResponse = PaginatedResponse<Shelf>;
export type PaginatedShelfItemResponse = PaginatedResponse<ShelfItem>;

type ShelfListBaseParams = {
  book?: string | number;
  page?: number;
  pageSize?: number;
  includePreviewBooks?: boolean;
  ordering?: string;
};

export type ShelfListParams =
  | (ShelfListBaseParams & {
      scope: "personal";
      ownerGroup?: never;
    })
  | (ShelfListBaseParams & {
      scope?: "shared";
      ownerGroup?: string | number;
    });

export type CreateShelfInput = {
  name: string;
  description?: string;
  owner_type: "user";
  visibility: "private" | "listed";
};

export type UpdateShelfInput = {
  name?: string;
  description?: string;
  visibility?: "private" | "listed";
};

export type AddShelfItemInput = {
  book: string;
};

export type UpdateShelfItemInput =
  | { move: "up" | "down"; position?: never }
  | { position: number; move?: never };
