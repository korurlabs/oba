package com.korur.oba.apps.kaya.signing.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;

// 3. Java SDK
import java.security.PublicKey;
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.signing.dto.embedded.SigningKey;
import com.korur.oba.apps.kaya.signing.dto.response.SigningKeyResponse;
import com.korur.oba.apps.kaya.signing.helper.SigningKeyProvider;

/**
 * Maps trusted signing keys to responses, with the public key in the raw form the CLI reads.
 */
@Mapper(componentModel = "spring")
public interface SigningKeyMapper {

    SigningKeyResponse toResponse(SigningKey key);

    List<SigningKeyResponse> toResponses(List<SigningKey> keys);

    default String toRawPublicKey(PublicKey publicKey) {
        return SigningKeyProvider.rawPublicKey(publicKey);
    }
}
