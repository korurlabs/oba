import { jwtDecode } from "jwt-decode";
import { JWT } from "next-auth/jwt";

export const decodeToken = (token: string): JWT => {
  const decoded = jwtDecode<JWT>(token);
  return decoded;
};
