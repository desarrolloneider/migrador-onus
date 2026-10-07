/** Entrega al navegador un archivo que generó el backend (las exportaciones quedan registradas allá, RNF-15). */
export function descargarArchivo(contenido: Blob, nombre: string): void {
  const url = URL.createObjectURL(contenido);
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = nombre;
  enlace.click();
  setTimeout(() => URL.revokeObjectURL(url), 1_000);
}

/** Convierte un objeto de filtros en parámetros de consulta, omitiendo los vacíos. */
export function parametrosDeConsulta(filtros: object): Record<string, string> {
  const parametros: Record<string, string> = {};
  for (const [clave, valor] of Object.entries(filtros)) {
    if (valor !== null && valor !== undefined && valor !== '') {
      parametros[clave] = String(valor);
    }
  }
  return parametros;
}
