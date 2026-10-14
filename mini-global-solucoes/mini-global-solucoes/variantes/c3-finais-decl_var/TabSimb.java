import java.util.*;

/**
 * Tabela de símbolos com escopos aninhados: uma pilha de tabelas hash.
 *
 * Recursos de visualização (para uso em aula):
 *   - verbose (-v)   : imprime cada escopo no momento em que ele é fechado;
 *   - trace   (-t)   : registra cada operação (abre, insere, resolve, fecha);
 *   - imprime()      : mostra a pilha inteira de escopos abertos (usado por $MOSTRA_TS).
 *
 * As operações que aparecem no trace recebem a linha do fonte como parâmetro,
 * apenas para as mensagens; insere() usa a linha guardada no próprio símbolo.
 */
public class TabSimb {
    private static class Escopo {
        final String nome;
        final int nivel;
        final Map<String, Simbolo> simbolos = new LinkedHashMap<>(); // mantém ordem de declaração
        Escopo(String nome, int nivel) { this.nome = nome; this.nivel = nivel; }
    }

    private final Deque<Escopo> pilha = new ArrayDeque<>();   // topo = escopo corrente
    private boolean verbose = false;
    private boolean trace = false;

    public void setVerbose(boolean v)      { verbose = v; }
    public void setTrace(boolean t)        { trace = t; }

    /* ======================= operações ======================= */

    public void abreEscopo(String nome, int linha) {
        pilha.push(new Escopo(nome, pilha.size()));
        log(linha, "abre escopo '" + nome + "' (nível " + nivel() + ")");
    }

    public void fechaEscopo(int linha) {
        Escopo e = pilha.peek();
        if (verbose) imprimeEscopo(e, "fechando escopo");
        log(linha, "fecha escopo '" + e.nome + "' (nível " + e.nivel + ") — "
            + e.simbolos.size() + " símbolo(s) descartado(s)");
        pilha.pop();                                          // símbolos locais deixam de ser visíveis
    }

    public int nivel() { return pilha.size() - 1; }           // 0 = global

    /** Insere no escopo corrente (quem chama já verificou redeclaração). */
    public void insere(Simbolo s) {
        pilha.peek().simbolos.put(s.nome, s);
        String d = s.classe == Simbolo.Classe.FUNCAO ? "FUNCAO (retorno " + s.tipo + ")" : descreve(s);
        log(s.linha, "insere " + s.nome + " : " + d + " no escopo '" + pilha.peek().nome + "'");
    }

    /** Busca só no escopo corrente — usada para detectar redeclaração. */
    public Simbolo buscaLocal(String nome) { return pilha.peek().simbolos.get(nome); }

    /** Busca do escopo mais interno para o mais externo (regra do aninhamento mais próximo). */
    public Simbolo busca(String nome) {
        for (Escopo e : pilha) {                              // ArrayDeque itera do topo para a base
            Simbolo s = e.simbolos.get(nome);
            if (s != null) return s;
        }
        return null;
    }

    /** Igual a busca(), mas registrada no trace: usada para cada USO de um nome. */
    public Simbolo resolve(String nome, int linha) {
        Simbolo s = busca(nome);
        if (s == null) log(linha, "resolve " + nome + " → NÃO ENCONTRADO");
        else           log(linha, "resolve " + nome + " → " + nome + "@" + s.linha
                           + "  (" + descreve(s) + ", nível " + s.nivel + ")");
        return s;
    }

    /* ======================= visualização ======================= */

    /** Imprime todos os escopos abertos, do topo para a base. */
    public void imprime(int linha) {
        System.out.println("╔══ Tabela de símbolos na linha " + linha
                           + " — " + pilha.size() + " escopo(s) aberto(s)");
        boolean topo = true;
        for (Escopo e : pilha) {
            System.out.println("║ [nível " + e.nivel + "] " + e.nome + (topo ? "   ← topo" : ""));
            if (e.simbolos.isEmpty()) System.out.println("║     (vazio)");
            for (Simbolo s : e.simbolos.values()) System.out.println("║     " + s);
            topo = false;
        }
        System.out.println("╚══");
    }

    private void imprimeEscopo(Escopo e, String titulo) {
        System.out.println("  ┌─ " + titulo + " '" + e.nome + "' (nível " + e.nivel + ")");
        for (Simbolo s : e.simbolos.values()) System.out.println("  │ " + s);
        System.out.println("  └─");
    }

    private void log(int linha, String msg) {
        if (!trace) return;
        String ind = "  ".repeat(Math.max(0, nivel()));
        System.out.printf("[ts] linha %3d  %s%s%n", linha, ind, msg);
    }

    private static String descreve(Simbolo s) {
        return s.classe == Simbolo.Classe.FUNCAO
             ? "FUNCAO " + s.params + " -> " + s.tipo
             : s.classe + " " + s.tipo;
    }
}
