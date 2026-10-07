# ADR 0001 · Java 25 y Spring Boot 4.1

- **Estado:** aceptada
- **Fecha:** 5 de octubre de 2026
- **Relacionada con:** DEC-35 de la spec, sección 7.1 de la arquitectura

## Contexto

La arquitectura v3.0 dice "Java 21 (LTS)" y "Spring Boot 3". El pedido fue usar la última versión de Java y la última de Spring Boot.

A la fecha:

| Componente | Última publicada | Observación |
|---|---|---|
| Java | 27 (septiembre de 2026) | No es LTS: soporte de 6 meses |
| Java LTS | 25 (septiembre de 2025) | Soporte largo |
| Spring Boot | 4.1.1 estable (4.2.0 en milestone) | Compatible con Java 17 hasta Java 26 inclusive |

## Decisión

- **Spring Boot 4.1.1**, la última estable. Trae Spring Framework 7.0, Hibernate 7.4, Liquibase 5.0, Testcontainers 2.0, JUnit 6 y Jackson 3.
- **Java 25**, el LTS vigente.

No se usa Java 27 porque Spring Boot 4.1.1 solo garantiza compatibilidad hasta Java 26, y porque Java 27 no tiene soporte largo. Para una herramienta que cambia configuración en las OLT de producción, una versión sin garantía de compatibilidad no es aceptable. Java 26 tampoco: no es LTS y su soporte ya terminó.

## Consecuencias

- La versión está en una sola propiedad (`java.version` en el `pom.xml` raíz). Subir de versión es cambiar esa línea cuando Spring Boot declare compatibilidad.
- Hay cambios frente a lo que asume la arquitectura v3.0 con Spring Boot 3:
  - Los starters web se llaman `spring-boot-starter-webmvc`.
  - Testcontainers 2 movió `PostgreSQLContainer` a `org.testcontainers.postgresql`.
  - Spring 7 ya no copia el mensaje de PostgreSQL en el texto de la excepción traducida: las pruebas revisan la causa raíz.
- Si el equipo fija otra versión para el conjunto de Contact Center, se alinea aquí.
