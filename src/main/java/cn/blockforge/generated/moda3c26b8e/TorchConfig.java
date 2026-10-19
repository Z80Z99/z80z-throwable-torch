package cn.blockforge.generated.moda3c26b8e;

import net.minecraftforge.common.ForgeConfigSpec;

public final class TorchConfig {
    public enum AnimationStyle { OVER_SHOULDER, VANILLA, EXTERNAL }
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<AnimationStyle> ANIMATION;
    public static final ForgeConfigSpec.BooleanValue TRAIL;
    public static final ForgeConfigSpec.BooleanValue SPARKS;
    public static final ForgeConfigSpec.BooleanValue CHARGE_BAR;
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        ANIMATION = builder.comment("OVER_SHOULDER：后摆及过肩投掷；VANILLA：仅原版挥臂；EXTERNAL：交给其他动画模组。")
                .defineEnum("animation", AnimationStyle.OVER_SHOULDER);
        TRAIL = builder.comment("显示持续 0.3 秒的橙色轨迹。不会产生动态方块照明。")
                .define("trail", true);
        SPARKS = builder.comment("每游戏刻生成两颗原版火星粒子。")
                .define("sparks", true);
        CHARGE_BAR = builder.comment("显示蓄力进度条。")
                .define("chargeBar", true);
        SPEC = builder.build();
    }
    private TorchConfig() {}
}
