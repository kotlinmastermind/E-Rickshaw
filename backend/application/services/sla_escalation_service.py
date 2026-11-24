import uuid
import logging
from datetime import datetime, timezone, timedelta
from typing import Dict, Any, List
from sqlalchemy import select, and_, or_, desc, update
from sqlalchemy.ext.asyncio import AsyncSession
from backend.domain.models import (
    SupportTicket, SupportEscalationPolicy, SupportMessage, User,
    SupportTicketEscalationLog
)

logger = logging.getLogger(__name__)

SQUAD_ESCALATION_HIERARCHY = {
    "VIP_CONCIERGE": "SAFETY_INCIDENTS",
    "BILLING_REFUNDS": "OPERATIONS_MANAGEMENT",
    "SAFETY_INCIDENTS": "EXECUTIVE_CRISIS_SQUAD",
    "FLEET_OPERATIONS": "OPERATIONS_MANAGEMENT",
}

PRIORITY_ESCALATION = {
    "LOW": "MEDIUM",
    "MEDIUM": "HIGH",
    "HIGH": "URGENT",
    "URGENT": "URGENT"
}

class SlaEscalationService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def execute_escalation_cycle(self) -> Dict[str, Any]:
        """
        Idempotent SLA evaluation cycle for all active support tickets.
        Breached tickets are elevated and assigned to higher-tier operational squads.
        """
        now = datetime.now(timezone.utc)
        
        # 1. Fetch active escalation policies
        pol_res = await self.db.execute(select(SupportEscalationPolicy).where(SupportEscalationPolicy.is_active == True))
        policies = {p.priority.upper(): p for p in pol_res.scalars().all()}

        # 2. Query open/investigating tickets
        ticket_stmt = select(SupportTicket).where(
            SupportTicket.status.in_(["OPEN", "INVESTIGATING"]),
            SupportTicket.is_escalated == False
        )
        tickets = (await self.db.execute(ticket_stmt)).scalars().all()
        escalated_count = 0

        for t in tickets:
            prio = (t.priority or "MEDIUM").upper()
            policy = policies.get(prio)
            
            # Default SLA if no dynamic policy: LOW=120m, MED=60m, HIGH=30m, URGENT=15m
            sla_mins = policy.sla_minutes if policy else (15 if prio == "URGENT" else (30 if prio == "HIGH" else 60))
            sla_deadline = t.sla_due_at or (t.created_at + timedelta(minutes=sla_mins))

            if sla_deadline.tzinfo is None:
                sla_deadline = sla_deadline.replace(tzinfo=timezone.utc)

            if now > sla_deadline:
                # Breached! Calculate next target squad and priority
                new_squad = policy.target_squad if policy else SQUAD_ESCALATION_HIERARCHY.get(t.assigned_squad, "OPERATIONS_MANAGEMENT")
                new_priority = PRIORITY_ESCALATION.get(prio, "URGENT")
                
                # Idempotency key ensures no duplicate escalation on worker crash/retry
                idempotency_key = f"esc:{t.id}:{new_priority}:{now.strftime('%Y-%m-%d')}"
                
                # Verify idempotency
                existing_log = (await self.db.execute(
                    select(SupportTicketEscalationLog).where(SupportTicketEscalationLog.idempotency_key == idempotency_key)
                )).scalar_one_or_none()

                if existing_log:
                    continue

                reason = f"Statutory SLA deadline breached ({sla_mins}m elapsed without resolution). Auto-escalated to {new_squad}."
                
                # Update ticket
                t.is_escalated = True
                t.escalated_at = now
                t.escalation_reason = reason
                t.priority = new_priority
                t.assigned_squad = new_squad
                
                # Insert immutable audit log
                esc_log = SupportTicketEscalationLog(
                    id=str(uuid.uuid4()),
                    ticket_id=t.id,
                    policy_id=policy.id if policy else None,
                    from_priority=prio,
                    to_priority=new_priority,
                    from_squad=t.assigned_squad,
                    to_squad=new_squad,
                    escalation_reason=reason,
                    idempotency_key=idempotency_key,
                    escalated_at=now
                )
                self.db.add(esc_log)

                # Add system message to ticket timeline
                sys_msg = SupportMessage(
                    id=str(uuid.uuid4()),
                    ticket_id=t.id,
                    sender_user_id=t.user_id, # System notice
                    sender_role="ADMIN",
                    message=f"🚨 [SLA BREACH ENGINE] {reason}",
                    is_internal_only=True
                )
                self.db.add(sys_msg)
                escalated_count += 1

        if escalated_count > 0:
            await self.db.commit()
            logger.info("SLA Escalation worker executed: %d tickets escalated successfully.", escalated_count)

        return {"evaluated_at": now.isoformat(), "escalated_tickets_count": escalated_count}