package com.mixaold.burmalda.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Cross-version shims for the GUI matrix stack.
 * <p>
 * Up to 1.21.5 {@code DrawContext.getMatrices()} returned a 3D {@code MatrixStack}
 * (push/pop, 3-arg translate/scale, quaternion multiply). Since 1.21.8 it returns a
 * 2D {@code org.joml.Matrix3x2fStack} (pushMatrix/popMatrix, 2-arg translate/scale,
 * {@code rotate(radians)}). All call sites go through these helpers so the branch
 * lives here only.
 */
@Environment(EnvType.CLIENT)
public final class BurmaldaRenderCompat {
    private BurmaldaRenderCompat() {}

    public static void push(DrawContext ctx) {
        //? if >=1.21.8 {
        /*ctx.getMatrices().pushMatrix();
        *///? } else
        ctx.getMatrices().push();
    }

    public static void pop(DrawContext ctx) {
        //? if >=1.21.8 {
        /*ctx.getMatrices().popMatrix();
        *///? } else
        ctx.getMatrices().pop();
    }

    public static void translate(DrawContext ctx, double x, double y) {
        //? if >=1.21.8 {
        /*ctx.getMatrices().translate((float) x, (float) y);
        *///? } else
        ctx.getMatrices().translate(x, y, 0);
    }

    public static void scale(DrawContext ctx, float sx, float sy) {
        //? if >=1.21.8 {
        /*ctx.getMatrices().scale(sx, sy);
        *///? } else
        ctx.getMatrices().scale(sx, sy, 1f);
    }

    /** Rotate about the current origin (z-axis), in degrees. */
    public static void rotateZ(DrawContext ctx, float degrees) {
        //? if >=1.21.8 {
        /*ctx.getMatrices().rotate((float) Math.toRadians(degrees));
        *///? } else
        ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(degrees));
    }

    /** {@code PositionedSoundInstance.master} was renamed to {@code ui} in 1.21.11. */
    public static PositionedSoundInstance uiSound(SoundEvent sound, float pitch) {
        //? if >=1.21.11 {
        /*return PositionedSoundInstance.ui(sound, pitch);
        *///? } else
        return PositionedSoundInstance.master(sound, pitch);
    }

    /**
     * Player name from a tab-list entry. authlib's {@code GameProfile} became a record in 1.21.11
     * ({@code getName()} → {@code name()}), so the accessor branches here. May be null for entries
     * without a resolved profile.
     */
    public static String profileName(net.minecraft.client.network.PlayerListEntry entry) {
        if (entry.getProfile() == null) return null;
        //? if >=1.21.11 {
        /*return entry.getProfile().name();
        *///? } else
        return entry.getProfile().getName();
    }

    /**
     * Draw a player's head from their skin. {@code PlayerSkinDrawer.draw(ctx, SkinTextures, x, y, size)}
     * exists on every target version; only the {@code SkinTextures} package moved (1.21.11), so we pass
     * {@code entry.getSkinTextures()} straight through without ever naming the type.
     */
    public static void drawHead(DrawContext ctx, net.minecraft.client.network.PlayerListEntry entry,
                                int x, int y, int size) {
        net.minecraft.client.gui.PlayerSkinDrawer.draw(ctx, entry.getSkinTextures(), x, y, size);
    }

    /** {@code DrawContext.drawBorder} was removed in 1.21.11; a 1px frame from four fills works on every version. */
    public static void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    /**
     * Draw a full GUI texture with a uniform alpha. The texture-draw entry point changed twice:
     * a {@code RenderLayer} factory in 1.21.2, then a {@code RenderPipeline} in 1.21.8 (where the
     * old {@code RenderSystem.setShaderColor} tint was replaced by a per-call color argument).
     */
    public static void drawGuiTexture(DrawContext ctx, Identifier tex,
                                      int x, int y, int w, int h, float alpha) {
        //? if >=1.21.8 {
        /*int tint = (((int) (alpha * 255f)) << 24) | 0xFFFFFF;
        ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, w, h, w, h, tint);
        *///? } else if >=1.21.2 {
        /*RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        ctx.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, tex, x, y, 0f, 0f, w, h, w, h);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        *///? } else {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        ctx.drawTexture(tex, x, y, 0, 0f, 0f, w, h, w, h);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        //? }
    }
}
