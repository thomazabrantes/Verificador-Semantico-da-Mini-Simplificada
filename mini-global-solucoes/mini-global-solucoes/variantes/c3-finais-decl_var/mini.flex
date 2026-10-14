/* mini.flex — analisador léxico da linguagem Mini (JFlex + BYACC/J) */
%%
%byaccj
%line
%unicode

%{
  private Parser yyparser;
  public Yylex(java.io.Reader r, Parser yyparser) { this(r); this.yyparser = yyparser; }
  public int getLine() { return yyline + 1; }
%}

NUM  = [0-9]+
REAL = [0-9]+"."[0-9]+
ID   = [a-zA-Z_][a-zA-Z0-9_]*
WS   = [ \t\r\n\f]+

%%
"//".*        { /* comentário */ }

"$MOSTRA_TS"   { return Parser.MOSTRA_TS; }   /* diretivas de depuração */
"$TRACE_ON"    { return Parser.TRACE_ON; }
"$TRACE_OFF"   { return Parser.TRACE_OFF; }
{WS}          { }

"int"         { return Parser.INT; }
"double"      { return Parser.DOUBLE; }
"bool"        { return Parser.BOOL; }
"void"        { return Parser.VOID; }
"if"          { return Parser.IF; }
"else"        { return Parser.ELSE; }
"while"       { return Parser.WHILE; }
"break"       { return Parser.BREAK; }
"return"      { return Parser.RETURN; }
"print"       { return Parser.PRINT; }
"true"        { return Parser.TRUE; }
"false"       { return Parser.FALSE; }

"=="          { return Parser.EQ; }
"!="          { return Parser.NE; }
"<="          { return Parser.LE; }
">="          { return Parser.GE; }
"&&"          { return Parser.AND; }
"||"          { return Parser.OR; }

"+" | "-" | "*" | "/" | "%" | "<" | ">" | "!" | "=" |
"(" | ")" | "{" | "}" | "[" | "]" | ";" | ","   { return yycharat(0); }

{REAL}        { yyparser.yylval = new ParserVal(Double.parseDouble(yytext())); return Parser.NUM_REAL; }
{NUM}         { yyparser.yylval = new ParserVal(Integer.parseInt(yytext()));   return Parser.NUM_INT; }
{ID}          { yyparser.yylval = new ParserVal(yytext());                     return Parser.ID; }

[^]           { System.err.println("Linha " + getLine() + ": caractere inválido '" + yytext() + "'"); }
