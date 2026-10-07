/** Generador pseudoaleatorio con semilla (mulberry32): los datos de ejemplo salen iguales en cada carga. */
export class Azar {
  private estado: number;

  constructor(semilla: number) {
    this.estado = semilla >>> 0;
  }

  siguiente(): number {
    this.estado = (this.estado + 0x6d2b79f5) >>> 0;
    let t = this.estado;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  }

  entero(minimo: number, maximo: number): number {
    return minimo + Math.floor(this.siguiente() * (maximo - minimo + 1));
  }

  elegir<T>(opciones: readonly T[]): T {
    return opciones[Math.floor(this.siguiente() * opciones.length)];
  }

  hex(digitos: number): string {
    let texto = '';
    for (let i = 0; i < digitos; i++) {
      texto += '0123456789ABCDEF'[this.entero(0, 15)];
    }
    return texto;
  }
}
