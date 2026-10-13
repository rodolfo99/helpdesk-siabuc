FROM debian:13-slim

# Debian slim excludes manual pages during package unpacking. Postfix's
# set-permissions follows the mailq/newaliases manual symlinks and fails if
# their targets are missing. Keep manuals before installing the packages;
# this later dpkg rule overrides slim's exclusion without masking failures.
RUN printf 'path-include=/usr/share/man/*\n' > /etc/dpkg/dpkg.cfg.d/zz-postfix-manpages

# Prevent package hooks from starting an SMTP process while building the image.
RUN printf '#!/bin/sh\nexit 101\n' > /usr/sbin/policy-rc.d \
    && chmod 755 /usr/sbin/policy-rc.d \
    && printf 'postfix postfix/main_mailer_type select No configuration\n' | debconf-set-selections \
    && apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
       postfix postfix-lmdb ca-certificates libsasl2-modules python3 \
    && test -s /usr/share/man/man1/sendmail.1.gz \
    && test -e /usr/share/man/man1/mailq.1.gz \
    && test -e /usr/share/man/man1/newaliases.1.gz \
    && rm -rf /var/lib/apt/lists/* \
    && rm /usr/sbin/policy-rc.d

COPY entrypoint.py healthcheck.py /opt/siabuc-smtp/
RUN chmod 755 /opt/siabuc-smtp/entrypoint.py /opt/siabuc-smtp/healthcheck.py

VOLUME ["/var/spool/postfix"]
EXPOSE 25
HEALTHCHECK --interval=10s --timeout=8s --start-period=15s --retries=6 \
    CMD ["python3", "/opt/siabuc-smtp/healthcheck.py"]
STOPSIGNAL SIGTERM
ENTRYPOINT ["python3", "/opt/siabuc-smtp/entrypoint.py"]
