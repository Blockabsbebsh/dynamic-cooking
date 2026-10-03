package io.github.blockabsbebsh.dynamiccooking.cooking;

/**
 * A status effect that may hit whoever eats a dish, like hunger from raw chicken. Unlike a buff, side effects never cancel out.
 *
 * @param effect        effect id, e.g. {@code minecraft:hunger}
 * @param amplifier     0 for level I, 1 for level II
 * @param durationTicks duration in ticks, 20 per second
 * @param chance        chance from 0 to 1 that the effect is applied
 */
public record SideEffect(String effect, int amplifier, int durationTicks, float chance) {
}
