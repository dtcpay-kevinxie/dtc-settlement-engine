#!/bin/sh -e

# This entrypoint script passes program parameters
# to the Java application and make sures that it runs
# as PID 1 so it can receive KILL signals.
# In addition, it will also expose a JMX endpoint
# via port JMX_PORT if the JMX_ENABLED environment variable
# is set to TRUE

cd /application

JMX_OPTS=""

# Enable JMX if required
if [ "$JMX_ENABLED" = "TRUE" ] || [ "$JMX_ENABLED" = "true" ]; then

    JMX_OPTS="-Dcom.sun.management.jmxremote=true \
              -Dcom.sun.management.jmxremote.port=${JMX_PORT} \
              -Dcom.sun.management.jmxremote.rmi.port=${JMX_PORT} \
              -Dcom.sun.management.jmxremote.local.only=false \
              -Dcom.sun.management.jmxremote.authenticate=false \
              -Dcom.sun.management.jmxremote.ssl=false \
              -Djava.rmi.server.hostname=localhost"

fi

JAVA_OPTS="${JAVA_OPTS} ${JMX_OPTS}"

exec java -jar $JAVA_OPTS *.jar "$@"
