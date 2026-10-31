"""
Centralized, standardized Pydantic API Schemas & Data Contracts.
Follows DRY (Don't Repeat Yourself) rules across all backend routes and clients.
"""

from backend.api.schemas.common import (
    ApiResponse,
    PaginationMeta,
    PaginatedResponse,
    ErrorDetail
)

from backend.api.schemas.auth import (
    IdentityLookupPayload,
    OtpRequestPayload,
    OtpVerifyPayload,
    TokenRefreshPayload,
    PasswordRegisterPayload,
    PasswordLoginPayload,
    AdminMfaEnablePayload,
    OAuthPayload,
    UserDto,
    AuthResponse
)

from backend.api.schemas.vehicles import (
    VehicleCategoryDto,
    VehicleDto
)

from backend.api.schemas.fares import (
    FareEstimateRequest,
    FareEstimateResponse
)

__all__ = [
    "ApiResponse",
    "PaginationMeta",
    "PaginatedResponse",
    "ErrorDetail",
    "IdentityLookupPayload",
    "OtpRequestPayload",
    "OtpVerifyPayload",
    "TokenRefreshPayload",
    "PasswordRegisterPayload",
    "PasswordLoginPayload",
    "AdminMfaEnablePayload",
    "OAuthPayload",
    "UserDto",
    "AuthResponse",
    "VehicleCategoryDto",
    "VehicleDto",
    "FareEstimateRequest",
    "FareEstimateResponse"
]

