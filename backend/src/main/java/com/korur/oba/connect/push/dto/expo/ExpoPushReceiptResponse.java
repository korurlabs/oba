package com.korur.oba.connect.push.dto.expo;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 3. Java SDK
import java.util.Map;

// 4. Internal

/**
 * Expo's answer to POST /--/api/v2/push/getReceipts. A ticket whose receipt is not ready yet, or no
 * longer kept, is absent from the map.
 *
 * @param data the receipts by ticket id
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExpoPushReceiptResponse(
        @JsonProperty("data") Map<String, ExpoPushReceipt> data
) {
}
