package com.korur.oba.connect.azure.constant.client;

/**
 * Log messages of the Azure Resource Manager client. No message carries a token, the Authorization
 * header or the body of an error answer; a failure names its operation, connection and status.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AzureResourceManagerClientLogConstant {

    private AzureResourceManagerClientLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String OPERATION_LIST_SUBSCRIPTIONS = "list subscriptions";

    public static final String SUBSCRIPTIONS_LOADED = "Loaded {} Azure subscriptions of connection {}";
    public static final String SUBSCRIPTION_INCOMPLETE =
            "Azure listed a subscription without its id, name or state; left out. Connection: {}";
    public static final String RESOURCE_ABSENT = "Azure Resource Manager has no such resource. Operation: {}, Connection: {}";
    public static final String SUBSCRIPTIONS_ABSENT =
            "Azure Resource Manager answered 404 to the subscription list. Connection: {}";
    public static final String NEXT_LINK_REFUSED =
            "Azure Resource Manager sent a next page link outside its own host; not followed. Operation: {}, Connection: {}";
    public static final String PAGE_LIMIT_REACHED =
            "Azure Resource Manager kept sending next page links; stopped after {} pages. Operation: {}, Connection: {}";
    public static final String CALL_FAILED_STATUS =
            "Azure Resource Manager call failed. Operation: {}, Connection: {}, Status: {}";
    public static final String CALL_RESPONSE_UNREADABLE =
            "Azure Resource Manager answered with a body that could not be read. Operation: {}, Connection: {}, Cause: {}";
    public static final String BODY_ABSENT = "no body";
    public static final String CALL_FAILED_IO =
            "Azure Resource Manager call failed without an answer. Operation: {}, Connection: {}";
}
