from typing import Optional, List
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from backend.infrastructure.database.session import get_db
from backend.api.deps import get_current_user, get_current_admin
from backend.domain.models import User
from backend.application.services.wallet_service import WalletService

router = APIRouter(prefix="/wallets", tags=["Wallet & Ledger"])

class TopUpRequest(BaseModel):
    amount_cents: int = Field(..., gt=0, json_schema_extra={"example": 500000})
    payment_method: str = Field("UPI", json_schema_extra={"example": "UPI"})
    idempotency_key: Optional[str] = Field(None, json_schema_extra={"example": "topup-uuid-12345"})

class CompensateRequest(BaseModel):
    original_tx_id: str
    reason: str

@router.get("/me")
async def get_my_wallet(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    return await WalletService.get_balance(db, user.id)

@router.post("/top-up")
async def top_up_wallet(
    payload: TopUpRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    tx = await WalletService.credit_wallet(
        session=db,
        user_id=user.id,
        amount_cents=payload.amount_cents,
        transaction_type="CREDIT",
        source="TOP_UP",
        description=f"Wallet top-up via {payload.payment_method}",
        idempotency_key=payload.idempotency_key
    )
    await db.commit()
    return {
        "status": "SUCCESS",
        "transaction_id": tx.id,
        "amount_cents": tx.amount_cents,
        "transaction_type": tx.transaction_type,
        "balance_after_cents": tx.balance_after_cents,
        "running_balance_cents": tx.balance_after_cents,
        "created_at": tx.created_at.isoformat()
    }

@router.get("/history")
async def get_wallet_history(
    limit: int = 50,
    offset: int = 0,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    txs = await WalletService.get_history(db, user.id, limit=limit, offset=offset)
    return [
        {
            "id": tx.id,
            "transaction_type": tx.transaction_type,
            "source": tx.source,
            "amount_cents": tx.amount_cents,
            "balance_after_cents": tx.balance_after_cents,
            "running_balance_cents": tx.balance_after_cents,
            "reference_id": tx.reference_id,
            "description": tx.description,
            "created_at": tx.created_at.isoformat()
        }
        for tx in txs
    ]

@router.post("/admin/compensate")
async def create_compensating_transaction(
    payload: CompensateRequest,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    comp_tx = await WalletService.create_compensating_entry(
        session=db,
        original_tx_id=payload.original_tx_id,
        reason=payload.reason,
        admin_user_id=admin.id
    )
    await db.commit()
    return {
        "status": "COMPENSATED",
        "transaction_id": comp_tx.id,
        "transaction_type": comp_tx.transaction_type,
        "amount_cents": comp_tx.amount_cents,
        "balance_after_cents": comp_tx.balance_after_cents,
        "running_balance_cents": comp_tx.balance_after_cents,
        "created_at": comp_tx.created_at.isoformat()
    }
