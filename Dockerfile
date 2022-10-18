FROM openjdk:17-slim-buster

# Expose the following HTTP and HTTPS ports, respectively
EXPOSE 8080
EXPOSE 8443

# Set the directory where we will add and run the binary
WORKDIR /app

RUN apt-get update && \
    apt-get install -y ttf-dejavu libfreetype6 fontconfig && \
    apt-get clean

# Add non-root user which will be used when running the java app
RUN adduser --disabled-login --disabled-password --gecos GECOS dtcuser && \
  chown -R dtcuser:dtcuser /app

# Set the non-root user as default user when running the container
# and add the binary to the app folder
USER dtcuser
ADD target/*.jar /app/

# Create a symlink to the actual jar file
RUN ln -s *.jar app.jar

# Entrypoint to run the app
ENTRYPOINT ["java","-jar","-Djdk.tls.ephemeralDHKeySize=2048","-Dsun.net.inetaddr.ttl=5","app.jar"]
