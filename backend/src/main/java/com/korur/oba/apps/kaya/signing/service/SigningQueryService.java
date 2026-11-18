package com.korur.oba.apps.kaya.signing.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.signing.dto.response.SigningKeyResponse;

/**
 * Lists the public keys releases are signed with ({@link com.korur.oba.apps.kaya.signing.dto.embedded.SigningKey}
 * from {@link com.korur.oba.apps.kaya.signing.helper.SigningKeyProvider}), so the CLI build and a
 * reviewer can check which keys Kaya trusts. Called by
 * {@link com.korur.oba.apps.kaya.signing.controller.SigningKeyController}.
 */
public interface SigningQueryService {

    /**
     * The trusted public keys, the signing key first.
     *
     * @return key id, raw public key in base64 and validity of each key
     */
    List<SigningKeyResponse> getSigningKeys();
}
