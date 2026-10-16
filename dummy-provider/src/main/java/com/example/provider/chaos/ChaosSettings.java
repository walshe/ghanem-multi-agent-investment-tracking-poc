package com.example.provider.chaos;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** The current chaos settings. New payments take a snapshot of these when created. */
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
