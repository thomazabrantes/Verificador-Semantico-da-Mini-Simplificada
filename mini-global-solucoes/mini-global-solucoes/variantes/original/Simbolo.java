import java.util.ArrayList;
import java.util.List;

/** Uma entrada da tabela de símbolos. */
public class Simbolo {
    public enum Classe { VAR_GLOBAL, VAR_LOCAL, PARAM, FUNCAO }

    public final String nome;
    public final Classe classe;
    public final Tipo tipo;          // FUNCAO: tipo de retorno
    public final int nivel;          // profundidade do escopo (0 = global)
    public final int linha;          // linha da declaração
    public final List<Tipo> params = new ArrayList<>();   // FUNCAO: assinatura

    public Simbolo(String nome, Classe classe, Tipo tipo, int nivel, int linha) {
        this.nome = nome; this.classe = classe; this.tipo = tipo; this.nivel = nivel; this.linha = linha;
    }

    @Override public String toString() {
        String t = (classe == Classe.FUNCAO) ? params + " -> " + tipo : tipo.toString();
        return String.format("%-8s %-10s %-22s nível %d  (linha %d)", nome, classe, t, nivel, linha);
    }
}
