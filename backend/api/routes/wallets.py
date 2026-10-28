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

# ----------------- Part 4 Auto Top-up, Alerts & Export (Features 91, 92, 93) -----------------
class AutoTopupConfigRequest(BaseModel):
    enabled: bool
    threshold_cents: int = Field(50000, gt=0)
    amount_cents: int = Field(200000, gt=0)
    method_id: Optional[str] = None

class GiftCardPurchaseRequest(BaseModel):
    amount_cents: int = Field(..., gt=0)
    sender_name: str
    recipient_email: Optional[str] = None
    recipient_phone: Optional[str] = None
    message: Optional[str] = None

class GiftCardRedeemRequest(BaseModel):
    code: str

@router.post("/auto-topup/config")
async def configure_auto_topup(
    payload: AutoTopupConfigRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.settlement_advanced_service import SettlementAdvancedService
    service = SettlementAdvancedService(db)
    return await service.configure_auto_topup(
        user_id=user.id,
        enabled=payload.enabled,
        threshold_cents=payload.threshold_cents,
        amount_cents=payload.amount_cents,
        method_id=payload.method_id
    )

@router.get("/alerts/low-balance")
async def check_wallet_low_balance(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.settlement_advanced_service import SettlementAdvancedService
    service = SettlementAdvancedService(db)
    return await service.check_wallet_low_balance(user.id)

@router.get("/export")
async def export_wallet_csv(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.settlement_advanced_service import SettlementAdvancedService
    from fastapi.responses import Response
    service = SettlementAdvancedService(db)
    csv_content = await service.export_wallet_transactions_csv(user.id)
    return Response(
        content=csv_content,
        media_type="text/csv",
        headers={"Content-Disposition": f"attachment; filename=wallet_ledger_{user.id[:8]}.csv"}
    )

# ----------------- Part 4 Gift Cards (Feature 90) -----------------
@router.post("/gift-cards/purchase")
async def purchase_gift_card(
    payload: GiftCardPurchaseRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.settlement_advanced_service import SettlementAdvancedService
    service = SettlementAdvancedService(db)
    return await service.purchase_gift_card(
        purchaser_user_id=user.id,
        amount_cents=payload.amount_cents,
        sender_name=payload.sender_name,
        recipient_email=payload.recipient_email,
        recipient_phone=payload.recipient_phone,
        message=payload.message
    )

@router.post("/gift-cards/redeem")
async def redeem_gift_card(
    payload: GiftCardRedeemRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    from backend.application.services.settlement_advanced_service import SettlementAdvancedService
    service = SettlementAdvancedService(db)
    return await service.redeem_gift_card(
        user_id=user.id,
        code=payload.code
    )

