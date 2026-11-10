package com.AnnindaRajDas.gravityshift;

import com.badlogic.gdx.Game;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class GravityShiftGame extends Game {
    @Override
    public void create() {
        setScreen(new FirstScreen());
    }
}