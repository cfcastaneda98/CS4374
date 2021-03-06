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
package org.eigenbase.rex;

import java.util.*;

import org.eigenbase.rel.*;
import org.eigenbase.relopt.*;
import org.eigenbase.reltype.*;
import org.eigenbase.sql.*;
import org.eigenbase.sql.fun.SqlStdOperatorTable;
import org.eigenbase.sql.type.*;
import org.eigenbase.util.*;
import org.eigenbase.util.mapping.*;


/**
 * A collection of expressions which read inputs, compute output expressions,
 * and optionally use a condition to filter rows.
 *
 * <p>Programs are immutable. It may help to use a {@link RexProgramBuilder},
 * which has the same relationship to {@link RexProgram} as {@link StringBuffer}
 * does has to {@link String}.
 *
 * <p>A program can contain aggregate functions. If it does, the arguments to
 * each aggregate function must be an {@link RexInputRef}.
 *
 * @author jhyde
 * @version $Id$
 * @see RexProgramBuilder
 * @since Aug 18, 2005
 */
public class RexProgram
{
    //~ Instance fields --------------------------------------------------------

    /**
     * First stage of expression evaluation. The expressions in this array can
     * refer to inputs (using input ordinal #0) or previous expressions in the
     * array (using input ordinal #1).
     */
    private final RexNode [] exprs;

    /**
     * With {@link #condition}, the second stage of expression evaluation.
     */
    private final RexLocalRef [] projects;

    /**
     * The optional condition. If null, the calculator does not filter rows.
     */
    private final RexLocalRef condition;

    private final RelDataType inputRowType;

    /**
     * Whether this program contains aggregates. TODO: obsolete this
     */
    private boolean aggs;
    private final RelDataType outputRowType;
    private final List<RexLocalRef> projectReadOnlyList;
    private final List<RexNode> exprReadOnlyList;

    /**
     * Reference counts for each expression, computed on demand.
     */
    private int [] refCounts;

    //~ Constructors -----------------------------------------------------------

    /**
     * Creates a program.
     *
     * @param inputRowType Input row type
     * @param exprs Common expressions
     * @param projects Projection expressions
     * @param condition Condition expression. If null, calculator does not
     * filter rows
     * @param outputRowType Description of the row produced by the program
     *
     * @pre !containCommonExprs(exprs)
     * @pre !containForwardRefs(exprs)
     * @pre !containNonTrivialAggs(exprs)
     */
    public RexProgram(
        RelDataType inputRowType,
        RexNode [] exprs,
        RexLocalRef [] projects,
        RexLocalRef condition,
        RelDataType outputRowType)
    {
        this.inputRowType = inputRowType;
        this.exprs = exprs.clone();
        this.exprReadOnlyList =
            Collections.unmodifiableList(Arrays.asList(exprs));
        this.projects = projects.clone();
        this.projectReadOnlyList =
            Collections.unmodifiableList(Arrays.asList(projects));
        this.condition = condition;
        this.outputRowType = outputRowType;
        assert isValid(true);
    }

    /**
     * Creates a program from lists of expressions.
     *
     * @param inputRowType Input row type
     * @param exprList List of common expressions
     * @param projectRefList List of projection expressions
     * @param condition Condition expression. If null, calculator does not
     * filter rows
     * @param outputRowType Description of the row produced by the program
     *
     * @pre !containCommonExprs(exprList)
     * @pre !containForwardRefs(exprList)
     * @pre !containNonTrivialAggs(exprList)
     */
    public RexProgram(
        RelDataType inputRowType,
        List<RexNode> exprList,
        List<RexLocalRef> projectRefList,
        RexLocalRef condition,
        RelDataType outputRowType)
    {
        this(
            inputRowType,
            (RexNode []) exprList.toArray(new RexNode[exprList.size()]),
            (RexLocalRef []) projectRefList.toArray(
                new RexLocalRef[projectRefList.size()]),
            condition,
            outputRowType);
    }

    //~ Methods ----------------------------------------------------------------

    /**
     * Returns the common sub-expressions of this program.
     *
     * <p>The list is never null but may be empty; each the expression in the
     * list is not null; and no further reduction into smaller common
     * subexpressions is possible.
     *
     * @post return != null
     * @post !containCommonExprs(exprs)
     */
    public List<RexNode> getExprList()
    {
        return exprReadOnlyList;
    }

    /**
     * Returns an array of references to the expressions which this program is
     * to project. Never null, may be empty.
     */
    public List<RexLocalRef> getProjectList()
    {
        return projectReadOnlyList;
    }

    /**
     * Returns the field reference of this program's filter condition, or null
     * if there is no condition.
     */
    public RexLocalRef getCondition()
    {
        return condition;
    }

    /**
     * Creates a program which calculates projections and filters rows based
     * upon a condition. Does not attempt to eliminate common sub-expressions.
     *
     * @param projectExprs Project expressions
     * @param conditionExpr Condition on which to filter rows, or null if rows
     * are not to be filtered
     * @param outputRowType Output row type
     * @param rexBuilder Builder of rex expressions
     *
     * @return A program
     */
    public static RexProgram create(
        RelDataType inputRowType,
        RexNode [] projectExprs,
        RexNode conditionExpr,
        RelDataType outputRowType,
        RexBuilder rexBuilder)
    {
        final RexProgramBuilder programBuilder =
            new RexProgramBuilder(inputRowType, rexBuilder);
        final RelDataTypeField [] fields = outputRowType.getFields();
        for (int i = 0; i < projectExprs.length; i++) {
            programBuilder.addProject(
                projectExprs[i],
                fields[i].getName());
        }
        if (conditionExpr != null) {
            programBuilder.addCondition(conditionExpr);
        }
        return programBuilder.getProgram();
    }

    /**
     * Helper method for 'explain' functionality. Creates a list of all
     * expressions (common, project, and condition)
     *
     * @deprecated Not used
     */
    public RexNode [] flatten()
    {
        final List<RexNode> list = new ArrayList<RexNode>();
        list.addAll(Arrays.asList(exprs));
        list.addAll(Arrays.asList(projects));
        if (condition != null) {
            list.add(condition);
        }
        return (RexNode []) list.toArray(new RexNode[list.size()]);
    }

    // description of this calc, chiefly intended for debugging
    public String toString()
    {
        // Intended to produce similar output to explainCalc,
        // but without requiring a RelNode or RelOptPlanWriter.
        List<String> termList = new ArrayList<String>();
        List<Object> valueList = new ArrayList<Object>();
        collectExplainTerms("", termList, valueList);
        final StringBuilder buf = new StringBuilder("(");
        for (int i = 0; i < valueList.size(); i++) {
            if (i > 0) {
                buf.append(", ");
            }
            buf.append(termList.get(i)).append("=[").append(valueList.get(i))
                .append("]");
        }
        buf.append(")");
        return buf.toString();
    }

    /**
     * Writes an explanation of the expressions in this program to a plan
     * writer.
     *
     * @param rel Relational expression which owns this program
     * @param pw Plan writer
     */
    public void explainCalc(
        RelNode rel,
        RelOptPlanWriter pw)
    {
        List<String> termList = new ArrayList<String>();
        List<Object> valueList = new ArrayList<Object>();
        termList.add("child");
        collectExplainTerms("", termList, valueList, pw.getDetailLevel());

        if ((pw.getDetailLevel() == SqlExplainLevel.DIGEST_ATTRIBUTES)
            && false)
        {
            termList.add("type");
            valueList.add(rel.getRowType());
        }

        // Relational expressions which contain a program should report their
        // children in a different way than getChildExps().
        assert rel.getChildExps().length == 0;
        pw.explain(rel, termList, valueList);
    }

    public void collectExplainTerms(
        String prefix,
        List<String> termList,
        List<Object> valueList)
    {
        collectExplainTerms(
            prefix,
            termList,
            valueList,
            SqlExplainLevel.EXPPLAN_ATTRIBUTES);
    }

    /**
     * Collects the expressions in this program into a list of terms and values.
     *
     * @param prefix Prefix for term names, usually the empty string, but useful
     * if a relational expression contains more than one program
     * @param termList Output list of terms
     * @param valueList Output list of expressions
     */
    public void collectExplainTerms(
        String prefix,
        List<String> termList,
        List<Object> valueList,
        SqlExplainLevel level)
    {
        final RelDataTypeField [] inFields = inputRowType.getFields();
        final RelDataTypeField [] outFields = outputRowType.getFields();
        assert outFields.length == projects.length : "outFields.length="
            + outFields.length
            + ", projects.length=" + projects.length;
        termList.add(
            prefix + "expr#0"
            + ((inFields.length > 1) ? (".." + (inFields.length - 1)) : ""));
        valueList.add("{inputs}");
        for (int i = inFields.length; i < exprs.length; i++) {
            termList.add(prefix + "expr#" + i);
            valueList.add(exprs[i]);
        }

        // If a lot of the fields are simply projections of the underlying
        // expression, try to be a bit less verbose.
        int trivialCount = 0;

        // Do not use the trivialCount optimization if computing digest for the
        // optimizer (as opposed to doing an explain plan).
        if (level != SqlExplainLevel.DIGEST_ATTRIBUTES) {
            trivialCount = countTrivial(projects);
        }

        switch (trivialCount) {
        case 0:
            break;
        case 1:
            trivialCount = 0;
            break;
        default:
            termList.add(prefix + "proj#0.." + (trivialCount - 1));
            valueList.add("{exprs}");
            break;
        }

        // Print the non-trivial fields with their names as they appear in the
        // output row type.
        for (int i = trivialCount; i < projects.length; i++) {
            termList.add(prefix + outFields[i].getName());
            valueList.add(projects[i]);
        }
        if (condition != null) {
            termList.add(prefix + "$condition");
            valueList.add(condition);
        }
    }

    /**
     * Returns the number of expressions at the front of an array which are
     * simply projections of the same field.
     */
    private static int countTrivial(RexLocalRef [] refs)
    {
        for (int i = 0; i < refs.length; i++) {
            RexLocalRef ref = refs[i];
            if (ref.getIndex() != i) {
                return i;
            }
        }
        return refs.length;
    }

    /**
     * Returns the number of expressions in this program.
     */
    public int getExprCount()
    {
        return exprs.length
            + projects.length
            + ((condition == null) ? 0 : 1);
    }

    /**
     * Creates a copy of this program.
     */
    public RexProgram copy()
    {
        return new RexProgram(
            inputRowType,
            exprs,
            projects,
            (condition == null) ? null : condition.clone(),
            outputRowType);
    }

    /**
     * Creates the identity program.
     */
    public static RexProgram createIdentity(RelDataType rowType)
    {
        final RelDataTypeField [] fields = rowType.getFields();
        final RexLocalRef [] projectRefs = new RexLocalRef[fields.length];
        final RexInputRef [] refs = new RexInputRef[fields.length];
        for (int i = 0; i < refs.length; i++) {
            final RelDataType type = fields[i].getType();
            refs[i] = new RexInputRef(i, type);
            projectRefs[i] = new RexLocalRef(i, type);
        }
        return new RexProgram(rowType, refs, projectRefs, null, rowType);
    }

    /**
     * Returns the type of the input row to the program.
     *
     * @return input row type
     */
    public RelDataType getInputRowType()
    {
        return inputRowType;
    }

    /**
     * Returns whether this program contains windowed aggregate functions
     *
     * @return whether this program contains windowed aggregate functions
     */
    public boolean containsAggs()
    {
        return aggs || RexOver.containsOver(this);
    }

    public void setAggs(boolean aggs)
    {
        this.aggs = aggs;
    }

    /**
     * Returns the type of the output row from this program.
     *
     * @return output row type
     */
    public RelDataType getOutputRowType()
    {
        return outputRowType;
    }

    /**
     * Checks that this program is valid.
     *
     * <p>If <code>fail</code> is true, executes <code>assert false</code>, so
     * will throw an {@link AssertionError} if assertions are enabled. If <code>
     * fail</code> is false, merely returns whether the program is valid.
     *
     * @param fail Whether to fail
     *
     * @return Whether the program is valid
     *
     * @throws AssertionError if program is invalid and <code>fail</code> is
     * true and assertions are enabled
     */
    public boolean isValid(boolean fail)
    {
        if (inputRowType == null) {
            assert !fail;
            return false;
        }
        if (exprs == null) {
            assert !fail;
            return false;
        }
        if (projects == null) {
            assert !fail;
            return false;
        }
        if (outputRowType == null) {
            assert !fail;
            return false;
        }

        // If the input row type is a struct (contains fields) then the leading
        // expressions must be references to those fields. But we don't require
        // this if the input row type is, say, a java class.
        if (inputRowType.isStruct()) {
            if (!RexUtil.containIdentity(exprs, inputRowType, fail)) {
                assert !fail;
                return false;
            }

            // None of the other fields should be inputRefs.
            for (int i = inputRowType.getFieldCount(); i < exprs.length; i++) {
                RexNode expr = exprs[i];
                if (expr instanceof RexInputRef) {
                    assert !fail;
                    return false;
                }
            }
        }
        if (false && RexUtil.containCommonExprs(exprs, fail)) { // todo: enable
            assert !fail;
            return false;
        }
        if (RexUtil.containForwardRefs(exprs, inputRowType, fail)) {
            assert !fail;
            return false;
        }
        if (RexUtil.containNonTrivialAggs(exprs, fail)) {
            assert !fail;
            return false;
        }
        final Checker checker =
            new Checker(
                fail,
                inputRowType,
                new AbstractList<RelDataType>()
                {
                    public RelDataType get(int index)
                    {
                        return exprs[index].getType();
                    }

                    @Override
                    public int size()
                    {
                        return exprs.length;
                    }
                });
        if (condition != null) {
            if (!SqlTypeUtil.inBooleanFamily(condition.getType())) {
                assert !fail : "condition must be boolean";
                return false;
            }
            condition.accept(checker);
            if (checker.failCount > 0) {
                assert !fail;
                return false;
            }
        }
        for (int i = 0; i < projects.length; i++) {
            projects[i].accept(checker);
            if (checker.failCount > 0) {
                assert !fail;
                return false;
            }
        }
        for (int i = 0; i < exprs.length; i++) {
            exprs[i].accept(checker);
            if (checker.failCount > 0) {
                assert !fail;
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether an expression always evaluates to null.
     *
     * <p/>Like {@link RexUtil#isNull(RexNode)}, null literals are null, and
     * casts of null literals are null. But this method also regards references
     * to null expressions as null.
     *
     * @param expr Expression
     *
     * @return Whether expression always evaluates to null
     */
    public boolean isNull(RexNode expr)
    {
        if (RexLiteral.isNullLiteral(expr)) {
            return true;
        }
        if (expr instanceof RexLocalRef) {
            RexLocalRef inputRef = (RexLocalRef) expr;
            return isNull(exprs[inputRef.index]);
        }
        if (expr.getKind() == RexKind.Cast) {
            return isNull(((RexCall) expr).operands[0]);
        }
        return false;
    }

    /**
     * Fully expands a RexLocalRef back into a pure RexNode tree containing no
     * RexLocalRefs (reversing the effect of common subexpression elimination).
     * For example, <code>program.expandLocalRef(program.getCondition())</code>
     * will return the expansion of a program's condition.
     *
     * @param ref a RexLocalRef from this program
     *
     * @return expanded form
     */
    public RexNode expandLocalRef(RexLocalRef ref)
    {
        // TODO jvs 19-Apr-2006:  assert that ref is part of
        // this program
        ExpansionShuttle shuttle = new ExpansionShuttle();
        return ref.accept(shuttle);
    }

    /**
     * Given a list of collations which hold for the input to this program,
     * returns a list of collations which hold for its output. The result is
     * mutable.
     */
    public List<RelCollation> getCollations(List<RelCollation> inputCollations)
    {
        List<RelCollation> outputCollations = new ArrayList<RelCollation>(1);
        deduceCollations(
            outputCollations,
            inputRowType.getFieldCount(),
            projectReadOnlyList,
            inputCollations);
        return outputCollations;
    }

    /**
     * Given a list of expressions and a description of which are ordered,
     * computes a list of collations. The result is mutable.
     */
    public static void deduceCollations(
        List<RelCollation> outputCollations,
        final int sourceCount,
        List<RexLocalRef> refs,
        List<RelCollation> inputCollations)
    {
        int [] targets = new int[sourceCount];
        Arrays.fill(targets, -1);
        for (int i = 0; i < refs.size(); i++) {
            final RexLocalRef ref = refs.get(i);
            final int source = ref.getIndex();
            if ((source < sourceCount) && (targets[source] == -1)) {
                targets[source] = i;
            }
        }
loop:
        for (RelCollation collation : inputCollations) {
            final ArrayList<RelFieldCollation> fieldCollations =
                new ArrayList<RelFieldCollation>(0);
            for (
                RelFieldCollation fieldCollation
                : collation.getFieldCollations())
            {
                final int source = fieldCollation.getFieldIndex();
                final int target = targets[source];
                if (target < 0) {
                    continue loop;
                }
                fieldCollations.add(
                    new RelFieldCollation(
                        target,
                        fieldCollation.getDirection()));
            }

            // Success -- all of the source fields of this key are mapped
            // to the output.
            outputCollations.add(new RelCollationImpl(fieldCollations));
        }
    }

    /**
     * Returns whether the fields on the leading edge of the project list are
     * the input fields.
     *
     * @param fail Whether to throw an assert failure if does not project
     * identity
     */
    public boolean projectsIdentity(final boolean fail)
    {
        final int fieldCount = inputRowType.getFieldCount();
        if (projects.length < fieldCount) {
            assert !fail : "program '" + toString()
                + "' does not project identity for input row type '"
                + inputRowType + "'";
            return false;
        }
        for (int i = 0; i < fieldCount; i++) {
            RexLocalRef project = projects[i];
            if (project.index != i) {
                assert !fail : "program " + toString()
                    + "' does not project identity for input row type '"
                    + inputRowType + "', field #" + i;
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether this program returns its input exactly.
     *
     * <p>This is a stronger condition than {@link #projectsIdentity(boolean)}.
     */
    public boolean isTrivial()
    {
        if (getCondition() != null) {
            return false;
        }
        if (projects.length != inputRowType.getFieldCount()) {
            return false;
        }
        for (int i = 0; i < projects.length; i++) {
            RexLocalRef project = projects[i];
            if (project.index != i) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets reference counts for each expression in the program, where the
     * references are detected from later expressions in the same program, as
     * well as the project list and condition. Expressions with references
     * counts greater than 1 are true common subexpressions.
     *
     * @return array of reference counts; the ith element in the returned array
     * is the number of references to getExprList()[i]
     */
    public int [] getReferenceCounts()
    {
        if (refCounts != null) {
            return refCounts;
        }
        refCounts = new int[exprs.length];
        ReferenceCounter refCounter = new ReferenceCounter();
        RexUtil.apply(refCounter, exprs, null);
        if (condition != null) {
            refCounter.visitLocalRef(condition);
        }
        for (int i = 0; i < projects.length; ++i) {
            refCounter.visitLocalRef(projects[i]);
        }
        return refCounts;
    }

    /**
     * @deprecated See {@link RexUtil#apply}; please remove next release
     */
    public static void apply(
        RexVisitor<Void> visitor,
        RexNode [] exprs,
        RexNode expr)
    {
        RexUtil.apply(visitor, exprs, expr);
    }

    /**
     * Returns whether an expression is constant.
     */
    public boolean isConstant(RexNode ref)
    {
        return ref.accept(new ConstantFinder());
    }

    public RexNode gatherExpr(RexNode expr)
    {
        return expr.accept(new Marshaller());
    }

    /**
     * Returns the input field that an output field is populated from, or -1 if
     * it is populated from an expression.
     *
     * @param outputOrdinal Ordinal of output field
     * @return Input field that output fields is populated from; or -1
     */
    public int getSourceField(int outputOrdinal)
    {
        assert (outputOrdinal >= 0) && (outputOrdinal < this.projects.length);
        RexLocalRef project = projects[outputOrdinal];
        int index = project.index;
        while (true) {
            RexNode expr = exprs[index];
            if (expr instanceof RexCall
                && ((RexCall) expr).getOperator()
                == SqlStdOperatorTable.inFennelFunc)
            {
                // drill through identity function
                expr = ((RexCall) expr).getOperands()[0];
            }
            if (expr instanceof RexLocalRef) {
                index = ((RexLocalRef) expr).index;
            } else if (expr instanceof RexInputRef) {
                return ((RexInputRef) expr).index;
            } else {
                return -1;
            }
        }
    }

    /**
     * Returns the expression from which a given output field is calculated.
     * Intermediate expressions are expanded; the result is in terms of
     * {@link RexLiteral} and {@link RexInputRef} leaves, combined using
     * {@link RexCall} nodes.
     *
     * @param outputOrdinal Ordinal of projected expression
     * @return Source expressed in terms of literals and input expressions
     */
    public RexNode getSourceExpression(int outputOrdinal)
    {
        assert (outputOrdinal >= 0) && (outputOrdinal < this.projects.length);
        return sourceOf(projects[outputOrdinal]);
    }

    /**
     * Helper for {@link #getSourceExpression(int)}.
     *
     * @param expr Expression
     * @return Source expressed in terms of literals and input expressions
     */
    private RexNode sourceOf(RexNode expr)
    {
        if (RexUtil.isCallTo(expr, SqlStdOperatorTable.inFennelFunc)) {
            // drill through identity function
            return sourceOf(((RexCall) expr).getOperands()[0]);
        }
        if (expr instanceof RexLocalRef) {
            return sourceOf(exprs[((RexLocalRef) expr).index]);
        }
        if (expr instanceof RexInputRef) {
            return expr;
        }
        if (expr instanceof RexLiteral) {
            return expr;
        }
        if (expr instanceof RexCall) {
            RexCall call = (RexCall) expr;
            final List<RexNode> newOperands = new ArrayList<RexNode>();
            for (RexNode operand : call.getOperands()) {
                newOperands.add(sourceOf(operand));
            }
            return call.clone(
                call.getType(),
                newOperands.toArray(new RexNode[newOperands.size()]));
        }
        if (expr instanceof RexFieldAccess) {
            RexFieldAccess fieldAccess = (RexFieldAccess) expr;
            return new RexFieldAccess(
                sourceOf(fieldAccess.getReferenceExpr()),
                fieldAccess.getField());
        }
        throw Util.newInternal("unknown expression type: " + expr);
    }

    /**
     * Returns whether this program is a permutation of its inputs.
     */
    public boolean isPermutation()
    {
        if (projects.length != inputRowType.getFields().length) {
            return false;
        }
        for (int i = 0; i < projects.length; ++i) {
            if (getSourceField(i) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a permutation, if this program is a permutation, otherwise null.
     */
    public Permutation getPermutation()
    {
        Permutation permutation = new Permutation(projects.length);
        if (projects.length != inputRowType.getFields().length) {
            return null;
        }
        for (int i = 0; i < projects.length; ++i) {
            int sourceField = getSourceField(i);
            if (sourceField < 0) {
                return null;
            }
            permutation.set(i, sourceField);
        }
        return permutation;
    }

    /**
     * Returns the set of correlation variables used (read) by this program.
     * The set is sorted, for determinism.
     *
     * @return set of correlation variable names
     */
    public SortedSet<String> getCorrelVariableNames()
    {
        final SortedSet<String> paramIdSet = new TreeSet<String>();
        RexUtil.apply(
            new RexVisitorImpl<Void>(true) {
                public Void visitCorrelVariable(
                    RexCorrelVariable correlVariable)
                {
                    paramIdSet.add(correlVariable.getName());
                    return null;
                }
            },
            exprs,
            null);
        return paramIdSet;
    }

    /**
     * Returns whether this program is in canonical form.
     *
     * @param fail Whether to throw an assertion error if not in canonical form
     * @param rexBuilder Rex builder
     * @return whether in canonical form
     */
    public boolean isNormalized(boolean fail, RexBuilder rexBuilder)
    {
        final RexProgram normalizedProgram =
            RexProgramBuilder.normalize(rexBuilder, this);
        String normalized = normalizedProgram.toString();
        String string = toString();
        if (!normalized.equals(string)) {
            assert !fail
                : "Program is not normalized:\n"
                + "program:    " + string + "\n"
                + "normalized: " + normalized + "\n";
            return false;
        }
        return true;
    }

    /**
     * Creates a program, with identical behavior to this, over a permuted
     * subset of the input fields.
     *
     * <p>The {@code mapping} parameter describes the permutation of source
     * fields. Suppose that field #0 has been removed (because it not used)
     * and field #1 is now field #0. Then mapping.getTarget(0) will throw, and
     * mapping.getTarget(1) will return 0.
     *
     * @param typeFactory Type factory
     * @param mapping Input field mapping
     */
    public RexProgram permuteInputs(
        RelDataTypeFactory typeFactory,
        final Mappings.SourceMapping mapping)
    {
        final int sourceCount = mapping.getSourceCount();
        assert sourceCount == inputRowType.getFieldCount();
        final int targetCount = mapping.getTargetCount();
        RelDataType newInputRowType =
            typeFactory.createStructType(
                new AbstractList<RelDataTypeField>()
                {
                    public RelDataTypeField get(int index)
                    {
                        return inputRowType.getFieldList().get(
                            mapping.getSource(index));
                    }

                    public int size()
                    {
                        return mapping.getTargetCount();
                    }
                }
            );

        List<RexNode> newExprList = new ArrayList<RexNode>();
        for (RelDataTypeField field : newInputRowType.getFieldList()) {
            newExprList.add(
                new RexInputRef(newExprList.size(), field.getType()));
        }
        final int lostFieldCount = sourceCount - targetCount;
        Mappings.TargetMapping extendedMapping =
            Mappings.create(
                MappingType.Surjection,
                exprs.length,
                exprs.length - lostFieldCount);
        for (IntPair o : mapping) {
            extendedMapping.set(o.source, o.target);
        }
        final RexPermutationShuttle shuttle =
            new RexPermutationShuttle(extendedMapping);
        for (int i = sourceCount; i < exprs.length; ++i) {
            extendedMapping.set(i, i - lostFieldCount);
            final RexNode expr = exprs[i];
            final RexNode newExpr = expr.accept(shuttle);
            newExprList.add(newExpr);
        }
        final RexVisitor<RexLocalRef> localShuttle =
            (RexVisitor<RexLocalRef>) (RexVisitor) shuttle;
        RexLocalRef[] newProjects = RexUtil.apply(localShuttle, projects);
        RexLocalRef newCondition;
        if (condition == null) {
            newCondition = null;
        } else {
            newCondition = condition.accept(localShuttle);
        }
        return new RexProgram(
            newInputRowType,
            newExprList.toArray(new RexNode[newExprList.size()]),
            newProjects,
            newCondition,
            outputRowType);
    }

    //~ Inner Classes ----------------------------------------------------------

    /**
     * Visitor which walks over a program and checks validity.
     */
    static class Checker extends RexChecker
    {
        private final List<RelDataType> internalExprTypeList;

        /**
         * Creates a Checker.
         *
         * @param fail Whether to fail
         * @param inputRowType Types of the input fields
         * @param internalExprTypeList Types of the internal expressions
         */
        public Checker(
            boolean fail,
            RelDataType inputRowType,
            List<RelDataType> internalExprTypeList)
        {
            super(inputRowType, fail);
            this.internalExprTypeList = internalExprTypeList;
        }

        // override RexChecker; RexLocalRef is illegal in most rex expressions,
        // but legal in a program
        public Boolean visitLocalRef(RexLocalRef localRef)
        {
            final int index = localRef.getIndex();
            if ((index < 0) || (index >= internalExprTypeList.size())) {
                assert !fail;
                ++failCount;
                return false;
            }
            if (!RelOptUtil.eq(
                    "type1",
                    localRef.getType(),
                    "type2",
                    internalExprTypeList.get(index),
                    fail))
            {
                assert !fail;
                ++failCount;
                return false;
            }
            return true;
        }
    }

    /**
     * A RexShuttle used in the implementation of {@link
     * RexProgram#expandLocalRef}.
     */
    private class ExpansionShuttle
        extends RexShuttle
    {
        public RexNode visitLocalRef(RexLocalRef localRef)
        {
            RexNode tree = getExprList().get(localRef.getIndex());
            return tree.accept(this);
        }
    }

    /**
     * Walks over an expression and determines whether it is constant.
     */
    private class ConstantFinder
        implements RexVisitor<Boolean>
    {
        private ConstantFinder()
        {
        }

        public Boolean visitLiteral(RexLiteral literal)
        {
            return true;
        }

        public Boolean visitInputRef(RexInputRef inputRef)
        {
            return false;
        }

        public Boolean visitLocalRef(RexLocalRef localRef)
        {
            final RexNode expr = exprs[localRef.index];
            return expr.accept(this);
        }

        public Boolean visitOver(RexOver over)
        {
            return false;
        }

        public Boolean visitCorrelVariable(RexCorrelVariable correlVariable)
        {
            // Correlating variables are constant WITHIN A RESTART, so that's
            // good enough.
            return true;
        }

        public Boolean visitDynamicParam(RexDynamicParam dynamicParam)
        {
            // Dynamic parameters are constant WITHIN A RESTART, so that's
            // good enough.
            return true;
        }

        public Boolean visitCall(RexCall call)
        {
            // Constant if operator is deterministic and all operands are
            // constant.
            return call.getOperator().isDeterministic()
                && RexVisitorImpl.visitArrayAnd(
                    this,
                    call.getOperands());
        }

        public Boolean visitRangeRef(RexRangeRef rangeRef)
        {
            return false;
        }

        public Boolean visitFieldAccess(RexFieldAccess fieldAccess)
        {
            // "<expr>.FIELD" is constant iff "<expr>" is constant.
            return fieldAccess.getReferenceExpr().accept(this);
        }
    }

    /**
     * Given an expression in a program, creates a clone of the expression with
     * sub-expressions (represented by {@link RexLocalRef}s) fully expanded.
     */
    private class Marshaller
        extends RexVisitorImpl<RexNode>
    {
        Marshaller()
        {
            super(false);
        }

        public RexNode visitInputRef(RexInputRef inputRef)
        {
            return inputRef;
        }

        public RexNode visitLocalRef(RexLocalRef localRef)
        {
            final RexNode expr = exprs[localRef.index];
            return expr.accept(this);
        }

        public RexNode visitLiteral(RexLiteral literal)
        {
            return literal;
        }

        public RexNode visitCall(RexCall call)
        {
            final RexNode [] operands = call.getOperands();
            final RexNode [] newOperands = new RexNode[operands.length];
            for (int i = 0; i < operands.length; i++) {
                newOperands[i] = operands[i].accept(this);
            }
            return call.clone(
                call.getType(),
                newOperands);
        }

        public RexNode visitOver(RexOver over)
        {
            return visitCall(over);
        }

        public RexNode visitCorrelVariable(RexCorrelVariable correlVariable)
        {
            return correlVariable;
        }

        public RexNode visitDynamicParam(RexDynamicParam dynamicParam)
        {
            return dynamicParam;
        }

        public RexNode visitRangeRef(RexRangeRef rangeRef)
        {
            return rangeRef;
        }

        public RexNode visitFieldAccess(RexFieldAccess fieldAccess)
        {
            final RexNode referenceExpr =
                fieldAccess.getReferenceExpr().accept(this);
            return new RexFieldAccess(
                referenceExpr,
                fieldAccess.getField());
        }
    }

    /**
     * Visitor which marks which expressions are used.
     */
    private class ReferenceCounter
        extends RexVisitorImpl<Void>
    {
        ReferenceCounter()
        {
            super(true);
        }

        public Void visitLocalRef(RexLocalRef localRef)
        {
            final int index = localRef.getIndex();
            refCounts[index]++;
            return null;
        }
    }
}

// End RexProgram.java
