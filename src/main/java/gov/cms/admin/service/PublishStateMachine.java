package gov.cms.admin.service;

import java.util.Map;
import java.util.Set;

public class PublishStateMachine {

  private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
      "created", Set.of("queued", "failed"),
      "queued", Set.of("staging_rendering", "production_rendering", "failed"),
      "staging_rendering", Set.of("staging_ready", "failed"),
      "staging_ready", Set.of("approved", "rejected", "failed"),
      "approved", Set.of("production_rendering", "failed"),
      "production_rendering", Set.of("published", "failed"),
      "published", Set.of("rolled_back"),
      "rejected", Set.of("queued"),
      "failed", Set.of("queued")
  );

  public static boolean canTransition(String from, String to) {
    if (from == null || to == null) {
      return false;
    }
    Set<String> allowed = VALID_TRANSITIONS.get(from);
    return allowed != null && allowed.contains(to);
  }

  public static void requireTransition(String from, String to) {
    if (!canTransition(from, to)) {
      throw new IllegalStateException(
          String.format("Invalid publish state transition: %s -> %s", from, to)
      );
    }
  }
}
