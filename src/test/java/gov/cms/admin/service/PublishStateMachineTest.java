package gov.cms.admin.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishStateMachineTest {

  @Test
  void canTransition_createdToQueued_shouldBeTrue() {
    assertTrue(PublishStateMachine.canTransition("created", "queued"));
  }

  @Test
  void canTransition_stagingReadyToApproved_shouldBeTrue() {
    assertTrue(PublishStateMachine.canTransition("staging_ready", "approved"));
  }

  @Test
  void canTransition_publishedToQueued_shouldBeFalse() {
    assertFalse(PublishStateMachine.canTransition("published", "queued"));
  }

  @Test
  void requireTransition_invalid_shouldThrow() {
    assertThrows(IllegalStateException.class, () ->
        PublishStateMachine.requireTransition("published", "queued"));
  }
}
