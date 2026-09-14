"""FastAPI app entrypoint.

Note on lifecycle: schema creation and user seeding are NOT done here. They
are dedicated one-shot containers in `docker-compose.yml` (`init-db`,
`seed-users`). The PR backend waits for both to complete before starting."""
from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .routers import auth, integration, purchase_requests, users


@asynccontextmanager
async def lifespan(_: FastAPI):
    yield


app = FastAPI(
    title="Purchase Request API",
    version="0.1.0",
    description=(
        "Case-study backend for the Purchase Request app. "
        "Auth uses an opaque cookie token. See `/docs` for the full API."
    ),
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router)
app.include_router(users.router)
app.include_router(purchase_requests.router)
app.include_router(integration.router)


@app.get("/health", tags=["meta"])
def health() -> dict:
    return {"status": "ok"}
