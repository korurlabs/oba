package com.korur.oba.apps.kaya.release.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.dto.QueryRequest;

/**
 * Paging of a repository's releases. Releases are listed newest number first; the page and amount
 * of QueryRequest apply, its sort fields do not.
 */
@Getter
@Setter
@Schema(description = "Paging of a repository's releases")
public class ReleaseQueryRequest extends QueryRequest {
}
