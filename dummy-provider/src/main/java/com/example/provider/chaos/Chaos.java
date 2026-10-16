package com.example.provider.chaos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Responsibility: be the chaos settings value: the Chaos schema from contract/provider-api.yaml.
 *
 * <p>Immutable. The same type is used as the API body and as the snapshot each payment takes
 * when it is created. {@link #normalised()} removes duplicate modes and fills in the default
 * delay.
 */
public record Chaos(@NotNull List<@NotNull ChaosMode> modes, @Min(0) Integer delaySeconds) {

    public static final int DEFAULT_DELAY_SECONDS = 1;

    public static Chaos none() {
        return new Chaos(List.of(), DEFAULT_DELAY_SECONDS);
    }

    /** Copy with duplicates removed and the default delay filled in. */
    public Chaos normalised() {
        Set<ChaosMode> set = modes.isEmpty() ? EnumSet.noneOf(ChaosMode.class) : EnumSet.copyOf(modes);
        return new Chaos(List.copyOf(set), delaySeconds == null ? DEFAULT_DELAY_SECONDS : delaySeconds);
    }

    public boolean has(ChaosMode mode) {
        return modes.contains(mode);
    }
}
