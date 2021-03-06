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
lexer grammar NimrodCString;

fragment
SCharSequence : SChar+ ;

fragment
SChar
	: ~["\\\r\n]
	| EscapeSequence
	| '\\\n'
	| '\\\r\n'
	;

fragment
OctalDigit : [0-7] ;

fragment
HexadecimalDigit : [0-9a-fA-F] ;

fragment
EscapeSequence
	:   SimpleEscapeSequence
	|   OctalEscapeSequence
	|   HexadecimalEscapeSequence
	;

fragment
SimpleEscapeSequence : '\\' ['"?abfnrtv\\] ;

fragment
OctalEscapeSequence
	:   '\\' OctalDigit
	|   '\\' OctalDigit OctalDigit
	|   '\\' OctalDigit OctalDigit OctalDigit
	;

fragment
HexadecimalEscapeSequence : '\\x' HexadecimalDigit+ ;

