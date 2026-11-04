import pytest
from backend.domain.state_machine.ride_state_machine import (
    RideStateMachine, RideStatus, ActorRole, RideTransitionError
)

def test_full_rapido_navigation_lifecycle():
    """Verify entire sequential lifecycle: REQUESTED -> ASSIGNED -> ARRIVED -> IN_PROGRESS -> COMPLETED."""
    current = RideStatus.REQUESTED.value

    # 1. Driver accepts ride offer
    assert RideStateMachine.can_transition(current, RideStatus.ASSIGNED.value, "DRIVER") is True
    current = RideStatus.ASSIGNED.value

    # 2. Driver arrives at pickup
    assert RideStateMachine.can_transition(current, RideStatus.ARRIVED.value, "DRIVER") is True
    current = RideStatus.ARRIVED.value

    # 3. OTP entered and verified -> start ride
    assert RideStateMachine.can_transition(current, RideStatus.IN_PROGRESS.value, "DRIVER") is True
    current = RideStatus.IN_PROGRESS.value

    # 4. Trip completed
    assert RideStateMachine.can_transition(current, RideStatus.COMPLETED.value, "DRIVER") is True
    current = RideStatus.COMPLETED.value

    # Terminal check
    assert RideStateMachine.is_terminal(current) is True

def test_customer_cancellation_rules():
    """Customer can cancel while SEARCHING or ASSIGNED or ARRIVED, but NEVER once ride is IN_PROGRESS."""
    assert RideStateMachine.can_transition(RideStatus.REQUESTED.value, RideStatus.CANCELLED.value, "CUSTOMER") is True
    assert RideStateMachine.can_transition(RideStatus.ASSIGNED.value, RideStatus.CANCELLED.value, "CUSTOMER") is True
    assert RideStateMachine.can_transition(RideStatus.ARRIVED.value, RideStatus.CANCELLED.value, "CUSTOMER") is True
    assert RideStateMachine.can_transition(RideStatus.IN_PROGRESS.value, RideStatus.CANCELLED.value, "CUSTOMER") is False

def test_invalid_skips_and_privileges():
    """Verify state skips are prohibited (e.g. REQUESTED cannot skip to IN_PROGRESS)."""
    assert RideStateMachine.can_transition(RideStatus.REQUESTED.value, RideStatus.IN_PROGRESS.value, "DRIVER") is False
    assert RideStateMachine.can_transition(RideStatus.REQUESTED.value, RideStatus.COMPLETED.value, "DRIVER") is False
    assert RideStateMachine.can_transition(RideStatus.ASSIGNED.value, RideStatus.COMPLETED.value, "DRIVER") is False

def test_terminal_state_immutability():
    """Terminal states (COMPLETED, CANCELLED, EXPIRED) can never transition further."""
    for terminal in [RideStatus.COMPLETED.value, RideStatus.CANCELLED.value, RideStatus.EXPIRED.value]:
        assert RideStateMachine.is_terminal(terminal) is True
        for target in [RideStatus.REQUESTED.value, RideStatus.ASSIGNED.value, RideStatus.IN_PROGRESS.value]:
            assert RideStateMachine.can_transition(terminal, target, "DRIVER") is False
            assert RideStateMachine.can_transition(terminal, target, "CUSTOMER") is False
            assert RideStateMachine.can_transition(terminal, target, "ADMIN") is False
