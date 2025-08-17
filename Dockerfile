FROM eclipse-temurin:17-jre

ARG VERSION=1.48.0

WORKDIR /app

# Copy JARs
COPY ../../../aeron-all/build/libs/aeron-all-${VERSION}.jar ./aeron-all.jar
COPY ../../../aeron-agent/build/libs/aeron-agent-${VERSION}.jar ./aeron-agent.jar


COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]

