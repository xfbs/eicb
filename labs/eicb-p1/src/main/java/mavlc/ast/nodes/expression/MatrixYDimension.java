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
package mavlc.ast.nodes.expression;

import mavlc.ast.visitor.ASTNodeVisitor;

/**
 * AST-node representation of the primitive ".yDimension"-expression for matrices.
 * Yields the number of columns in the underlying matrix.
 */
public class MatrixYDimension extends UnaryExpression {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5969255947754287469L;

	/**
	 * Constructor.
	 * @param sourceLine The source line in which the node was specified.
	 * @param sourceColumn The source column in which the node was specified.
	 * @param operand The underlying matrix.
	 */
	public MatrixYDimension(int sourceLine, int sourceColumn, Expression operand){
		super(sourceLine, sourceColumn, operand);
	}

	@Override
	public String dump() {
		return op.dump()+".yDimension";
	}

	@Override
	public <RetTy, ArgTy> RetTy accept(ASTNodeVisitor<? extends RetTy, ArgTy> visitor, ArgTy obj){
		return visitor.visitMatrixYDimension(this, obj);
	}

}
