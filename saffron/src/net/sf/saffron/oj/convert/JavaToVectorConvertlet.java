/*
// Saffron preprocessor and data engine.
// Copyright (C) 2002-2004 Disruptive Tech
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
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

package net.sf.saffron.oj.convert;

import java.util.Vector;

import openjava.ptree.*;

import org.eigenbase.oj.rel.JavaRel;
import org.eigenbase.oj.rel.JavaRelImplementor;
import org.eigenbase.oj.util.OJUtil;
import org.eigenbase.rel.convert.ConverterRel;
import org.eigenbase.relopt.CallingConvention;
import org.eigenbase.util.Util;

import net.sf.saffron.oj.util.UnboundVariableCollector;

/**
 * Thunk to convert between {@link CallingConvention#JAVA java}
 * and {@link CallingConvention#VECTOR vector} calling-conventions.
 *
 * @author jhyde
 * @since May 27, 2004
 * @version $Id$
 **/
public class JavaToVectorConvertlet extends JavaConvertlet
{
    public JavaToVectorConvertlet()
    {
        super(CallingConvention.JAVA, CallingConvention.VECTOR);
    }

    public void implementJavaParent(
        JavaRelImplementor implementor,
        ConverterRel converter)
    {
        // Generate
        //   v.addElement(i)
        //   Rowtype[] variable = <<child variable>>;
        //   <<parent body (references variable)>>
        StatementList stmtList = implementor.getStatementList();
        final JavaConverterRel javaConverter = (JavaConverterRel) converter;
        stmtList.add(
            new ExpressionStatement(
                new MethodCall(
                    javaConverter.var_v,
                    "addElement",
                    new ExpressionList(
                        OJUtil.box(
                            OJUtil.typeToOJClass(
                                converter.getChild().getRowType(),
                                implementor.getTypeFactory()),
                            implementor.translateInput(javaConverter, 0))))));
    }

    public ParseTree implement(
        JavaRelImplementor implementor,
        ConverterRel converter)
    {
        // Find all unbound variables in expressions in this tree
        UnboundVariableCollector unboundVars =
            UnboundVariableCollector.collectFromRel(converter);

        // Generate
        //   new Object() {
        //       /** Executes <code> ... </code>. **/
        //       Vector asVector(C0 v0, ...) {
        //         Vector v = new Vector();
        //         <<child loop
        //           v.addElement(i);
        //         >>
        //         return v;
        //       }
        //     }.asVector(v0, ...)
        final Variable var_v =
            ((JavaConverterRel) converter).var_v = implementor.newVariable();
        implementor.setExitStatement(new ReturnStatement(var_v));
        StatementList stmtList =
            new StatementList(
            // "Vector v = new Vector();"
            new VariableDeclaration(null, // no modifiers
                    OJUtil.typeNameForClass(Vector.class),
                    new VariableDeclarator(var_v.toString(),
                        new AllocationExpression(OJUtil.typeNameForClass(
                                Vector.class),
                            null))));

        // Give child chance to write its code into "stmtList" (and to
        // call us back so we can write "v.addElement(i);".
        implementor.pushStatementList(stmtList);
        Object o =
            implementor.visitJavaChild(
                converter, 0, (JavaRel) converter.getChild());
        assert (o == null);
        implementor.popStatementList(stmtList);

        // "return v;"
        stmtList.add(new ReturnStatement(var_v));

        // "public void asVector(C0 v0, ...) { ... }"
        MethodDeclaration asVector =
            new MethodDeclaration(new ModifierList(ModifierList.PUBLIC),
                OJUtil.typeNameForClass(Vector.class), "asVector",
                unboundVars.getParameterList(), // "(C0 v0, ...)"
                null, // throws nothing
                stmtList);
        asVector.setComment("/** Evaluates <code>"
            + converter.getCluster().getOriginalExpression().toString()
            + "</code> and returns the results as a vector. **/");

        return new MethodCall(
            new AllocationExpression(
                OJUtil.typeNameForClass(Object.class), // "Object"
                null, // "()"
                new MemberDeclarationList(asVector)),
            "asVector",
            unboundVars.getArgumentList());
    }
}


// End JavaToVectorConvertlet.java
