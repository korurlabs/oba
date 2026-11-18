package com.korur.oba.connect.github.dto.api;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 3. Java SDK

// 4. Internal

/**
 * One repository as GitHub returns it in the installation's repository list.
 *
 * @param id                GitHub's numeric repository ID, stable across renames
 * @param fullName          "owner/name"
 * @param privateRepository whether the repository is private
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubApiRepository(
        @JsonProperty("id") long id,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("private") boolean privateRepository
) {
}
