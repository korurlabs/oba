export enum Locale {
  EN = "en",
  NL = "nl",
  TR = "tr",
}


export interface ProxyOptions {
  publicRoutes: string[];
  loginPath?: string;
  authErrorPath?: string;
  externalLoginUrl?: string;
  invitationsPath?: string;
}
