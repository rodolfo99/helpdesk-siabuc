FROM postgres:17-alpine
RUN apk add --no-cache python3 tzdata
COPY worker.py /opt/siabuc-backup/worker.py
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
ENTRYPOINT ["python3", "/opt/siabuc-backup/worker.py"]
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 CMD python3 /opt/siabuc-backup/worker.py --health
