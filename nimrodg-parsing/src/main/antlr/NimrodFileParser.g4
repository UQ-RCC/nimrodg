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
parser grammar NimrodFileParser;

options { tokenVocab=NimrodFileLexer; }

@header {
package au.edu.uq.rcc.nimrodg.parsing.antlr;
}

positiveInteger		: PLUS? INTEGER_CONSTANT ;
negativeInteger		: MINUS INTEGER_CONSTANT ;
positiveDecimal		: PLUS? DECIMAL_CONSTANT ;
negativeDecimal		: MINUS DECIMAL_CONSTANT ;

integer				: positiveInteger | negativeInteger ;
decimal				: positiveDecimal | negativeDecimal ;
number				: integer | decimal ;
positiveNumber		: positiveInteger | positiveDecimal ;
negativeNumber		: negativeInteger | negativeDecimal ;


variableName			: IDENTIFIER ;
variableIndex		: INTEGER_CONSTANT ;
variableValue		: STRING_LITERAL | number ;
variableStatement	: VARIABLE variableName PARAMETER_INDEX variableIndex PARAMETER_LIST variableValue+ NEWLINE+ ;

jobIndex			: INTEGER_CONSTANT ;
jobVarIndex			: INTEGER_CONSTANT ;
jobEntry			: jobIndex jobVarIndex+ NEWLINE+ ;
jobsBlock			: JOBS NEWLINE+ jobEntry* JOBS_ENDJOBS NEWLINE+ ;

parameterName		: IDENTIFIER ;
parameterLabel		: STRING_LITERAL ;
parameterType		: PARAMETER_FLOAT | PARAMETER_INTEGER | PARAMETER_TEXT | PARAMETER_FILES ;



domainDefault		: variableValue ;
domainRange			: PARAMETER_RANGE PARAMETER_FROM number PARAMETER_TO number (PARAMETER_STEP positiveNumber | PARAMETER_POINTS positiveInteger)? ;
domainRandom		: PAREMETER_RANDOM PARAMETER_FROM number PARAMETER_TO number (PARAMETER_POINTS positiveInteger)? ;
domainAnyof			: PARAMETER_SELECT PARAMETER_ANYOF variableValue+ ;

parameterDomain		: domainDefault
					| domainRange
					| domainRandom
					| domainAnyof
					;

parameterStatement	: PARAMETER parameterName (PARAMETER_LABEL parameterLabel)? (parameterType parameterDomain)? NEWLINE+ ;

/*
** Task Mode
*/

sliteral			: STRING_LITERAL
					| TM_SLITERAL_ARG
					| TM_LITERAL_ARG
					| TM_TASKNAME
					| TM_ENDTASK
					| TM_ONERROR
					| TM_REDIRECT
					| TM_COPY
					| TM_SHEXEC
					| TM_EXEC
					| TM_LEXEC
					| TM_LPEXEC
					| TM_APPEND
					| TM_STDOUT
					| TM_STDERR
					| TM_OFF
					| TM_TO
					| TM_CONTEXT
					| TM_ACTION
					;

literal				: STRING_LITERAL | TM_LITERAL_ARG | INTEGER_CONSTANT;

onerrorCommand		: TM_ONERROR TM_ACTION ;

copyFile			: (TM_CONTEXT TM_COLON)? sliteral ;
copyCommand			: TM_COPY copyFile copyFile ;

argList				: (sliteral+ | TM_CONTINUATION NEWLINE? | TM_CONTINUATION sliteral+)* ;
shexecCommand		: TM_SHEXEC sliteral ;
execCommand			: TM_EXEC literal argList ;
lexecCommand		: TM_LEXEC literal argList ;
lpexecCommand		: TM_LPEXEC literal argList ;

redirectStream		: TM_STDOUT | TM_STDERR ;
redirectTarget		: TM_OFF | TM_APPEND? TM_TO sliteral ;
redirectCommand		: TM_REDIRECT redirectStream redirectTarget ;

taskCommand			: onerrorCommand
					| redirectCommand
					| copyCommand
					| shexecCommand
					| execCommand
					| lexecCommand
					| lpexecCommand
					;

variableBlock		: (variableStatement | parameterStatement)+;
taskBlock			: TASK TM_TASKNAME NEWLINE+ (taskCommand NEWLINE+)* TM_ENDTASK NEWLINE* ;

nimrodFile			:
	NEWLINE*
	variableBlock
	jobsBlock?
	taskBlock*
	EOF
	;