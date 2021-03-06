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

import openjava.mop.OJClass;
import openjava.mop.Toolbox;
import openjava.ptree.*;

import org.eigenbase.oj.rel.JavaRel;
import org.eigenbase.oj.rel.JavaRelImplementor;
import org.eigenbase.oj.util.OJUtil;
import org.eigenbase.rel.convert.ConverterRel;
import org.eigenbase.relopt.CallingConvention;
import org.eigenbase.util.Util;


/**
 * Thunk to convert between {@link CallingConvention#ITERABLE iterable}
 * and {@link CallingConvention#JAVA java} calling-conventions.
 *
 * @author jhyde
 * @since May 27, 2004
 * @version $Id$
 **/
public class IterableToJavaConvertlet extends JavaConvertlet
{
    public IterableToJavaConvertlet()
    {
        super(CallingConvention.ITERABLE, CallingConvention.JAVA);
    }

    public ParseTree implement(
        JavaRelImplementor implementor,
        ConverterRel converter)
    {
        // Generate
        //   Iterator iter = <<exp>>.iterator();
        //   while (iter.hasNext()) {
        //     V row = (Type) iter.next();
        //     <<body>>
        //   }
        //
        StatementList stmtList = implementor.getStatementList();

        // Generate
        //   Iterator iter = <<exp>>.iterator();
        //   while (iter.hasNext()) {
        //     V row = (Type) iter.next();
        //     <<body>>
        //   }
        //
        StatementList whileBody = new StatementList();
        Variable variable_iter = implementor.newVariable();
        Expression exp =
            implementor.visitJavaChild(
                converter, 0, (JavaRel) converter.getChild());
        stmtList.add(
            new VariableDeclaration(
                new TypeName("java.util.Iterator"),
                variable_iter.toString(),
                exp));
        stmtList.add(
            new WhileStatement(
                new MethodCall(variable_iter, "hasNext", null),
                whileBody));
        OJClass rowType = OJUtil.typeToOJClass(
            converter.getChild().getRowType(),
            implementor.getTypeFactory());
        Variable variable_row =
            implementor.bind(
                converter,
                whileBody,
                Util.castObject(
                    new MethodCall(variable_iter, "next", null),
                    Toolbox.clazzObject,
                    rowType));
        Util.discard(variable_row);
        implementor.generateParentBody(converter, whileBody);
        return null;
    }
}


// End IterableToJavaConvertlet.java
