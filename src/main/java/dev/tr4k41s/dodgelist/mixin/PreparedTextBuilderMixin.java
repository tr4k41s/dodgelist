package dev.tr4k41s.dodgelist.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.tr4k41s.dodgelist.Messages;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
public class PreparedTextBuilderMixin {
    @ModifyVariable(method = "accept", at = @At("HEAD"), argsOnly = true)
    private Style dodgelist$wave(Style style, @Local(argsOnly = true, ordinal = 0) int index) {
        if (style.getHoverEvent() != Messages.WAVE) return style;
        return style.withColor(Messages.waveColor(index));
    }
}
