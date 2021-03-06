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
import openjava.ptree.AllocationExpression;
import openjava.ptree.Expression;
import openjava.ptree.ExpressionList;
import openjava.ptree.ParseTree;

import org.eigenbase.oj.rel.JavaRel;
import org.eigenbase.oj.rel.JavaRelImplementor;
import org.eigenbase.rel.convert.ConverterRel;
import org.eigenbase.relopt.CallingConvention;
import org.eigenbase.runtime.EnumerationIterator;


/**
 * Thunk to convert between {@link CallingConvention#ENUMERATION}
 * and {@link CallingConvention#ITERATOR iterator} calling-conventions.
 *
 * @author jhyde
 * @since May 27, 2004
 * @version $Id$
 **/
public class EnumerationToIteratorConvertlet extends JavaConvertlet
{
    public EnumerationToIteratorConvertlet()
    {
        super(CallingConvention.ENUMERATION, CallingConvention.ITERATOR);
    }

    public ParseTree implement(
        JavaRelImplementor implementor,
        ConverterRel converter)
    {
        // Generate
        //   new saffron.runtime.EnumerationIterator(<<child>>)
        Expression exp =
            implementor.visitJavaChild(
                converter, 0, (JavaRel) converter.getChild());
        return new AllocationExpression(
            OJClass.forClass(EnumerationIterator.class),
            new ExpressionList(exp));
    }
}


// End EnumerationToIteratorConvertlet.java
