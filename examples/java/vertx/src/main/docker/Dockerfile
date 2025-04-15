FROM --platform=linux/arm64/v8 alpine:3.21
RUN apk add --update --no-cache openjdk11-jre
COPY maven/ /app/
WORKDIR /app
ENTRYPOINT java -Xmx32M -jar /app/${project.artifactId}-${project.version}.jar
LABEL de.eq3.hmip.plugin.metadata="{\"pluginId\":\"org.example.plugin.java\", \
  \"version\":\"${project.version}\", \
  \"hcuMinVersion\":\"1.4.0\", \
  \"friendlyName\":{\"de\":\"SDK Plugin\",\"en\":\"SDK Plugin\"}, \
  \"changelog\": \"New Features\",\
  \"issuer\":\"Developer\", \
  \"description\":{\"de\":\"SDK Plugin Beispiel\",\"en\":\"SDK Plugin Example\"}, \
  \"scope\":\"LOCAL\", \
  \"logsEnabled\": true, \
  \"image\":null \
  }"