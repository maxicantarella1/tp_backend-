# =====================================================================
# Etapa 1: Compilación (instala common-lib y empaqueta reservas-service)
# Se ejecuta con contexto en la raíz del repositorio (context: .)
# =====================================================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /workspace

# 1) Compilar e instalar nuestra librería común en el repositorio .m2 del contenedor
COPY common-lib/pom.xml common-lib/pom.xml
COPY common-lib/src common-lib/src
RUN mvn -f common-lib/pom.xml clean install -DskipTests -B

# 2) Descargar dependencias y empaquetar el microservicio de Reservas
COPY ReservaSpring/pom.xml ReservaSpring/pom.xml
COPY ReservaSpring/src ReservaSpring/src
RUN mvn -f ReservaSpring/pom.xml clean package -DskipTests -B

# =====================================================================
# Etapa 2: Imagen liviana de ejecución (solo JRE 17 + el JAR generado)
# =====================================================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/ReservaSpring/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
