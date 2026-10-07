/**
 * Clientes de bajo nivel, sin reglas de negocio (arquitectura 5.8): SmartOltClient y WisproClient sobre RestClient,
 * y OltConsoleSession con transporte SSH (Apache MINA SSHD) o Telnet (Apache Commons Net) según PC-35. Manejan
 * conexión, espera del prompt y lectura del texto; no saben qué es un perfil TR-069.
 */
package com.guajiranet.migradoronu.integraciones;
