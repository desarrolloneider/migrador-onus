package com.guajiranet.migradoronu.conciliacion.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Combinaciones reales de SN y modelo del inventario de SmartOLT (15 de octubre de 2026). */
class MarcaDeOnuTest {

    @ParameterizedTest(name = "{0} + {1} = {2}")
    @CsvSource(delimiter = '|', value = {
            // Huawei de verdad: SN HWTC y modelo de la familia Huawei.
            "HWTC3BDD79AE     | EG8041V5                     | Huawei",
            "485754433BDD79AE | EG8145V5                     | Huawei",
            "HWTC12345678     | EG8141A5                     | Huawei",
            "HWTC12345678     | EG8041X6-10                  | Huawei",
            "HWTC12345678     | HG8010H                      | Huawei",
            "HWTC12345678     | HS8545M5                     | Huawei",
            // SN HWTC pero el equipo no es Huawei.
            "HWTCA687BAD1     | MONUV411                     | Genérica MONU",
            "HWTC12345678     | E4L-H5400WA                  | Genérica E4L",
            "HWTC12345678     | FD511GW-X-R330               | C-Data",
            "HWTC12345678     | D512XW-X-R330                | C-Data",
            "HWTC12345678     | ONU-type-eth-4-pots-2-catv-1 | Huawei (modelo sin definir)",
            // Modelo Huawei sobre SN de otro fabricante: no se da por Huawei.
            "DC9012345678     | HG8545M                      | No confirmada (HG8545M con SN DC90)",
            "GPON12345678     | HG8545M                      | No confirmada (HG8545M con SN GPON)",
            // Otras marcas.
            "ZTEG12345678     | F670LV9.0                    | ZTE",
            "TPLG12345678     | XC220-G3                     | TP-Link",
            "FHTT12345678     | HG6145D2                     | FiberHome",
            "OPTI12345678     | MONUV142                     | Genérica MONU",
            "FHTT12345678     | ZX8414DWTL                   | Genérica ZX",
            "DF1812345678     | FD504GW-DX-R471              | C-Data",
            "ZTEG12345678     | E4L-H5400WA                  | Genérica E4L",
            "OPTI12345678     | ONU-type-eth-2-pots-2-catv-1 | Optictimes (modelo sin definir)",
            "DF1D12345678     | HG643-W                      | Genérica (DF1D)",
            "RUIJ12345678     | PON3000GX                    | Ruijie",
    })
    void reconoceLaMarca(String sn, String modelo, String marca) {
        assertThat(MarcaDeOnu.de(sn, modelo)).isEqualTo(marca);
    }
}
