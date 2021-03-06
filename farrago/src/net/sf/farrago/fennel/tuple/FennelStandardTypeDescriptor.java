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
package net.sf.farrago.fennel.tuple;

import java.io.*;

import org.eigenbase.util14.*;


/**
 * FennelStandardTypeDescriptor implements the {@link
 * FennelStandardTypeDescriptor} enumerations as kept in fennel. This must be
 * kept in sync with any changes to fennel's <code>
 * FennelStandardTypeDescriptor.h</code>. This class is JDK 1.4 compatible.
 *
 * @author Mike Bennett
 * @version $Id$
 */
public abstract class FennelStandardTypeDescriptor
    extends Enum14.BasicValue
    implements FennelStoredTypeDescriptor
{
    //~ Static fields/initializers ---------------------------------------------

    /**
     * SerialVersionUID created with JDK 1.5 serialver tool.
     */
    private static final long serialVersionUID = 5514431391550418871L;

    public static final int MIN_ORDINAL = 1;
    public static final int INT_8_ORDINAL = 1;
    public static final int UINT_8_ORDINAL = 2;
    public static final int INT_16_ORDINAL = 3;
    public static final int UINT_16_ORDINAL = 4;
    public static final int INT_32_ORDINAL = 5;
    public static final int UINT_32_ORDINAL = 6;
    public static final int INT_64_ORDINAL = 7;
    public static final int UINT_64_ORDINAL = 8;
    public static final int BOOL_ORDINAL = 9;
    public static final int REAL_ORDINAL = 10;
    public static final int DOUBLE_ORDINAL = 11;
    public static final int CHAR_ORDINAL = 12;
    public static final int VARCHAR_ORDINAL = 13;
    public static final int BINARY_ORDINAL = 14;
    public static final int VARBINARY_ORDINAL = 15;
    public static final int UNICODE_CHAR_ORDINAL = 16;
    public static final int UNICODE_VARCHAR_ORDINAL = 17;
    public static final int EXTENSION_MIN_ORDINAL = 1000;

    /**
     * Describes a signed byte.
     */
    public static final Type_INT_8 INT_8 = new Type_INT_8();

    /**
     * Describes an unsigned signed byte.
     */
    public static final Type_UINT_8 UINT_8 = new Type_UINT_8();

    /**
     * Describes a signed short.
     */
    public static final Type_INT_16 INT_16 = new Type_INT_16();

    /**
     * Describes an unsigned short.
     */
    public static final Type_UINT_16 UINT_16 = new Type_UINT_16();

    /**
     * Describes a signed int.
     */
    public static final Type_INT_32 INT_32 = new Type_INT_32();

    /**
     * Describes an unsigned int.
     */
    public static final Type_UINT_32 UINT_32 = new Type_UINT_32();

    /**
     * Describes a signed long.
     */
    public static final Type_INT_64 INT_64 = new Type_INT_64();

    /**
     * Describes an unsigned long.
     */
    public static final Type_UINT_64 UINT_64 = new Type_UINT_64();

    /**
     * Describes a boolean.
     */
    public static final Type_BOOL BOOL = new Type_BOOL();

    /**
     * Describes a float.
     */
    public static final Type_REAL REAL = new Type_REAL();

    /**
     * Describes a double.
     */
    public static final Type_DOUBLE DOUBLE = new Type_DOUBLE();

    /**
     * Describes a fixed-width character string.
     */
    public static final Type_CHAR CHAR = new Type_CHAR();

    /**
     * Describes a variable-width character string.
     */
    public static final Type_VARCHAR VARCHAR = new Type_VARCHAR();

    /**
     * Describes a fixed-width binary string.
     */
    public static final Type_BINARY BINARY = new Type_BINARY();

    /**
     * Describes a variable-width binary string.
     */
    public static final Type_VARBINARY VARBINARY = new Type_VARBINARY();

    /**
     * Describes a fixed-width UNICODE character string.
     */
    public static final Type_UNICODE_CHAR UNICODE_CHAR =
        new Type_UNICODE_CHAR();

    /**
     * Describes a variable-width binary string.
     */
    public static final Type_UNICODE_VARCHAR UNICODE_VARCHAR =
        new Type_UNICODE_VARCHAR();

    private static final FennelStandardTypeDescriptor [] values =
    {
        INT_8,
        UINT_8,
        INT_16,
        UINT_16,
        INT_32,
        UINT_32,
        INT_64,
        UINT_64,
        BOOL,
        REAL,
        DOUBLE,
        CHAR,
        VARCHAR,
        BINARY,
        VARBINARY,
        UNICODE_CHAR,
        UNICODE_VARCHAR,
    };

    public static final Enum14 enumeration = new Enum14(values);

    //~ Constructors -----------------------------------------------------------

    private FennelStandardTypeDescriptor(String name, int ordinal)
    {
        super(name, ordinal, null);
    }

    //~ Methods ----------------------------------------------------------------

    /**
     * Returns the {@link FennelStandardTypeDescriptor} with a given name.
     */
    public static FennelStandardTypeDescriptor get(String name)
    {
        return (FennelStandardTypeDescriptor) enumeration.getValue(name);
    }

    /**
     * Returns the {@link FennelStandardTypeDescriptor} with a given ordinal.
     */
    public static FennelStandardTypeDescriptor forOrdinal(int ordinal)
    {
        return (FennelStandardTypeDescriptor) enumeration.getValue(ordinal);
    }

    /**
     * Returns whether this type is numeric.
     */
    public abstract boolean isNumeric();

    /**
     * Returns whether this is primitive type.
     */
    public boolean isNative()
    {
        return getOrdinal() < DOUBLE_ORDINAL;
    }

    /**
     * Returns whether this ordinal represents a primitive non-boolean type.
     */
    public boolean isNativeNotBool()
    {
        return (getOrdinal() <= DOUBLE_ORDINAL)
            && (getOrdinal() != BOOL_ORDINAL);
    }

    /**
     * Returns whether this ordinal represents an integral native type.
     */
    public boolean isIntegralNative(int st)
    {
        if (getOrdinal() <= BOOL_ORDINAL) {
            return true;
        }
        return false;
    }

    /**
     * Returns whether this ordinal is an exact numeric.
     */
    public boolean isExact()
    {
        if (getOrdinal() <= UINT_64_ORDINAL) {
            return true;
        }
        return false;
    }

    /**
     * Returns whether this ordinal is an approximate numeric.
     */
    public boolean isApprox()
    {
        if ((getOrdinal() == REAL_ORDINAL)
            || (getOrdinal() == DOUBLE_ORDINAL))
        {
            return true;
        }
        return false;
    }

    /**
     * Returns whether this ordinal represents an array.
     */
    public boolean isArray()
    {
        if ((getOrdinal() >= CHAR_ORDINAL)
            && (getOrdinal() <= UNICODE_VARCHAR_ORDINAL))
        {
            return true;
        }
        return false;
    }

    /**
     * Returns whether this ordinal represents a variable length array.
     */
    public boolean isVariableLenArray()
    {
        if ((getOrdinal() == VARCHAR_ORDINAL)
            || (getOrdinal() == VARBINARY_ORDINAL)
            || (getOrdinal() == UNICODE_VARCHAR_ORDINAL))
        {
            return true;
        }
        return false;
    }

    /**
     * Returns whether this ordinal represents a fixed length array.
     */
    public boolean isFixedLenArray()
    {
        if ((getOrdinal() == CHAR_ORDINAL)
            || (getOrdinal() == BINARY_ORDINAL)
            || (getOrdinal() == UNICODE_CHAR_ORDINAL))
        {
            return true;
        }
        return false;
    }

    /**
     * Returns whether this ordinal represents a text array.
     */
    public boolean isTextArray()
    {
        if ((getOrdinal() == CHAR_ORDINAL)
            || (getOrdinal() == VARCHAR_ORDINAL)
            || (getOrdinal() == UNICODE_CHAR_ORDINAL)
            || (getOrdinal() == UNICODE_VARCHAR_ORDINAL))
        {
            return true;
        }
        return false;
    }

    /**
     * Returns whether this ordinal represent a binary array.
     */
    public boolean isBinaryArray()
    {
        if ((getOrdinal() == VARBINARY_ORDINAL)
            || (getOrdinal() == BINARY_ORDINAL))
        {
            return true;
        }
        return false;
    }

    //~ Inner Classes ----------------------------------------------------------

    /**
     * Abstract base class for all types.
     */
    private static abstract class FennelType
        extends FennelStandardTypeDescriptor
        implements FennelStoredTypeDescriptor,
            Serializable
    {
        /**
         * SerialVersionUID created with JDK 1.5 serialver tool.
         */
        private static final long serialVersionUID = 2487596130623297086L;

        FennelType(String name, int ordinal)
        {
            super(name, ordinal);
        }

        public int getBitCount()
        {
            return 0;
        }

        public int getFixedByteCount()
        {
            return 0;
        }

        public int getMinByteCount(int maxWidth)
        {
            return 0;
        }

        public int getAlignmentByteCount(int width)
        {
            return 1;
        }

        public boolean isExact()
        {
            return false;
        }

        public boolean isSigned()
        {
            return false;
        }

        public boolean isNumeric()
        {
            return false;
        }
    }

    /**
     * Abstract base class for all numeric types.
     */
    private static abstract class FennelNumericType
        extends FennelType
    {
        /**
         * SerialVersionUID created with JDK 1.5 serialver tool.
         */
        private static final long serialVersionUID = 1126249335998415507L;

        private final int bitCount;
        private final int fixedByteCount;
        private final boolean signed;
        private final boolean exact;

        /**
         * Creates a FennelNumericType.
         */
        protected FennelNumericType(
            String name,
            int ordinal,
            int bitCount,
            int fixedByteCount,
            boolean signed,
            boolean exact)
        {
            super(name, ordinal);
            this.bitCount = bitCount;
            this.fixedByteCount = fixedByteCount;
            this.signed = signed;
            this.exact = exact;
        }

        /**
         * Required by the serialization mechanism; should never be used.
         */
        protected FennelNumericType()
        {
            super(null, -1);
            this.bitCount = 0;
            this.fixedByteCount = 0;
            this.signed = false;
            this.exact = true;
        }

        public int getBitCount()
        {
            return bitCount;
        }

        public int getFixedByteCount()
        {
            return fixedByteCount;
        }

        public int getMinByteCount(int maxWidth)
        {
            return maxWidth;
        }

        public int getAlignmentByteCount(int width)
        {
            return width;
        }

        public boolean isSigned()
        {
            return signed;
        }

        public boolean isExact()
        {
            return exact;
        }

        public boolean isNumeric()
        {
            return true;
        }
    }

    /**
     * Describes a signed byte.
     */
    private static class Type_INT_8
        extends FennelNumericType
    {
        /**
         * SerialVersionUID created with JDK 1.5 serialver tool.
         */
        private static final long serialVersionUID = -1929462493400009575L;

        Type_INT_8()
        {
            super("s1", INT_8_ORDINAL, 0, 1, true, true);
        }

        public FennelAttributeAccessor newAttributeAccessor()
        {
            return new FennelAttributeAccessor.FennelByteAccessor();
        }
    }

    /**
     * Describes an unsigned byte.
     */
    private static class Type_UINT_8
        extends FennelNumericType
    {
        /**
         * SerialVersionUID created with JDK 1.5 serialver tool.
         */
        private static final long serialVersionUID = 4611295449204872120L;

        Type_UINT_8()
        {
            super("u1", UINT_8_ORDINAL, 0, 1, false, true);
        }

        public FennelAttributeAccessor newAttributeAccessor()
        {
            return new FennelAttributeAccessor.FennelByteAccessor();
        }
    }

    /**
     * Describes a signed short.
     */
    private static class Type_INT_16
        extends FennelNumericType
    {
        /**
         * SerialVersionUID created with JDK 1.5 serialver tool.
         */
        private static final long serialVersionUID = 7178036227179809714L;

        Type_INT_16()
        {
            super("s2", INT_16_ORDINAL, 0, 2, true, true);
        }

        public FennelAttributeAccessor newAttributeAccessor()
        {
            return new FennelAttributeAccessor.FennelShortAccessor();
        }
    }

    /**
     * Describes an unsigned short.
     */
    private static class Type_UINT_16
        extends FennelNumericType
    {
        /**
         * SerialVersionUID created with JDK 1.5 serialver tool.
         */
        private static final long serialVersionUID = -4187803364067898572L;

        Type_UINT_16()
        {
            super("u2", UINT_16_ORDINAL, 0, 2, false, true);
        }

        public FennelAttributeAccessor newAttributeAccessor()
        {
            return new FennelAttributeAccessor.FennelShortAccessor();
        }
    }

    /**
     * Describes a signed int.
     */
    private static class Type_INT_32
        extends FennelNumericType
    {
        /**
         * SerialVersionUID created with JDK 1.5 serialver tool.
         */
        private static final long serialVersionUID = 4459795783565074222L;

        Type_INT_32()
        {
            super("s4", INT_32_ORDINAL, 0, 4, true, true);
        }

        public FennelAttributeAccessor newAttributeAccessor()
        {
            return new FennelAttributeAccessor.FennelIntAccessor();
        }
    }

    /**
     * Describes an unsigned int.
     */
    private static class Type_UINT_32
        extends FennelNumericType
    {
        /**
         * SerialVersionUID created with JDK 1.5 serialver tool.
         */
        private static final long serialVersionUID = 4551603883805108096L;

        Type_UINT_32()
        {
            super("u4", UINT_32_ORDINAL, 0, 4, false, true);
        }

        public FennelAttributeAccessor newAttributeAccessor()
        {
            return new FennelAttributeAccessor.FennelIntAccessor();
        }
    }

    /**
     * Describes a signed long.
     */
    private static class Type_INT_64
        extends FennelNumericType
    {
        /**
         * SerialVersionUID created with JDK 1.5 serialver tool.
         */
        private static final long serialVersionUID = 6188765410255919222L;

        Type_INT_64()
        {
            super("s8", INT_64_ORDINAL, 0, 8, true, true);
        }

        public FennelAttributeAccessor newAttributeAccessor()
        {
            return new FennelAttributeAccessor.FennelLongAccessor();
        }
    }

    /**
     * Describes an unsigned long.
     */
    private static class Type_UINT_64
        extends FennelNumericType
    {
        /**
         * SerialVersionUID created with JDK 1.5 serialver tool.
         */
        private static final long serialVersionUID = -2632732624177829892L;

        Type_UINT_64()
        {
            super("u8", UINT_64_ORDINAL, 0, 8, false, true);
        }

        public FennelAttributeAccessor newAttributeAccessor()
        {
            return new FennelAttributeAccessor.FennelLongAccessor();
        }
    }

    /**
     * Describes a float.
     */
    private static class Type_REAL
        extends FennelNumericType
    {
        /**
         * SerialVersionUID created with JDK 1.5 serialver tool.
         */
        private static final long serialVersionUID = 8925748663583683424L;

        Type_REAL()
        {
            super("r", REAL_ORDINAL, 0, 4, true, false);
        }

        public FennelAttributeAccessor newAttributeAccessor()
        {
            return new FennelAttributeAccessor.FennelIntAccessor();
        }
    }

    /**
     * Describes a double.
     */
    private static class Type_DOUBLE
        extends FennelNumericType
    {
        /**
         * SerialVersionUID created with JDK 1.5 serialver tool.
         */
        private static final long serialVersionUID = -2610530167912464104L;

        public Type_DOUBLE()
        {
            super("d", DOUBLE_ORDINAL, 0, 8, true, false);
        }

        public FennelAttributeAccessor newAttributeAccessor()
        {
            return new FennelAttributeAccessor.FennelLongAccessor();
        }
    }

    /**
     * Describes a boolean.
     */
    private static class Type_BOOL
        extends FennelNumericType
    {
        /**
         * SerialVersionUID created with JDK 1.5 serialver tool.
         */
        private static final long serialVersionUID = 1589041391248552300L;

        public Type_BOOL()
        {
            super("bo", BOOL_ORDINAL, 1, 1, false, true);
        }

        public FennelAttributeAccessor newAttributeAccessor()
        {
            return new FennelAttributeAccessor.FennelBitAccessor();
        }
    }

    /**
     * Describes a fixed-width character string.
     */
    private static class Type_CHAR
        extends FennelType
    {
        /**
         * SerialVersionUID created with JDK 1.5 serialver tool.
         */
        private static final long serialVersionUID = 422697645810009320L;

        Type_CHAR()
        {
            super("c", CHAR_ORDINAL);
        }

        public int getFixedBitCount()
        {
            return 0;
        }

        public int getMinByteCount(int maxWidth)
        {
            return maxWidth;
        }

        public FennelAttributeAccessor newAttributeAccessor()
        {
            return new FennelAttributeAccessor.FennelFixedWidthAccessor();
        }
    }

    /**
     * Describes a fixed-width character string.
     */
    private static class Type_UNICODE_CHAR
        extends FennelType
    {
        /**
         * SerialVersionUID created with JDK 1.5 serialver tool.
         */
        private static final long serialVersionUID = 2829309515586470045L;

        Type_UNICODE_CHAR()
        {
            super("U", UNICODE_CHAR_ORDINAL);
        }

        public int getFixedBitCount()
        {
            return 0;
        }

        public int getAlignmentByteCount(int width)
        {
            return 2;
        }

        public int getMinByteCount(int maxWidth)
        {
            return maxWidth;
        }

        public FennelAttributeAccessor newAttributeAccessor()
        {
            return new FennelAttributeAccessor.FennelFixedWidthAccessor();
        }
    }

    /**
     * Describes a variable-width character string.
     */
    private static class Type_VARCHAR
        extends FennelType
    {
        /**
         * SerialVersionUID created with JDK 1.5 serialver tool.
         */
        private static final long serialVersionUID = 563172148879378524L;

        Type_VARCHAR()
        {
            super("vc", VARCHAR_ORDINAL);
        }

        public int getFixedBitCount()
        {
            return 0;
        }

        public FennelAttributeAccessor newAttributeAccessor()
        {
            return new FennelAttributeAccessor.FennelVarWidthAccessor();
        }
    }

    /**
     * Describes a variable-width UNICODE character string.
     */
    private static class Type_UNICODE_VARCHAR
        extends FennelType
    {
        /**
         * SerialVersionUID created with JDK 1.5 serialver tool.
         */
        private static final long serialVersionUID = -7747093963788655633L;

        Type_UNICODE_VARCHAR()
        {
            super("vU", UNICODE_VARCHAR_ORDINAL);
        }

        public int getFixedBitCount()
        {
            return 0;
        }

        public int getAlignmentByteCount(int width)
        {
            return 2;
        }

        public FennelAttributeAccessor newAttributeAccessor()
        {
            return new FennelAttributeAccessor.FennelVarWidthAccessor();
        }
    }

    /**
     * Describes a fixed-width binary string.
     */
    private static class Type_BINARY
        extends FennelType
    {
        /**
         * SerialVersionUID created with JDK 1.5 serialver tool.
         */
        private static final long serialVersionUID = -2878656484355172778L;

        Type_BINARY()
        {
            super("b", BINARY_ORDINAL);
        }

        public int getFixedBitCount()
        {
            return 0;
        }

        public int getMinByteCount(int maxWidth)
        {
            return maxWidth;
        }

        public FennelAttributeAccessor newAttributeAccessor()
        {
            return new FennelAttributeAccessor.FennelFixedWidthAccessor();
        }
    }

    /**
     * Describes a variable-width binary array.
     */
    private static class Type_VARBINARY
        extends FennelType
    {
        /**
         * SerialVersionUID created with JDK 1.5 serialver tool.
         */
        private static final long serialVersionUID = -8867996175313892900L;

        Type_VARBINARY()
        {
            super("vb", VARBINARY_ORDINAL);
        }

        public int getFixedBitCount()
        {
            return 0;
        }

        public FennelAttributeAccessor newAttributeAccessor()
        {
            return new FennelAttributeAccessor.FennelVarWidthAccessor();
        }
    }
}

// End FennelStandardTypeDescriptor.java
