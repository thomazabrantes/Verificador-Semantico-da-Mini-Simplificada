import java.util.List;

/** Identificador numa lista de declaração: "x" (dims vazia), "v[10]" ou "m[3][4]". */
public record DeclId(String nome, List<Integer> dims, int linha) {}
