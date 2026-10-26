import pytest
from backend.domain.state_machine.ride_state_machine import (
    RideStateMachine, RideStatus, ActorRole, RideTransitionError
)

def test_permitted_ride_transitions():
    # Customer request -> Driver accept
    assert RideStateMachine.can_transition(RideStatus.REQUESTED.value, RideStatus.ASSIGNED.value, "DRIVER") is True
    # Driver arrive
    assert RideStateMachine.can_transition(RideStatus.ASSIGNED.value, RideStatus.ARRIVED.value, "DRIVER") is True
    # Trip start
    assert RideStateMachine.can_transition(RideStatus.ARRIVED.value, RideStatus.IN_PROGRESS.value, "DRIVER") is True
    # Trip completion
    assert RideStateMachine.can_transition(RideStatus.IN_PROGRESS.value, RideStatus.COMPLETED.value, "DRIVER") is True

def test_prohibited_customer_mutations():
    # Customer cannot accept a ride
    assert RideStateMachine.can_transition(RideStatus.REQUESTED.value, RideStatus.ASSIGNED.value, "CUSTOMER") is False
    # Customer cannot mark driver arrived
    assert RideStateMachine.can_transition(RideStatus.ASSIGNED.value, RideStatus.ARRIVED.value, "CUSTOMER") is False
    # Customer cannot start trip
    assert RideStateMachine.can_transition(RideStatus.ARRIVED.value, RideStatus.IN_PROGRESS.value, "CUSTOMER") is False
    # Customer cannot complete trip
    assert RideStateMachine.can_transition(RideStatus.IN_PROGRESS.value, RideStatus.COMPLETED.value, "CUSTOMER") is False
    # Customer cannot cancel an IN_PROGRESS ride
    assert RideStateMachine.can_transition(RideStatus.IN_PROGRESS.value, RideStatus.CANCELLED.value, "CUSTOMER") is False

def test_state_machine_raises_exception():
    with pytest.raises(RideTransitionError) as exc_info:
        RideStateMachine.validate_transition(RideStatus.IN_PROGRESS.value, RideStatus.CANCELLED.value, "CUSTOMER")
    assert "Illegal transition" in str(exc_info.value)
    assert exc_info.value.current_status == "IN_PROGRESS"
    assert exc_info.value.target_status == "CANCELLED"

def test_terminal_states():
    assert RideStateMachine.is_terminal(RideStatus.COMPLETED.value) is True
    assert RideStateMachine.is_terminal(RideStatus.CANCELLED.value) is True
    assert RideStateMachine.is_terminal(RideStatus.EXPIRED.value) is True
    assert RideStateMachine.is_terminal(RideStatus.IN_PROGRESS.value) is False
