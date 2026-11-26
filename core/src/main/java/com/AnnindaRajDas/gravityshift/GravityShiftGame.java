package com.AnnindaRajDas.gravityshift;

import com.AnnindaRajDas.gravityshift.debug.FallbackRuntimeMetricsProvider;
import com.AnnindaRajDas.gravityshift.debug.RuntimeMetricsProvider;
import com.AnnindaRajDas.gravityshift.screen.PlayScreen;
import com.badlogic.gdx.Game;

/**
 * Root game controller shared across Desktop and Android platforms.
 * Manages global screen transitions and holds the platform-neutral runtime metrics provider.
 */
public class GravityShiftGame extends Game {

    private final RuntimeMetricsProvider metricsProvider;

    public GravityShiftGame() {
        this(new FallbackRuntimeMetricsProvider());
    }

    public GravityShiftGame(RuntimeMetricsProvider metricsProvider) {
        this.metricsProvider = (metricsProvider != null)
            ? metricsProvider
            : new FallbackRuntimeMetricsProvider();
    }

    public RuntimeMetricsProvider getMetricsProvider() {
        return metricsProvider;
    }

    @Override
    public void create() {
        setScreen(new PlayScreen(this));
    }
}
