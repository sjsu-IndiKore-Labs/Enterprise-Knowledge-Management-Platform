from fastapi import FastAPI

app = FastAPI(title="fixture ai-service")


@app.get("/health")
def health():
    return {"status": "UP", "provider": "fake"}
