package com.korur.oba.connect.github.exception;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for the GitHub connect domain (GHB).
 *
 * The application reads GitHub as a GitHub App, outbound only. The upstream codes tell the user
 * whether to retry ({@link #UPS_GITHUB_UNREACHABLE}), to ask an administrator to fix the App
 * ({@link #UPS_GITHUB_CREDENTIALS_REFUSED}) or to wait ({@link #UPS_GITHUB_RATE_LIMITED}). The
 * internal codes mark a call that should never have been made: the caller checks
 * {@link com.korur.oba.connect.github.client.GithubClient#isEnabledFor} first.
 */
public enum ExceptionDomainGithubError implements ExceptionDomainError {

    // ── Upstream (GHB_UPS) ──────────────────────────────────────────────────
    UPS_GITHUB_UNREACHABLE("GHB_UPS_001",
            "GitHub could not be reached or could not answer the request. Try again later.", ExceptionCategory.UPS),
    UPS_GITHUB_CREDENTIALS_REFUSED("GHB_UPS_002",
            "GitHub refused the credentials of the GitHub App.", ExceptionCategory.UPS),
    UPS_GITHUB_RATE_LIMITED("GHB_UPS_003",
            "GitHub is limiting requests from the GitHub App. Wait a minute and try again.", ExceptionCategory.UPS),

    // ── Internal (GHB_INT) ──────────────────────────────────────────────────
    INT_GITHUB_NOT_CONFIGURED("GHB_INT_001",
            "The GitHub integration is not configured.", ExceptionCategory.INT),
    INT_GITHUB_PRIVATE_KEY_UNREADABLE("GHB_INT_002",
            "The private key of the GitHub App could not be read.", ExceptionCategory.INT);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainGithubError(String code, String message, ExceptionCategory category) {
        this.code = code;
        this.message = message;
        this.category = category;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public ExceptionCategory getCategory() {
        return category;
    }
}
