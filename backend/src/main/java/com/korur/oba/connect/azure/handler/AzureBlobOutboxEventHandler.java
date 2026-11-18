package com.korur.oba.connect.azure.handler;

// 1. Framework-level
import org.springframework.stereotype.Service;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 4. Internal
import com.korur.oba.connect.azure.constant.handler.AzureBlobOutboxHandlerLogConstant;
import com.korur.oba.connect.azure.exception.ExceptionDomainAzureError;
import com.korur.oba.connect.azure.service.AzureBlobStorageService;
import com.korur.oba.connect.outbox.dto.payload.AzureBlobContainerDeletePayload;
import com.korur.oba.connect.outbox.dto.payload.AzureBlobDeletePayload;
import com.korur.oba.connect.outbox.entity.OutboxEvent;
import com.korur.oba.connect.outbox.enums.ConnectOutboxSystem;
import com.korur.oba.connect.outbox.enums.OutboxEventType;
import com.korur.oba.connect.outbox.exception.ExceptionDomainOutboxError;
import com.korur.oba.connect.outbox.handler.OutboxEventHandler;
import com.korur.oba.dojo.exception.external.InternalProcessException;
import com.korur.oba.dojo.exception.external.ThirdPartyException;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.util.JsonUtil;

/**
 * Handles outbox events targeted at Azure Blob Storage.
 * Dispatches delete operations with outbox-backed retry.
 */
@Service
@RequiredArgsConstructor
public class AzureBlobOutboxEventHandler implements OutboxEventHandler {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AzureBlobOutboxEventHandler.class);

    private final LoggingService loggingService;
    private final AzureBlobStorageService azureBlobStorageService;

    @Override
    public boolean supports(ConnectOutboxSystem connectOutboxSystem) {
        return ConnectOutboxSystem.AZURE_BLOB_STORAGE.equals(connectOutboxSystem);
    }

    @Override
    public void handle(OutboxEvent event) {
        OutboxEventType eventType = event.getOutboxEventType();

        switch (eventType) {
            case AZURE_BLOB_DELETE -> handleDelete(event);
            default -> throw new InternalProcessException(
                    ExceptionDomainOutboxError.INT_UNSUPPORTED_EVENT_TYPE, null);
        }
    }

    private void handleDelete(OutboxEvent event) {
        loggingService.info(LOG_PREFIX, LogLayer.HANDLER,
                AzureBlobOutboxHandlerLogConstant.OUTBOX_HANDLER_AZURE_DELETING,
                event.getOutboxEventType());

        try {
            AzureBlobDeletePayload payload = JsonUtil.fromJson(event.getPayload(), AzureBlobDeletePayload.class);
            if (payload.fileUrl() != null) {
                azureBlobStorageService.deleteFile(payload.fileUrl());
            } else {
                AzureBlobContainerDeletePayload containerPayload = JsonUtil.fromJson(
                        event.getPayload(), AzureBlobContainerDeletePayload.class);
                azureBlobStorageService.deleteFile(containerPayload.containerKey(), containerPayload.fileUrl());
            }
        } catch (InternalProcessException e) {
            throw e;
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new ThirdPartyException(ExceptionDomainAzureError.UPS_AZURE_BLOB_DELETE, e);
        }

        loggingService.info(LOG_PREFIX, LogLayer.HANDLER,
                AzureBlobOutboxHandlerLogConstant.OUTBOX_HANDLER_AZURE_DELETED,
                event.getOutboxEventType());
    }
}
