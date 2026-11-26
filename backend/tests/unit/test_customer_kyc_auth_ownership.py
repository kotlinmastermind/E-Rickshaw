import pytest

from backend.domain.models import User


@pytest.mark.asyncio
async def test_customer_kyc_requires_authenticated_owner_and_ignores_submitted_phone(
    client,
    db_session,
    customer_user,
    customer_token,
    second_customer_user,
):
    # Profile completion is not proof of phone ownership or KYC verification.
    customer_user.is_verified = False
    second_customer_user.is_verified = False
    await db_session.commit()

    payload = {
        "phone_number": second_customer_user.phone_number,
        "full_name": "Attempted Account Takeover",
        "email": "attacker-controlled@example.com",
        "govt_id_number": "TEST-IDENTIFIER",
    }

    # A caller who only knows the victim's phone number cannot update the victim.
    unauthenticated = await client.post("/api/v1/auth/kyc/customer", json=payload)
    assert unauthenticated.status_code in (401, 403)

    # Even with a valid session, a submitted victim phone cannot redirect the update.
    authenticated = await client.put(
        "/api/v1/auth/kyc/customer",
        json=payload,
        headers={"Authorization": f"Bearer {customer_token}"},
    )
    assert authenticated.status_code == 200, authenticated.text
    assert authenticated.json()["id"] == customer_user.id
    assert "not been submitted or verified" in authenticated.json()["message"].lower()

    await db_session.refresh(customer_user)
    await db_session.refresh(second_customer_user)

    assert customer_user.full_name == "Attempted Account Takeover"
    assert customer_user.email == "attacker-controlled@example.com"
    assert customer_user.is_verified is False

    assert second_customer_user.full_name == "Second Test Customer"
    assert second_customer_user.email != "attacker-controlled@example.com"
    assert second_customer_user.is_verified is False


@pytest.mark.asyncio
async def test_customer_kyc_rejects_invalid_bearer_even_when_phone_is_supplied(
    client,
    second_customer_user,
):
    response = await client.post(
        "/api/v1/auth/kyc/customer",
        json={
            "phone_number": second_customer_user.phone_number,
            "full_name": "Unauthenticated Update",
            "email": "unauthenticated@example.com",
        },
        headers={"Authorization": "Bearer invalid-token"},
    )
    assert response.status_code == 401
