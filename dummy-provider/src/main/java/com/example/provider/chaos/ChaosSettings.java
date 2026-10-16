package com.example.provider.chaos;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Responsibility: hold the current chaos settings, safely shared between threads.
 *
 * <p>New payments copy these when they are created, so changing them never affects existing
 * payments. Does not apply any chaos itself.
 */
@Component
public class ChaosSettings {

    private final AtomicReference<Chaos> current = new AtomicReference<>(Chaos.none());

    public Chaos get() {
        return current.get();
    }

    public void set(Chaos chaos) {
        current.set(chaos.normalised());
    }
}
