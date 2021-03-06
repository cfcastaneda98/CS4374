/*
// $Id$
// Farrago is an extensible data management system.
// Copyright (C) 2005 The Eigenbase Project
// Copyright (C) 2005 SQLstream, Inc.
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
package net.sf.farrago.query;

import java.sql.*;
import java.util.List;

import net.sf.farrago.session.*;
import net.sf.farrago.util.*;

import org.eigenbase.oj.stmt.*;
import org.eigenbase.relopt.*;
import org.eigenbase.reltype.*;


/**
 * FarragoExecutableExplainStmt implements FarragoSessionExecutableStmt for an
 * EXPLAIN PLAN statement.
 *
 * <p>NOTE: be sure to read superclass warnings before modifying this class.
 *
 * @author John V. Sichi
 * @version $Id$
 */
class FarragoExecutableExplainStmt
    extends FarragoExecutableStmtImpl
{
    //~ Instance fields --------------------------------------------------------

    private final String explanation;

    //~ Constructors -----------------------------------------------------------

    FarragoExecutableExplainStmt(
        RelDataType dynamicParamRowType,
        String explanation)
    {
        super(
            dynamicParamRowType,
            false,
            null,
            new TableAccessMap());

        this.explanation = explanation;
    }

    //~ Methods ----------------------------------------------------------------

    // implement FarragoSessionExecutableStmt
    public RelDataType getRowType()
    {
        // TODO:  make a proper type descriptor (and use it for execute also)
        throw new UnsupportedOperationException();
    }

    // implement FarragoSessionExecutableStmt
    public List<List<String>> getFieldOrigins()
    {
        throw new UnsupportedOperationException();
    }

    // implement FarragoSessionExecutableStmt
    public ResultSet execute(FarragoSessionRuntimeContext runtimeContext)
    {
        // don't need a context or repository session
        runtimeContext.closeAllocation();
        runtimeContext.getSession().getRepos().endReposSession();
        return PreparedExplanation.executeStatic(explanation);
    }

    // implement FarragoSessionExecutableStmt
    public long getMemoryUsage()
    {
        return FarragoUtil.getStringMemoryUsage(explanation);
    }
}

// End FarragoExecutableExplainStmt.java
