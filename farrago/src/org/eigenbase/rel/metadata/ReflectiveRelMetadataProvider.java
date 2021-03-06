/*
// $Id$
// Package org.eigenbase is a class library of data management components.
// Copyright (C) 2006 The Eigenbase Project
// Copyright (C) 2006 SQLstream, Inc.
// Copyright (C) 2006 Dynamo BI Corporation
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
package org.eigenbase.rel.metadata;

import java.lang.reflect.*;

import java.util.*;

import org.eigenbase.rel.*;
import org.eigenbase.util.*;


/**
 * ReflectiveRelMetadataProvider provides an abstract base for reflective
 * implementations of the {@link RelMetadataProvider} interface. For an example,
 * see {@link DefaultRelMetadataProvider}.
 *
 * <p>TODO jvs 28-Mar-2006: most of this should probably be refactored into
 * ReflectUtil.
 *
 * @author John V. Sichi
 * @version $Id$
 */
public abstract class ReflectiveRelMetadataProvider
    implements RelMetadataProvider,
        ReflectiveVisitor
{
    //~ Instance fields --------------------------------------------------------

    private final Map<String, List<Class>> parameterTypeMap;

    private final ReflectiveVisitDispatcher<ReflectiveRelMetadataProvider,
        RelNode> visitDispatcher;

    //~ Constructors -----------------------------------------------------------

    /**
     * Creates a ReflectiveRelMetadataProvider.
     */
    protected ReflectiveRelMetadataProvider()
    {
        parameterTypeMap = new HashMap<String, List<Class>>();
        visitDispatcher =
            ReflectUtil.createDispatcher(
                ReflectiveRelMetadataProvider.class,
                RelNode.class);
    }

    //~ Methods ----------------------------------------------------------------

    /**
     * Maps the parameter type signature to look up for a given metadata query.
     *
     * @param metadataQueryName name of metadata query to map
     * @param parameterTypes argument types (beyond the overloaded rel type) to
     * map
     */
    protected void mapParameterTypes(
        String metadataQueryName,
        List<Class> parameterTypes)
    {
        parameterTypeMap.put(metadataQueryName, parameterTypes);
    }

    // implement RelMetadataProvider
    public Object getRelMetadata(
        RelNode rel,
        String metadataQueryName,
        Object [] args)
    {
        List<Class> parameterTypes = parameterTypeMap.get(metadataQueryName);
        if (parameterTypes == null) {
            parameterTypes = Collections.emptyList();
        }
        Method method =
            visitDispatcher.lookupVisitMethod(
                getClass(),
                rel.getClass(),
                metadataQueryName,
                parameterTypes);

        if (method == null) {
            return null;
        }

        Object [] allArgs;
        if (args != null) {
            allArgs = new Object[args.length + 1];
            allArgs[0] = rel;
            System.arraycopy(args, 0, allArgs, 1, args.length);
        } else {
            allArgs = new Object[] { rel };
        }

        try {
            return method.invoke(this, allArgs);
        } catch (Throwable ex) {
            // TODO jvs 28-Mar-2006:  share code with ReflectUtil
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            } else if (ex instanceof Error) {
                throw (Error) ex;
            } else {
                throw Util.newInternal(ex);
            }
        }
    }
}

// End ReflectiveRelMetadataProvider.java
