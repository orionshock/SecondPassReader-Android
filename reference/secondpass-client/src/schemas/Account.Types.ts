export type CurrentUserGroup = {
  id: string;
  name: string;
  isPublicGroup: boolean;
  isCurator: boolean;
};

export type CurrentUser = {
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  profileId: string;
  role: string;
  mustChangePassword: boolean;
  isOwner: boolean;
  isManager: boolean;
  isLibrarian: boolean;
  isReader: boolean;
  canAccessDjangoAdmin: boolean;
  groups: CurrentUserGroup[];
};

export type MePayload = CurrentUser;
