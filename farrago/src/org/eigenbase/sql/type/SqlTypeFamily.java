/*
// $Id$
// Package org.eigenbase is a class library of data management components.
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
package org.eigenbase.sql.type;

import java.sql.*;

import java.util.*;

import org.eigenbase.reltype.*;


/**
 * SqlTypeFamily provides SQL type categorization.
 *
 * <p>The <em>primary</em> family categorization is a complete disjoint
 * partitioning of SQL types into families, where two types are members of the
 * same primary family iff instances of the two types can be the operands of an
 * SQL equality predicate such as <code>WHERE v1 = v2</code>. Primary families
 * are returned by RelDataType.getFamily().
 *
 * <p>There is also a <em>secondary</em> family categorization which overlaps
 * with the primary categorization. It is used in type strategies for more
 * specific or more general categorization than the primary families. Secondary
 * families are never returned by RelDataType.getFamily().
 *
 * @author John V. Sichi
 * @version $Id$
 */
public enum SqlTypeFamily
    implements RelDataTypeFamily
{
    // Primary families.

    CHARACTER(SqlTypeName.charTypes, 0),

    BINARY(SqlTypeName.binaryTypes, 1),

    NUMERIC(SqlTypeName.numericTypes, 2),

    DATE(new SqlTypeName[] { SqlTypeName.DATE }, 3),

    TIME(new SqlTypeName[] { SqlTypeName.TIME }, 4),

    TIMESTAMP(new SqlTypeName[] { SqlTypeName.TIMESTAMP }, 5),

    BOOLEAN(SqlTypeName.booleanTypes, 6),

    INTERVAL_YEAR_MONTH(
        new SqlTypeName[] { SqlTypeName.INTERVAL_YEAR_MONTH },
        7),

    INTERVAL_DAY_TIME(new SqlTypeName[] { SqlTypeName.INTERVAL_DAY_TIME }, 8),

    // Secondary families.

    STRING(SqlTypeName.stringTypes, 9),

    APPROXIMATE_NUMERIC(SqlTypeName.approxTypes, 10),

    EXACT_NUMERIC(SqlTypeName.exactTypes, 11),

    INTEGER(SqlTypeName.intTypes, 12),

    DATETIME(SqlTypeName.datetimeTypes, 13),

    DATETIME_INTERVAL(SqlTypeName.timeIntervalTypes, 14),

    MULTISET(SqlTypeName.multisetTypes, 15),

    ANY(SqlTypeName.allTypes, 16),

    CURSOR(SqlTypeName.cursorTypes, 17),

    COLUMN_LIST(SqlTypeName.columnListTypes, 18);

    private static SqlTypeFamily [] jdbcTypeToFamily;

    private static SqlTypeFamily [] sqlTypeToFamily;

    static {
        // This squanders some memory since MAX_JDBC_TYPE == 2006!
        jdbcTypeToFamily =
            new SqlTypeFamily[(1 + SqlTypeName.MAX_JDBC_TYPE)
                - SqlTypeName.MIN_JDBC_TYPE];

        setFamilyForJdbcType(Types.BIT, NUMERIC);
        setFamilyForJdbcType(Types.TINYINT, NUMERIC);
        setFamilyForJdbcType(Types.SMALLINT, NUMERIC);
        setFamilyForJdbcType(Types.BIGINT, NUMERIC);
        setFamilyForJdbcType(Types.INTEGER, NUMERIC);
        setFamilyForJdbcType(Types.NUMERIC, NUMERIC);
        setFamilyForJdbcType(Types.DECIMAL, NUMERIC);

        setFamilyForJdbcType(Types.FLOAT, NUMERIC);
        setFamilyForJdbcType(Types.REAL, NUMERIC);
        setFamilyForJdbcType(Types.DOUBLE, NUMERIC);

        setFamilyForJdbcType(Types.CHAR, CHARACTER);
        setFamilyForJdbcType(Types.VARCHAR, CHARACTER);
        setFamilyForJdbcType(Types.LONGVARCHAR, CHARACTER);
        setFamilyForJdbcType(Types.CLOB, CHARACTER);

        setFamilyForJdbcType(Types.BINARY, BINARY);
        setFamilyForJdbcType(Types.VARBINARY, BINARY);
        setFamilyForJdbcType(Types.LONGVARBINARY, BINARY);
        setFamilyForJdbcType(Types.BLOB, BINARY);

        setFamilyForJdbcType(Types.DATE, DATE);
        setFamilyForJdbcType(Types.TIME, TIME);
        setFamilyForJdbcType(Types.TIMESTAMP, TIMESTAMP);
        setFamilyForJdbcType(Types.BOOLEAN, BOOLEAN);

        setFamilyForJdbcType(
            SqlTypeName.CURSOR.getJdbcOrdinal(),
            CURSOR);

        setFamilyForJdbcType(
            SqlTypeName.COLUMN_LIST.getJdbcOrdinal(),
            COLUMN_LIST);

        sqlTypeToFamily = new SqlTypeFamily[SqlTypeName.values().length];
        sqlTypeToFamily[SqlTypeName.BOOLEAN.ordinal()] = BOOLEAN;
        sqlTypeToFamily[SqlTypeName.CHAR.ordinal()] = CHARACTER;
        sqlTypeToFamily[SqlTypeName.VARCHAR.ordinal()] = CHARACTER;
        sqlTypeToFamily[SqlTypeName.BINARY.ordinal()] = BINARY;
        sqlTypeToFamily[SqlTypeName.VARBINARY.ordinal()] = BINARY;
        sqlTypeToFamily[SqlTypeName.DECIMAL.ordinal()] = NUMERIC;
        sqlTypeToFamily[SqlTypeName.TINYINT.ordinal()] = NUMERIC;
        sqlTypeToFamily[SqlTypeName.SMALLINT.ordinal()] = NUMERIC;
        sqlTypeToFamily[SqlTypeName.INTEGER.ordinal()] = NUMERIC;
        sqlTypeToFamily[SqlTypeName.BIGINT.ordinal()] = NUMERIC;
        sqlTypeToFamily[SqlTypeName.REAL.ordinal()] = NUMERIC;
        sqlTypeToFamily[SqlTypeName.DOUBLE.ordinal()] = NUMERIC;
        sqlTypeToFamily[SqlTypeName.FLOAT.ordinal()] = NUMERIC;
        sqlTypeToFamily[SqlTypeName.DATE.ordinal()] = DATE;
        sqlTypeToFamily[SqlTypeName.TIME.ordinal()] = TIME;
        sqlTypeToFamily[SqlTypeName.TIMESTAMP.ordinal()] = TIMESTAMP;
        sqlTypeToFamily[SqlTypeName.INTERVAL_YEAR_MONTH.ordinal()] =
            INTERVAL_YEAR_MONTH;
        sqlTypeToFamily[SqlTypeName.NULL.ordinal()] = ANY;
        sqlTypeToFamily[SqlTypeName.ANY.ordinal()] = ANY;
        sqlTypeToFamily[SqlTypeName.INTERVAL_DAY_TIME.ordinal()] =
            INTERVAL_DAY_TIME;
        sqlTypeToFamily[SqlTypeName.CURSOR.ordinal()] = CURSOR;
        sqlTypeToFamily[SqlTypeName.COLUMN_LIST.ordinal()] = COLUMN_LIST;
    }

    /**
     * List of {@link SqlTypeName}s included in this family.
     */
    private List<SqlTypeName> typeNames;

    private int ordinal;

    private SqlTypeFamily(SqlTypeName [] typeNames, int ordinal)
    {
        this.typeNames =
            Collections.unmodifiableList(
                Arrays.asList(typeNames));
        this.ordinal = ordinal;
    }

    private static void setFamilyForJdbcType(
        int jdbcType,
        SqlTypeFamily family)
    {
        jdbcTypeToFamily[jdbcType - SqlTypeName.MIN_JDBC_TYPE] = family;
    }

    /**
     * Gets the primary family containing a SqlTypeName.
     *
     * @param sqlTypeName the type of interest
     *
     * @return containing family, or null for none
     */
    public static SqlTypeFamily getFamilyForSqlType(SqlTypeName sqlTypeName)
    {
        return sqlTypeToFamily[sqlTypeName.ordinal()];
    }

    /**
     * Gets the primary family containing a JDBC type.
     *
     * @param jdbcType the JDBC type of interest
     *
     * @return containing family
     */
    public static SqlTypeFamily getFamilyForJdbcType(int jdbcType)
    {
        return jdbcTypeToFamily[jdbcType - SqlTypeName.MIN_JDBC_TYPE];
    }

    /**
     * @return collection of {@link SqlTypeName}s included in this family
     */
    public Collection<SqlTypeName> getTypeNames()
    {
        return typeNames;
    }
}

// End SqlTypeFamily.java
