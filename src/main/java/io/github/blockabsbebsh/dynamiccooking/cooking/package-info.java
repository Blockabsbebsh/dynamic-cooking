/**
 * The cooking rules engine.
 *
 * <p>Nothing in this package imports Minecraft classes. It works on plain item ids and data records,
 * so the rules can be unit tested without a game and changed without touching the Minecraft-facing code.
 * The Minecraft side turns item stacks into {@link io.github.blockabsbebsh.dynamiccooking.cooking.CookingInput}
 * and turns a {@link io.github.blockabsbebsh.dynamiccooking.cooking.DishResult} back into an item stack.
 */
package io.github.blockabsbebsh.dynamiccooking.cooking;
