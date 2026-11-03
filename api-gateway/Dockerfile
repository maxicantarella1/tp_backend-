# =====================================================================
# Etapa 1: Compilación (instala common-lib y empaqueta api-gateway)
# Se ejecuta con contexto en la raíz del repositorio (context: .)
# =====================================================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /workspace

# 1) Compilar e instalar nuestra librería común en el repositorio .m2 del contenedor
COPY common-lib/pom.xml common-lib/pom.xml
COPY common-lib/src common-lib/src
RUN mvn -f common-lib/pom.xml clean install -DskipTests -B

# 2) Descargar dependencias y empaquetar el API Gateway
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY api-gateway/src api-gateway/src
RUN mvn -f api-gateway/pom.xml clean package -DskipTests -B

# =====================================================================
# Etapa 2: Imagen liviana de ejecución (solo JRE 17 + el JAR generado)
# =====================================================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/api-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
