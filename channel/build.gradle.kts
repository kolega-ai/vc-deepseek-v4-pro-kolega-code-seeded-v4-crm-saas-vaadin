plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.vaadin") version "24.8.17"
}

dependencyManagement {
    imports {
        mavenBom("com.vaadin:vaadin-bom:24.8.17")
    }
}

dependencies {
    implementation(project(":common"))

    implementation("com.vaadin:vaadin-spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.apache.commons:commons-csv:1.12.0")
    implementation("com.thoughtworks.xstream:xstream:1.4.11.1")
    implementation("com.auth0:java-jwt:4.5.0")
    implementation("org.dom4j:dom4j:2.2.0")
    implementation("org.mvel:mvel2:2.4.4.Final")
    implementation("log4j:log4j:1.2.12")
    runtimeOnly("org.postgresql:postgresql")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("com.h2database:h2")
}

vaadin {
    productionMode = project.findProperty("vaadin.productionMode")?.toString()?.toBoolean() ?: false
}
