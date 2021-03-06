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
package net.sf.farrago.ddl;

import java.util.*;

import javax.jmi.reflect.*;

import net.sf.farrago.catalog.*;
import net.sf.farrago.cwm.core.*;
import net.sf.farrago.cwm.relational.*;
import net.sf.farrago.fem.med.*;
import net.sf.farrago.fem.sql2003.*;
import net.sf.farrago.namespace.util.*;
import net.sf.farrago.session.*;


/**
 * DdlTruncateStmt represents a DDL TRUNCATE statement of any kind.
 *
 * @author John V. Sichi
 * @version $Id$
 */
public class DdlTruncateStmt
    extends DdlStmt
    implements DdlMultipleTransactionStmt
{
    //~ Instance fields --------------------------------------------------------

    private String tableMofId;
    private RefClass tableClass;
    private List<String> indexMofIds;

    //~ Constructors -----------------------------------------------------------

    /**
     * Constructs a new DdlTruncateStmt.
     *
     * @param truncatedElement top-level element truncated by this stmt
     */
    public DdlTruncateStmt(CwmModelElement truncatedElement)
    {
        super(truncatedElement, true);
        tableMofId = truncatedElement.refMofId();
        tableClass = truncatedElement.refClass();
    }

    //~ Methods ----------------------------------------------------------------

    // implement DdlStmt
    public void visit(DdlVisitor visitor)
    {
        visitor.visit(this);
    }

    // implement DdlMultipleTransactionStmt
    public void prepForExecuteUnlocked(
        FarragoSessionDdlValidator ddlValidator,
        FarragoSession session)
    {
        indexMofIds = new ArrayList<String>();
        CwmTable table = (CwmTable) getModelElement();
        Collection<FemLocalIndex> tableIndexes =
            FarragoCatalogUtil.getTableIndexes(session.getRepos(), table);
        for (FemLocalIndex index : tableIndexes) {
            indexMofIds.add(index.refMofId());
        }
    }

    // implement DdlMultipleTransactionStmt
    public void executeUnlocked(
        FarragoSessionDdlValidator ddlValidator,
        FarragoSession session)
    {
        FarragoSessionIndexMap baseIndexMap = ddlValidator.getIndexMap();
        FarragoDataWrapperCache wrapperCache =
            ddlValidator.getDataWrapperCache();
        for (String indexMofId : indexMofIds) {
            baseIndexMap.dropIndexStorage(wrapperCache, indexMofId, true);
        }
    }

    // implement DdlMultipleTransactionStmt
    public boolean completeRequiresWriteTxn()
    {
        return true;
    }

    // implement DdlMultipleTransactionStmt
    public void completeAfterExecuteUnlocked(
        FarragoSessionDdlValidator ddlValidator,
        FarragoSession session,
        boolean success)
    {
        if (!success) {
            // NOTE jvs 11-Dec-2008:  I'm not sure whether anything
            // can cause a TRUNCATE to fail, but if it does fail, we
            // shouldn't reset the rowcounts.
            return;
        }
        FemAbstractColumnSet table =
            (FemAbstractColumnSet) session.getRepos().getEnkiMdrRepos()
            .getByMofId(
                tableMofId,
                tableClass);
        session.getPersonality().resetRowCounts(table);
    }
}

// End DdlTruncateStmt.java
