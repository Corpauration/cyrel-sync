FROM registry.access.redhat.com/ubi8/openjdk-17:1.14

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en'


# We make four distinct layers so if there are application changes the library layers can be re-used
COPY --chown=185 build/libs/*.jar /deployments/cyrel-sync.jar

USER 185
ENV AB_JOLOKIA_OFF=""
ENV JAVA_APP_JAR="/deployments/cyrel-sync.jar"