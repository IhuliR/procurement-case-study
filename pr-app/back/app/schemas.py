"""Pydantic request/response models."""
from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, EmailStr, Field

Role = Literal["employee", "finance"]
PRStatus = Literal["initiated", "sent for approval", "approved", "rejected"]


class LoginIn(BaseModel):
    username: str
    password: str


class UserCreate(BaseModel):
    username: str = Field(min_length=2, max_length=80)
    password: str = Field(min_length=6, max_length=200)
    role: Role


class UserEdit(BaseModel):
    username: str | None = None
    password: str | None = None
    role: Role | None = None


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    username: str
    role: Role


class PurchaseRequestCreate(BaseModel):
    request_name: str = Field(min_length=1, max_length=200)
    supplier_name: str = Field(min_length=1, max_length=200)
    supplier_email: EmailStr
    request_details: str = Field(min_length=1)


class PurchaseRequestUpdate(BaseModel):
    request_name: str | None = None
    supplier_name: str | None = None
    supplier_email: EmailStr | None = None
    request_details: str | None = None
    request_approval_status: PRStatus | None = None


class PurchaseRequestOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    request_author: str
    request_name: str
    request_code: str
    supplier_name: str
    supplier_email: str
    request_details: str
    request_approval_status: PRStatus
    created_at: datetime
    updated_at: datetime


class PurchaseRequestInvoiceOption(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    request_code: str
    request_name: str
    supplier_name: str


class PurchaseRequestInvoiceContext(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    request_code: str
    supplier_name: str
