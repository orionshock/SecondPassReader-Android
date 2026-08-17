import { authErrorMessages, requestJson } from "./ApiHttp.Adapter";
import type { AuthenticatedClientContext } from "./ClientContext.Policy";
import type { ServerInfo } from "./schemas/Server.Types";

type ServerInfoWire = {
  server_name?: string;
  server_description?: string;
  server_banner_message?: string;
  advanced_library_groups_enabled?: boolean;
  reading_client_base_url?: string;
  marginalia_profile_uri?: string;
  public_group?: {
    id?: string;
    name?: string;
    description?: string;
  };
  server_version?: string;
  server_release_date?: string;
};

export async function getServerInfo(ctx: AuthenticatedClientContext): Promise<ServerInfo> {
  const wire = await requestJson<ServerInfoWire>({
    apiBaseUrl: ctx.apiBaseUrl,
    accessToken: ctx.accessToken,
    tokenType: ctx.tokenType,
    endpointOrUrl: "/server/info/",
    options: {
      errorMessages: authErrorMessages({ forbidden: "Token is not allowed for /server/info (403)." }),
    },
  });

  return {
    name: wire.server_name ?? "",
    description: wire.server_description ?? "",
    bannerText: wire.server_banner_message ?? "",
    advancedLibraryGroupsEnabled: wire.advanced_library_groups_enabled === true,
    readingClientBaseUrl: wire.reading_client_base_url ?? "",
    marginaliaProfileUri: wire.marginalia_profile_uri ?? "",
    publicGroup: {
      id: wire.public_group?.id ?? "",
      name: wire.public_group?.name ?? "",
      description: wire.public_group?.description ?? "",
    },
    version: wire.server_version ?? "",
    releaseDate: wire.server_release_date ?? "",
  };
}
