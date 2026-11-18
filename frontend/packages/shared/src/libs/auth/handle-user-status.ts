import { UserStatus } from "@oba/shared/features/users/schema";
import { AuthError } from "./types";

export function handleUserStatus(userStatus: UserStatus): AuthError | undefined {
    switch (userStatus) {
        case "PENDING_ONBOARDING":
            return AuthError.ONBOARDING_REQUIRED;
        case "ACTIVE":
            return undefined;
        case "DELETED":
            return AuthError.UNEXPECTED_ERROR;
        default:
            return undefined;
    }
}