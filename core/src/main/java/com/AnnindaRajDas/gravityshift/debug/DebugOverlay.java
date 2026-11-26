package com.AnnindaRajDas.gravityshift.debug;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;

/**
 * Lightweight, allocation-conscious runtime debug overlay displaying:
 * - FPS
 * - GC invocation count
 * - Java heap used and maximum heap
 * - Native heap usage
 *
 * Samples metrics at a low frequency (default 0.5s), formats into a single preallocated
 * {@link StringBuilder}, and renders via a cached {@link BitmapFontCache}
 * so recurring frames perform zero application-level heap allocations.
 */
public class DebugOverlay implements Disposable {

    public static final float DEFAULT_SAMPLE_INTERVAL = 0.5f;
    private static final int TEXT_BUFFER_CAPACITY = 160;
    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

    private final RuntimeMetricsProvider metricsProvider;
    private final RuntimeMetricsSnapshot metricsSnapshot = new RuntimeMetricsSnapshot();
    private final StringBuilder textBuffer = new StringBuilder(TEXT_BUFFER_CAPACITY);

    private final float sampleInterval;
    private float sampleTimer = 0.0f;
    private boolean enabled;
    private boolean hasSampledOnce = false;
    private boolean layoutDirty = true;
    private int lastSampledFps = 0;
    private int sampleCount = 0;

    // Screen-space layout configuration
    private float fontScale = 1.15f;
    private float textX = 20.0f;
    private float textY = 556.0f;

    // Lazily initialized OpenGL rendering resources (null in headless unit tests)
    private SpriteBatch batch;
    private BitmapFont font;
    private BitmapFontCache fontCache;
    private GlyphLayout glyphLayout;
    private boolean disposed = false;

    public DebugOverlay(RuntimeMetricsProvider metricsProvider, boolean initiallyEnabled) {
        this(metricsProvider, initiallyEnabled, DEFAULT_SAMPLE_INTERVAL);
    }

    public DebugOverlay(RuntimeMetricsProvider metricsProvider, boolean initiallyEnabled, float sampleInterval) {
        if (metricsProvider == null) {
            throw new IllegalArgumentException("RuntimeMetricsProvider cannot be null.");
        }
        if (!Float.isFinite(sampleInterval) || sampleInterval <= 0.0f) {
            throw new IllegalArgumentException("sampleInterval must be finite and positive.");
        }
        this.metricsProvider = metricsProvider;
        this.enabled = initiallyEnabled;
        this.sampleInterval = sampleInterval;
        this.metricsProvider.setSamplingEnabled(initiallyEnabled);
        formatTextBuffer(0);
    }

    /**
     * Advances the low-frequency sampling accumulator and refreshes the reusable text buffer
     * only when the sampling interval elapses (or on first enabled update).
     *
     * @param delta frame delta time in seconds
     * @param fps   current reported frames per second
     */
    public void update(float delta, int fps) {
        if (disposed || !enabled) {
            return;
        }
        if (!Float.isFinite(delta) || delta < 0.0f) {
            delta = 0.0f;
        }

        if (!hasSampledOnce) {
            sampleNow(fps);
            hasSampledOnce = true;
            sampleTimer = 0.0f;
            return;
        }

        sampleTimer += delta;
        if (sampleTimer >= sampleInterval) {
            sampleTimer %= sampleInterval;
            sampleNow(fps);
        }
    }

    /**
     * Renders the cached debug overlay text in HUD screen coordinates.
     * Performs zero allocations during steady-state rendering frames.
     *
     * @param hudCamera screen-space HUD camera
     */
    public void render(OrthographicCamera hudCamera) {
        if (disposed || !enabled || hudCamera == null || Gdx.gl == null) {
            return;
        }

        ensureRenderResources();
        if (batch == null || fontCache == null) {
            return;
        }

        if (layoutDirty) {
            font.getData().setScale(fontScale);
            glyphLayout.setText(font, textBuffer);
            fontCache.setText(glyphLayout, textX, textY);
            layoutDirty = false;
        }

        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();
        fontCache.draw(batch);
        batch.end();
    }

    /**
     * Adjusts overlay scaling and top-left screen anchor on viewport resize.
     *
     * @param width  viewport width in screen pixels
     * @param height viewport height in screen pixels
     */
    public void resize(int width, int height) {
        if (disposed || width <= 0 || height <= 0) {
            return;
        }
        float scale = Math.max(1.0f, height / 540.0f);
        this.fontScale = scale;
        this.textX = 28.0f * scale;
        this.textY = height - (20.0f * scale);
        this.layoutDirty = true;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        metricsProvider.setSamplingEnabled(enabled);
        if (enabled) {
            // Trigger fresh sample on next update without waiting for full interval
            this.hasSampledOnce = false;
            this.sampleTimer = 0.0f;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public float getSampleInterval() {
        return sampleInterval;
    }

    public int getLastSampledFps() {
        return lastSampledFps;
    }

    public RuntimeMetricsSnapshot getMetricsSnapshot() {
        return metricsSnapshot;
    }

    /**
     * Returns the reusable character sequence backing the overlay display.
     * Intended for inspection and testing without creating String copies.
     *
     * @return reusable {@link CharSequence} text buffer
     */
    public CharSequence getDisplayText() {
        return textBuffer;
    }

    public boolean isDisposed() {
        return disposed;
    }

    private void sampleNow(int fps) {
        metricsSnapshot.reset();
        metricsProvider.sample(metricsSnapshot);
        lastSampledFps = Math.max(0, fps);
        formatTextBuffer(lastSampledFps);
        sampleCount++;
        layoutDirty = true;
    }

    private void formatTextBuffer(int fps) {
        textBuffer.setLength(0);

        textBuffer.append("FPS: ").append(fps).append('\n');

        textBuffer.append("GC: ");
        if (metricsSnapshot.gcInvocationCount >= 0L) {
            textBuffer.append(metricsSnapshot.gcInvocationCount);
        } else {
            textBuffer.append("N/A");
        }
        textBuffer.append('\n');

        textBuffer.append("Heap: ");
        if (metricsSnapshot.javaHeapUsedBytes >= 0L) {
            appendMegabytes(textBuffer, metricsSnapshot.javaHeapUsedBytes);
            textBuffer.append(" / ");
            if (metricsSnapshot.javaHeapMaxBytes >= 0L) {
                appendMegabytes(textBuffer, metricsSnapshot.javaHeapMaxBytes);
            } else {
                textBuffer.append("max N/A");
            }
        } else {
            textBuffer.append("N/A");
        }
        textBuffer.append('\n');

        textBuffer.append("Native: ");
        if (metricsSnapshot.nativeHeapBytes >= 0L) {
            appendMegabytes(textBuffer, metricsSnapshot.nativeHeapBytes);
        } else {
            textBuffer.append("N/A");
        }
    }

    /**
     * Formats byte count as {@code X.Y MB} using pure integer math (zero float-formatter allocations).
     */
    private static void appendMegabytes(StringBuilder sb, long bytes) {
        long clampedBytes = Math.max(0L, bytes);
        long tenthsMb = (clampedBytes * 10L) / BYTES_PER_MEGABYTE;
        long wholeMb = tenthsMb / 10L;
        long fracTenth = tenthsMb % 10L;
        sb.append(wholeMb).append('.').append((char) ('0' + fracTenth)).append(" MB");
    }

    private void ensureRenderResources() {
        if (batch != null) {
            return;
        }
        batch = new SpriteBatch(96);
        font = new BitmapFont();
        font.setColor(0.35f, 0.95f, 0.55f, 0.95f);
        fontCache = font.newFontCache();
        fontCache.setColor(Color.CHARTREUSE);
        glyphLayout = new GlyphLayout();
        layoutDirty = true;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        enabled = false;
        metricsProvider.setSamplingEnabled(false);
        metricsProvider.dispose();
        if (font != null) {
            font.dispose();
            font = null;
        }
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        fontCache = null;
        glyphLayout = null;
    }
}
