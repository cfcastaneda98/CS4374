/*
// $Id$
// Package org.eigenbase is a class library of data management components.
// Copyright (C) 2004 The Eigenbase Project
// Copyright (C) 2004 SQLstream, Inc.
// Copyright (C) 2005 Dynamo BI Corporation
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
package org.eigenbase.sql.validate;

import org.eigenbase.reltype.*;
import org.eigenbase.sql.*;


/**
 * Supplies a {@link SqlValidator} with the metadata for a table.
 *
 * @author jhyde
 * @version $Id$
 * @see SqlValidatorCatalogReader
 * @since Mar 25, 2003
 */
public interface SqlValidatorTable
{
    //~ Methods ----------------------------------------------------------------

    /**
     * Returns the row type of this table.
     *
     * <p>Unlike {@link SqlValidatorNamespace#getRowType()}, does not include
     * any system columns.
     *
     * @return Row type of this table
     */
    RelDataType getRowType();

    /**
     * Returns the qualified name of this table.
     *
     * <p>Typically something like {@code {"LOCALDB", "SALES", "EMPS"} }.
     *
     * @return Name of this table qualified by catalog and schema name
     */
    String [] getQualifiedName();

    /**
     * Returns whether a given column is monotonic.
     */
    SqlMonotonicity getMonotonicity(String columnName);

    /**
     * Returns the access type of the table
     */
    SqlAccessType getAllowedAccess();
}

// End SqlValidatorTable.java
