import type { SecondPassDiscovery } from "./schemas/ClientApiAuth.Types";
import type { BookDetail, CompactBook } from "./schemas/Library.Types";
import { consumeLoginRequest, createLoginRequest, discoverSecondPass, pollLoginRequest } from "./ClientApiAuth.Api";
import { getCurrentUser } from "./Account.Api";
import { getServerInfo } from "./Server.Api";
import {
  downloadBookFile,
  getAuthor,
  getBook,
  getGroup,
  getSeries,
  getTag,
  listAuthors,
  listBooks,
  listGroupAuthors,
  listGroupBooks,
  listGroups,
  listGroupSeries,
  listGroupTags,
  listSeries,
  listTags,
  searchBooks,
} from "./Library.Api";
import {
  batchMarginaliaAnnotations,
  closeMarginaliaSession,
  getActiveMarginaliaSession,
  getMarginaliaAnnotations,
  getMarginaliaBook,
  getMarginaliaProgress,
  getMarginaliaSession,
  listBookSessions,
  listMarginaliaBooks,
  listMarginaliaSessions,
  listRecentMarginaliaSessions,
  openMarginaliaBook,
  replaceMarginaliaProgress,
  startOverMarginaliaBook,
  updateMarginaliaSession,
} from "./Marginalia.Api";
import {
  addShelfItem,
  createShelf,
  deleteShelf,
  deleteShelfItem,
  getShelf,
  listShelfItems,
  listShelves,
  updateShelf,
  updateShelfItem,
} from "./Shelves.Api";
import { createClientContext, requireAuth } from "./ClientContext.Policy";
import type { SecondPassClient, SecondPassClientConfig } from "./SecondPassClient.Types";

export function createSecondPassClient(config: SecondPassClientConfig): SecondPassClient {
  const frozenConfig = Object.freeze({ ...config });
  const ctx = createClientContext(frozenConfig);

  const getBookDownloadUrl = async (book: CompactBook | BookDetail | string | number): Promise<string> => {
    const auth = requireAuth(ctx);
    const resolved =
      typeof book === "string" || typeof book === "number" || "fileFormat" in book
        ? await getBook(auth, String(typeof book === "object" ? book.id : book))
        : book;
    const url = resolved.file?.downloadUrl ?? null;
    if (!url) throw new Error("Server returned a book without a file download URL.");
    return url;
  };

  const downloadBookBlob = async (book: CompactBook | BookDetail | string | number): Promise<Blob> => {
    const auth = requireAuth(ctx);
    const bookId = typeof book === "string" || typeof book === "number" ? String(book) : String(book.id);
    const dl = await downloadBookFile(auth, bookId);
    return dl.blob;
  };

  return {
    config: frozenConfig,

    server: {
      discover: (serverBaseUrl: string) => discoverSecondPass(serverBaseUrl),
      info: () => {
        const auth = requireAuth(ctx);
        return getServerInfo(auth);
      },
      createLoginRequest: (discovery: SecondPassDiscovery, input?: { clientName?: string; clientType?: string }) =>
        createLoginRequest(discovery, input),
      pollLoginRequest: (pollUrl: string) => pollLoginRequest(pollUrl),
      consumeLoginRequest: (consumeUrl: string) => consumeLoginRequest(consumeUrl),
    },

    account: {
      getCurrentUser: () => {
        const auth = requireAuth(ctx);
        return getCurrentUser(auth);
      },
    },

    library: {
      search: (params) => {
        const auth = requireAuth(ctx);
        return searchBooks(auth, params);
      },
      books: {
        list: (params) => {
          const auth = requireAuth(ctx);
          return listBooks(auth, params);
        },
        get: (bookId) => {
          const auth = requireAuth(ctx);
          return getBook(auth, bookId);
        },
        getDownloadUrl: async (book) => {
          return getBookDownloadUrl(book);
        },
        download: async (book) => {
          return downloadBookBlob(book);
        },
      },

      series: {
        list: (params) => {
          const auth = requireAuth(ctx);
          return listSeries(auth, {
            q: params?.q,
            tag: params?.tag,
            page: params?.page,
            pageSize: params?.pageSize,
            includePreviewBooks: params?.includePreviewBooks,
            previewLimit: params?.previewLimit,
            excludeId: params?.excludeId,
            ordering: params?.ordering,
          });
        },
        get: (seriesId, params) => {
          const auth = requireAuth(ctx);
          return getSeries(auth, seriesId, params);
        },
      },

      authors: {
        list: (params) => {
          const auth = requireAuth(ctx);
          return listAuthors(auth, {
            q: params?.q,
            tag: params?.tag,
            page: params?.page,
            pageSize: params?.pageSize,
            includePreviewBooks: params?.includePreviewBooks,
            previewLimit: params?.previewLimit,
            excludeId: params?.excludeId,
            ordering: params?.ordering,
          });
        },
        get: (authorId, params) => {
          const auth = requireAuth(ctx);
          return getAuthor(auth, authorId, params);
        },
      },

      tags: {
        list: (params) => {
          const auth = requireAuth(ctx);
          return listTags(auth, params);
        },
        get: (tagId) => {
          const auth = requireAuth(ctx);
          return getTag(auth, tagId);
        },
      },

      groups: {
        list: (params) => {
          const auth = requireAuth(ctx);
          return listGroups(auth, {
            q: params?.q,
            page: params?.page,
            pageSize: params?.pageSize,
            includePreviewBooks: params?.includePreviewBooks,
            previewLimit: params?.previewLimit,
            book: params?.book,
            ordering: params?.ordering,
          });
        },
        get: (groupId, params) => {
          const auth = requireAuth(ctx);
          return getGroup(auth, groupId, params);
        },
        books: (groupId, params) => {
          const auth = requireAuth(ctx);
          return listGroupBooks(auth, groupId, params);
        },
        authors: (groupId, params) => {
          const auth = requireAuth(ctx);
          return listGroupAuthors(auth, groupId, params);
        },
        series: (groupId, params) => {
          const auth = requireAuth(ctx);
          return listGroupSeries(auth, groupId, params);
        },
        tags: (groupId, params) => {
          const auth = requireAuth(ctx);
          return listGroupTags(auth, groupId, params);
        },
      },
    },

    shelves: {
      list: (params) => {
        const auth = requireAuth(ctx);
        return listShelves(auth, params);
      },
      create: (input) => {
        const auth = requireAuth(ctx);
        return createShelf(auth, input);
      },
      get: (shelfId, params) => {
        const auth = requireAuth(ctx);
        return getShelf(auth, { shelfId, includePreviewBooks: params?.includePreviewBooks });
      },
      update: (shelfId, input) => {
        const auth = requireAuth(ctx);
        return updateShelf(auth, { shelfId, update: input });
      },
      remove: (shelfId) => {
        const auth = requireAuth(ctx);
        return deleteShelf(auth, { shelfId });
      },
      items: (shelfId, params) => {
        const auth = requireAuth(ctx);
        return listShelfItems(auth, { shelfId, page: params?.page, pageSize: params?.pageSize, ordering: params?.ordering });
      },
      addItem: (shelfId, input) => {
        const auth = requireAuth(ctx);
        return addShelfItem(auth, { shelfId, item: input });
      },
      updateItem: (shelfId, itemId, input) => {
        const auth = requireAuth(ctx);
        return updateShelfItem(auth, { shelfId, itemId, update: input });
      },
      removeItem: (shelfId, itemId) => {
        const auth = requireAuth(ctx);
        return deleteShelfItem(auth, { shelfId, itemId });
      },
    },

    marginalia: {
      books: {
        list: (params) => listMarginaliaBooks(requireAuth(ctx), params),
        get: (bookId) => getMarginaliaBook(requireAuth(ctx), bookId),
        sessions: (bookId, params) => listBookSessions(requireAuth(ctx), bookId, params),
        open: (bookId, input) => openMarginaliaBook(requireAuth(ctx), bookId, input),
        getActiveSession: (bookId) => getActiveMarginaliaSession(requireAuth(ctx), bookId),
        startOver: (bookId, input, options) => startOverMarginaliaBook(requireAuth(ctx), bookId, input, options.idempotencyKey),
      },
      sessions: {
        list: (params) => listMarginaliaSessions(requireAuth(ctx), params),
        recent: (params) => listRecentMarginaliaSessions(requireAuth(ctx), params),
        get: (sessionId) => getMarginaliaSession(requireAuth(ctx), sessionId),
        update: (sessionId, input) => updateMarginaliaSession(requireAuth(ctx), sessionId, input),
        close: (sessionId, input) => closeMarginaliaSession(requireAuth(ctx), sessionId, input),
        getProgress: (sessionId) => getMarginaliaProgress(requireAuth(ctx), sessionId),
        replaceProgress: (sessionId, input) => replaceMarginaliaProgress(requireAuth(ctx), sessionId, input),
        getAnnotations: (sessionId) => getMarginaliaAnnotations(requireAuth(ctx), sessionId),
        batchAnnotations: (sessionId, operations) => batchMarginaliaAnnotations(requireAuth(ctx), sessionId, operations),
      },
    },
  };
}
