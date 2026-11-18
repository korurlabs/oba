export interface TokenWithExpiry {
  expiresAt?: number;
}

export const isTokenExpired = (
  token: TokenWithExpiry | null | undefined,
  safetyWindowSeconds: number = 30
): boolean => {
  if (!token || !token.expiresAt) {
    return true;
  }

  const nowInSeconds = Math.floor(Date.now() / 1000);
  return token.expiresAt - safetyWindowSeconds <= nowInSeconds;
};

export const isTokenValid = (
  token: TokenWithExpiry | null | undefined,
  safetyWindowSeconds: number = 30
): boolean => {
  return !isTokenExpired(token, safetyWindowSeconds);
};
