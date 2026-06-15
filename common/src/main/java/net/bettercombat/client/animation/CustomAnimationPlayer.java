package net.bettercombat.client.animation;

import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.NotNull;

public class CustomAnimationPlayer extends KeyframeAnimationPlayer {
    public CustomAnimationPlayer(KeyframeAnimation emote, int t, boolean mutable) {
        super(emote, t, mutable);
    }

    public CustomAnimationPlayer(KeyframeAnimation emote, int t) {
        super(emote, t, false);
    }

    /**
     * The recovery (wind-down) phase of an attack is hidden in first person so the arm returning
     * to idle isn't shown. But while the player is holding attack, the next swing replaces this
     * animation before it finishes, so handing first-person rendering back to vanilla during the
     * brief recovery window between swings only causes a one-frame revert - on fast weapons (e.g.
     * daggers) that revert is visible as the held off-hand item flickering back to its vanilla
     * position. Only the final swing of a combo should wind down, so suppress it while attack is
     * held; the recovery-hide still applies once the player stops attacking.
     */
    public boolean isWindingDown(float tickDelta) {
        var client = MinecraftClient.getInstance();
        if (client != null && client.player != null
                && ((MinecraftClient_BetterCombat) client).isHoldingAttack()) {
            return false;
        }
        int windDownStart = getData().endTick + ((getData().stopTick - getData().endTick) / 4);
        return ((getTick() + tickDelta) > (windDownStart + 0.5F)); // + 0.5 for smoother transition
    }

    @Override
    public @NotNull FirstPersonMode getFirstPersonMode(float tickDelta) {
        if (isWindingDown(tickDelta)) {
            return FirstPersonMode.NONE;
        }
        return super.getFirstPersonMode(tickDelta);
    }
}
