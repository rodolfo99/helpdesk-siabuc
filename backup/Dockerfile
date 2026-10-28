FROM postgres:17-alpine
# The worker invokes pg_dump/pg_restore directly and never uses gosu or the
# database-server entrypoint. Remove the unused Go binary from this runtime.
RUN apk upgrade --no-cache \
    && apk add --no-cache python3 tzdata \
    && rm /usr/local/bin/gosu
COPY worker.py /opt/siabuc-backup/worker.py
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
ENTRYPOINT ["python3", "/opt/siabuc-backup/worker.py"]
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 CMD python3 /opt/siabuc-backup/worker.py --health
