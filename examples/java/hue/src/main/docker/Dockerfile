FROM --platform=linux/arm64/v8 ghcr.io/homematicip/alpine-jre:0.0.1
RUN apk add --update --no-cache openjdk11-jre

WORKDIR /app
RUN mkdir -p /data && chown -R daemon /data
RUN mkdir -p /app && chown -R daemon /app
COPY --chown=daemon:daemon maven/ /app/

ENTRYPOINT java -Xms8m -Xmx32m -XX:-TieredCompilation -Dplugin.hue.ws.server.host=host.containers.internal -Dfile.encoding=UTF-8 -jar /app/${project.artifactId}-${project.version}.jar