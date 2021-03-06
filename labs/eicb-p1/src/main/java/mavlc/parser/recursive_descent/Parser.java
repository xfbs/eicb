/**
 * ***************************************************************************** Copyright (C)
 * 2016-2018 Embedded Systems and Applications Group Department of Computer Science, Technische
 * Universitaet Darmstadt, Hochschulstr. 10, 64289 Darmstadt, Germany.
 *
 * <p>All rights reserved.
 *
 * <p>This software is provided free for educational use only. It may not be used for commercial
 * purposes without the prior written permission of the authors.
 * ****************************************************************************
 */
package mavlc.parser.recursive_descent;

import static mavlc.ast.nodes.expression.Compare.Comparison.*;
import static mavlc.parser.recursive_descent.Token.TokenType.*;

import java.util.*;
import mavlc.ast.nodes.expression.*;
import mavlc.ast.nodes.expression.Compare.Comparison;
import mavlc.ast.nodes.function.FormalParameter;
import mavlc.ast.nodes.function.Function;
import mavlc.ast.nodes.module.Module;
import mavlc.ast.nodes.record.RecordElementDeclaration;
import mavlc.ast.nodes.record.RecordTypeDeclaration;
import mavlc.ast.nodes.statement.*;
import mavlc.ast.type.*;
import mavlc.error_reporting.SyntaxError;
import mavlc.parser.recursive_descent.Token.TokenType;

/* EiCB group number: 43
 * Authors: 
 * - Michael Matthe (2716677)
 * - Viola Hofmeister (2684741)
 * - Patrick Elsen (2656300)
 */

/** A recursive-descent parser for MAVL. */
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
   * @return A {@link Module} node that is the root of the AST representing the tokenized input
   *     progam.
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
    if (t.type != type) throw new SyntaxError(t, type);
    acceptIt();
    return t.spelling;
  }

  private void acceptIt() {
    currentToken = tokens.poll();
    if (currentToken.type == ERROR) throw new SyntaxError(currentToken);
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
    while (currentToken.type != RBRACE) function.addStatement(parseStatement());
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
      case VAL:
        acceptIt();
        isVariable = false;
        break;
      case VAR:
        acceptIt();
        isVariable = true;
        break;
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
      case VAL:
        accept(VAL);
        isVariable = false;
        break;
      case VAR:
        accept(VAR);
        isVariable = true;
        break;
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
      case INT:
        acceptIt();
        return Type.getIntType();
      case FLOAT:
        acceptIt();
        return Type.getFloatType();
      case BOOL:
        acceptIt();
        return Type.getBoolType();
      case VOID:
        acceptIt();
        return Type.getVoidType();
      case STRING:
        acceptIt();
        return Type.getStringType();
      case VECTOR:
        accept(VECTOR);
        vector = true;
        break;
      case MATRIX:
        accept(MATRIX);
        break;
      case ID:
        String name = accept(ID);
        return new RecordType(name);
      default:
        throw new SyntaxError(currentToken, INT, FLOAT, BOOL, VOID, STRING, VECTOR, MATRIX, ID);
    }

    accept(LANGLE);
    ScalarType subtype = null;
    switch (currentToken.type) {
      case INT:
        subtype = Type.getIntType();
        break;
      case FLOAT:
        subtype = Type.getFloatType();
        break;
      default:
        throw new SyntaxError(currentToken, INT, FLOAT);
    }
    acceptIt();
    accept(RANGLE);
    accept(LBRACKET);
    Expression x = parseExpr();
    accept(RBRACKET);

    if (vector) return new VectorType(subtype, x);

    accept(LBRACKET);
    Expression y = parseExpr();
    accept(RBRACKET);

    return new MatrixType(subtype, x, y);
  }

  private Statement parseStatement() throws SyntaxError {
    Statement s = null;
    switch (currentToken.type) {
      case VAL:
        s = parseValueDef();
        break;
      case VAR:
        s = parseVarDecl();
        break;
      case RETURN:
        s = parseReturn();
        break;
      case ID:
        s = parseAssignOrCall();
        break;
      case FOR:
        s = parseFor();
        break;
      case FOREACH:
        s = parseForEach();
        break;
      case IF:
        s = parseIf();
        break;
      case SWITCH:
        s = parseSwitch();
        break;
      case LBRACE:
        s = parseCompound();
        break;
      default:
        throw new SyntaxError(currentToken, VAL, VAR, RETURN, ID, FOR, FOREACH, IF, SWITCH, LBRACE);
    }

    return s;
  }

  /**
   * Parses a value definition.
   *
   * <p>A value definition has the form:
   *
   * <pre>
   * 'val' type ID '=' expr ';'
   * </pre>
   *
   * An example for such a definition would be:
   *
   * <pre>
   * val float pi = 3.14;
   * </pre>
   *
   * @return ValueDefinition
   * @throws SyntaxError, if a parsing error occurred.
   */
  private ValueDefinition parseValueDef() throws SyntaxError {
    int line = currentToken.line;
    int column = currentToken.column;

    accept(VAL);
    Type type = parseType();
    String name = accept(ID);
    accept(ASSIGN);
    Expression expression = parseExpr();
    accept(SEMICOLON);

    return new ValueDefinition(line, column, type, name, expression);
  }

  /**
   * Parses a variable declaration.
   *
   * <p>A variable declaration has the form
   *
   * <pre>
   * 'var' type ID ';'
   * </pre>
   *
   * An example for such a declaration would be:
   *
   * <pre>
   * var int count;
   * </pre>
   *
   * @return VariableDeclaration
   * @throws SyntaxError
   */
  private VariableDeclaration parseVarDecl() throws SyntaxError {
    int line = currentToken.line;
    int column = currentToken.column;

    accept(VAR);
    Type type = parseType();
    String name = accept(ID);
    accept(SEMICOLON);

    return new VariableDeclaration(line, column, type, name);
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
    if (currentToken.type != LPAREN) s = parseAssign(name, line, column);
    else s = new CallStatement(line, column, parseCall(name, line, column));
    accept(SEMICOLON);

    return s;
  }

  /**
   * Parses the right side (after the identifier) of a variable assignment.
   *
   * <p>Variable Assignment has the form:
   *
   * <pre>
   * ID (('[' expr ']' ('[' expr ']')? | '@' ID)? '=' expr ';'
   * </pre>
   *
   * An example statement would be:
   *
   * <pre>
   * my_vector[5] = 9;
   * </pre>
   *
   * This pases the right part of this, everything after the `ID` except the semicolon.
   *
   * @see parseAssignOrCall()
   * @param name: Name of the variable that is assigned.
   * @param line: Line on which the variable is specified.
   * @param column: Column in which the variable is specified.
   * @return VariableAssignment
   * @throws SyntaxError
   */
  private VariableAssignment parseAssign(String name, int line, int column) throws SyntaxError {
    LeftHandIdentifier lhs;

    switch (currentToken.type) {
      case LBRACKET:
        // my_vector[1] = 5
        acceptIt();
        Expression x = parseExpr();
        accept(RBRACKET);
        if (currentToken.type == LBRACKET) {
          // my_matrix[1][3] = 9
          acceptIt();
          lhs = new MatrixLHSIdentifier(line, column, name, x, parseExpr());
          accept(RBRACKET);
        } else {
          lhs = new VectorLHSIdentifier(line, column, name, x);
        }
        break;
      case AT:
        // my_struct@number = 5
        acceptIt();
        lhs = new RecordLHSIdentifier(line, column, name, accept(ID));
        break;
      default:
        // my_variable = 9
        lhs = new LeftHandIdentifier(line, column, name);
        break;
    }

    accept(ASSIGN);
    Expression expr = parseExpr();

    return new VariableAssignment(line, column, lhs, expr);
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

  /**
   * Parses an if statement.
   *
   * @return IfStatement
   * @throws SyntaxError
   */
  private IfStatement parseIf() throws SyntaxError {
    int line = currentToken.line;
    int column = currentToken.column;

    accept(IF);
    accept(LPAREN);
    Expression expr = parseExpr();
    accept(RPAREN);
    Statement ifStatement = parseStatement();

    // if there is no else statement, we're done here.
    if (currentToken.type != ELSE) {
      return new IfStatement(line, column, expr, ifStatement);
    }

    // parse else statement
    acceptIt();
    Statement elseStatement = parseStatement();
    return new IfStatement(line, column, expr, ifStatement, elseStatement);
  }

  /**
   * Parses a switch statement for switch-case.
   *
   * @return SwitchStatement
   * @throws SyntaxError
   */
  private SwitchStatement parseSwitch() throws SyntaxError {
    int line = currentToken.line;
    int column = currentToken.column;

    SwitchStatement switchStatement = new SwitchStatement(line, column);

    // parse test expression
    accept(SWITCH);
    accept(LPAREN);
    switchStatement.setTestExpression(parseExpr());
    accept(RPAREN);
    accept(LBRACE);

    // parse cases
    while (currentToken.type != RBRACE) {
      switch (currentToken.type) {
        case CASE:
          switchStatement.addCase(parseCase());
          break;
        case DEFAULT:
          switchStatement.addDefault(parseDefault());
          break;
      }
    }

    acceptIt();
    return switchStatement;
  }

  /**
   * Parses a case for switch-case.
   *
   * @return Case
   * @throws SyntaxError
   */
  private Case parseCase() throws SyntaxError {
    int line = currentToken.line;
    int column = currentToken.column;

    accept(CASE);
    Expression expression = parseExpr();
    accept(COLON);
    Statement statement = parseStatement();

    return new Case(line, column, expression, statement);
  }

  /**
   * Parses the default case for switch-case.
   *
   * @return Default
   * @throws SyntaxError
   */
  private Default parseDefault() throws SyntaxError {
    int line = currentToken.line;
    int column = currentToken.column;

    accept(DEFAULT);
    accept(COLON);
    Statement statement = parseStatement();

    return new Default(line, column, statement);
  }

  /**
   * Parses a compound statement.
   *
   * @return CompoundStatement
   * @throws SyntaxError
   */
  private CompoundStatement parseCompound() throws SyntaxError {
    int line = currentToken.line;
    int column = currentToken.column;
    CompoundStatement compoundStatement = new CompoundStatement(line, column);
    accept(LBRACE);

    // read statements
    while (currentToken.type != RBRACE) {
      compoundStatement.addStatement(parseStatement());
    }

    // accept right closing brace
    acceptIt();
    return compoundStatement;
  }

  private Expression parseExpr() throws SyntaxError {
    return parseSelect();
  }

  private Expression parseSelect() throws SyntaxError {
    int line = currentToken.line;
    int column = currentToken.column;

    Expression cond = parseOr();
    if (currentToken.type == QMARK) {
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

    // create a negation if there is a NOT operator
    if (currentToken.type == NOT) {
      acceptIt();
      return new BoolNot(line, column, parseCompare());
    }

    return parseCompare();
  }

  /**
   * Parses a compare expression
   *
   * <p>Parses an expression of the form:
   *
   * <pre>
   * addSub (( '&gt;' | '&lt;' | '&lt;=' | '&gt;=' | '==' | '!=' ) addSub)*
   * </pre>
   *
   * <p>Example:
   *
   * <pre>
   * 5 &lt; 3 == true
   * </pre>
   *
   * @return Expression
   * @throws SyntaxError
   */
  private Expression parseCompare() throws SyntaxError {
    int line = currentToken.line;
    int column = currentToken.column;

    Expression expression = parseAddSub();
    Comparison type;
    out:
    while (true) {
      // find out what type of comparison this is.
      switch (currentToken.type) {
        case RANGLE:
          type = GREATER;
          break;
        case LANGLE:
          type = LESS;
          break;
        case CMPLE:
          type = LESS_EQUAL;
          break;
        case CMPGE:
          type = GREATER_EQUAL;
          break;
        case CMPEQ:
          type = EQUAL;
          break;
        case CMPNE:
          type = NOT_EQUAL;
          break;
          // well, shit, if this isn't a comparison at all we're done.
        default:
          break out;
      }

      // this is a comparison of `type`, so we update the expression.
      acceptIt();
      expression = new Compare(line, column, expression, parseAddSub(), type);
    }

    return expression;
  }

  /**
   * Parses an addition / subtraction expression.
   *
   * <p>Addition and subtraction expressions have the form:
   *
   * <pre>
   * mulDiv (('+' | '-') mulDiv)*
   * </pre>
   *
   * Ein Beispiel für einen solchen Ausdruck ist
   *
   * <pre>
   * 2 + 18 - 5;
   * </pre>
   *
   * @return Expression
   * @throws SyntaxError
   */
  private Expression parseAddSub() throws SyntaxError {
    int line = currentToken.line;
    int column = currentToken.column;
    Expression expression = parseMulDiv();

    out:
    while (true) {
      switch (currentToken.type) {
        case ADD:
          acceptIt();
          expression = new Addition(line, column, expression, parseMulDiv());
          break;
        case SUB:
          acceptIt();
          expression = new Subtraction(line, column, expression, parseMulDiv());
          break;
        default:
          break out;
      }
    }

    return expression;
  }

  /**
   * Parses a multiplication / division expression.
   *
   * <p>These expressions have the form:
   *
   * <pre>
   * unaryMinus (('*' | '/') unaryMinus)*
   * </pre>
   *
   * An example of such an expression is
   *
   * <pre>
   * 5 * 9 / 2
   * </pre>
   *
   * @return Expression
   * @throws SyntaxError
   */
  private Expression parseMulDiv() throws SyntaxError {
    int line = currentToken.line;
    int column = currentToken.column;
    Expression expression = parseUnaryMinus();

    out:
    while (true) {
      switch (currentToken.type) {
        case MULT:
          acceptIt();
          expression = new Multiplication(line, column, expression, parseUnaryMinus());
          break;
        case DIV:
          acceptIt();
          expression = new Division(line, column, expression, parseUnaryMinus());
          break;
        default:
          break out;
      }
    }

    return expression;
  }

  private Expression parseUnaryMinus() throws SyntaxError {
    int line = currentToken.line;
    int column = currentToken.column;

    if (currentToken.type != SUB) {
      return parseExponentiation();
    }

    acceptIt();
    return new UnaryMinus(line, column, parseExponentiation());
  }

  /**
   * Parses an exponentiation expression.
   *
   * <p>Exponentiation expressions have the form
   *
   * <pre>
   * dim ('^' dim)*
   * </pre>
   *
   * An example of such an expression would be
   *
   * <pre>
   * 2^4.5^9
   * </pre>
   *
   * The important bit is that they are right-associative.
   *
   * @return Expression
   * @throws SyntaxError
   */
  private Expression parseExponentiation() throws SyntaxError {
    int line = currentToken.line;
    int column = currentToken.column;

    Expression dim = parseDim();

    if (currentToken.type != EXP) {
      return dim;
    }

    acceptIt();
    return new Exponentiation(line, column, dim, parseExponentiation());
  }

  private Expression parseDim() throws SyntaxError {
    int line = currentToken.line;
    int column = currentToken.column;

    Expression x = parseDotProd();
    switch (currentToken.type) {
      case XDIM:
        acceptIt();
        return new MatrixXDimension(line, column, x);
      case YDIM:
        acceptIt();
        return new MatrixYDimension(line, column, x);
      case DIM:
        acceptIt();
        return new VectorDimension(line, column, x);
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

  /**
   * Parses a sub vector / sub matrix expression.
   *
   * @return Expression
   * @throws SyntaxError
   */
  private Expression parseSubrange() throws SyntaxError {
    int line = currentToken.line;
    int column = currentToken.column;

    Expression expression = parseElementSelect();

    if (currentToken.type == LBRACE) {
      acceptIt();
      Expression start1 = parseExpr();
      accept(COLON);
      Expression base1 = parseExpr();
      accept(COLON);
      Expression end1 = parseExpr();
      accept(RBRACE);

      if (currentToken.type == LBRACE) {
        acceptIt();
        Expression start2 = parseExpr();
        accept(COLON);
        Expression base2 = parseExpr();
        accept(COLON);
        Expression end2 = parseExpr();
        accept(RBRACE);
        return new SubMatrix(line, column, expression, base1, start1, end1, base2, start2, end2);
      } else {
        return new SubVector(line, column, expression, base1, start1, end1);
      }
    }

    return expression;
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
      case INTLIT:
        return new IntValue(line, column, parseIntLit());
      case FLOATLIT:
        return new FloatValue(line, column, parseFloatLit());
      case BOOLLIT:
        return new BoolValue(line, column, parseBoolLit());
      case STRINGLIT:
        return new StringValue(line, column, accept(STRINGLIT));
      default: /* check other cases below */
    }

    if (currentToken.type == ID) {
      String name = accept(ID);
      if (currentToken.type != LPAREN) {
        return new IdentifierReference(line, column, name);

      } else {
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

    throw new SyntaxError(
        currentToken, INTLIT, FLOATLIT, BOOLLIT, STRINGLIT, ID, LPAREN, LBRACKET, AT);
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
