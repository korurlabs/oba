package com.korur.oba.connect.realtime.dto;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * A frame body the realtime bus delivers. Clients switch on type.
 *
 * A signal reaches only the sessions that the destination's
 * {@link com.korur.oba.connect.realtime.service.RealtimeDestinationAuthorizer} admitted to
 * subscribe, and every session subscribed to a topic receives the same body. A signal may
 * therefore carry content only when every session the authorizer admits may read it, as on a
 * channel topic whose subscribers passed the channel read check; anything else carries ids,
 * enums, timestamps and counts, and the client reads the data over REST, where the full access
 * check runs. Implementations are records of the consumer that publishes them.
 */
public interface RealtimeSignal {

    /**
     * The discriminator the clients switch on, serialized as the "type" field.
     *
     * @return the signal type, such as "MESSAGE_CREATED"
     */
    String type();
}
