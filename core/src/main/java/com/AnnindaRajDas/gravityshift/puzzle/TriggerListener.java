package com.AnnindaRajDas.gravityshift.puzzle;

/**
 * Listener interface for puzzle activation signals published on {@link TriggerBus}.
 */
public interface TriggerListener {

    /**
     * Called when a subscribed puzzle channel changes activation state.
     *
     * @param channelId the signal channel identifier
     * @param active    true if activated, false if deactivated
     */
    void onTriggerChanged(String channelId, boolean active);
}
