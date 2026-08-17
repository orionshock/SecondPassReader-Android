import { authErrorMessages, requestJson } from "./ApiHttp.Adapter";
import type { AuthenticatedClientContext } from "./ClientContext.Policy";
import type { CurrentUser } from "./schemas/Account.Types";

type CurrentUserWire = {
  username?: string;
  email?: string;
  first_name?: string;
  last_name?: string;
  profile_id?: string;
  role?: string;
  must_change_password?: boolean;
  is_owner?: boolean;
  can_access_django_admin?: boolean;
  groups?: Array<{
    id?: string;
    name?: string;
    is_public_group?: boolean;
    is_curator?: boolean;
  }>;
};

export async function getCurrentUser(ctx: AuthenticatedClientContext): Promise<CurrentUser> {
  const wire = await requestJson<CurrentUserWire>({
    apiBaseUrl: ctx.apiBaseUrl,
    accessToken: ctx.accessToken,
    tokenType: ctx.tokenType,
    endpointOrUrl: "/accounts/me/",
    options: {
      errorMessages: authErrorMessages({ forbidden: "Token is not allowed for /me (403)." }),
    },
  });
  const role = wire.role ?? "";
  const isOwner = wire.is_owner === true;

  return {
    username: wire.username ?? "",
    email: wire.email ?? "",
    firstName: wire.first_name ?? "",
    lastName: wire.last_name ?? "",
    profileId: wire.profile_id ?? "",
    role,
    mustChangePassword: wire.must_change_password === true,
    isOwner,
    isManager: !isOwner && role === "manager",
    isLibrarian: !isOwner && role === "librarian",
    isReader: !isOwner && role === "reader",
    canAccessDjangoAdmin: wire.can_access_django_admin === true,
    groups: (wire.groups ?? []).map((group) => ({
      id: group.id ?? "",
      name: group.name ?? "",
      isPublicGroup: group.is_public_group === true,
      isCurator: group.is_curator === true,
    })),
  };
}
