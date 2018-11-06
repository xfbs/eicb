/*******************************************************************************
 * Copyright (C) 2016-2018 Embedded Systems and Applications Group
 * Department of Computer Science, Technische Universitaet Darmstadt,
 * Hochschulstr. 10, 64289 Darmstadt, Germany.
 * 
 * All rights reserved.
 * 
 * This software is provided free for educational use only.
 * It may not be used for commercial purposes without the
 * prior written permission of the authors.
 ******************************************************************************/
package mavlc.parser.recursive_descent;

import mavlc.ast.nodes.expression.*;
import mavlc.ast.nodes.function.FormalParameter;
import mavlc.ast.nodes.function.Function;
import mavlc.ast.nodes.module.Module;
import mavlc.ast.nodes.record.RecordElementDeclaration;
import mavlc.ast.nodes.record.RecordTypeDeclaration;
import mavlc.ast.nodes.statement.*;
import mavlc.ast.type.*;
import mavlc.error_reporting.SyntaxError;

import java.util.*;

import mavlc.parser.recursive_descent.Token.TokenType;
import static mavlc.ast.nodes.expression.Compare.Comparison.*;
import static mavlc.parser.recursive_descent.Token.TokenType.*;

/* TODO: Please fill this out!
 *
 * EiCB group number:
 * Names and student ID numbers of group members:
 */

/**
 * A recursive-descent parser for MAVL.
 */
public final class Parser {

	private final Deque<Token> tokens;
	private Token currentToken;

	/**
	 * Constructor.
	 *
	 * @param tokens A token stream that was produced by the {@link Scanner}.
	 */
	public Parser(Deque<Token> tokens) {
		this.tokens = tokens;
		currentToken = tokens.poll();
	}

	/**
	 * Parses the MAVL grammar's start symbol, Module.
	 *
	 * @return A {@link Module} node that is the root of the AST representing the tokenized input progam.
	 * @throws SyntaxError to indicate that an unexpected token was encountered.
	 */
	public Module parse() throws SyntaxError {
		Module compilationUnit = new Module(tokens.peek().line, 0);
		while (currentToken.type != EOF) {
			switch (currentToken.type) {
				case FUNCTION:
					Function func = parseFunction();
					compilationUnit.addFunction(func);
					break;
				case RECORD:
					RecordTypeDeclaration record = parseRecordTypeDeclaration();
					compilationUnit.addRecord(record);
					break;
				default:
					throw new SyntaxError(currentToken, FUNCTION, RECORD);
			}
		}
		return compilationUnit;
	}

	private String accept(TokenType type) throws SyntaxError {
		Token t = currentToken;
		if (t.type != type)
			throw new SyntaxError(t, type);
		acceptIt();
		return t.spelling;
	}

	private void acceptIt() {
		currentToken = tokens.poll();
		if (currentToken.type == ERROR)
			throw new SyntaxError(currentToken);
	}

	private Function parseFunction() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		accept(FUNCTION);
		Type type = parseType();
		String name = accept(ID);

		Function function = new Function(line, column, name, type);

		accept(LPAREN);
		if (currentToken.type != RPAREN) {
			function.addParameter(parseFormalParameter());
			while (currentToken.type != RPAREN) {
				accept(COMMA);
				function.addParameter(parseFormalParameter());
			}
		}
		accept(RPAREN);

		accept(LBRACE);
		while (currentToken.type != RBRACE)
			function.addStatement(parseStatement());
		accept(RBRACE);

		return function;
	}

	private FormalParameter parseFormalParameter() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		Type type = parseType();
		String name = accept(ID);

		return new FormalParameter(line, column, name, type);
	}

	private RecordTypeDeclaration parseRecordTypeDeclaration() {
		int line = currentToken.line;
		int column = currentToken.column;

		accept(RECORD);
		String name = accept(ID);
		accept(LBRACE);
		List<RecordElementDeclaration> elements = new ArrayList<>();
		// no empty records allowed
		elements.add(parseRecordElementDeclaration());
		while (currentToken.type != RBRACE) {
			elements.add(parseRecordElementDeclaration());
		}
		accept(RBRACE);

		return new RecordTypeDeclaration(line, column, name, elements);
	}

	private RecordElementDeclaration parseRecordElementDeclaration() {
		int line = currentToken.line;
		int column = currentToken.column;

		boolean isVariable;
		switch (currentToken.type) {
			case VAL: acceptIt(); isVariable = false; break;
			case VAR: acceptIt(); isVariable = true;  break;
			default:
				throw new SyntaxError(currentToken, VAL, VAR);
		}

		Type type = parseType();
		String name = accept(ID);
		accept(SEMICOLON);

		return new RecordElementDeclaration(line, column, isVariable, type, name);
	}

	private IteratorDeclaration parseIteratorDeclaration() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		boolean isVariable;
		switch (currentToken.type) {
			case VAL: accept(VAL); isVariable = false; break;
			case VAR: accept(VAR); isVariable = true;  break;
			default:
				throw new SyntaxError(currentToken, VAL, VAR);
		}
		Type type = parseType();
		String name = accept(ID);
		return new IteratorDeclaration(line, column, name, type, isVariable);
	}

	private Type parseType() throws SyntaxError {
		boolean vector = false;
		switch (currentToken.type) {
			case INT:    acceptIt(); return Type.getIntType();
			case FLOAT:  acceptIt(); return Type.getFloatType();
			case BOOL:   acceptIt(); return Type.getBoolType();
			case VOID:   acceptIt(); return Type.getVoidType();
			case STRING: acceptIt(); return Type.getStringType();
			case VECTOR: accept(VECTOR); vector = true; break;
			case MATRIX: accept(MATRIX); break;
			case ID:	 String name = accept(ID);
				return new RecordType(name);
			default:
				throw new SyntaxError(currentToken, INT, FLOAT, BOOL, VOID, STRING, VECTOR, MATRIX, ID);
		}

		accept(LANGLE);
		ScalarType subtype = null;
		switch (currentToken.type) {
			case INT:   subtype = Type.getIntType(); break;
			case FLOAT: subtype = Type.getFloatType(); break;
			default:
				throw new SyntaxError(currentToken, INT, FLOAT);
		}
		acceptIt();
		accept(RANGLE);
		accept(LBRACKET);
		Expression x = parseExpr();
		accept(RBRACKET);

		if (vector)
			return new VectorType(subtype, x);

		accept(LBRACKET);
		Expression y = parseExpr();
		accept(RBRACKET);

		return new MatrixType(subtype, x, y);
	}

	private Statement parseStatement() throws SyntaxError {
		Statement s = null;
		switch (currentToken.type) {
			case VAL:    s = parseValueDef();     break;
			case VAR:    s = parseVarDecl();      break;
			case RETURN: s = parseReturn();       break;
			case ID:     s = parseAssignOrCall(); break;
			case FOR:    s = parseFor();          break;
			case FOREACH:s = parseForEach();      break;
			case IF:     s = parseIf();           break;
			case SWITCH: s = parseSwitch();       break;
			case LBRACE: s = parseCompound();     break;
			default:
				throw new SyntaxError(currentToken, VAL, VAR, RETURN, ID, FOR, FOREACH, IF, SWITCH, LBRACE);
		}

		return s;
	}

	private ValueDefinition parseValueDef() throws SyntaxError {
		/* TODO: implement (exercise 1.1) */
		throw new UnsupportedOperationException();
	}

	private VariableDeclaration parseVarDecl() throws SyntaxError {
		/* TODO: implement (exercise 1.1) */
		throw new UnsupportedOperationException();
	}

	private ReturnStatement parseReturn() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;
		accept(RETURN);
		Expression e = parseExpr();
		accept(SEMICOLON);

		return new ReturnStatement(line, column, e);
	}

	private Statement parseAssignOrCall() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		String name = accept(ID);

		Statement s;
		if (currentToken.type != LPAREN)
			s = parseAssign(name, line, column);
		else
			s = new CallStatement(line, column, parseCall(name, line, column));
		accept(SEMICOLON);

		return s;
	}

	private VariableAssignment parseAssign(String name, int line, int column) throws SyntaxError {
		/* TODO: implement (exercise 1.1) */
		throw new UnsupportedOperationException();
	}

	private CallExpression parseCall(String name, int line, int column) {
		CallExpression callExpression = new CallExpression(line, column, name);
		accept(LPAREN);
		if (currentToken.type != RPAREN) {
			callExpression.addActualParameter(parseExpr());
			while (currentToken.type != RPAREN) {
				accept(COMMA);
				callExpression.addActualParameter(parseExpr());
			}
		}
		accept(RPAREN);

		return callExpression;
	}

	private ForLoop parseFor() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		accept(FOR);
		accept(LPAREN);
		String name = accept(ID);
		accept(ASSIGN);
		Expression a = parseExpr();
		accept(SEMICOLON);
		Expression b = parseExpr();
		accept(SEMICOLON);
		String inc = accept(ID);
		accept(ASSIGN);
		Expression c = parseExpr();
		accept(RPAREN);
		return new ForLoop(line, column, name, a, b, inc, c, parseStatement());
	}

	private ForEachLoop parseForEach() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		accept(FOREACH);
		accept(LPAREN);
		IteratorDeclaration param = parseIteratorDeclaration();
		accept(COLON);
		Expression struct = parseExpr();
		accept(RPAREN);
		return new ForEachLoop(line, column, param, struct, parseStatement());
	}

	private IfStatement parseIf() throws SyntaxError {
		/* TODO: implement (exercise 1.5) */
		throw new UnsupportedOperationException();
	}

	private SwitchStatement parseSwitch() throws SyntaxError {
		/* TODO: implement (exercise 1.6) */
		throw new UnsupportedOperationException();
	}

	private Case parseCase() throws SyntaxError {
		/* TODO: implement (exercise 1.6) */
		throw new UnsupportedOperationException();
	}

	private Default parseDefault() throws SyntaxError {
		/* TODO: implement (exercise 1.6) */
		throw new UnsupportedOperationException();
	}

	private CompoundStatement parseCompound() throws SyntaxError {
		/* TODO: implement (exercise 1.3) */
		throw new UnsupportedOperationException();
	}

	private Expression parseExpr() throws SyntaxError {
		return parseSelect();
	}

	private Expression parseSelect() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		Expression cond = parseOr();
		if(currentToken.type == QMARK) {
			acceptIt();
			Expression trueCase = parseOr();
			accept(COLON);
			Expression falseCase = parseOr();
			return new SelectExpression(line, column, cond, trueCase, falseCase);
		}
		return cond;
	}

	private Expression parseOr() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		Expression x = parseAnd();
		while (currentToken.type == OR) {
			acceptIt();
			x = new Or(line, column, x, parseAnd());
		}
		return x;
	}

	private Expression parseAnd() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		Expression x = parseNot();
		while (currentToken.type == AND) {
			acceptIt();
			x = new And(line, column, x, parseNot());
		}
		return x;
	}

	private Expression parseNot() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		if (currentToken.type == NOT) {
			acceptIt();
			return new BoolNot(line, column, /* TODO: insert call to the appropriate parse method (exercise 1.2) */ null);
		}
		return /* TODO: insert call to the appropriate parse method (exercise 1.2) */ null;
	}

	private Expression parseCompare() throws SyntaxError {
		/* TODO: implement (exercise 1.2) */
		throw new UnsupportedOperationException();
	}

	private Expression parseAddSub() throws SyntaxError {
		/* TODO: implement (exercise 1.2) */
		throw new UnsupportedOperationException();
	}

	private Expression parseMulDiv() throws SyntaxError {
		/* TODO: implement (exercise 1.2) */
		throw new UnsupportedOperationException();
	}

	private Expression parseUnaryMinus() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		if (currentToken.type == SUB) {
			acceptIt();
			return new UnaryMinus(line, column, /* TODO: insert call to the appropriate parse method (exercise 1.2) */ null);
		} else {
			return /* TODO: insert call to the appropriate parse method (exercise 1.2) */ null;
		}
	}

	private Expression parseExponentiation() throws SyntaxError {
		/* TODO: implement (exercise 1.2) */
		throw new UnsupportedOperationException();
	}

	private Expression parseDim() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		Expression x = parseDotProd();
		switch (currentToken.type) {
			case XDIM: acceptIt(); return new MatrixXDimension(line, column, x);
			case YDIM: acceptIt(); return new MatrixYDimension(line, column, x);
			case DIM:  acceptIt(); return new VectorDimension(line, column, x);
			default:
				return x;
		}
	}

	private Expression parseDotProd() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		Expression x = parseMatrixMul();
		while (currentToken.type == DOTPROD) {
			acceptIt();
			x = new DotProduct(line, column, x, parseMatrixMul());
		}

		return x;
	}

	private Expression parseMatrixMul() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		Expression x = parseSubrange();
		while (currentToken.type == MATMULT) {
			acceptIt();
			x = new MatrixMultiplication(line, column, x, parseSubrange());
		}
		return x;

	}

	private Expression parseSubrange() throws SyntaxError {
		/* TODO: implement (exercise 1.4) */
		throw new UnsupportedOperationException();
	}

	private Expression parseElementSelect() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		Expression x = parseRecordElementSelect();

		while (currentToken.type == LBRACKET) {
			acceptIt();
			Expression idx = parseExpr();
			accept(RBRACKET);
			x = new ElementSelect(line, column, x, idx);
		}

		return x;
	}

	private Expression parseRecordElementSelect() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		Expression x = parseAtom();

		if (currentToken.type == AT) {
			accept(AT);
			String elementName = accept(ID);
			x = new RecordElementSelect(line, column, x, elementName);
		}

		return x;
	}

	private Expression parseAtom() throws SyntaxError {
		int line = currentToken.line;
		int column = currentToken.column;

		switch (currentToken.type) {
			case INTLIT:    return new IntValue(line, column, parseIntLit());
			case FLOATLIT:  return new FloatValue(line, column, parseFloatLit());
			case BOOLLIT:   return new BoolValue(line, column, parseBoolLit());
			case STRINGLIT: return new StringValue(line, column, accept(STRINGLIT));
			default: /* check other cases below */
		}

		if (currentToken.type == ID) {
			String name = accept(ID);
			if (currentToken.type != LPAREN){
				return  new IdentifierReference(line, column, name);

			}else{
				return parseCall(name, line, column);
			}
		}

		if (currentToken.type == LPAREN) {
			acceptIt();
			Expression x = parseExpr();
			accept(RPAREN);
			return x;
		}

		StructureInit s = new StructureInit(line, column);
		if (currentToken.type == AT) {
			acceptIt();
			String name = accept(ID);
			s = new RecordInit(line, column, name);
		}
		if (currentToken.type == LBRACKET) {
			acceptIt();
			s.addElement(parseExpr());
			while (currentToken.type == COMMA) {
				accept(COMMA);
				s.addElement(parseExpr());
			}
			accept(RBRACKET);
			return s;
		}

		throw new SyntaxError(currentToken, INTLIT, FLOATLIT, BOOLLIT, STRINGLIT, ID, LPAREN, LBRACKET, AT);
	}

	private int parseIntLit() throws SyntaxError {
		String s = accept(INTLIT);
		return Integer.parseInt(s);
	}

	private float parseFloatLit() throws SyntaxError {
		return Float.parseFloat(accept(FLOATLIT));
	}

	private boolean parseBoolLit() throws SyntaxError {
		return Boolean.parseBoolean(accept(BOOLLIT));
	}
}
