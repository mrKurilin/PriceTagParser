FROM eclipse-temurin:21-jre
EXPOSE 8080
WORKDIR /app
COPY build/libs/fat.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
