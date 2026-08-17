export type ServerPublicGroup = {
  id: string;
  name: string;
  description: string;
};

export type ServerInfo = {
  name: string;
  description: string;
  bannerText: string;
  advancedLibraryGroupsEnabled: boolean;
  readingClientBaseUrl: string;
  marginaliaProfileUri: string;
  publicGroup: ServerPublicGroup;
  version: string;
  releaseDate: string;
};
