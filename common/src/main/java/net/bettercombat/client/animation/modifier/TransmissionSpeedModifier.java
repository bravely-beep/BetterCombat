package net.bettercombat.client.animation.modifier;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.util.Vec3f;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Speed modifier with per-phase ("gear") speeds, used to play the windup of an attack faster than
 * its recovery.
 *
 * <p>This re-implements the sub-tick time integration of {@link SpeedModifier} using its own,
 * resettable fields instead of the library's private accumulators. The library accumulates
 * sub-tick progress across {@code setupAnim} calls and never exposes a way to reset it. Because the
 * same modifier instance is reused for every attack, the leftover accumulation from a previous
 * attack would carry into the next one. That is normally invisible, but it also is not refreshed
 * while the player model is not being rendered (e.g. after an attack winds down and the
 * first-person pass stops being entered). The next attack then inherits a stale carry and, combined
 * with a fast windup speed (such as the vanilla mace's near-instant slam), fast-forwards straight
 * past the windup before the first frame is drawn - the swing appears to snap to its final pose.
 * See issue #589.
 *
 * <p>{@link #set} marks the integrator to re-baseline on the next frame so each attack starts its
 * timing from zero.
 */
public class TransmissionSpeedModifier extends SpeedModifier {
    private float elapsed = 0;
    private float accumulatedTickDelta = 0; // within-tick position of the last update, 0 to 1
    private float carry = 0;                // sub-tick progress carried to the next update
    private boolean needsBaseline = false;  // re-sync the integrator on the next update

    public List<Gear> gears = List.of();
    public record Gear(float time, float speed) {}

    public void set(float speed, List<Gear> gears) {
        this.speed = speed;
        this.gears = gears;
        this.elapsed = 0;
        this.carry = 0;
        this.needsBaseline = true;
    }

    private float elapsed(float delta) {
        return elapsed + delta;
    }

    private void advance(float delta) {
        delta *= speed;
        delta += carry;
        while (delta > 1) {
            delta -= 1;
            var anim = getAnim();
            if (anim != null) {
                anim.tick();
            }
        }
        var anim = getAnim();
        if (anim != null) {
            anim.setupAnim(delta);
        }
        this.carry = delta;
    }

    @Override
    public void tick() {
        if (needsBaseline) {
            // A fresh attack has not been anchored by a render yet. Hold on the first frame
            // (advance nothing, don't start the gear clock) so the windup isn't consumed before
            // it is ever drawn. The baseline is established by the first setupAnim (render).
            this.carry = 0;
            var anim = getAnim();
            if (anim != null) {
                anim.setupAnim(0);
            }
            return;
        }
        float delta = 1f - this.accumulatedTickDelta;
        this.accumulatedTickDelta = 0;
        advance(delta);
        this.elapsed += 1;
    }

    @Override
    public void setupAnim(float tickDelta) {
        var time = elapsed(tickDelta);
        for (var gear : gears) {
            if (time > gear.time) {
                speed = gear.speed();
            }
        }
        if (needsBaseline) {
            // First render of a fresh attack: anchor here and advance nothing this frame, so the
            // windup is actually shown instead of being skipped by leftover sub-tick progress.
            needsBaseline = false;
            this.accumulatedTickDelta = tickDelta;
            this.carry = 0;
            var anim = getAnim();
            if (anim != null) {
                anim.setupAnim(0);
            }
            return;
        }
        float delta = tickDelta - this.accumulatedTickDelta;
        this.accumulatedTickDelta = tickDelta;
        advance(delta);
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float tickDelta, @NotNull Vec3f value0) {
        var anim = getAnim();
        return anim == null ? value0 : anim.get3DTransform(modelName, type, this.carry, value0);
    }
}
