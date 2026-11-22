package com.AnnindaRajDas.gravityshift.physics;

/**
 * Listener interface notified before and after each discrete Box2D simulation step.
 * Used by entity systems to capture transform snapshots for fixed-timestep render interpolation.
 */
public interface PhysicsStepListener {

    /**
     * Invoked immediately prior to executing World.step().
     */
    void onPreStep();

    /**
     * Invoked immediately following execution of World.step().
     */
    void onPostStep();
}
