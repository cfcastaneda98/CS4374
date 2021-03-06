/*
// $Id$
// Package org.eigenbase is a class library of data management components.
// Copyright (C) 2010 The Eigenbase Project
// Copyright (C) 2010 SQLstream, Inc.
// Copyright (C) 2010 Dynamo BI Corporation
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
package org.eigenbase.sql.test;

import java.sql.Types;

import junit.framework.TestCase;

import org.eigenbase.sql.type.*;

/**
 * Tests types supported by SqlTypeName.java.
 */
public class SqlTypeNameTest
     extends TestCase
{
    public void testBit() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.BIT);
        assertEquals(
            "BIT did not map to BOOLEAN",
            SqlTypeName.BOOLEAN,
            tn);
    }

    public void testTinyint() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.TINYINT);
        assertEquals(
            "TINYINT did not map to TINYINT",
            SqlTypeName.TINYINT,
            tn);
    }

    public void testSmallint() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.SMALLINT);
        assertEquals(
            "SMALLINT did not map to SMALLINT",
            SqlTypeName.SMALLINT,
            tn);
    }

    public void testInteger() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.INTEGER);
        assertEquals(
            "INTEGER did not map to INTEGER",
            SqlTypeName.INTEGER,
            tn);
    }

    public void testBigint() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.BIGINT);
        assertEquals(
            "BIGINT did not map to BIGINT",
            SqlTypeName.BIGINT,
            tn);
    }

    public void testFloat() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.FLOAT);
        assertEquals(
            "FLOAT did not map to FLOAT",
            SqlTypeName.FLOAT,
            tn);
    }

    public void testReal() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.REAL);
        assertEquals(
            "REAL did not map to REAL",
            SqlTypeName.REAL,
            tn);
    }

    public void testDouble() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.DOUBLE);
        assertEquals(
            "DOUBLE did not map to DOUBLE",
            SqlTypeName.DOUBLE,
            tn);
    }

    public void testNumeric() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.NUMERIC);
        assertEquals(
            "NUMERIC did not map to DECIMAL",
            SqlTypeName.DECIMAL,
            tn);
    }

    public void testDecimal() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.DECIMAL);
        assertEquals(
            "DECIMAL did not map to DECIMAL",
            SqlTypeName.DECIMAL,
            tn);
    }

    public void testChar() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.CHAR);
        assertEquals(
            "CHAR did not map to CHAR",
            SqlTypeName.CHAR,
            tn);
    }

    public void testVarchar() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.VARCHAR);
        assertEquals(
            "VARCHAR did not map to VARCHAR",
            SqlTypeName.VARCHAR,
            tn);
    }

    public void testLongvarchar() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.LONGVARCHAR);
        assertEquals(
            "LONGVARCHAR did not map to null",
            null,
            tn);
    }

    public void testDate() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.DATE);
        assertEquals(
            "DATE did not map to DATE",
            SqlTypeName.DATE,
            tn);
    }

    public void testTime() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.TIME);
        assertEquals(
            "TIME did not map to TIME",
            SqlTypeName.TIME,
            tn);
    }

    public void testTimestamp() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.TIMESTAMP);
        assertEquals(
            "TIMESTAMP did not map to TIMESTAMP",
            SqlTypeName.TIMESTAMP,
            tn);
    }

    public void testBinary() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.BINARY);
        assertEquals(
            "BINARY did not map to BINARY",
            SqlTypeName.BINARY,
            tn);
    }

    public void testVarbinary() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.VARBINARY);
        assertEquals(
            "VARBINARY did not map to VARBINARY",
            SqlTypeName.VARBINARY,
            tn);
    }

    public void testLongvarbinary() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.LONGVARBINARY);
        assertEquals(
            "LONGVARBINARY did not map to null",
            null,
            tn);
    }

    public void testNull() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.NULL);
        assertEquals(
            "NULL did not map to null",
            null,
            tn);
    }

    public void testOther() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.OTHER);
        assertEquals(
            "OTHER did not map to null",
            null,
            tn);
    }

    public void testJavaobject() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.JAVA_OBJECT);
        assertEquals(
            "JAVA_OBJECT did not map to null",
            null,
            tn);
    }

    public void testDistinct() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.DISTINCT);
        assertEquals(
            "DISTINCT did not map to DISTINCT",
            SqlTypeName.DISTINCT,
            tn);
    }

    public void testStruct() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.STRUCT);
        assertEquals(
            "STRUCT did not map to null",
            SqlTypeName.STRUCTURED,
            tn);
    }

    public void testArray() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.ARRAY);
        assertEquals(
            "ARRAY did not map to null",
            null,
            tn);
    }

    public void testBlob() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.BLOB);
        assertEquals(
            "BLOB did not map to null",
            null,
            tn);
    }

    public void testClob() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.CLOB);
        assertEquals(
            "CLOB did not map to null",
            null,
            tn);
    }

    public void testRef() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.REF);
        assertEquals(
            "REF did not map to null",
            null,
            tn);
    }

    public void testDatalink() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.DATALINK);
        assertEquals(
            "DATALINK did not map to null",
            null,
            tn);
    }

    public void testBoolean() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(Types.BOOLEAN);
        assertEquals(
            "BOOLEAN did not map to BOOLEAN",
            SqlTypeName.BOOLEAN,
            tn);
    }

    public void testRowid() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(ExtraSqlTypes.ROWID);

        //ROWID not supported yet
        assertEquals(
            "ROWID maps to non-null type",
            null,
            tn);
    }

    public void testNchar() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(ExtraSqlTypes.NCHAR);

        //NCHAR not supported yet, currently maps to CHAR
        assertEquals(
            "NCHAR did not map to CHAR",
            SqlTypeName.CHAR,
            tn);
    }

    public void testNvarchar() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(ExtraSqlTypes.NVARCHAR);

        //NVARCHAR not supported yet, currently maps to VARCHAR
        assertEquals(
            "NVARCHAR did not map to VARCHAR",
            SqlTypeName.VARCHAR,
            tn);
    }

    public void testLongnvarchar() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(ExtraSqlTypes.LONGNVARCHAR);

        //LONGNVARCHAR not supported yet
        assertEquals(
            "LONGNVARCHAR maps to non-null type",
            null,
            tn);
    }

    public void testNclob() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(ExtraSqlTypes.NCLOB);

        //NCLOB not supported yet
        assertEquals(
            "NCLOB maps to non-null type",
            null,
            tn);
    }

    public void testSqlxml() {
        SqlTypeName tn =
            SqlTypeName.getNameForJdbcType(ExtraSqlTypes.SQLXML);

        //SQLXML not supported yet
        assertEquals(
            "SQLXML maps to non-null type",
            null,
            tn);
    }
}

// End SqlTypeNameTest.java