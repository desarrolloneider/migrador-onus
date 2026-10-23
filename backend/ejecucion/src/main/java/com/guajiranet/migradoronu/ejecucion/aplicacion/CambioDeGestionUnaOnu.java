package com.guajiranet.migradoronu.ejecucion.aplicacion;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.guajiranet.migradoronu.comun.puertos.AuditAction;
import com.guajiranet.migradoronu.comun.puertos.AuditEntry;
import com.guajiranet.migradoronu.comun.puertos.AuditTrailPort;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Contexto;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Final;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Paso;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Resultado;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.GestionSmartOltPort;

/**
 * IP de gestión DHCP en la VLAN de gestión (46) de una ONU, por la API de SmartOLT: lo mismo que la pantalla
 * "Actualizar IP de gestión" (Mgmt IP mode = DHCP, VLAN-ID de gestión = 46), probado a mano en OLT_FONSECA 0/5/11
 * ONT 55 el 13 de octubre (hizo Inform en GenieACS un minuto después). No toca el internet del cliente (su
 * service-port y su WAN siguen igual). Lee antes, cambia una sola vez y verifica después; todo queda en la auditoría.
 */
public class CambioDeGestionUnaOnu {

    static final int VERIFICACIONES = 6;
    static final Duration ESPERA_VERIFICACION = Duration.ofSeconds(5);

    private final GestionSmartOltPort smartOlt;
    private final AuditTrailPort auditoria;
    private final Consumer<Duration> esperar;

    public CambioDeGestionUnaOnu(GestionSmartOltPort smartOlt, AuditTrailPort auditoria) {
        this(smartOlt, auditoria, d -> {
            try {
                Thread.sleep(d.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrumpido esperando a SmartOLT", e);
            }
        });
    }

    CambioDeGestionUnaOnu(GestionSmartOltPort smartOlt, AuditTrailPort auditoria, Consumer<Duration> esperar) {
        this.smartOlt = Objects.requireNonNull(smartOlt, "smartOlt");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.esperar = Objects.requireNonNull(esperar, "esperar");
    }

    /** ¿SmartOLT muestra ahora la gestión DHCP en esa VLAN? Si no se puede leer, false. */
    public boolean tieneGestionEn(String sn, int vlan) {
        try {
            return smartOlt.leer(sn).map(g -> g.tieneDhcpEn(vlan)).orElse(false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public Resultado ejecutar(TrabajoOnu t, int vlan, boolean real, Contexto ctx) {
        Registro r = new Registro(t, ctx);
        String posicion = t.oltCodigo() + " " + t.tarjeta() + "/" + t.pon() + " ONT " + t.onuId();
        Optional<EstadoGestionOnu> antes;
        try {
            antes = smartOlt.leer(t.sn());
        } catch (RuntimeException e) {
            return r.fin(real, Final.FALLIDA, "P02", "FALLO_SISTEMA_NO_RESPONDE", "No se pudo leer la ONU en SmartOLT: " + e.getMessage());
        }
        if (antes.isEmpty()) {
            r.paso("P03", "SmartOLT: leer ONU " + t.sn(), "", false, "SmartOLT no tiene esta ONU.");
            return r.fin(real, Final.FALLIDA, "P03", "FALLO_FOTO_PREVIA", "SmartOLT no tiene la ONU " + t.sn() + " (" + posicion + ").");
        }
        EstadoGestionOnu a = antes.get();
        r.paso("P03", "SmartOLT: leer ONU " + t.sn(), a.describir(), true, "Foto previa en SmartOLT: " + a.describir() + ".");
        if (a.tieneDhcpEn(vlan) && a.tr069()) {
            return r.fin(real, Final.YA_TENIA, "P03", null, "La ONU ya tiene la IP de gestión DHCP en la VLAN " + vlan
                    + " y el TR-069 activo: no hay nada que cambiar.");
        }
        if (!a.enLinea()) {
            return r.fin(real, Final.EXCLUIDA, "P03", "EXC_FUERA_DE_LINEA", "La ONU está fuera de línea en SmartOLT: no se toca.");
        }
        // SmartOLT solo acepta cambios con el "External ID" de la ONU; sin él responde "specify a valid ONU external ID"
        // (OLT_SAN_JUAN_2 1/4, 23 de octubre). No se toca: hay que ponérselo en SmartOLT.
        if (a.idExterno() == null || a.idExterno().isBlank()) {
            if (!real) {
                r.paso("SIM", "SmartOLT: External ID = SN", "", true, "Simulación: no se mandó.");
            } else {
                try {
                    String id = smartOlt.ponerExternalIdIgualAlSn(t.sn());
                    r.paso("P04", "SmartOLT: External ID = " + id + " (update_unique_external_id_by_sn)", "OK", true,
                            "La ONU no tenía External ID en SmartOLT: se le puso su SN.");
                    a = new EstadoGestionOnu(id, a.enLinea(), a.modo(), a.vlan(), a.ip(), a.tr069());
                } catch (RuntimeException e) {
                    r.paso("P04", "SmartOLT: External ID = SN", String.valueOf(e.getMessage()), false, "SmartOLT no le puso el External ID.");
                    return r.fin(real, Final.EXCLUIDA, "P03", "EXC_SIN_EXTERNAL_ID", "La ONU no tiene External ID en SmartOLT y no se le pudo"
                            + " poner (" + e.getMessage() + "): no se tocó nada más.");
                }
            }
        }
        if (a.tieneDhcpEn(vlan)) {
            // Ya tiene la VLAN pero no el TR-069 (así quedaban antes): solo falta activarlo.
            return conTr069(r, a, real, posicion, vlan);
        }
        String comando = "SmartOLT: IP de gestión DHCP, VLAN " + vlan + " (set_onu_mgmt_ip_dhcp " + a.idExterno() + ")";
        if (!real) {
            r.paso("SIM", comando, "", true, "Simulación: no se mandó.");
            return r.fin(false, Final.SIMULADA, "P05", null, "Simulación terminada: la ONU cumple las condiciones; no se cambió nada.");
        }
        try {
            smartOlt.activarDhcp(a.idExterno(), vlan);
            r.paso("P05", comando, "OK", true, "SmartOLT aceptó el cambio.");
        } catch (RuntimeException e) {
            r.paso("P05", comando, String.valueOf(e.getMessage()), false, "SmartOLT no aceptó el cambio.");
            // No se sabe si quedó: se lee una vez antes de decir que falló.
            Optional<EstadoGestionOnu> ahora = leerSinFallar(t.sn());
            if (ahora.map(x -> x.tieneDhcpEn(vlan)).orElse(false)) {
                r.paso("P06", "SmartOLT: leer ONU " + t.sn(), ahora.get().describir(), true,
                        "SmartOLT respondió con error, pero la ONU quedó con la IP de gestión DHCP en la VLAN " + vlan + ".");
                return conTr069(r, ahora.get(), true, posicion, vlan);
            }
            return r.fin(true, Final.FALLIDA, "P05", "FALLO_OLT_RECHAZO", "SmartOLT no aplicó el cambio: " + e.getMessage());
        }
        // SmartOLT a veces tarda en mostrar el cambio: se lee hasta que aparezca.
        EstadoGestionOnu despues = null;
        for (int i = 0; i < VERIFICACIONES; i++) {
            Optional<EstadoGestionOnu> leida = leerSinFallar(t.sn());
            if (leida.isPresent()) {
                despues = leida.get();
                if (despues.tieneDhcpEn(vlan)) {
                    break;
                }
            }
            esperar.accept(ESPERA_VERIFICACION);
        }
        boolean quedo = despues != null && despues.tieneDhcpEn(vlan);
        r.paso("P06", "SmartOLT: leer ONU " + t.sn(), despues == null ? "" : despues.describir(), quedo,
                quedo ? "Verificado en SmartOLT: " + despues.describir() + "." : "SmartOLT no muestra la IP de gestión DHCP en la VLAN " + vlan + ".");
        if (!quedo) {
            return r.fin(true, Final.FALLIDA, "P06", "FALLO_VERIFICACION", "SmartOLT aceptó el cambio pero no lo muestra: "
                    + (despues == null ? "no se pudo leer la ONU" : despues.describir()) + ".");
        }
        return conTr069(r, despues, true, posicion, vlan);
    }

    /** El perfil TR-069 que SmartOLT activa por la IP de gestión (el mismo que se escoge a mano). */
    static final String PERFIL_TR069_SMARTOLT = "SmartOLT";

    /**
     * P07: activar el TR-069 por la IP de gestión, como hace SmartOLT a mano ("TR069 Profile: SmartOLT vía IP de
     * gestión"). Sin esto la ONU no pide IP por la VLAN. Si la OLT no tiene TR-069 en SmartOLT, se termina igual y se avisa.
     */
    private Resultado conTr069(Registro r, EstadoGestionOnu a, boolean real, String posicion, int vlan) {
        String comando = "SmartOLT: TR-069 perfil " + PERFIL_TR069_SMARTOLT + " por IP de gestión (enable_tr069 " + a.idExterno() + ")";
        if (!real) {
            r.paso("SIM", comando, "", true, "Simulación: no se mandó.");
            return r.fin(false, Final.SIMULADA, "P07", null, "Simulación terminada: la ONU cumple las condiciones; no se cambió nada.");
        }
        String aviso = "";
        if (a.tr069()) {
            r.paso("P07", comando, "", true, "El TR-069 ya estaba activo.");
        } else {
            try {
                try {
                    smartOlt.activarTr069(a.idExterno(), PERFIL_TR069_SMARTOLT);
                } catch (RuntimeException ocupada) {
                    // "OLT wasn't able to process the command": la OLT estaba ocupada. Se espera y se intenta una vez más.
                    if (!String.valueOf(ocupada.getMessage()).contains("olt_unable_to_process_command")) {
                        throw ocupada;
                    }
                    r.paso("P07", comando, String.valueOf(ocupada.getMessage()), true, "La OLT estaba ocupada: se reintenta en 15 s.");
                    esperar.accept(Duration.ofSeconds(15));
                    smartOlt.activarTr069(a.idExterno(), PERFIL_TR069_SMARTOLT);
                }
                r.paso("P07", comando, "OK", true, "SmartOLT activó el TR-069 por la IP de gestión.");
            } catch (RuntimeException e) {
                boolean sinTr069EnOlt = String.valueOf(e.getMessage()).toLowerCase(java.util.Locale.ROOT).contains("not enabled for this olt");
                r.paso("P07", comando, String.valueOf(e.getMessage()), sinTr069EnOlt, sinTr069EnOlt
                        ? "Esta OLT no tiene TR-069 en SmartOLT: queda solo la IP de gestión." : "SmartOLT no activó el TR-069.");
                if (!sinTr069EnOlt) {
                    return r.fin(true, Final.FALLIDA, "P07", "FALLO_OLT_RECHAZO", "La IP de gestión quedó en la VLAN " + vlan
                            + ", pero SmartOLT no activó el TR-069: " + e.getMessage() + ". Vuelve a intentarlo.");
                }
                aviso = " (esta OLT no tiene TR-069 en SmartOLT)";
            }
        }
        return r.fin(true, Final.COMPLETADA, "P07", null, "Gestión lista: " + posicion + " quedó con IP de gestión DHCP en la VLAN " + vlan
                + " y TR-069 por la IP de gestión" + aviso + ". En pocos minutos debería tener IP y reportarse en GenieACS.");
    }

    private Optional<EstadoGestionOnu> leerSinFallar(String sn) {
        try {
            return smartOlt.leer(sn);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Los pasos de una ejecución y su auditoría (igual que en el cambio de perfil). */
    private final class Registro {
        private final TrabajoOnu t;
        private final Contexto ctx;
        private final List<Paso> pasos = new ArrayList<>();
        private String ultimoPaso = "P02";

        Registro(TrabajoOnu t, Contexto ctx) {
            this.t = t;
            this.ctx = ctx;
        }

        void paso(String codigo, String comando, String respuesta, boolean bien, String mensaje) {
            pasos.add(new Paso(codigo, comando, respuesta, bien, mensaje));
            boolean simulado = codigo.equals("SIM");
            if (!simulado && !codigo.equals(ultimoPaso)) {
                ultimoPaso = codigo;
                try {
                    ctx.alAvanzar().accept(codigo);
                } catch (RuntimeException ignorada) {
                    // Que no se pueda mostrar el avance no detiene el cambio.
                }
            }
            Map<String, Object> peticion = new LinkedHashMap<>();
            peticion.put("comando", comando);
            peticion.put("onu", t.oltCodigo() + " " + t.tarjeta() + "/" + t.pon() + " ONT " + t.onuId());
            auditoria.record(base(simulado ? AuditAction.PASO_SIMULADO : bien ? AuditAction.COMANDO_RESPONDIDO : AuditAction.PASO_FALLIDO)
                    .paso(simulado ? null : codigo).peticion(peticion).respuesta(Map.of("texto", respuesta == null ? "" : respuesta))
                    .resultado(bien ? "OK" : "FALLO").mensaje(mensaje).build());
        }

        Resultado fin(boolean real, Final fin, String paso, String codigo, String mensaje) {
            AuditAction accion = switch (fin) {
                case COMPLETADA -> AuditAction.ONU_COMPLETADA;
                case FALLIDA -> AuditAction.ONU_FALLIDA;
                case EXCLUIDA, YA_TENIA -> AuditAction.ONU_EXCLUIDA_AL_EJECUTAR;
                case SIMULADA -> AuditAction.PASO_SIMULADO;
            };
            auditoria.record(base(accion).paso(paso).codigo(codigo).peticion(Map.of("onu", t.oltCodigo() + " " + t.tarjeta() + "/" + t.pon()
                    + " ONT " + t.onuId(), "real", real, "cambio", "VLAN de gestión")).resultado(fin.name()).mensaje(mensaje).build());
            return new Resultado(real, fin, mensaje, t.sn(), codigo, paso, List.copyOf(pasos));
        }

        private AuditEntry.Builder base(AuditAction accion) {
            return AuditEntry.builder(ctx.actor(), accion).usuarioId(ctx.usuarioId()).loteId(ctx.loteId())
                    .ejecucionId(ctx.ejecucionId()).sn(t.sn()).sistema("SmartOLT");
        }
    }
}
