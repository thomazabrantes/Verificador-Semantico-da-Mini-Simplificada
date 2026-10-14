/**
 * D4 — atributo sintetizado de expr: o TIPO e, quando conhecido em tempo de compilação,
 * o VALOR constante (dobramento de constantes). cte == null  =>  valor desconhecido.
 */
public record Info(Tipo tipo, Integer cte) {
    public static Info sem(Tipo t)  { return new Info(t, null); }
    public boolean temCte()         { return cte != null; }
}
