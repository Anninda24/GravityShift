package com.AnnindaRajDas.gravityshift;

import com.AnnindaRajDas.gravityshift.screen.PlayScreen;
import com.badlogic.gdx.Game;

/**
 * Root game controller shared across Desktop and Android platforms.
 * Manages global screen transitions.
 */
public class GravityShiftGame extends Game {

    @Override
    public void create() {
        setScreen(new PlayScreen(this));
    }
}