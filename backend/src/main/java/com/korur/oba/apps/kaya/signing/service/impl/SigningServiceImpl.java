package com.korur.oba.apps.kaya.signing.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionInternalError;
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.signing.constant.SigningConstant;
import com.korur.oba.apps.kaya.signing.dto.embedded.Signature;
import com.korur.oba.apps.kaya.signing.dto.embedded.SignedManifest;
import com.korur.oba.apps.kaya.signing.dto.embedded.SigningKey;
import com.korur.oba.apps.kaya.signing.helper.SignedManifestWriter;
import com.korur.oba.apps.kaya.signing.helper.SigningKeyProvider;
import com.korur.oba.apps.kaya.signing.service.SigningService;

/**
 * The {@link SigningService} implementation.
 */
@Service
@RequiredArgsConstructor
public class SigningServiceImpl implements SigningService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SigningServiceImpl.class);

    private final SigningKeyProvider keyProvider;
    private final LoggingService loggingService;

    @Override
    public Signature sign(SignedManifest manifest) {
        String canonicalJson = SignedManifestWriter.toCanonicalJson(manifest);
        try {
            return signCanonical(canonicalJson);
        } catch (GeneralSecurityException e) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, SigningConstant.SIGNING_FAILED_LOG, e,
                    manifest.remoteUrl(), manifest.number());
            throw new InternalServerErrorException(ExceptionInternalError.UNEXPECTED);
        }
    }

    private Signature signCanonical(String canonicalJson) throws GeneralSecurityException {
        byte[] bytes = canonicalJson.getBytes(StandardCharsets.UTF_8);
        java.security.Signature signer = java.security.Signature.getInstance(SigningConstant.ALGORITHM);
        signer.initSign(keyProvider.getPrivateKey());
        signer.update(bytes);
        String signature = Base64.getEncoder().encodeToString(signer.sign());
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance(SigningConstant.DIGEST).digest(bytes));
        return new Signature(canonicalJson, sha256, signature, keyProvider.getKeyId());
    }

    @Override
    public boolean verify(String canonicalJson, String signature, String keyId) {
        Optional<SigningKey> key = keyProvider.findTrustedKey(keyId);
        if (key.isEmpty()) {
            return false;
        }
        try {
            java.security.Signature verifier = java.security.Signature.getInstance(SigningConstant.ALGORITHM);
            verifier.initVerify(key.get().publicKey());
            verifier.update(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // A malformed signature is a signature that does not verify; the caller refuses it.
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, SigningConstant.VERIFY_FAILED_LOG, keyId, e.getMessage());
            return false;
        }
    }
}
