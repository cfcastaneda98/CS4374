/*
// $Id$
// Package org.eigenbase is a class library of data management components.
// Copyright (C) 2005 The Eigenbase Project
// Copyright (C) 2002 SQLstream, Inc.
// Copyright (C) 2005 Dynamo BI Corporation
// Portions Copyright (C) 2003 John V. Sichi
//
// This program is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License as published by the Free
// Software Foundation; either version 2 of the License, or (at your option)
// any later version approved by The Eigenbase Project.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.eigenbase.sql;

import java.util.*;

import org.eigenbase.reltype.*;
import org.eigenbase.resource.*;
import org.eigenbase.sql.parser.*;
import org.eigenbase.sql.type.*;
import org.eigenbase.sql.util.*;
import org.eigenbase.sql.validate.*;
import org.eigenbase.util.*;


/**
 * A <code>SqlOperator</code> is a type of node in a SQL parse tree (it is NOT a
 * node in a SQL parse tree). It includes functions, operators such as '=', and
 * syntactic constructs such as 'case' statements. Operators may represent
 * query-level expressions (e.g. {@link SqlSelectOperator} or row-level
 * expressions (e.g. {@link org.eigenbase.sql.fun.SqlBetweenOperator}.
 *
 * <p>Operators have <em>formal operands</em>, meaning ordered (and optionally
 * named) placeholders for the values they operate on. For example, the division
 * operator takes two operands; the first is the numerator and the second is the
 * denominator. In the context of subclass {@link SqlFunction}, formal operands
 * are referred to as <em>parameters</em>.
 *
 * <p>When an operator is instantiated via a {@link SqlCall}, it is supplied
 * with <em>actual operands</em>. For example, in the expression <code>3 /
 * 5</code>, the literal expression <code>3</code> is the actual operand
 * corresponding to the numerator, and <code>5</code> is the actual operand
 * corresponding to the denominator. In the context of SqlFunction, actual
 * operands are referred to as <em>arguments</em>
 *
 * <p>In many cases, the formal/actual distinction is clear from context, in
 * which case we drop these qualifiers.
 */
public abstract class SqlOperator
{
    //~ Static fields/initializers ---------------------------------------------

    public static final String NL = System.getProperty("line.separator");

    /**
     * Maximum precedence.
     */
    protected static final int MaxPrec = 200;

    //~ Instance fields --------------------------------------------------------

    /**
     * The name of the operator/function. Ex. "OVERLAY" or "TRIM"
     */
    private final String name;

    /**
     * See {@link SqlKind}. It's possible to have a name that doesn't match the
     * kind
     */
    private final SqlKind kind;

    /**
     * The precedence with which this operator binds to the expression to the
     * left. This is less than the right precedence if the operator is
     * left-associative.
     */
    private final int leftPrec;

    /**
     * The precedence with which this operator binds to the expression to the
     * right. This is more than the left precedence if the operator is
     * left-associative.
     */
    private final int rightPrec;

    /**
     * used to infer the return type of a call to this operator
     */
    private final SqlReturnTypeInference returnTypeInference;

    /**
     * used to infer types of unknown operands
     */
    private final SqlOperandTypeInference operandTypeInference;

    /**
     * used to validate operand types
     */
    private final SqlOperandTypeChecker operandTypeChecker;

    //~ Constructors -----------------------------------------------------------

    /**
     * Creates an operator.
     *
     * @pre kind != null
     */
    protected SqlOperator(
        String name,
        SqlKind kind,
        int leftPrecedence,
        int rightPrecedence,
        SqlReturnTypeInference returnTypeInference,
        SqlOperandTypeInference operandTypeInference,
        SqlOperandTypeChecker operandTypeChecker)
    {
        Util.pre(kind != null, "kind != null");
        this.name = name;
        this.kind = kind;
        this.leftPrec = leftPrecedence;
        this.rightPrec = rightPrecedence;
        this.returnTypeInference = returnTypeInference;
        this.operandTypeInference = operandTypeInference;
        this.operandTypeChecker = operandTypeChecker;
    }

    /**
     * Creates an operator specifying left/right associativity.
     */
    protected SqlOperator(
        String name,
        SqlKind kind,
        int prec,
        boolean leftAssoc,
        SqlReturnTypeInference returnTypeInference,
        SqlOperandTypeInference operandTypeInference,
        SqlOperandTypeChecker operandTypeChecker)
    {
        this(
            name,
            kind,
            leftPrec(prec, leftAssoc),
            rightPrec(prec, leftAssoc),
            returnTypeInference,
            operandTypeInference,
            operandTypeChecker);
    }

    //~ Methods ----------------------------------------------------------------

    protected static int leftPrec(int prec, boolean leftAssoc)
    {
        assert (prec % 2) == 0;
        if (!leftAssoc) {
            ++prec;
        }
        return prec;
    }

    protected static int rightPrec(int prec, boolean leftAssoc)
    {
        assert (prec % 2) == 0;
        if (leftAssoc) {
            ++prec;
        }
        return prec;
    }

    public SqlOperandTypeChecker getOperandTypeChecker()
    {
        return operandTypeChecker;
    }

    /**
     * Returns a constraint on the number of operands expected by this operator.
     * Subclasses may override this method; when they don't, the range is
     * derived from the {@link SqlOperandTypeChecker} associated with this
     * operator.
     *
     * @return acceptable range
     */
    public SqlOperandCountRange getOperandCountRange()
    {
        if (operandTypeChecker != null) {
            return operandTypeChecker.getOperandCountRange();
        }

        // If you see this error you need to overide this method
        // or give operandTypeChecker a value.
        throw Util.needToImplement(this);
    }

    public String getName()
    {
        return name;
    }

    public SqlKind getKind()
    {
        return kind;
    }

    public String toString()
    {
        return name;
    }

    public int getLeftPrec()
    {
        return leftPrec;
    }

    public int getRightPrec()
    {
        return rightPrec;
    }

    /**
     * Returns the syntactic type of this operator.
     *
     * @post return != null
     */
    public abstract SqlSyntax getSyntax();

    /**
     * Creates a call to this operand with an array of operands.
     *
     * <p>The position of the resulting call is the union of the <code>
     * pos</code> and the positions of all of the operands.
     *
     * @param functionQualifier function qualifier (e.g. "DISTINCT"), may be
     * @param pos parser position of the identifier of the call
     * @param operands array of operands
     */
    public SqlCall createCall(
        SqlLiteral functionQualifier,
        SqlParserPos pos,
        SqlNode ... operands)
    {
        pos = pos.plusAll(operands);
        return new SqlCall(this, operands, pos, false, functionQualifier);
    }

    /**
     * Creates a call to this operand with an array of operands.
     *
     * <p>The position of the resulting call is the union of the <code>
     * pos</code> and the positions of all of the operands.
     *
     * @param pos Parser position
     * @param operands List of arguments
     *
     * @return call to this operator
     */
    public final SqlCall createCall(
        SqlParserPos pos,
        SqlNode ... operands)
    {
        return createCall(null, pos, operands);
    }

    /**
     * Creates a call to this operand with a list of operands contained in a
     * {@link SqlNodeList}.
     *
     * <p>The position of the resulting call inferred from the SqlNodeList.
     *
     * @param nodeList List of arguments
     *
     * @return call to this operator
     */
    public final SqlCall createCall(
        SqlNodeList nodeList)
    {
        return createCall(
            null,
            nodeList.getParserPosition(),
            nodeList.toArray());
    }

    /**
     * Creates a call to this operand with a list of operands.
     *
     * <p>The position of the resulting call is the union of the <code>
     * pos</code> and the positions of all of the operands.
     */
    public final SqlCall createCall(
        SqlParserPos pos,
        List<? extends SqlNode> operandList)
    {
        return createCall(
            null,
            pos,
            operandList.toArray(new SqlNode[operandList.size()]));
    }

    /**
     * Rewrites a call to this operator. Some operators are implemented as
     * trivial rewrites (e.g. NULLIF becomes CASE). However, we don't do this at
     * createCall time because we want to preserve the original SQL syntax as
     * much as possible; instead, we do this before the call is validated (so
     * the trivial operator doesn't need its own implementation of type
     * derivation methods). The default implementation is to just return the
     * original call without any rewrite.
     *
     * @param validator
     * @param call to be rewritten
     *
     * @return rewritten call
     */
    public SqlNode rewriteCall(SqlValidator validator, SqlCall call)
    {
        return call;
    }

    /**
     * Writes a SQL representation of a call to this operator to a writer,
     * including parentheses if the operators on either side are of greater
     * precedence.
     *
     * <p>The default implementation of this method delegates to {@link
     * SqlSyntax#unparse}.
     */
    public void unparse(
        SqlWriter writer,
        SqlNode [] operands,
        int leftPrec,
        int rightPrec)
    {
        getSyntax().unparse(writer, this, operands, leftPrec, rightPrec);
    }

    // REVIEW jvs 9-June-2006: See http://issues.eigenbase.org/browse/FRG-149
    // for why this method exists.
    protected void unparseListClause(SqlWriter writer, SqlNode clause)
    {
        unparseListClause(writer, clause, null);
    }

    protected void unparseListClause(
        SqlWriter writer,
        SqlNode clause,
        SqlKind sepKind)
    {
        if (clause instanceof SqlNodeList) {
            if (sepKind != null) {
                ((SqlNodeList) clause).andOrList(writer, sepKind);
            } else {
                ((SqlNodeList) clause).commaList(writer);
            }
        } else {
            clause.unparse(writer, 0, 0);
        }
    }

    // override Object
    public boolean equals(Object obj)
    {
        if (!(obj instanceof SqlOperator)) {
            return false;
        }
        if (!obj.getClass().equals(this.getClass())) {
            return false;
        }
        SqlOperator other = (SqlOperator) obj;
        return name.equals(other.name) && kind == other.kind;
    }

    public boolean isName(String testName)
    {
        return name.equals(testName);
    }

    // override Object
    public int hashCode()
    {
        return kind.hashCode() + name.hashCode();
    }

    /**
     * Validates a call to this operator.
     *
     * <p>This method should not perform type-derivation or perform validation
     * related related to types. That is done later, by {@link
     * #deriveType(SqlValidator, SqlValidatorScope, SqlCall)}. This method
     * should focus on structural validation.
     *
     * <p>A typical implementation of this method first validates the operands,
     * then performs some operator-specific logic. The default implementation
     * just validates the operands.
     *
     * <p>This method is the default implementation of {@link SqlCall#validate};
     * but note that some sub-classes of {@link SqlCall} never call this method.
     *
     * @param call the call to this operator
     * @param validator the active validator
     * @param scope validator scope
     * @param operandScope validator scope in which to validate operands to this
     * call; usually equal to scope, but not always because some operators
     * introduce new scopes
     *
     * @see SqlNode#validateExpr(SqlValidator, SqlValidatorScope)
     * @see #deriveType(SqlValidator, SqlValidatorScope, SqlCall)
     */
    public void validateCall(
        SqlCall call,
        SqlValidator validator,
        SqlValidatorScope scope,
        SqlValidatorScope operandScope)
    {
        assert call.getOperator() == this;
        final SqlNode [] operands = call.getOperands();
        for (int i = 0; i < operands.length; i++) {
            operands[i].validateExpr(validator, operandScope);
        }
    }

    /**
     * Validates the operands of a call, inferring the return type in the
     * process.
     *
     * @param validator active validator
     * @param scope validation scope
     * @param call call to be validated
     *
     * @return inferred type
     */
    public final RelDataType validateOperands(
        SqlValidator validator,
        SqlValidatorScope scope,
        SqlCall call)
    {
        // Let subclasses know what's up.
        preValidateCall(validator, scope, call);

        // Check the number of operands
        checkOperandCount(validator, operandTypeChecker, call);

        SqlCallBinding opBinding = new SqlCallBinding(validator, scope, call);

        checkOperandTypes(
            opBinding,
            true);

        // Now infer the result type.
        RelDataType ret = inferReturnType(opBinding);
        validator.setValidatedNodeType(call, ret);
        return ret;
    }

    /**
     * Receives notification that validation of a call to this operator is
     * beginning. Subclasses can supply custom behavior; default implementation
     * does nothing.
     *
     * @param validator invoking validator
     * @param scope validation scope
     * @param call the call being validated
     */
    protected void preValidateCall(
        SqlValidator validator,
        SqlValidatorScope scope,
        SqlCall call)
    {
    }

    /**
     * Infers the return type of an invocation of this operator; only called
     * after the number and types of operands have already been validated.
     * Subclasses must either override this method or supply an instance of
     * {@link SqlReturnTypeInference} to the constructor.
     *
     * @param opBinding description of invocation (not necessarily a {@link
     * SqlCall})
     *
     * @return inferred return type
     */
    public RelDataType inferReturnType(
        SqlOperatorBinding opBinding)
    {
        if (returnTypeInference != null) {
            return returnTypeInference.inferReturnType(opBinding);
        }

        // Derived type should have overridden this method, since it didn't
        // supply a type inference rule.
        throw Util.needToImplement(this);
    }

    /**
     * Derives the type of a call to this operator.
     *
     * <p>This method is an intrinsic part of the validation process so, unlike
     * {@link #inferReturnType}, specific operators would not typically override
     * this method.
     *
     * @param validator Validator
     * @param scope Scope of validation
     * @param call Call to this operator
     *
     * @return Type of call
     */
    public RelDataType deriveType(
        SqlValidator validator,
        SqlValidatorScope scope,
        SqlCall call)
    {
        final SqlNode [] operands = call.operands;
        for (int i = 0; i < operands.length; ++i) {
            RelDataType nodeType = validator.deriveType(scope, operands[i]);
            assert nodeType != null;
        }

        RelDataType type = validateOperands(validator, scope, call);

        // Validate and determine coercibility and resulting collation
        // name of binary operator if needed.
        type = adjustType(validator, call, type);
        SqlValidatorUtil.checkCharsetAndCollateConsistentIfCharType(type);
        return type;
    }

    /**
     * Validates and determines coercibility and resulting collation name of
     * binary operator if needed.
     */
    protected RelDataType adjustType(
        SqlValidator validator,
        final SqlCall call,
        RelDataType type)
    {
        return type;
    }

    /**
     * Infers the type of a call to this operator with a given set of operand
     * types. Shorthand for {@link #inferReturnType(SqlOperatorBinding)}.
     */
    public final RelDataType inferReturnType(
        RelDataTypeFactory typeFactory,
        RelDataType [] operandTypes)
    {
        return inferReturnType(
            new ExplicitOperatorBinding(
                typeFactory,
                this,
                operandTypes));
    }

    /**
     * Checks that the operand values in a {@link SqlCall} to this operator are
     * valid. Subclasses must either override this method or supply an instance
     * of {@link SqlOperandTypeChecker} to the constructor.
     *
     * @param callBinding description of call
     * @param throwOnFailure whether to throw an exception if check fails
     * (otherwise returns false in that case)
     *
     * @return whether check succeeded
     */
    public boolean checkOperandTypes(
        SqlCallBinding callBinding,
        boolean throwOnFailure)
    {
        // Check that all of the operands are of the right type.
        if (null == operandTypeChecker) {
            // If you see this you must either give operandTypeChecker a value
            // or override this method.
            throw Util.needToImplement(this);
        }

        return operandTypeChecker.checkOperandTypes(
            callBinding,
            throwOnFailure);
    }

    protected void checkOperandCount(
        SqlValidator validator,
        SqlOperandTypeChecker argType,
        SqlCall call)
    {
        SqlOperandCountRange od = call.getOperator().getOperandCountRange();
        if (od.isVariadic()) {
            return;
        }
        if (!od.getAllowedList().contains(call.operands.length)) {
            if (od.getAllowedList().size() == 1) {
                throw validator.newValidationError(
                    call,
                    EigenbaseResource.instance().InvalidArgCount.ex(
                        call.getOperator().getName(),
                        od.getAllowedList().get(0)));
            } else {
                throw validator.newValidationError(
                    call,
                    EigenbaseResource.instance().WrongNumOfArguments.ex());
            }
        }
    }

    /**
     * Returns a template describing how the operator signature is to be built.
     * E.g for the binary + operator the template looks like "{1} {0} {2}" {0}
     * is the operator, subsequent numbers are operands.
     *
     * @param operandsCount is used with functions that can take a variable
     * number of operands
     *
     * @return signature template, or null to indicate that a default template
     * will suffice
     */
    public String getSignatureTemplate(final int operandsCount)
    {
        return null;
    }

    /**
     * Returns a string describing the expected operand types of a call, e.g.
     * "SUBSTR(VARCHAR, INTEGER, INTEGER)".
     */
    public String getAllowedSignatures()
    {
        return getAllowedSignatures(name);
    }

    /**
     * Returns a string describing the expected operand types of a call, e.g.
     * "SUBSTRING(VARCHAR, INTEGER, INTEGER)" where the name (SUBSTRING in this
     * example) can be replaced by a specifed name.
     */
    public String getAllowedSignatures(String opNameToUse)
    {
        assert operandTypeChecker != null
            : "If you see this, assign operandTypeChecker a value "
            + "or override this function";
        return operandTypeChecker.getAllowedSignatures(this, opNameToUse)
            .trim();
    }

    public SqlOperandTypeInference getOperandTypeInference()
    {
        return operandTypeInference;
    }

    /**
     * Returns whether this operator is an aggregate function. By default,
     * subclass type is used (an instance of SqlAggFunction is assumed to be an
     * aggregator; anything else is not).
     *
     * @return whether this operator is an aggregator
     */
    public boolean isAggregator()
    {
        return (this instanceof SqlAggFunction);
    }

    /**
     * Accepts a {@link SqlVisitor}, visiting each operand of a call. Returns
     * null.
     *
     * @param visitor Visitor
     * @param call Call to visit
     */
    public <R> R acceptCall(SqlVisitor<R> visitor, SqlCall call)
    {
        SqlNode [] operands = call.operands;
        for (int i = 0; i < operands.length; i++) {
            SqlNode operand = operands[i];
            if (operand == null) {
                continue;
            }
            operand.accept(visitor);
        }
        return null;
    }

    /**
     * Accepts a {@link SqlVisitor}, directing an {@link
     * org.eigenbase.sql.util.SqlBasicVisitor.ArgHandler} to visit operand of a
     * call. The argument handler allows fine control about how the operands are
     * visited, and how the results are combined.
     *
     * @param visitor Visitor
     * @param call Call to visit
     * @param onlyExpressions If true, ignores operands which are not
     * expressions. For example, in the call to the <code>AS</code> operator
     * @param argHandler Called for each operand
     */
    public <R> void acceptCall(
        SqlVisitor<R> visitor,
        SqlCall call,
        boolean onlyExpressions,
        SqlBasicVisitor.ArgHandler<R> argHandler)
    {
        SqlNode [] operands = call.operands;
        for (int i = 0; i < operands.length; i++) {
            argHandler.visitChild(visitor, call, i, operands[i]);
        }
    }

    /**
     * @return the return type inference strategy for this operator, or null if
     * return type inference is implemented by a subclass override
     */
    public SqlReturnTypeInference getReturnTypeInference()
    {
        return returnTypeInference;
    }

    /**
     * Returns whether this operator is monotonic.
     *
     * <p>Default implementation returns {@link SqlMonotonicity#NotMonotonic}.
     *
     * @param call Call to this operator
     * @param scope Scope in which the call occurs
     */
    public SqlMonotonicity getMonotonicity(
        SqlCall call,
        SqlValidatorScope scope)
    {
        return SqlMonotonicity.NotMonotonic;
    }

    /**
     * @return true iff a call to this operator is guaranteed to always return
     * the same result given the same operands; true is assumed by default
     */
    public boolean isDeterministic()
    {
        return true;
    }

    /**
     * @return true iff it is unsafe to cache query plans referencing this
     * operator; false is assumed by default
     */
    public boolean isDynamicFunction()
    {
        return false;
    }

    /**
     * Method to check if call requires expansion when it has decimal operands.
     * The default implementation is to return true.
     */
    public boolean requiresDecimalExpansion()
    {
        return true;
    }

    /**
     * Returns whether the <code>ordinal</code>th argument to this operator must
     * be scalar (as opposed to a query).
     *
     * <p>If true (the default), the validator will attempt to convert the
     * argument into a scalar subquery, which must have one column and return at
     * most one row.
     *
     * <p>Operators such as <code>SELECT</code> and <code>EXISTS</code> override
     * this method.
     */
    public boolean argumentMustBeScalar(int ordinal)
    {
        return true;
    }
}

// End SqlOperator.java
