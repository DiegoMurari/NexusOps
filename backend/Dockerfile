FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

RUN apk add --no-cache maven bash

# Copy all source
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY nexusops-shared-kernel/ nexusops-shared-kernel/
COPY nexusops-iam/ nexusops-iam/
COPY nexusops-platform/ nexusops-platform/
COPY nexusops-sla/ nexusops-sla/
COPY nexusops-ticketing/ nexusops-ticketing/
COPY nexusops-asset/ nexusops-asset/
COPY nexusops-knowledge/ nexusops-knowledge/
COPY nexusops-notification/ nexusops-notification/
COPY nexusops-reporting/ nexusops-reporting/
COPY nexusops-integration/ nexusops-integration/
COPY nexusops-bootstrap/ nexusops-bootstrap/

# Install parent first
RUN ./mvnw install -N -DskipTests -Dmaven.javadoc.skip=true -B

# Install shared-kernel
RUN ./mvnw install -pl nexusops-shared-kernel -DskipTests -Dmaven.javadoc.skip=true -B

# Install platform
RUN ./mvnw install -pl nexusops-platform -DskipTests -Dmaven.javadoc.skip=true -B

# Install iam
RUN ./mvnw install -pl nexusops-iam -DskipTests -Dmaven.javadoc.skip=true -B

# Install sla
RUN ./mvnw install -pl nexusops-sla -DskipTests -Dmaven.javadoc.skip=true -B

# Install ticketing
RUN ./mvnw install -pl nexusops-ticketing -DskipTests -Dmaven.javadoc.skip=true -B

# Install asset
RUN ./mvnw install -pl nexusops-asset -DskipTests -Dmaven.javadoc.skip=true -B

# Install knowledge
RUN ./mvnw install -pl nexusops-knowledge -DskipTests -Dmaven.javadoc.skip=true -B

# Install notification
RUN ./mvnw install -pl nexusops-notification -DskipTests -Dmaven.javadoc.skip=true -B

# Install reporting
RUN ./mvnw install -pl nexusops-reporting -DskipTests -Dmaven.javadoc.skip=true -B

# Install integration
RUN ./mvnw install -pl nexusops-integration -DskipTests -Dmaven.javadoc.skip=true -B

# Build bootstrap with all dependencies (fat JAR)
RUN ./mvnw package -pl nexusops-bootstrap -DskipTests -Dmaven.javadoc.skip=true -B

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /workspace/nexusops-bootstrap/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]