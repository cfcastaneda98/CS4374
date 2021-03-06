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
package org.eigenbase.sarg;

import java.util.*;

import org.eigenbase.reltype.*;
import org.eigenbase.rex.*;
import org.eigenbase.sql.*;


/**
 * SargIntervalExpr represents an expression which can be resolved to a fixed
 * {@link SargInterval}.
 *
 * <p>Null values require special treatment in expressions. Normally, for
 * intervals of any kind, nulls are not considered to be within the domain of
 * search values. This behavior can be modified by setting the {@link
 * SqlNullSemantics} to a value other than the default. This happens implicitly
 * when a point interval is created matching the null value. When null values
 * are considered to be part of the domain, the ordering is defined as for
 * {@link SargInterval}.
 *
 * @author John V. Sichi
 * @version $Id$
 */
public class SargIntervalExpr
    extends SargIntervalBase
    implements SargExpr
{
    //~ Instance fields --------------------------------------------------------

    private SqlNullSemantics nullSemantics;

    //~ Constructors -----------------------------------------------------------

    /**
     * @see SargFactory#newIntervalExpr
     */
    SargIntervalExpr(
        SargFactory factory,
        RelDataType dataType,
        SqlNullSemantics nullSemantics)
    {
        super(factory, dataType);
        this.nullSemantics = nullSemantics;
    }

    //~ Methods ----------------------------------------------------------------

    /**
     * @return null semantics which apply for searches on this interval
     */
    public SqlNullSemantics getNullSemantics()
    {
        return nullSemantics;
    }

    // publicize SargIntervalBase
    public void setPoint(RexNode coordinate)
    {
        super.setPoint(coordinate);
        if (RexLiteral.isNullLiteral(coordinate)) {
            // since they explicitly asked for the null value as a point,
            // adjust the null semantics to match
            if (nullSemantics == SqlNullSemantics.NULL_MATCHES_NOTHING) {
                nullSemantics = SqlNullSemantics.NULL_MATCHES_NULL;
            }
        }
    }

    // publicize SargIntervalBase
    public void setNull()
    {
        super.setNull();
    }

    // publicize SargIntervalBase
    public void setLower(RexNode coordinate, SargStrictness strictness)
    {
        super.setLower(coordinate, strictness);
    }

    // publicize SargIntervalBase
    public void setUpper(RexNode coordinate, SargStrictness strictness)
    {
        super.setUpper(coordinate, strictness);
    }

    // publicize SargIntervalBase
    public void unsetLower()
    {
        super.unsetLower();
    }

    // publicize SargIntervalBase
    public void unsetUpper()
    {
        super.unsetUpper();
    }

    // publicize SargIntervalBase
    public void setUnconstrained()
    {
        super.setUnconstrained();
    }

    // publicize SargIntervalBase
    public void setEmpty()
    {
        super.setEmpty();
    }

    // implement SargExpr
    public String toString()
    {
        String s = super.toString();
        if (nullSemantics == SqlNullSemantics.NULL_MATCHES_NOTHING) {
            // default semantics, so omit them for brevity
            return s;
        } else {
            return s + " " + nullSemantics;
        }
    }

    // implement SargExpr
    public SargIntervalSequence evaluate()
    {
        SargIntervalSequence seq = new SargIntervalSequence();

        // If at least one of the bounds got flipped by overflow, the
        // result is empty.
        if ((lowerBound.getBoundType() != SargBoundType.LOWER)
            || (upperBound.getBoundType() != SargBoundType.UPPER))
        {
            // empty sequence
            return seq;
        }

        // Under the default null semantics, if one of the endpoints is
        // known to be null, the result is empty.
        if ((nullSemantics == SqlNullSemantics.NULL_MATCHES_NOTHING)
            && (lowerBound.isNull() || upperBound.isNull()))
        {
            // empty sequence
            return seq;
        }

        // Copy the endpoints to the new interval.
        SargInterval interval =
            new SargInterval(
                factory,
                getDataType());
        interval.copyFrom(this);

        // Then adjust for null semantics.

        // REVIEW jvs 17-Jan-2006:  For unconstrained intervals, we
        // include null values.  Why is this?

        if ((nullSemantics == SqlNullSemantics.NULL_MATCHES_NOTHING)
            && getDataType().isNullable()
            && (lowerBound.isFinite() || upperBound.isFinite())
            && (!lowerBound.isFinite() || lowerBound.isNull()))
        {
            // The test above says that this is a constrained range
            // with no lower bound (or null for the lower bound).  Since nulls
            // aren't supposed to match anything, adjust the lower bound
            // to exclude null.
            interval.setLower(
                factory.newNullLiteral(),
                SargStrictness.OPEN);
        } else if (nullSemantics == SqlNullSemantics.NULL_MATCHES_ANYTHING) {
            if (!lowerBound.isFinite()
                || lowerBound.isNull()
                || upperBound.isNull())
            {
                // Since null is supposed to match anything, and it
                // is included in the interval, expand the interval to
                // match anything.
                interval.setUnconstrained();
            }
        }

        // NOTE jvs 27-Jan-2006: We don't currently filter out the empty
        // interval here because we rely on being able to create them
        // explicitly.  See related comment in FennelRelUtil.convertSargExpr;
        // if that changes, we could filter out the empty interval here.
        seq.addInterval(interval);

        return seq;
    }

    // implement SargExpr
    public void collectDynamicParams(Set<RexDynamicParam> dynamicParams)
    {
        if (lowerBound.getCoordinate() instanceof RexDynamicParam) {
            dynamicParams.add((RexDynamicParam) lowerBound.getCoordinate());
        }
        if (upperBound.getCoordinate() instanceof RexDynamicParam) {
            dynamicParams.add((RexDynamicParam) upperBound.getCoordinate());
        }
    }

    // implement SargExpr
    public SargIntervalSequence evaluateComplemented()
    {
        SargIntervalSequence originalSeq = evaluate();
        SargIntervalSequence seq = new SargIntervalSequence();

        // Complement of empty set is unconstrained set.
        if (originalSeq.getList().isEmpty()) {
            seq.addInterval(
                new SargInterval(
                    factory,
                    getDataType()));
            return seq;
        }

        assert (originalSeq.getList().size() == 1);
        SargInterval originalInterval = originalSeq.getList().get(0);

        // Complement of universal set is empty set.
        if (originalInterval.isUnconstrained()) {
            return seq;
        }

        // Use null as a lower bound rather than infinity (see
        // http://issues.eigenbase.org/browse/LDB-60).
        // REVIEW jvs 17-Apr-2006:  This assumes NULL_MATCHES_NOTHING
        // semantics.  We've lost the original null semantics
        // flag by now.  Is there ever a case where other null
        // semantics are required here?

        SargInterval interval =
            new SargInterval(
                factory,
                getDataType());
        interval.setLower(
            factory.newNullLiteral(),
            SargStrictness.OPEN);

        if (originalInterval.getUpperBound().isFinite()
            && originalInterval.getLowerBound().isFinite())
        {
            // Complement of a fully bounded range is the union of two
            // disjoint half-bounded ranges.
            interval.setUpper(
                originalInterval.getLowerBound().getCoordinate(),
                originalInterval.getLowerBound().getStrictnessComplement());
            if (!originalInterval.getLowerBound().isNull()) {
                seq.addInterval(interval);
            } else {
                // Don't bother adding an empty interval.
            }

            interval =
                new SargInterval(
                    factory,
                    getDataType());
            interval.setLower(
                originalInterval.getUpperBound().getCoordinate(),
                originalInterval.getUpperBound().getStrictnessComplement());
            seq.addInterval(interval);
        } else if (originalInterval.getLowerBound().isFinite()) {
            // Complement of a half-bounded range is the opposite
            // half-bounded range (with open for closed and vice versa)
            interval.setUpper(
                originalInterval.getLowerBound().getCoordinate(),
                originalInterval.getLowerBound().getStrictnessComplement());
            if (!originalInterval.getLowerBound().isNull()) {
                seq.addInterval(interval);
            } else {
                // Don't bother adding an empty interval.
            }
        } else {
            // Mirror image of previous case.
            assert (originalInterval.getUpperBound().isFinite());
            interval.setLower(
                originalInterval.getUpperBound().getCoordinate(),
                originalInterval.getUpperBound().getStrictnessComplement());
            seq.addInterval(interval);
        }

        return seq;
    }
}

// End SargIntervalExpr.java
