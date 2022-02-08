FROM docker.io/gradle:jdk11 as builder

ADD settings.gradle /wehe-cmdline/
ADD build.gradle /wehe-cmdline/app/
ADD src /wehe-cmdline/app/src/main/java
WORKDIR /wehe-cmdline
RUN gradle build

FROM openjdk:11
COPY --from=builder /wehe-cmdline/app/build/libs/app.jar /wehe/
WORKDIR /wehe
ENTRYPOINT ["java", "-jar", "app.jar"]
