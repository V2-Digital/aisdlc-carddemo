# GnuCOBOL harness for CardDemo batch programs.
# Debian trixie ships GnuCOBOL 3.2.0 built with the Berkeley DB (BDB) indexed
# file handler, and OpenJDK 21 is one `apt-get install` away for the agent image.
FROM debian:trixie-slim

RUN apt-get update \
 && apt-get install -y --no-install-recommends gnucobol3 \
 && rm -rf /var/lib/apt/lists/* \
 && cobc --version | head -1 \
 && cobc --info | grep -q 'indexed file handler *: BDB'

ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8
