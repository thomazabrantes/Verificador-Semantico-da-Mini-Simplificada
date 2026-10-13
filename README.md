# Verificador Semântico da Mini Simplificada — funcionamento e guia de testes

Construção de Compiladores · Escola Politécnica · PUCRS · Unidades 4.3–4.4
Projeto-base: `mini-global-byaccj` (JFlex + BYACC/J + Java)

Este documento explica **como o verificador funciona por dentro**, **como compilá-lo e executá-lo**, **como testar cada comportamento** e **o que foi alterado** nas variantes feitas para as questões C3, D3 e D4. Todas as saídas mostradas aqui foram obtidas executando o projeto (JFlex 1.7.0, BYACC/J 1.15, OpenJDK 21).

> As respostas completas e justificadas da atividade (C1–C14 e D1–D4) estão no documento de respostas. Este arquivo é o "manual" do trabalho.

---

## Sumário

1. [O que foi entregue](#1-o-que-foi-entregue)
2. [Requisitos e instalação](#2-requisitos-e-instalação)
3. [A linguagem Mini simplificada](#3-a-linguagem-mini-simplificada)
4. [Arquitetura e fluxo de execução](#4-arquitetura-e-fluxo-de-execução)
5. [Como o verificador funciona](#5-como-o-verificador-funciona)
6. [Como compilar e executar](#6-como-compilar-e-executar)
7. [Roteiro de testes](#7-roteiro-de-testes)
8. [As variantes do código (C3, D3, D4)](#8-as-variantes-do-código-c3-d3-d4)
9. [Escrevendo seus próprios testes](#9-escrevendo-seus-próprios-testes)
10. [Mapa questão → evidência](#10-mapa-questão--evidência)
11. [Limitações conhecidas e solução de problemas](#11-limitações-conhecidas-e-solução-de-problemas)

---

## 1. O que foi entregue

| Item | Conteúdo |
|---|---|
| Documento de respostas | Respostas de C1–C14 e D1–D4, com trechos de código, saídas coladas e explicações |
| `README_Verificador_Mini.md` | Este guia |
| `mini-global-solucoes.zip` | Projeto original + 3 variantes alteradas + programas de teste + script que roda tudo |

Estrutura do zip:

```
mini-global-solucoes/
├── rodar_experimentos.sh            # roda TODOS os experimentos e mostra as saídas
├── testes/                          # programas .mini usados nas respostas
│   ├── c3a.mini  c3b.mini           # C3: erro sintático × semântico
│   ├── c5a.mini  c5b.mini  c5c.mini  c5d.mini
│   ├── c6.mini   c8.mini   c9.mini  c12.mini  c13.mini
│   ├── d2.mini   d3.mini   d4.mini
└── variantes/
    ├── original/                    # projeto fornecido, sem alterações
    ├── c3-finais-decl_var/          # C3: mensagem semântica para "int b;" após main
    ├── d3-locais/                   # D3: variáveis locais (1 linha de gramática)
    └── d4-limites-constantes/       # D4: índices constantes fora dos limites
```

Cada pasta de `variantes/` é um projeto completo e independente (tem seu próprio `Makefile`).

---

## 2. Requisitos e instalação

Precisa de: **JDK 17+** (testado com 21), **JFlex**, **BYACC/J** e **make**.

Ubuntu/Debian:

```sh
sudo apt-get install openjdk-21-jdk-headless jflex byacc-j make
```

Isso instala os comandos `jflex` e `byaccj`, que são os nomes que o `Makefile` espera. Se as ferramentas tiverem outro nome ou caminho na sua máquina (por exemplo o `.jar` do JFlex ou o executável `yacc.linux` distribuído com o BYACC/J), troque na linha de comando:

```sh
make JFLEX="java -jar jflex-full-1.9.1.jar" BYACCJ=./yacc.linux
```

Verificação rápida:

```sh
jflex --version ; byaccj -h 2>&1 | head -3 ; javac -version
```

---

## 3. A linguagem Mini simplificada

Subconjunto de C com **apenas variáveis globais** e **uma única função, `void main()`**.

| Elemento | O que existe |
|---|---|
| Tipos | `int`, `double`, `bool` e arranjos de **uma ou mais dimensões** (`int v[10]; double m[3][4];`) |
| Declarações | só globais, antes de `main` (lista separada por vírgula: `int a, t[2][5], b;`) |
| Função | somente `void main() { ... }`, sem parâmetros e sem locais |
| Comandos | `x = e;`, `m[i][j] = e;`, `if/else`, `while`, `break`, `return`, `print(e);`, bloco `{ ... }` |
| Expressões | `+ - * / %`, relacionais, `== !=`, `&& \|\| !`, menos unário, constantes `int`/`double`/`true`/`false`, `x`, `m[i][j]` |
| Coerção | apenas `int → double` |
| Diretivas de depuração | `$MOSTRA_TS`, `$TRACE_ON`, `$TRACE_OFF` (escritas no próprio fonte) |

Estrutura imposta pela gramática (`mini.y`, linha 34):

```
programa : globais  decl_main  finais   { verificaMain(); }
```

ou seja: declarações globais → exatamente um `main` → (somente diretivas) até o fim.

---

## 4. Arquitetura e fluxo de execução

### 4.1 Do fonte ao executável

```
 mini.flex ──► jflex ───────────────► Yylex.java ────────┐
                                                          │
 mini.y ─────► byaccj -J -Jnorun ──► Parser.java ─────────┤
                                  └► ParserVal.java ──────┼──► javac ──► *.class
                                                          │
 Tipo.java  Simbolo.java  TabSimb.java  DeclId.java ──────┘      (escritos à mão)
```

* `-J` faz o BYACC/J gerar **Java** (o yacc clássico gera C).
* `-Jnorun` suprime o método `run()` gerado por padrão (um auxiliar para usar o parser como `Thread`). Aqui o parser é chamado direto pelo `main`, então não é necessário.
* O `main()` está **dentro de `mini.y`** (seção de código, depois do segundo `%%`) porque essa seção é copiada literalmente para dentro da classe `Parser`. Assim o `main` enxerga o construtor `Parser(Reader, verbose, trace)` e o campo privado `nErros`, e `java Parser arquivo.mini` funciona sem uma classe extra.

### 4.2 Quem chama quem em tempo de execução

```
java Parser [-v] [-t] arq.mini
  └─ Parser.main()
       └─ new Parser(reader, verbose, trace)
            ├─ new Yylex(reader, this)          # léxico guarda referência ao parser
            └─ ts.abreEscopo("global")          # escopo de nível 0
       └─ p.yyparse()                           # laço LALR(1) gerado pelo byaccj
            ├─ yylex()  ──► lexer.yylex()       # devolve token; coloca valor em yylval
            ├─ empilha / reduz
            └─ a cada redução executa a ação semântica:
                 declaraVars · atribui · indexa · usaVar · aritmetico · checaCond ...
                      └─ TabSimb (insere/busca/resolve) · Tipo · Simbolo · DeclId
```

A análise é de **uma passada**: o parser e as verificações semânticas andam juntos; não há AST.

### 4.3 O papel de cada arquivo

| Arquivo | Papel |
|---|---|
| `mini.flex` | Léxico. Em `ID` guarda o lexema em `yylval.sval`; em `NUM_INT` guarda o inteiro em `yylval.ival`; em `NUM_REAL`, o `double` em `dval`. Reconhece as diretivas `$...`. |
| `mini.y` | Gramática, ações semânticas e `main`. Atributo de `expr` = `Tipo` (em `ParserVal.obj`). |
| `Tipo.java` | Expressões de tipo. Primitivos são objetos únicos; arranjo é `array(tam, elem)`; `ERRO` é o tipo ⊥. Tem a relação "≤" (`atribuivelA`). |
| `Simbolo.java` | Uma entrada da tabela: nome, classe (`VAR_GLOBAL`, `VAR_LOCAL`, `PARAM`, `FUNCAO`), tipo, nível, linha, `params`. |
| `TabSimb.java` | Pilha de escopos (`Deque` de `LinkedHashMap`). `abreEscopo`, `fechaEscopo`, `insere`, `buscaLocal`, `busca`, `resolve`, `imprime`; implementa `-v`, `-t` e `$MOSTRA_TS`. |
| `DeclId.java` | `record(nome, dims, linha)`: um item de declaração (`x`, `v[10]`, `m[3][4]`). |

---

## 5. Como o verificador funciona

### 5.1 Atributos

Todos **sintetizados** (calculados a partir dos filhos). Não há atributo herdado: o tipo-base de `int a, t[2][5], b;` não "desce" até os itens; em vez disso, `decl_var` recebe o tipo (`$1`) e a lista de itens (`$2`) e junta os dois em `declaraVars()`.

| Não-terminal | Atributo (em `ParserVal.obj`) |
|---|---|
| `tipo` | `Tipo` (`INT`, `DOUBLE` ou `BOOL`) |
| `expr` | `Tipo` da expressão |
| `item_id` | `DeclId` |
| `lista_ids` | `List<DeclId>` |
| `dims_decl` | `List<Integer>` (dimensões lidas na declaração) |
| `indices` | `List<Tipo>` (tipos das expressões de índice) |

`%token <ival> NUM_INT` declara que o valor do token está no campo `int ival`; `%type <obj> ...` diz que o atributo dos não-terminais está em `Object obj`, por isso as ações usam *casts*: `(Tipo)$1`, `(List<DeclId>)$2`.

### 5.2 Arranjos de várias dimensões

`double m[3][4]` vira **`array(3, array(4, double))`**. O laço de `declaraVars()` percorre as dimensões **da última para a primeira**, porque o tipo é construído de dentro para fora: começa em `double`, embrulha com `[4]`, depois com `[3]`. Na ordem direta sairia `double[4][3]`.

```
array(3)                     m[i]    : double[4]   (descasca uma dimensão)
  └─ array(4)                m[i][j] : double
       └─ double
```

Em `indexa()`, cada índice "descasca" um `array`: `t = t.elem`.

### 5.3 Tabela de símbolos e escopos

* Pilha de escopos; o topo é o escopo corrente.
* `insere(s)` sempre no topo. `buscaLocal(n)` só no topo (detecta redeclaração). `busca(n)` do topo para a base (regra do aninhamento mais próximo). `resolve(n, linha)` = `busca` + registro no trace (usada em cada **uso** de nome).
* Nesta versão os escopos abertos são: `global` (nível 0), `função main` (nível 1) e um `bloco` por `{ }` interno (nível 2, 3, …). Em `ok.mini` há no máximo **4 simultâneos** (global, main, `while` externo, `while` interno).
* O escopo `global` só fecha em `verificaMain()`, depois das diretivas finais.

### 5.4 Regras de verificação (resumo)

| Situação | Mensagem / comportamento | Tipo devolvido |
|---|---|---|
| Redeclaração no mesmo escopo | `'x' já declarado neste escopo (linha N)`; mantém a 1ª | — |
| Dimensão ≤ 0 | `dimensão k de 'z' deve ser positiva` | — |
| Nome não declarado | `identificador 'y' não declarado` | `ERRO` |
| Uso de `main` como variável | `'main' é uma função; use main(...)` | `ERRO` |
| `a + b`, `a * b`… com não numéricos | `operador '+' não se aplica a T1 e T2` | `ERRO` |
| `int`/`double` misturados | coerção; resultado `double` | `DOUBLE` |
| `%` | exige `int` e `int` | `INT` ou `ERRO` |
| `<`, `>`, `<=`, `>=` inválidos | erro, mas devolve `bool` | `BOOL` |
| `==`, `!=` inválidos | `não é possível comparar ...`; devolve `bool` | `BOOL` |
| `&&`, `\|\|`, `!` com não-bool | erro; devolve `bool` | `BOOL` |
| Indexar não-arranjo | `'ok' não é um arranjo` | `ERRO` |
| Índices demais | `'m' tem 2 dimensão(ões), mas foi indexado com 3 índice(s)` | `ERRO` |
| Índice não-int | `índice k de 'v' deve ser int, encontrado double` | tipo do elemento |
| Atribuição incompatível | `tipos incompatíveis na atribuição a 'x': esperado T1, encontrado T2` | — |
| Alvo é arranjo | `não é permitido atribuir a um arranjo (...)` | — |
| Condição não-bool | `condição deve ser bool, encontrado T` | — |
| `break` fora de laço | `'break' fora de um laço` (contador `laco`) | — |
| `return expr;` em `main` | `função void 'main' não pode retornar valor` | — |
| `print` de arranjo/void | `print não aceita expressão do tipo T` | — |
| Função diferente de `main` | `nesta versão só é permitida a função 'main' (encontrado 'f')` | — |

### 5.5 Recuperação de erros (evitar cascata)

O tipo `ERRO` (⊥) é aceito em qualquer lugar (`ERRO ≤ T`), e as regras que recebem `ERRO` **não emitem nova mensagem**. Exemplo: em `x = (y + 1) * 2;` com `y` não declarado há **uma** só mensagem. Quando o resultado independe dos operandos (relacionais, lógicos), a regra devolve o tipo correto (`bool`) mesmo com erro.

Erros **sintáticos** não têm recuperação: o BYACC/J (sem produção `error`) aborta; por isso aparece a segunda linha `stack underflow. aborting...`.

### 5.6 Relação ≤ e equivalência de tipos

* `atribuivelA(dest)` (`this ≤ dest`): `ERRO` aceita tudo; arranjo nunca é atribuível; `igual(dest)` ou `int → double`.
* `igual(o)`: primitivos por identidade (objeto único); arranjos **estruturalmente**, comparando só o tipo do elemento recursivamente — o **tamanho é ignorado**: `int[3][4] ≡ int[5][4] ≡ int[3][5]`, mas `int[3] ≢ int[3][4]`.

---

## 6. Como compilar e executar

Dentro de uma pasta de projeto (`variantes/original/`, por exemplo):

```sh
make              # jflex + byaccj -J -Jnorun + javac
make ok           # exemplos/ok.mini com -v     (programa correto, imprime os escopos ao fechar)
make erros        # exemplos/erros.mini         (15 erros semânticos)
make tabela       # exemplos/tabela.mini        ($MOSTRA_TS e $TRACE_ON)
make trace        # exemplos/ok.mini com -t     (trace completo da tabela)
make test         # confere os três exemplos
make conflitos    # gera y.output e procura conflitos na gramática
make clean        # remove arquivos gerados
```

Execução direta:

```sh
java Parser [-v] [-t] [arquivo.mini]      # sem arquivo, lê da entrada padrão
java Parser -h                            # ajuda
echo 'int x; void main() { x = 1; }' | java Parser
```

| Opção / diretiva | Efeito |
|---|---|
| `-v` | imprime cada escopo **no momento em que fecha** |
| `-t` | trace: abre/fecha escopo, insere, resolve (`m → m@5`) |
| `$MOSTRA_TS` | (no fonte) imprime a pilha de escopos abertos naquele ponto |
| `$TRACE_ON` / `$TRACE_OFF` | (no fonte) liga/desliga o trace em um trecho |

Código de saída da análise: última linha `Programa semanticamente correto.` ou `N erro(s) encontrado(s).`

---

## 7. Roteiro de testes

### 7.1 Tudo de uma vez

```sh
cd mini-global-solucoes
bash rodar_experimentos.sh
```

O script compila o projeto original e cada variante, executa todos os programas de `testes/` e imprime as saídas separadas por seção (`C3`, `C5`, … `D4`). Para guardar a saída: `bash rodar_experimentos.sh > saida.txt 2>&1`.

### 7.2 Testes automáticos (`make test`)

Resultado esperado, no original e em **todas** as variantes (nenhuma regrediu):

```
ok.mini    : OK (sem erros)
erros.mini : OK (15 erros)
tabela.mini: OK (sem erros)
```

### 7.3 Ausência de conflitos na gramática

```sh
make conflitos
# → Nenhum conflito na gramática.
```

Vale para o original e para as variantes C3, D3 e D4.

### 7.4 `erros.mini` — saída esperada

```
Linha 5: erro: 'x' já declarado neste escopo (linha 2)
Linha 6: erro: dimensão 1 de 'z' deve ser positiva
Linha 10: erro: tipos incompatíveis na atribuição a 'x': esperado int, encontrado double
Linha 11: erro: identificador 'y' não declarado
Linha 12: erro: tipos incompatíveis na atribuição a 'ok': esperado bool, encontrado int
Linha 13: erro: condição deve ser bool, encontrado int
Linha 14: erro: tipos incompatíveis na atribuição a 'x': esperado int, encontrado int[5]
Linha 15: erro: índice 1 de 'v' deve ser int, encontrado double
Linha 16: erro: não é permitido atribuir a um arranjo ('m' indexado tem tipo double[4])
Linha 17: erro: 'm' tem 2 dimensão(ões), mas foi indexado com 3 índice(s)
Linha 18: erro: 'ok' não é um arranjo
Linha 19: erro: operador '*' não se aplica a double[4] e int
Linha 20: erro: identificador 'y' não declarado
Linha 21: erro: 'break' fora de um laço
Linha 22: erro: função void 'main' não pode retornar valor
15 erro(s) encontrado(s).
```

Note: a linha 9 (`d = 3;`, coerção) **não** gera erro, e a linha 20 gera **um** erro (não dois) graças ao tipo `ERRO`.

### 7.5 `tabela.mini` — o que observar

`make tabela` produz três impressões da tabela (linhas 4, 12 e 17 do fonte):

1. **Linha 4** — 1 escopo (`global`) com `n`, `v`, `m`. Ainda não existem `fim` nem `main`: a tabela é preenchida **durante** a leitura (uma passada).
2. **Linha 12** — 3 escopos: `bloco` (vazio, topo), `função main` (vazio), `global` (agora com `fim` e `main`).
3. **Linha 17** — só `global`, completo; é o estado final antes de `verificaMain()` fechá-lo.

O trace (ligado na linha 8, desligado na 14) mostra `abre escopo 'bloco'` (linha 10) e `fecha escopo 'bloco'` (linha 13), e a ordem `resolve n, n, v, m` na linha 11 (o alvo `m` é resolvido por último porque `atribui()` só roda quando o comando inteiro é reduzido).

### 7.6 Experimentos extras

| Teste | Comando | Resultado esperado |
|---|---|---|
| C3 (a) erro sintático | `java Parser testes/c3a.mini` | `Linha 3: erro sintático (syntax error)` + `stack underflow` |
| C3 (b) erro semântico | `java Parser testes/c3b.mini` | `só é permitida a função 'main' (encontrado 'f')` e `programa sem função 'main'` |
| C5 `main` redeclarado | `java Parser testes/c5a.mini` | `'main' já declarado neste escopo (linha 1)` |
| C5 `main` como variável | `java Parser testes/c5b.mini` | `'main' é uma função; use main(...)` |
| C6 ordem das inserções | `java Parser -t testes/c6.mini` | `insere a`, `insere t : int[2][5]`, `insere b` (juntos, linha 1) |
| C8 indexação | `java Parser testes/c8.mini` | 3 erros (linhas 4, 5, 6) |
| C9 `v == w` | `java Parser testes/c9.mini` | `não é possível comparar int[5] == int[5]` |
| C12 `break` aninhado | `java Parser testes/c12.mini` | só o `break` fora de laços é erro (linha 4) |
| C13 blocos | `java Parser -t testes/c13.mini` | escopos `bloco` níveis 2 e 3, com 0 símbolos |
| D2 | `java Parser testes/d2.mini` | 4 erros: linhas 7, 8, 9 e 10 |

Saída do **D2** (as linhas 5 e 6 do fonte são aceitas):

```
Linha 7: erro: tipos incompatíveis na atribuição a 'x': esperado int, encontrado int[4]
Linha 8: erro: 'c' tem 3 dimensão(ões), mas foi indexado com 4 índice(s)
Linha 9: erro: tipos incompatíveis na atribuição a 'c': esperado int, encontrado double
Linha 10: erro: operador '+' não se aplica a int e int[4]
4 erro(s) encontrado(s).
```

---

## 8. As variantes do código (C3, D3, D4)

Todas passam em `make test` e em `make conflitos`.

### 8.1 `c3-finais-decl_var` — mensagem semântica para declaração após `main`

Alteração em `mini.y` (regra `finais`):

```yacc
finais     : /* vazio */
           | finais diretiva
           | finais decl_var   { erro("declaração global após 'main': declare as variáveis antes da função main"); }
           ;
```

Antes: `int a; void main() { a = 1; } int b;` → *erro sintático (syntax error)* e o parser aborta. Depois:

```
Linha 3: erro: declaração global após 'main': declare as variáveis antes da função main
1 erro(s) encontrado(s).
```

A gramática passa a **aceitar um superconjunto** da linguagem e a análise semântica filtra, que é exatamente a filosofia da unidade.

### 8.2 `d3-locais` — variáveis locais com **uma linha** de gramática

```yacc
itens      : /* vazio */
           | itens cmd
           | itens decl_var            /* ← linha acrescentada */
           | itens diretiva
           ;
```

`itens` é usado por `corpo` (função) e por `bloco`, então locais passam a valer nos dois. Sem conflitos, porque `decl_var` começa com `INT|DOUBLE|BOOL` e nenhum `cmd` começa assim.

Programa de teste (`testes/d3.mini`):

```c
int a, m[2][2];
void main() {
    int a;
    a = 1;
    { double a; int k; a = 2.5; k = m[1][0]; $MOSTRA_TS }
    int m;
}
```

Saída:

```
Linha 3: aviso: 'a' oculta a declaração da linha 1
Linha 5: aviso: 'a' oculta a declaração da linha 3
╔══ Tabela de símbolos na linha 5 — 3 escopo(s) aberto(s)
║ [nível 2] bloco   ← topo
║     a        VAR_LOCAL  double                 nível 2  (linha 5)
║     k        VAR_LOCAL  int                    nível 2  (linha 5)
║ [nível 1] função main
║     a        VAR_LOCAL  int                    nível 1  (linha 3)
║ [nível 0] global
║     a        VAR_GLOBAL int                    nível 0  (linha 1)
║     m        VAR_GLOBAL int[2][2]              nível 0  (linha 1)
║     main     FUNCAO     [] -> void             nível 0  (linha 2)
╚══
Linha 6: aviso: 'm' oculta a declaração da linha 1
Programa semanticamente correto.
```

Pontos de atenção: `k = m[1][0]` usa o `m` **global** (o `int m;` local só é declarado depois — declaração antes do uso); o aviso da linha 6 é da ocultação de `m`; `declaraVars()` escolhe `VAR_LOCAL` sozinho quando `ts.nivel() > 0`.

### 8.3 `d4-limites-constantes` — índices constantes fora dos limites

O atributo de `expr` deixa de ser só um `Tipo` e passa a ser um `Info(Tipo tipo, Integer cte)` (novo arquivo `Info.java`): `cte` guarda o valor quando ele é conhecido em compilação (dobramento de constantes) e é `null` caso contrário.

O que muda em `mini.y`:

* `NUM_INT` → `new Info(Tipo.INT, $1)`; os demais literais e variáveis → `Info.sem(tipo)`.
* `+ - * / %` e o menos unário passam por `dobra(...)`/`menosUnarioInfo(...)`: reutilizam `aritmetico()`/`modulo()` para o tipo e calculam o valor quando os dois operandos são constantes `int` (divisão/módulo por 0 não dobra).
* `indices` vira `List<Info>`; `indexa()` confere `0 ≤ cte < tam` para cada índice constante.
* `cmd`/`cond`/`print`/`return` extraem `.tipo()`.

Programa de teste (`testes/d4.mini`) e saída:

```
 5  v[7] = 1;                 Linha 5: ... 'v' fora dos limites: 7 (válido: 0..4)
 6  d = m[1][4];              Linha 6: índice 2 de 'm' ... 4 (válido: 0..3)
 7  v[2 + 3] = 0;             Linha 7: ... 5 (dobrado: 2+3)
 8  v[-1] = 0;                Linha 8: ... -1
 9  v[10 / 2] = 0;            Linha 9: ... 5
10  d = m[3][0];              Linha 10: índice 1 de 'm' ... 3 (válido: 0..2)
11  v[4] = 1;                 (ok)
12  d = m[2][3];              (ok)
13  v[i] = 1;                 (não detectável)
14  v[i + 10] = 2;            (não detectável)
15  d = m[1 * 3][0];          Linha 15: ... 3 (dobrado: 1*3)
16  v[5 % 3] = 1;             (ok: 5 % 3 = 2)
7 erro(s) encontrado(s).
```

**Limite fundamental:** `v[i]` com `i` variável não é resolvido porque o valor de `i` só existe em tempo de execução; o compilador só conhece valores de expressões **inteiramente constantes**. Detectar mais exigiria análise de fluxo de dados (intervalos de valores) ou verificação em execução. Como a análise estática é conservadora, em dúvida o verificador **não** reclama.

---

## 9. Escrevendo seus próprios testes

Um programa Mini válido tem a forma:

```c
// declarações globais...
int n, v[5];
double m[2][3];
bool ok;

void main() {
    // comandos...
    n = 2;
    v[n] = 1;
    m[1][n] = v[n] * 1.5;      // int * double -> double
    if (m[1][2] > 1.0 && !ok) { print(m[1][2]); }
    $MOSTRA_TS                  // opcional: imprime a tabela neste ponto
}
```

Receitas:

* **Provocar um erro específico**: escreva só o comando problemático dentro de `main` e rode `java Parser meu.mini`.
* **Ver a ordem de resolução dos nomes**: ligue `-t` (ou use `$TRACE_ON` / `$TRACE_OFF` em volta do trecho).
* **Ver o que há na tabela em um ponto**: coloque `$MOSTRA_TS` ali.
* **Testar sem arquivo**: `printf 'int x; void main() { x = 1.5; }' | java Parser`.

---

## 10. Mapa questão → evidência

| Questão | Onde olhar |
|---|---|
| C1 | `Makefile` (linhas 28–39); `Parser.java` gerado (`yyparse`, `yylex`) |
| C2 | `mini.y` linhas 12–14, 29, 59–60; `mini.flex` linhas 49–51; `Parser.java` (`val_peek(1).ival`) |
| C3 | `mini.y` linhas 34–43, 68–74; `testes/c3a.mini`, `c3b.mini`; variante `c3-finais-decl_var` |
| C4–C5 | `TabSimb.java` (linhas 54–72); `mini.y` `declara()` (linha 156) |
| C6–C7 | `mini.y` linhas 51–66 e `declaraVars()` (linha 165); `Tipo.java` |
| C8–C10 | `mini.y` `indexa()` (197), `atribui()` (214); `erros.mini`; `ok.mini` linha 27 |
| C11 | `make tabela`, linha 11 de `tabela.mini` |
| C12–C14 | `mini.y` linhas 89–93, `checaReturn()` (277), seção "RESERVADO" (299+) |
| D1 | `make tabela` |
| D2 | `testes/d2.mini` |
| D3 | variante `d3-locais`; `testes/d3.mini` |
| D4 | variante `d4-limites-constantes`; `testes/d4.mini` |

---

## 11. Limitações conhecidas e solução de problemas

| Sintoma | Causa / solução |
|---|---|
| `jflex: command not found` / `byaccj: command not found` | instale os pacotes (seção 2) ou passe `JFLEX=` / `BYACCJ=` ao `make` |
| `Could not find or load main class Parser` | falta compilar: rode `make` antes de `java Parser` |
| Aviso `Parser.java uses unchecked or unsafe operations` | esperado: os *casts* `(List<Tipo>)$2` são não verificados por causa do `Object obj` do `ParserVal`; inofensivo |
| `make` não regenera após editar `mini.y` | rode `make clean && make` |
| Duas linhas de erro sintático (`syntax error` + `stack underflow`) | comportamento do BYACC/J sem produção `error`: a análise para no primeiro erro sintático |
| Número de linha do erro "deslocado" | o número vem do léxico (`linha()`), isto é, da posição do último token lido; em geral é a linha do `;` que fecha o comando |
| `caractere inválido` | o léxico escreve esse aviso em `stderr` e ignora o caractere |
| Caracteres `╔ ║ ┌` ilegíveis no terminal | o `main` força saída UTF-8; use um terminal UTF-8 |

Limitações da linguagem (por projeto): sem chamadas de função, parâmetros nem variáveis locais na versão original; sem atribuição de arranjos inteiros; `print` aceita apenas escalares; só `int → double` é coerção implícita.
