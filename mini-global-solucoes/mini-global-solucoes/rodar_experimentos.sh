#!/bin/bash
# Roda os experimentos das Partes C e D. Requer: jflex, byaccj, JDK (javac/java), make.
# Uso:  bash rodar_experimentos.sh        (a partir desta pasta)
cd "$(dirname "$0")" || exit 1
T=$(pwd)/testes
secao() { printf '\n\n############ %s ############\n' "$1"; }
filtra() { grep -v '^Note'; }

( cd variantes/original && make -s 2>&1 | filtra )
run() { ( cd variantes/original && java Parser "$@" ); }

secao "ORIGINAL: make test";                                ( cd variantes/original && make -s test 2>&1 | filtra )
secao "C3 (a): declaração após main  -> erro SINTÁTICO";   run $T/c3a.mini
secao "C3 (b): função diferente de main -> erro SEMÂNTICO"; run $T/c3b.mini
secao "C5: 'int main; void main(){}'";                      run $T/c5a.mini
secao "C5: 'x = main;'";                                    run $T/c5b.mini
secao "C5: 'int a, a;'";                                    run $T/c5d.mini
secao "C6: trace de 'int a, t[2][5], b;'";                  run -t $T/c6.mini | head -5
secao "C8: indexa()";                                       run $T/c8.mini
secao "C9: v == w (arranjos)";                              run $T/c9.mini
secao "C12: break aninhado / return 1";                     run $T/c12.mini
secao "C13: trace de blocos aninhados";                     run -t $T/c13.mini
secao "D1: make tabela";                                    ( cd variantes/original && make -s tabela 2>&1 | filtra )
secao "D2";                                                 run $T/d2.mini

secao "D3: variante com variáveis locais"
( cd variantes/d3-locais && make -s conflitos 2>&1 | tail -1; make -s 2>&1 | filtra; java Parser $T/d3.mini; make -s test )

secao "C3 (a) alterado: mensagem semântica clara"
( cd variantes/c3-finais-decl_var && make -s conflitos 2>&1 | tail -1; make -s 2>&1 | filtra; java Parser $T/c3a.mini; make -s test )

secao "D4: índices constantes fora dos limites"
( cd variantes/d4-limites-constantes && make -s conflitos 2>&1 | tail -1; make -s 2>&1 | filtra; java Parser $T/d4.mini; make -s test )
