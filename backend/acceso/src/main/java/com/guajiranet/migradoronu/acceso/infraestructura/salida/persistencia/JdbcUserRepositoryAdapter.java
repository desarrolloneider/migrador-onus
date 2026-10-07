package com.guajiranet.migradoronu.acceso.infraestructura.salida.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.guajiranet.migradoronu.acceso.dominio.Usuario;
import com.guajiranet.migradoronu.acceso.puertos.salida.UserRepositoryPort;
import com.guajiranet.migradoronu.comun.errores.ErrorDeNegocio;

/** La tabla usuarios con consultas nativas. No existe ningún DELETE: un usuario se desactiva (ESP-02). */
public class JdbcUserRepositoryAdapter implements UserRepositoryPort {

    private static final String COLUMNAS = """
            SELECT id, usuario, nombre, hash_clave, activo, creado_en, ultimo_ingreso_en, intentos_fallidos, bloqueado_hasta
            FROM usuarios
            """;

    private final JdbcClient jdbc;

    public JdbcUserRepositoryAdapter(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    @Override
    public Optional<Usuario> buscarPorUsuario(String usuario) {
        return jdbc.sql(COLUMNAS + " WHERE usuario = :usuario").param("usuario", usuario).query(this::aUsuario).optional();
    }

    @Override
    public Optional<Usuario> buscarPorId(long id) {
        return jdbc.sql(COLUMNAS + " WHERE id = :id").param("id", id).query(this::aUsuario).optional();
    }

    @Override
    public long insertar(Usuario usuario) {
        try {
            return jdbc.sql("""
                            INSERT INTO usuarios (usuario, nombre, hash_clave, activo, creado_en, intentos_fallidos)
                            VALUES (:usuario, :nombre, :hash, :activo, :creadoEn, 0)
                            RETURNING id
                            """)
                    .param("usuario", usuario.usuario())
                    .param("nombre", usuario.nombre())
                    .param("hash", usuario.hashClave())
                    .param("activo", usuario.activo())
                    .param("creadoEn", utc(usuario.creadoEn()), Types.TIMESTAMP_WITH_TIMEZONE)
                    .query(Long.class)
                    .single();
        } catch (DuplicateKeyException e) {
            throw ErrorDeNegocio.conflicto("El usuario " + usuario.usuario() + " ya existe.");
        }
    }

    @Override
    public void actualizar(Usuario usuario) {
        jdbc.sql("""
                        UPDATE usuarios SET nombre = :nombre, hash_clave = :hash, activo = :activo,
                               ultimo_ingreso_en = :ultimoIngreso, intentos_fallidos = :intentos, bloqueado_hasta = :bloqueadoHasta
                        WHERE id = :id
                        """)
                .param("nombre", usuario.nombre())
                .param("hash", usuario.hashClave())
                .param("activo", usuario.activo())
                .param("ultimoIngreso", utc(usuario.ultimoIngresoEn()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("intentos", usuario.intentosFallidos())
                .param("bloqueadoHasta", utc(usuario.bloqueadoHasta()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("id", usuario.id())
                .update();
    }

    @Override
    public List<Usuario> listar() {
        return jdbc.sql(COLUMNAS + " ORDER BY usuario").query(this::aUsuario).list();
    }

    @Override
    public Map<Long, String> nombres(Collection<Long> ids) {
        Map<Long, String> nombres = new LinkedHashMap<>();
        jdbc.sql("SELECT id, nombre FROM usuarios WHERE id IN (:ids)").param("ids", List.copyOf(new HashSet<>(ids)))
                .query((rs, n) -> nombres.put(rs.getLong("id"), rs.getString("nombre")))
                .list();
        return nombres;
    }

    @Override
    public Set<Long> idsQueCoinciden(String texto) {
        String patron = "%" + texto.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return new HashSet<>(jdbc.sql("SELECT id FROM usuarios WHERE lower(usuario) LIKE :p OR lower(nombre) LIKE :p")
                .param("p", patron).query(Long.class).list());
    }

    private Usuario aUsuario(ResultSet rs, int fila) throws SQLException {
        return new Usuario(
                rs.getLong("id"),
                rs.getString("usuario"),
                rs.getString("nombre"),
                rs.getString("hash_clave"),
                rs.getBoolean("activo"),
                instante(rs, "creado_en"),
                instante(rs, "ultimo_ingreso_en"),
                rs.getInt("intentos_fallidos"),
                instante(rs, "bloqueado_hasta"));
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime valor = rs.getObject(columna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante == null ? null : instante.atOffset(ZoneOffset.UTC);
    }
}
