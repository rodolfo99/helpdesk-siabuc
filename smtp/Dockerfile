FROM alpine:3.24.3

# Alpine supplies Postfix with LMDB maps and Cyrus SASL client mechanisms.
# Runtime packages are upgraded before scanning the final image.
RUN apk upgrade --no-cache \
    && apk add --no-cache postfix ca-certificates cyrus-sasl-login python3

COPY entrypoint.py healthcheck.py /opt/siabuc-smtp/
RUN chmod 755 /opt/siabuc-smtp/entrypoint.py /opt/siabuc-smtp/healthcheck.py

VOLUME ["/var/spool/postfix"]
EXPOSE 25
HEALTHCHECK --interval=10s --timeout=8s --start-period=15s --retries=6 \
    CMD ["python3", "/opt/siabuc-smtp/healthcheck.py"]
STOPSIGNAL SIGTERM
ENTRYPOINT ["python3", "/opt/siabuc-smtp/entrypoint.py"]
