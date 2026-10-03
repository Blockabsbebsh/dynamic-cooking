package io.github.blockabsbebsh.dynamiccooking.cooking;

/**
 * One ingredient put into the pot, with its profile already looked up.
 */
public record CookingInput(String itemId, IngredientProfile profile) {
}
