package com.AnnindaRajDas.gravityshift.puzzle;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Decoupled, deterministic event bus connecting puzzle producers (e.g. PressurePlate)
 * and consumers (e.g. SlidingDoor, LaserHazard) by string channel ID.
 */
public class TriggerBus {

    private final ObjectMap<String, Array<TriggerListener>> subscribers = new ObjectMap<>();

    public void subscribe(String channelId, TriggerListener listener) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null or empty.");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null.");
        }
        Array<TriggerListener> list = subscribers.get(channelId);
        if (list == null) {
            list = new Array<>(false, 4);
            subscribers.put(channelId, list);
        }
        if (!list.contains(listener, true)) {
            list.add(listener);
        }
    }

    public void unsubscribe(String channelId, TriggerListener listener) {
        if (channelId == null || listener == null) {
            return;
        }
        Array<TriggerListener> list = subscribers.get(channelId);
        if (list != null) {
            list.removeValue(listener, true);
        }
    }

    public void unsubscribeAll(TriggerListener listener) {
        if (listener == null) {
            return;
        }
        for (Array<TriggerListener> list : subscribers.values()) {
            list.removeValue(listener, true);
        }
    }

    public void publish(String channelId, boolean active) {
        if (channelId == null) {
            return;
        }
        Array<TriggerListener> list = subscribers.get(channelId);
        if (list == null || list.size == 0) {
            return;
        }
        for (int i = 0; i < list.size; i++) {
            list.get(i).onTriggerChanged(channelId, active);
        }
    }

    public int getSubscriberCount(String channelId) {
        Array<TriggerListener> list = subscribers.get(channelId);
        return (list != null) ? list.size : 0;
    }

    public void clear() {
        subscribers.clear();
    }
}
