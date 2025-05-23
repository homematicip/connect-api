FROM --platform=linux/arm64/v8 ghcr.io/homematicip/alpine-jre:0.0.1
COPY maven/ /app/
WORKDIR /app
ENTRYPOINT java -Xmx32M -jar /app/${project.artifactId}-${project.version}.jar
LABEL de.eq3.hmip.plugin.metadata="{\"pluginId\":\"org.example.plugin.java\", \
  \"version\":\"${project.version}\", \
  \"hcuMinVersion\":\"1.4.7\", \
  \"friendlyName\":{\"de\":\"Java-Beispiel\",\"en\":\"Java Example\"}, \
  \"changelog\": \"New Features\",\
  \"issuer\":\"Developer\", \
  \"description\":{\"de\":\"Dieses Beispiel-Plugin wurde mit Java und Vert.x erstellt.\",\"en\":\"This example plugin was built with Java and Vert.x\"}, \
  \"scope\":\"LOCAL\", \
  \"logsEnabled\": true, \
  \"image\":null \
  }"