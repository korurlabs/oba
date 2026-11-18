package com.korur.oba.apps.kaya.signing.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.signing.dto.embedded.SigningKey;
import com.korur.oba.apps.kaya.signing.dto.response.SigningKeyResponse;
import com.korur.oba.apps.kaya.signing.helper.SigningKeyProvider;
import com.korur.oba.apps.kaya.signing.mapper.SigningKeyMapper;
import com.korur.oba.apps.kaya.signing.service.SigningQueryService;

/**
 * The {@link SigningQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
public class SigningQueryServiceImpl implements SigningQueryService {

    private final SigningKeyProvider keyProvider;
    private final SigningKeyMapper signingKeyMapper;

    @Override
    public List<SigningKeyResponse> getSigningKeys() {
        return signingKeyMapper.toResponses(keyProvider.getTrustedKeys());
    }
}
