from typing import TypeVar, Generic, Optional, List, Any
from pydantic import BaseModel, Field

T = TypeVar("T")

class ApiResponse(BaseModel, Generic[T]):
    """Standardized top-level API envelope following DRY rules."""
    success: bool = Field(default=True, description="Indicates if the operation completed successfully")
    message: Optional[str] = Field(default=None, description="Human-readable response message")
    data: Optional[T] = Field(default=None, description="Response payload")
    error: Optional[str] = Field(default=None, description="Error code or description if failed")

class PaginationMeta(BaseModel):
    """Standard pagination metadata for listing endpoints."""
    total_count: int = Field(..., description="Total available records matching query")
    page: int = Field(default=1, ge=1, description="Current page number")
    page_size: int = Field(default=20, ge=1, le=100, description="Records per page")
    total_pages: int = Field(..., description="Total pages available")

class PaginatedResponse(BaseModel, Generic[T]):
    """Standard paginated collection response."""
    items: List[T] = Field(default_factory=list)
    pagination: PaginationMeta

class ErrorDetail(BaseModel):
    """Standardized error contract for all API responses."""
    code: str
    message: str
    field: Optional[str] = None
    correlation_id: Optional[str] = None

