/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2021 The University of Queensland
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
package au.edu.uq.rcc.nimrodg.parsing;

import au.edu.uq.rcc.nimrodg.api.PlanfileParseException;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParser;
import java.util.HashSet;
import java.util.Set;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

/**
 * This could probably do more, but this is good-enough.
 */
class UselessErrorListener extends BaseErrorListener {

	private final PlanfileParseException exception;
	private final Set<RuntimeException> rtes;

	public UselessErrorListener(PlanfileParseException exception) {
		this.exception = exception;
		this.rtes = new HashSet<>();
	}

	@Override
	public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
		if(rtes.add(e)) {
			exception.addError(line, charPositionInLine, msg);
		}
	}

	public void recognitionException(RecognitionException e) {
		/* This is uber-paranoid of me, but whatever. */
		String[] expected = e.getExpectedTokens().toList().stream()
				.map(i -> NimrodFileParser.VOCABULARY.getDisplayName(i))
				.toArray(String[]::new);

		String toks;
		if(expected.length == 1) {
			toks = expected[0];
		} else {
			toks = "{" + String.join(", ", expected) +  "}";
		}

		Token tok = e.getOffendingToken();
		String msg = String.format("mismatched input '%s' expecting %s", tok.getText(), toks);

		syntaxError(e.getRecognizer(), tok, tok.getLine(), tok.getCharPositionInLine(), msg, e);
	}

	public PlanfileParseException getException() {
		return exception;
	}
}
