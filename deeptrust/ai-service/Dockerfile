FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app/ ./app/

# Runs as a non-root user — this service accepts arbitrary uploaded files,
# so minimizing container privilege matters.
RUN useradd -m deeptrust && chown -R deeptrust /app
USER deeptrust

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
