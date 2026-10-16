package com.guajiranet.migradoronu.comun.puertos;

/**
 * Catálogo cerrado de acciones de auditoría (arquitectura 10.1). Es el vocabulario con que todos los módulos
 * registran eventos por {@link AuditTrailPort}; vive aquí para que un nombre mal escrito no compile.
 * Una acción nueva se agrega a este catálogo, nunca como texto suelto.
 */
public enum AuditAction {

    SESION_INICIADA(Group.ACCESO),
    SESION_FALLIDA(Group.ACCESO),
    USUARIO_BLOQUEADO(Group.ACCESO),
    SESION_CERRADA(Group.ACCESO),
    SESION_VENCIDA(Group.ACCESO),
    USUARIO_CREADO(Group.ACCESO),
    USUARIO_DESACTIVADO(Group.ACCESO),
    USUARIO_REACTIVADO(Group.ACCESO),
    CLAVE_CAMBIADA(Group.ACCESO),
    /** Un administrador cambió el nombre, los módulos o la marca de administrador de un usuario. */
    USUARIO_PERMISOS_CAMBIADOS(Group.ACCESO),
    /** Un administrador restableció la contraseña de un usuario (contraseña temporal, cambio obligatorio). */
    CLAVE_RESTABLECIDA(Group.ACCESO),

    VISTA_PREVIA_ARMADA(Group.LOTES),
    VISTA_PREVIA_FALLIDA(Group.LOTES),
    VISTA_PREVIA_DESCARTADA(Group.LOTES),
    VISTA_PREVIA_VENCIDA(Group.LOTES),
    /** No estaba en la arquitectura v3.0; RNF-15 pide registrar toda exportación. */
    VISTA_PREVIA_EXPORTADA(Group.LOTES),
    LOTE_CONFIRMADO(Group.LOTES),
    LOTE_INICIADO(Group.LOTES),
    LOTE_DETENIENDOSE(Group.LOTES),
    LOTE_DETENIDO(Group.LOTES),
    LOTE_DETENIDO_AUTOMATICO(Group.LOTES),
    LOTE_TERMINADO(Group.LOTES),
    /** Las fallidas de un lote terminado vuelven a pendientes en el mismo lote. */
    LOTE_REINTENTADO(Group.LOTES),

    ONU_TOMADA(Group.EJECUCION),
    ONU_EXCLUIDA_AL_EJECUTAR(Group.EJECUCION),
    SESION_OLT_ABIERTA(Group.EJECUCION),
    SESION_OLT_CERRADA(Group.EJECUCION),
    FOTOGRAFIA_GUARDADA(Group.EJECUCION),
    PASO_INICIADO(Group.EJECUCION),
    PASO_YA_ESTABA(Group.EJECUCION),
    COMANDO_PLANEADO(Group.EJECUCION),
    PASO_SIMULADO(Group.EJECUCION),
    COMANDO_ENVIADO(Group.EJECUCION),
    COMANDO_RESPONDIDO(Group.EJECUCION),
    PREGUNTA_OLT_RESPONDIDA(Group.EJECUCION),
    PASO_VERIFICADO(Group.EJECUCION),
    PASO_FALLIDO(Group.EJECUCION),
    ONU_COMPLETADA(Group.EJECUCION),
    ONU_FALLIDA(Group.EJECUCION),
    ONU_INTERRUMPIDA(Group.EJECUCION),
    ONU_REVISADA(Group.EJECUCION),

    PARAMETRO_CAMBIADO(Group.CONFIGURACION_Y_SISTEMA),
    TARJETAS_OLT_CAMBIADAS(Group.CONFIGURACION_Y_SISTEMA),
    ACCESO_OLT_CAMBIADO(Group.CONFIGURACION_Y_SISTEMA),
    HUELLA_OLT_FIJADA(Group.CONFIGURACION_Y_SISTEMA),
    EJECUTOR_INICIADO(Group.CONFIGURACION_Y_SISTEMA),
    EJECUTOR_DETENIDO(Group.CONFIGURACION_Y_SISTEMA),
    INTEGRIDAD_VERIFICADA(Group.CONFIGURACION_Y_SISTEMA),
    INTEGRIDAD_ROTA(Group.CONFIGURACION_Y_SISTEMA),
    AUDITORIA_EXPORTADA(Group.CONFIGURACION_Y_SISTEMA),
    HISTORIAL_EXPORTADO(Group.CONFIGURACION_Y_SISTEMA);

    public enum Group { ACCESO, LOTES, EJECUCION, CONFIGURACION_Y_SISTEMA }

    private final Group group;

    AuditAction(Group group) {
        this.group = group;
    }

    public Group group() {
        return group;
    }
}
