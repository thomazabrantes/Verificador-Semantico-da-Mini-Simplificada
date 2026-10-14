/**
 * Representação dos tipos da linguagem Mini (expressões de tipo).
 * Arranjos de várias dimensões são arranjos de arranjos:
 *   int m[3][4]  ->  array(3, array(4, int))
 */
public final class Tipo {
    public enum Cat { INT, DOUBLE, BOOL, VOID, ARRAY, ERRO }

    public final Cat cat;
    public final Tipo elem;   // só para ARRAY
    public final int tam;     // só para ARRAY

    private Tipo(Cat cat, Tipo elem, int tam) { this.cat = cat; this.elem = elem; this.tam = tam; }

    // um único objeto por tipo primitivo -> comparação por referência (equivalência por nome)
    public static final Tipo INT    = new Tipo(Cat.INT, null, 0);
    public static final Tipo DOUBLE = new Tipo(Cat.DOUBLE, null, 0);
    public static final Tipo BOOL   = new Tipo(Cat.BOOL, null, 0);
    public static final Tipo VOID   = new Tipo(Cat.VOID, null, 0);
    public static final Tipo ERRO   = new Tipo(Cat.ERRO, null, 0);   // tipo "bottom" (⊥)

    public static Tipo array(Tipo elem, int tam) { return new Tipo(Cat.ARRAY, elem, tam); }

    public boolean numerico() { return this == INT || this == DOUBLE; }
    public boolean ehErro()   { return this == ERRO; }
    public boolean ehArray()  { return cat == Cat.ARRAY; }

    /** Número de dimensões: int -> 0, int[3] -> 1, int[3][4] -> 2. */
    public int dimensoes() { return ehArray() ? 1 + elem.dimensoes() : 0; }

    /** Tipo básico dos elementos: int[3][4] -> int. */
    public Tipo base() { return ehArray() ? elem.base() : this; }

    /** Equivalência: por nome para primitivos; estrutural (tipo do elemento) para arranjos. */
    public boolean igual(Tipo o) {
        if (this == o) return true;
        return ehArray() && o.ehArray() && elem.igual(o.elem);
    }

    /** Pode um valor deste tipo ser atribuído a uma variável do tipo dest?  (this <= dest) */
    public boolean atribuivelA(Tipo dest) {
        if (ehErro() || dest.ehErro()) return true;          // ⊥ <= T : evita erros em cascata
        if (ehArray() || dest.ehArray()) return false;        // Mini não atribui arranjos inteiros
        return igual(dest) || (this == INT && dest == DOUBLE); // coerção de alargamento int -> double
    }

    @Override public String toString() {
        return switch (cat) {
            case INT -> "int"; case DOUBLE -> "double"; case BOOL -> "bool";
            case VOID -> "void"; case ERRO -> "<erro>";
            case ARRAY -> base() + dimsStr();
        };
    }

    // "[3][4]" na ordem da declaração (a dimensão externa primeiro)
    private String dimsStr() { return ehArray() ? "[" + tam + "]" + elem.dimsStr() : ""; }
}
