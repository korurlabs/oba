package com.korur.oba.apps.kaya.signing.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.signing.dto.embedded.Signature;
import com.korur.oba.apps.kaya.signing.dto.embedded.SignedManifest;
import com.korur.oba.apps.kaya.signing.helper.SigningKeyProvider;

/**
 * Signs and verifies the canonical documents of releases ({@link SignedManifest}) with the key of
 * {@link SigningKeyProvider}, so the CLI trusts them with the keys it holds.
 *
 * Called by the release domain when it writes a release, and by delivery when it checks a stored
 * signature. It holds no state of its own
 * and joins no transaction.
 */
public interface SigningService {

    /**
     * Signs a document.
     *
     * @param manifest the document
     * @return the canonical text, its SHA-256, the signature and the key id
     */
    Signature sign(SignedManifest manifest);

    /**
     * Verifies a signature against a trusted key.
     *
     * @param canonicalJson the signed text
     * @param signature     the base64 signature
     * @param keyId         the key it names
     * @return true when a trusted key with that id verifies the signature over the text
     */
    boolean verify(String canonicalJson, String signature, String keyId);
}
