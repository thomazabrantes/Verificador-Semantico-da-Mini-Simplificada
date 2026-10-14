# Makefile — verificador semântico da Mini simplificada (globais + main; JFlex + BYACC/J)
#
#   make            gera e compila tudo
#   make ok         executa exemplos/ok.mini com -v (mostra os escopos)
#   make erros      executa exemplos/erros.mini
#   make tabela     executa exemplos/tabela.mini (usa $MOSTRA_TS e $TRACE_ON)
#   make trace      executa exemplos/ok.mini com -t (trace completo da TS)
#   make test       roda os três exemplos e confere o resultado esperado
#   make conflitos  gera y.output e mostra conflitos da gramática
#   make clean      remove arquivos gerados
#
# Ferramentas com nome ou caminho diferente podem ser trocadas na linha de comando:
#   make JFLEX="java -jar jflex-full-1.9.1.jar" BYACCJ=./yacc.linux

JFLEX   ?= jflex
BYACCJ  ?= byaccj
JAVAC   ?= javac
JAVA    ?= java
JFLAGS  ?= -encoding UTF-8 -nowarn

GERADOS = Yylex.java Parser.java ParserVal.java
FONTES  = Tipo.java Simbolo.java TabSimb.java DeclId.java

.PHONY: all ok erros tabela trace test conflitos clean

all: Parser.class

Yylex.java: mini.flex
	$(JFLEX) -q mini.flex

# -J: gera Java; -Jnorun: o main() está na seção de código do mini.y
# (regra compatível com o GNU Make 3.81 do macOS: ParserVal.java sai junto)
Parser.java: mini.y
	$(BYACCJ) -J -Jnorun mini.y

ParserVal.java: Parser.java

Parser.class: $(GERADOS) $(FONTES)
	$(JAVAC) $(JFLAGS) $(GERADOS) $(FONTES)

ok: all
	$(JAVA) Parser -v exemplos/ok.mini

erros: all
	$(JAVA) Parser exemplos/erros.mini

tabela: all
	$(JAVA) Parser exemplos/tabela.mini

trace: all
	$(JAVA) Parser -t exemplos/ok.mini

test: all
	@$(JAVA) Parser exemplos/ok.mini    | tail -1 | grep -q "semanticamente correto" \
	    && echo "ok.mini    : OK (sem erros)" || { echo "ok.mini    : FALHOU"; exit 1; }
	@$(JAVA) Parser exemplos/erros.mini | tail -1 | grep -q "^15 erro" \
	    && echo "erros.mini : OK (15 erros)" || { echo "erros.mini : FALHOU"; exit 1; }
	@$(JAVA) Parser exemplos/tabela.mini | tail -1 | grep -q "semanticamente correto" \
	    && echo "tabela.mini: OK (sem erros)" || { echo "tabela.mini: FALHOU"; exit 1; }

conflitos: mini.y
	$(BYACCJ) -v -J -Jnorun mini.y
	@grep -i conflict y.output || echo "Nenhum conflito na gramática."

clean:
	rm -f *.class $(GERADOS) y.output
