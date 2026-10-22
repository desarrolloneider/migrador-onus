package com.guajiranet.migradoronu.conciliacion.infraestructura.configuracion;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.guajiranet.migradoronu.conciliacion.aplicacion.ClasificarOnusUseCase;
import com.guajiranet.migradoronu.conciliacion.aplicacion.ConsultarOnusDeOlt;
import com.guajiranet.migradoronu.conciliacion.infraestructura.salida.smartolt.SmartOltInventoryAdapter;
import com.guajiranet.migradoronu.conciliacion.infraestructura.salida.wispro.WisproDirectoryAdapter;
import com.guajiranet.migradoronu.conciliacion.puertos.entrada.ClassifyOnusPort;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.OltProfilesQueryPort;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.OltSettingsQueryPort;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.OnuParticipationQueryPort;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.TargetProfilePort;
import com.guajiranet.migradoronu.integraciones.smartolt.SmartOltClient;
import com.guajiranet.migradoronu.integraciones.wispro.WisproClient;

/**
 * Arma el módulo conciliacion. Los clientes de SmartOLT y Wispro se crean la primera vez que se usan: sin tokens la
 * aplicación arranca igual y la vista previa responde con un mensaje claro.
 */
@Configuration(proxyBeanMethods = false)
public class ConciliacionConfig {

    @Bean
    SmartOltInventoryAdapter smartOltInventario(Clock reloj, @Value("${SMARTOLT_BASE_URL:}") String url,
                                                @Value("${SMARTOLT_API_TOKEN:}") String token) {
        return new SmartOltInventoryAdapter(unaVez(() -> new SmartOltClient(url, token, Duration.ofSeconds(60))), reloj);
    }

    @Bean
    ConsultarOnusDeOlt consultarOnusDeOlt(SmartOltInventoryAdapter smartOlt, OltProfilesQueryPort perfilesOlt,
            OnuParticipationQueryPort participacion,
            org.springframework.beans.factory.ObjectProvider<com.guajiranet.migradoronu.conciliacion.puertos.salida.UltimoReporteAcsPort> acs) {
        return new ConsultarOnusDeOlt(smartOlt, perfilesOlt, () -> {
            java.util.Set<String> r = new java.util.HashSet<>(participacion.servicioWanTerminado().keySet());
            var genie = acs.getIfAvailable();
            if (genie != null) {
                try {
                    r.addAll(genie.conServicioWanListo());
                } catch (RuntimeException e) {
                    // Sin GenieACS se marcan solo las que terminó la herramienta.
                }
            }
            return r;
        });
    }

    @Bean
    com.guajiranet.migradoronu.conciliacion.aplicacion.EstadoDeOlts estadoDeOlts(SmartOltInventoryAdapter smartOlt,
            WisproDirectoryAdapter wisproDirectorio, OltSettingsQueryPort olts, OltProfilesQueryPort perfilesOlt,
            TargetProfilePort perfil, Clock reloj, @Value("${MIGRADOR_VLAN_GESTION:46}") int vlanGestion,
            org.springframework.beans.factory.ObjectProvider<com.guajiranet.migradoronu.conciliacion.puertos.salida.UltimoReporteAcsPort> acs) {
        var estado = new com.guajiranet.migradoronu.conciliacion.aplicacion.EstadoDeOlts(smartOlt, wisproDirectorio, olts, perfilesOlt,
                perfil::perfilDestino, reloj, vlanGestion);
        // Las migradas salen de GenieACS (TR069_INTERNET), con su copia de 2 minutos.
        acs.ifAvailable(a -> estado.conMigradas(a::conServicioWanListo));
        return estado;
    }

    @Bean
    WisproDirectoryAdapter wisproDirectorio(Clock reloj, @Value("${WISPRO_BASE_URL:}") String url,
                                            @Value("${WISPRO_API_TOKEN:}") String token) {
        return new WisproDirectoryAdapter(unaVez(() -> new WisproClient(url, token, Duration.ofSeconds(60))), reloj);
    }

    @Bean
    ClassifyOnusPort classifyOnus(SmartOltInventoryAdapter smartOltInventario, WisproDirectoryAdapter wisproDirectorio, OltProfilesQueryPort perfilesOlt,
                                  @Value("${migrador.perfil-origen:GUAJIRANET}") String perfilOrigen,
                                  @Value("${migrador.marcas-onu:Huawei}") String marcas,
                                  @Value("${MIGRADOR_EXIGIR_CONTRATO_WISPRO:false}") boolean exigirContrato,
                                  @Value("${MIGRADOR_VLAN_GESTION:46}") int vlanGestion,
                                  OltSettingsQueryPort olts, OnuParticipationQueryPort participacion, TargetProfilePort perfil,
                                  org.springframework.beans.factory.ObjectProvider<com.guajiranet.migradoronu.conciliacion.puertos.salida.UltimoReporteAcsPort> acs,
                                  Clock reloj) {
        return new ClasificarOnusUseCase(smartOltInventario, wisproDirectorio,
                olts, participacion, perfil::perfilDestino, Set.of(marcas.split("\\s*,\\s*")), perfilesOlt, () -> perfilOrigen, exigirContrato).conVlanGestion(vlanGestion)
                .conAcs(acs.getIfAvailable(), reloj);
    }

    private static <T> Supplier<T> unaVez(Supplier<T> fabrica) {
        return new Supplier<>() {
            private T valor;

            @Override
            public synchronized T get() {
                if (valor == null) {
                    valor = fabrica.get();
                }
                return valor;
            }
        };
    }
}
