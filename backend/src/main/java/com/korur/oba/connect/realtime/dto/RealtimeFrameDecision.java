package com.korur.oba.connect.realtime.dto;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * The verdict on one SUBSCRIBE or SEND frame, with the reason that is logged when the frame is
 * dropped or refused.
 *
 * @param verdict {@link Verdict#PASS}, {@link Verdict#DROP} or {@link Verdict#REFUSE}
 * @param reason  why the frame does not pass; null on {@link Verdict#PASS}
 */
public record RealtimeFrameDecision(Verdict verdict, String reason) {

    private static final RealtimeFrameDecision PASS = new RealtimeFrameDecision(Verdict.PASS, null);

    /**
     * What the inbound channel does with a SUBSCRIBE or SEND frame.
     *
     * {@link #PASS} hands the frame on. {@link #DROP} discards it and keeps the session open: a
     * destination its consumer refused, a failing consumer check, a subscription past the cap or a
     * SEND past the rate limit. {@link #REFUSE} ends the session with RTM_SEC_002: a frame no correct client sends, such
     * as a destination nobody claims, a wildcard pattern or another organization's topic.
     */
    public enum Verdict {
        PASS,
        DROP,
        REFUSE
    }

    /**
     * The frame goes on.
     *
     * @return the {@link Verdict#PASS} decision
     */
    public static RealtimeFrameDecision pass() {
        return PASS;
    }

    /**
     * The frame is discarded and the session stays open.
     *
     * @param reason one of the REASON_ values of
     *               {@link com.korur.oba.connect.realtime.constant.RealtimeConstant}
     * @return the {@link Verdict#DROP} decision
     */
    public static RealtimeFrameDecision drop(String reason) {
        return new RealtimeFrameDecision(Verdict.DROP, reason);
    }

    /**
     * The session is ended with RTM_SEC_002.
     *
     * @param reason one of the REASON_ values of
     *               {@link com.korur.oba.connect.realtime.constant.RealtimeConstant}
     * @return the {@link Verdict#REFUSE} decision
     */
    public static RealtimeFrameDecision refuse(String reason) {
        return new RealtimeFrameDecision(Verdict.REFUSE, reason);
    }

    /**
     * Whether the frame goes on.
     *
     * @return true on {@link Verdict#PASS}
     */
    public boolean passes() {
        return verdict == Verdict.PASS;
    }
}
