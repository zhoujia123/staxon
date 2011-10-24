/*
 * Copyright 2011 Odysseus Software GmbH
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
package de.odysseus.staxon.json.stream.simple;

import java.io.Closeable;
import java.io.IOException;

import de.odysseus.staxon.json.stream.JsonStreamSource;
import de.odysseus.staxon.json.stream.JsonStreamToken;

/**
 * Simple <code>JsonStreamSource</code> implementation.
 */
class SimpleJsonStreamSource implements JsonStreamSource {
	/**
	 * Scanner interface
	 */
	interface Scanner extends Closeable {
		enum Symbol {
		    START_OBJECT,
		    END_OBJECT,
		    START_ARRAY,
		    END_ARRAY,
		    COLON,
		    COMMA,
		    STRING,
		    NUMBER,
		    TRUE,
		    FALSE,
		    NULL,
		    EOF;
		}
		Symbol nextSymbol() throws IOException;
		String getText();

		int getCharOffset();
		int getLineNumber();
		int getColumnNumber();
	}
	
	private final Scanner scanner;
	private final boolean[] arrays = new boolean[64];
	private final boolean closeScanner;

	private JsonStreamToken token;
	private int depth;
	
	SimpleJsonStreamSource(Scanner scanner, boolean closeScanner) {
		this.scanner = scanner;
		this.closeScanner = closeScanner;
	}

	private JsonStreamToken startJsonValue(Scanner.Symbol symbol) throws IOException {
		switch (symbol) {
		case FALSE:
		case NULL:
		case NUMBER:
		case TRUE:
		case STRING:
			return JsonStreamToken.VALUE;
		case START_ARRAY:
			if (arrays[depth]) {
				throw new IOException("Already in an array");
			}
			arrays[depth] = true;
			return JsonStreamToken.START_ARRAY;
		case START_OBJECT:
			depth++;
			return JsonStreamToken.START_OBJECT;
		default:
			throw new IOException("Unexpected symbol: " + symbol);
		}
	}
	
	private void require(Scanner.Symbol expected, Scanner.Symbol actual) throws IOException {
		if (actual != expected) {
			throw new IOException("Unexpected symbol:" + actual);
		}		
	}

	private JsonStreamToken next() throws IOException {
		Scanner.Symbol symbol = scanner.nextSymbol();
		if (symbol == Scanner.Symbol.EOF) {
			return JsonStreamToken.NONE;
		}
		if (token == null) {
			if (symbol == Scanner.Symbol.START_OBJECT) {
				return JsonStreamToken.START_OBJECT;
			} else {
				throw new IOException("Unexpected symbol: " + symbol);
			}
		}
		switch (token) {
		case NAME:
			require(Scanner.Symbol.COLON, symbol);
			symbol = scanner.nextSymbol();
			return startJsonValue(symbol);
		case END_OBJECT:
		case END_ARRAY:
		case VALUE:
			switch (symbol) {
			case COMMA:
				symbol = scanner.nextSymbol();
				if (arrays[depth]) {
					return startJsonValue(symbol);
				} else {
					require(Scanner.Symbol.STRING, symbol);
					return JsonStreamToken.NAME;
				}
			case END_ARRAY:
				if (!arrays[depth]) {
					throw new IOException("Not in an array");
				}
				arrays[depth] = false;
				return JsonStreamToken.END_ARRAY;
			case END_OBJECT:
				depth--;
				return JsonStreamToken.END_OBJECT;
			default:
				throw new IOException("Unexpected symbol: " + symbol);
			}
		case START_OBJECT:
			switch (symbol) {
			case END_OBJECT:
				depth--;
				return JsonStreamToken.END_OBJECT;
			case STRING:
				return JsonStreamToken.NAME;
			default:
				throw new IOException("Unexpected symbol: " + symbol);
			}
		case START_ARRAY:
			switch (symbol) {
			case END_ARRAY:
				arrays[depth] = false;
				return JsonStreamToken.END_ARRAY;
			default:
				return startJsonValue(symbol);
			}
		default:
			throw new IOException("Unexpected token: " + token);
		}
	}
	
	@Override
	public void close() throws IOException {
		if (closeScanner) {
			scanner.close();
		}
	}

	private void require(JsonStreamToken token) throws IOException {
		if (token != peek()) {
			throw new IOException();
		}
	}
	
	@Override
	public String name() throws IOException {
		require(JsonStreamToken.NAME);
		String result = scanner.getText();
		token = next();
		return result;
	}

	@Override
	public String value() throws IOException {
		require(JsonStreamToken.VALUE);
		String result = scanner.getText();
		token = next();
		return result;
	}

	@Override
	public void startObject() throws IOException {
		require(JsonStreamToken.START_OBJECT);
		token = next();
	}

	@Override
	public void endObject() throws IOException {
		require(JsonStreamToken.END_OBJECT);
		token = next();
	}

	@Override
	public void startArray() throws IOException {
		require(JsonStreamToken.START_ARRAY);
		token = next();
	}

	@Override
	public void endArray() throws IOException {
		require(JsonStreamToken.END_ARRAY);
		token = next();
	}

	@Override
	public JsonStreamToken peek() throws IOException {
		if (token == null) {
			token = next();
		}
		return token;
	}
}
