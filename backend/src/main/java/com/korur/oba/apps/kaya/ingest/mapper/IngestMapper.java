package com.korur.oba.apps.kaya.ingest.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.ingest.dto.response.IngestBatchResponse;

@Mapper(componentModel = "spring")
public interface IngestMapper {

    IngestBatchResponse toBatchResponse(String sessionId, long acknowledgedSeq);
}
