package com.guajiranet.migradoronu.conciliacion.infraestructura.configuracion;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.guajiranet.migradoronu.conciliacion.aplicacion.ClasificarOnusUseCase;
import com.guajiranet.migradoronu.conciliacion.infraestructura.salida.smartolt.SmartOltInventoryAdapter;
import com.guajiranet.migradoronu.conciliacion.infraestructura.salida.wispro.WisproDirectoryAdapter;
import com.guajiranet.migradoronu.conciliacion.puertos.entrada.ClassifyOnusPort;
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
    ClassifyOnusPort classifyOnus(OltSettingsQueryPort olts, OnuParticipationQueryPort participacion, TargetProfilePort perfil, Clock reloj,
                                  @Value("${SMARTOLT_BASE_URL:}") String smartOltUrl, @Value("${SMARTOLT_API_TOKEN:}") String smartOltToken,
                                  @Value("${WISPRO_BASE_URL:}") String wisproUrl, @Value("${WISPRO_API_TOKEN:}") String wisproToken) {
        Duration tiempo = Duration.ofSeconds(30);
        Supplier<SmartOltClient> smartOlt = unaVez(() -> new SmartOltClient(smartOltUrl, smartOltToken, tiempo));
        Supplier<WisproClient> wispro = unaVez(() -> new WisproClient(wisproUrl, wisproToken, tiempo));
        return new ClasificarOnusUseCase(new SmartOltInventoryAdapter(smartOlt, reloj), new WisproDirectoryAdapter(wispro, reloj),
                olts, participacion, perfil::perfilDestino, Set.of("Huawei"));
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
