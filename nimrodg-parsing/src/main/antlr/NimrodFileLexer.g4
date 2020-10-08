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
NONDIGIT            : [a-zA-Z_];
fragment
DIGIT               : [0-9];
fragment
EXPONENT            : ('e'|'E') ('+'|'-')? DIGIT+ ;
STRING_LITERAL      : '"' SCharSequence? '"' ;


VARIABLE            : 'variable'  -> pushMode(VARIABLE_MODE);
PARAMETER           : 'parameter' -> pushMode(PARAMETER_MODE);
JOBS                : 'jobs'      -> pushMode(JOBS_MODE);
TASK                : 'task'      -> pushMode(TASK_MODE) ;

IDENTIFIER          : NONDIGIT (NONDIGIT|DIGIT)* ;
INTEGER_CONSTANT    : DIGIT+ ;
DECIMAL_CONSTANT    : DIGIT+ '.' DIGIT* EXPONENT?
                    | '.' DIGIT+ EXPONENT?
                    | DIGIT+ EXPONENT
                    ;

PLUS                : '+' ;
MINUS               : '-' ;

NEWLINE             : ('\r' '\n'? | '\n' | '\r') ;
WHITESPACE          : [ \r\t\u000C]+ -> skip ;
BLOCK_COMMENT       : '/*' .*? '*/'  -> skip ;
LINE_COMMENT        : ('//' | '#') ~[\r\n]*  -> skip ;

ERROR_CHAR          : . ;

mode PARAMETER_MODE ;
PARAMETER_FLOAT            : 'float'   ;
PARAMETER_INTEGER          : 'integer' ;
PARAMETER_TEXT             : 'text'    ;
PARAMETER_FILES            : 'files'   ;
PARAMETER_LABEL            : 'label'   ;
PARAMETER_SELECT           : 'select'  ;
PARAMETER_ANYOF            : 'anyof'   ;
PARAMETER_ONEOF            : 'oneof'   ;
PARAMETER_RANGE            : 'range'   ;
PARAMETER_FROM             : 'from'    ;
PARAMETER_TO               : 'to'      ;
PAREMETER_RANDOM           : 'random'  ;
PARAMETER_STEP             : 'step'    ;
PARAMETER_POINTS           : 'points'  ;
PAREMETER_DEFAULT          : 'default' ;

PARAMETER_IDENTIFIER       : IDENTIFIER       -> type(IDENTIFIER) ;
PARAMETER_STRING_LITERAL   : STRING_LITERAL   -> type(STRING_LITERAL) ;
PARAMETER_INTEGER_CONSTANT : INTEGER_CONSTANT -> type(INTEGER_CONSTANT);
PARAMETER_DECIMAL_CONSTANT : DECIMAL_CONSTANT -> type(DECIMAL_CONSTANT);
PARAMETER_PLUS             : PLUS             -> type(PLUS);
PARAMETER_MINUS            : MINUS            -> type(MINUS);

PARAMETER_WHITESPACE       : WHITESPACE       -> skip ;
PARAMETER_NEWLINE          : NEWLINE          -> type(NEWLINE), popMode ;
PARAMETER_BLOCK_COMMENT    : BLOCK_COMMENT    -> skip ;
PARAMETER_LINE_COMMENT     : LINE_COMMENT     -> skip ;
PARAMETER_ERROR_CHAR       : ERROR_CHAR       -> type(ERROR_CHAR) ;

mode VARIABLE_MODE ;
PARAMETER_INDEX            : 'index'   ;
PARAMETER_LIST             : 'list'    ;

VARIABLE_IDENTIFIER        : IDENTIFIER       -> type(IDENTIFIER) ;
VARIABLE_STRING_LITERAL    : STRING_LITERAL   -> type(STRING_LITERAL) ;
VARIABLE_INTEGER_CONSTANT  : INTEGER_CONSTANT -> type(INTEGER_CONSTANT);
VARIABLE_DECIMAL_CONSTANT  : DECIMAL_CONSTANT -> type(DECIMAL_CONSTANT);
VARIABLE_PLUS              : PLUS             -> type(PLUS);
VARIABLE_MINUS             : MINUS            -> type(MINUS);

VARIABLE_WHITESPACE        : WHITESPACE       -> skip ;
VARIABLE_NEWLINE           : NEWLINE          -> type(NEWLINE), popMode ;
VARIABLE_BLOCK_COMMENT     : BLOCK_COMMENT    -> skip ;
VARIABLE_LINE_COMMENT      : LINE_COMMENT     -> skip ;
VARIABLE_ERROR_CHAR        : ERROR_CHAR       -> type(ERROR_CHAR) ;

mode JOBS_MODE ;
JOBS_ENDJOBS          : 'endjobs'        -> popMode ;
JOBS_INTEGER_CONSTANT : INTEGER_CONSTANT -> type(INTEGER_CONSTANT);

JOBS_WHITESPACE       : WHITESPACE       -> skip ;
JOBS_NEWLINE          : NEWLINE          -> type(NEWLINE) ;
JOBS_BLOCK_COMMENT    : BLOCK_COMMENT    -> skip ;
JOBS_LINE_COMMENT     : LINE_COMMENT     -> skip ;
JOBS_ERROR_CHAR       : ERROR_CHAR       -> type(ERROR_CHAR) ;


mode TASK_MODE ;
TM_TASKNAME             : 'nodestart' | 'main' ;
TM_ENDTASK              : 'endtask' -> popMode ;

TM_ONERROR              : 'onerror' ;
TM_REDIRECT             : 'redirect' ;
TM_COPY                 : 'copy' ;
TM_SHEXEC               : 'shexec' ;
TM_EXEC                 : 'exec' ;
TM_LEXEC                : 'lexec' ;
TM_LPEXEC               : 'lpexec' ;

TM_APPEND               : 'append' ;
TM_STDOUT               : 'stdout' ;
TM_STDERR               : 'stderr' ;
TM_OFF                  : 'off' ;
TM_TO                   : 'to' ;
TM_CONTEXT              : 'node' | 'root' ;
TM_ACTION               : 'fail' | 'ignore' ;

fragment
TM_SUBSTITUTION         : '$' (IDENTIFIER | '{' IDENTIFIER '}');
fragment
TM_LITERAL_CHARS        : [./<>&?\-] ;
fragment
TM_LITERAL_COMPONENT    : DIGIT | NONDIGIT | IDENTIFIER | TM_LITERAL_CHARS;

TM_STRING_LITERAL       : STRING_LITERAL -> type(STRING_LITERAL) ;
TM_LITERAL_ARG          : TM_LITERAL_COMPONENT+ ;
TM_SLITERAL_ARG         : (TM_LITERAL_COMPONENT | TM_SUBSTITUTION)+ ;
TM_COLON                : ':' ;
TM_CONTINUATION         : '\\' ;

TM_NEWLINE              : NEWLINE -> type(NEWLINE) ;
TM_WHITESPACE           : WHITESPACE -> skip ;
TM_BLOCK_COMMENT        : BLOCK_COMMENT -> skip ;
TM_LINE_COMMENT         : LINE_COMMENT -> skip ;

TM_ERROR_CHAR           : ERROR_CHAR -> type(ERROR_CHAR) ;