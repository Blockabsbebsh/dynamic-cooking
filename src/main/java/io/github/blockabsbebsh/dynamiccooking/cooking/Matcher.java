package io.github.blockabsbebsh.dynamiccooking.cooking;

import java.util.Set;

/**
 * Matches an ingredient by item id or by role. An ingredient matches if its item is listed or it has any listed role.
 */
public record Matcher(Set<String> items, Set<String> roles) {
	public Matcher {
		items = Set.copyOf(items);
		roles = Set.copyOf(roles);
	}

	public static Matcher role(String... roles) {
		return new Matcher(Set.of(), Set.of(roles));
	}

	public static Matcher item(String... items) {
		return new Matcher(Set.of(items), Set.of());
	}

	public boolean matches(CookingInput input) {
		if (items.contains(input.itemId())) {
			return true;
		}

		for (String role : roles) {
			if (input.profile().hasRole(role)) {
				return true;
			}
		}

		return false;
	}
}
