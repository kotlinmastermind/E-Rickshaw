from enum import Enum
from typing import Dict, Set, Tuple, Optional

class RideStatus(str, Enum):
    REQUESTED = "REQUESTED"
    ASSIGNED = "ASSIGNED"
    ARRIVED = "ARRIVED"
    IN_PROGRESS = "IN_PROGRESS"
    COMPLETED = "COMPLETED"
    CANCELLED = "CANCELLED"
    EXPIRED = "EXPIRED"

class ActorRole(str, Enum):
    CUSTOMER = "CUSTOMER"
    DRIVER = "DRIVER"
    ADMIN = "ADMIN"
    SYSTEM = "SYSTEM"

class RideTransitionError(Exception):
    def __init__(self, message: str, current_status: str, target_status: str, actor: str):
        super().__init__(message)
        self.message = message
        self.current_status = current_status
        self.target_status = target_status
        self.actor = actor

class RideStateMachine:
    """
    Authoritative state machine governing lifecycle transitions for luxury rides.
    Guarantees strict actor authorization and prohibits invalid state mutations.
    """

    # Format: (CurrentStatus, TargetStatus) -> Set of allowed ActorRoles
    _ALLOWED_TRANSITIONS: Dict[Tuple[RideStatus, RideStatus], Set[ActorRole]] = {
        (RideStatus.REQUESTED, RideStatus.ASSIGNED): {ActorRole.DRIVER},
        (RideStatus.REQUESTED, RideStatus.CANCELLED): {ActorRole.CUSTOMER, ActorRole.ADMIN, ActorRole.SYSTEM},
        (RideStatus.REQUESTED, RideStatus.EXPIRED): {ActorRole.SYSTEM},

        (RideStatus.ASSIGNED, RideStatus.ARRIVED): {ActorRole.DRIVER},
        (RideStatus.ASSIGNED, RideStatus.CANCELLED): {ActorRole.CUSTOMER, ActorRole.DRIVER, ActorRole.ADMIN},

        (RideStatus.ARRIVED, RideStatus.IN_PROGRESS): {ActorRole.DRIVER},
        (RideStatus.ARRIVED, RideStatus.CANCELLED): {ActorRole.CUSTOMER, ActorRole.DRIVER, ActorRole.ADMIN},

        (RideStatus.IN_PROGRESS, RideStatus.COMPLETED): {ActorRole.DRIVER},
        # Once a ride is IN_PROGRESS, only an emergency administrative override can cancel it.
        (RideStatus.IN_PROGRESS, RideStatus.CANCELLED): {ActorRole.ADMIN},
    }

    @classmethod
    def can_transition(cls, current: str, target: str, actor: str) -> bool:
        try:
            curr_enum = RideStatus(current)
            target_enum = RideStatus(target)
            actor_enum = ActorRole(actor.upper())
            allowed_actors = cls._ALLOWED_TRANSITIONS.get((curr_enum, target_enum), set())
            return actor_enum in allowed_actors
        except ValueError:
            return False

    @classmethod
    def validate_transition(cls, current: str, target: str, actor: str) -> None:
        if not cls.can_transition(current, target, actor):
            raise RideTransitionError(
                message=f"Illegal transition from '{current}' to '{target}' by actor '{actor}'.",
                current_status=current,
                target_status=target,
                actor=actor
            )

    @classmethod
    def is_terminal(cls, status: str) -> bool:
        return status in {RideStatus.COMPLETED.value, RideStatus.CANCELLED.value, RideStatus.EXPIRED.value}
