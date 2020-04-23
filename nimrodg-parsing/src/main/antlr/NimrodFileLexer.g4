/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2019 The University of Queensland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
lexer grammar NimrodFileLexer;
import NimrodCString;

@header {
package au.edu.uq.rcc.nimrodg.parsing.antlr;
}

fragment
NONDIGIT					: [a-zA-Z_];

fragment
DIGIT					: [0-9];


STRING_LITERAL			: '"' SCharSequence? '"' ;


/*
** Reserved Words
*/

/* Variable and parameter definitions */
VARIABLE					: 'variable' ;
PARAMETER				: 'parameter' ;
OUTPUT                  : 'output' ;

INDEX					: 'index' ;
LIST						: 'list' ;

FLOAT					: 'float' ;
INTEGER					: 'integer' ;
TEXT						: 'text' ;
FILES					: 'files' ;
LABEL					: 'label' ;
SELECT					: 'select' ;
ANYOF					: 'anyof' ;
ONEOF					: 'oneof' ;
RANGE					: 'range' ;
FROM						: 'from' ;
TO						: 'to' ;
RANDOM					: 'random';
STEP						: 'step' ;
POINTS					: 'points' ;
DEFAULT					: 'default' ;

/* Job definitions */
JOBS						: 'jobs' ;
ENDJOBS					: 'endjobs' ;

TASK						: 'task' -> pushMode(TASK_MODE) ;

IDENTIFIER				: NONDIGIT (NONDIGIT|DIGIT)* ;

INTEGER_CONSTANT			: DIGIT+ ;

fragment
EXPONENT					: ('e'|'E') ('+'|'-')? DIGIT+ ;

DECIMAL_CONSTANT			: DIGIT+ '.' DIGIT* EXPONENT?
						| '.' DIGIT+ EXPONENT?
						| DIGIT+ EXPONENT
						;

SEMICOLON				: ';' ;
PLUS						: '+' ;
MINUS					: '-' ;

NEWLINE					: ('\r' '\n'? | '\n' | '\r') ;
WHITESPACE				: [ \r\t\u000C]+ -> skip ;
BLOCK_COMMENT			: '/*' .*? '*/' -> skip ;
LINE_COMMENT				: '//' ~[\r\n]* -> skip ;

ERROR_CHAR				: . ;

/*
** Task mode, this uses almost completely different rules.
*/
mode TASK_MODE ;

TM_TASKNAME				: 'nodestart' | 'main' ;

TM_ENDTASK				: 'endtask' -> popMode ;

TM_ONERROR				: 'onerror' ;
TM_REDIRECT				: 'redirect' ;
TM_COPY					: 'copy' ;
TM_SHEXEC				: 'shexec' ;
TM_EXEC					: 'exec' ;
TM_LEXEC					: 'lexec' ;
TM_LPEXEC				: 'lpexec' ;
TM_SLURP                : 'slurp' ;

TM_APPEND				: 'append' ;
TM_STDOUT				: 'stdout' ;
TM_STDERR				: 'stderr' ;
TM_OFF					: 'off' ;
TM_TO					: 'to' ;
TM_CONTEXT				: 'node' | 'root' ;
TM_ACTION				: 'fail' | 'ignore' ;

TM_SLURP_REGEX          : 'regex' ;
TM_SLURP_NONREGEX       : 'json' | 'xml' | 'csv' | 'hcsv';

fragment
TM_SUBSTITUTION			: '$' (IDENTIFIER | '{' IDENTIFIER '}');

fragment
TM_LITERAL_CHARS			: [./<>&?\-|^*+()$\\,] | '[' | ']' ;

TM_STRING_LITERAL			: STRING_LITERAL -> type(STRING_LITERAL) ;


fragment
TM_LITERAL_COMPONENT		: DIGIT | NONDIGIT | TM_LITERAL_CHARS;

TM_IDENTIFIER           : IDENTIFIER ;
TM_LITERAL_ARG			: (TM_LITERAL_COMPONENT | TM_IDENTIFIER)+ ;
TM_SLITERAL_ARG			: (TM_LITERAL_COMPONENT | TM_SUBSTITUTION)+ ;

TM_COLON					: ':' ;

TM_CONTINUATION			: '\\' ;

TM_NEWLINE				: NEWLINE -> type(NEWLINE) ;
TM_WHITESPACE			: WHITESPACE -> skip ;
TM_BLOCK_COMMENT			: BLOCK_COMMENT -> skip ;
TM_LINE_COMMENT			: LINE_COMMENT -> skip ;

TM_ERROR_CHAR			: ERROR_CHAR -> type(ERROR_CHAR) ;
