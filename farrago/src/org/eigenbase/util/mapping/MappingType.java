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
package org.eigenbase.util.mapping;

/**
 * Describes the type of a mapping, from the most general {@link #MultiFunction}
 * (every element in the source and target domain can participate in many
 * mappings) to the most retricted {@link #Bijection} (every element in the
 * source and target domain must be paired with precisely one element in the
 * other domain).
 *
 * <p>Some common types:
 *
 * <ul>
 * <li>A surjection is a mapping if every target has at least one source; also
 * known as an 'onto' mapping.
 * <li>A mapping is a partial function if every source has at most one target.
 * <li>A mapping is a function if every source has precisely one target.
 * <li>An injection is a mapping where a target has at most one source; also
 * somewhat confusingly known as a 'one-to-one' mapping.
 * <li>A bijection is a mapping which is both an injection and a surjection.
 * Every source has precisely one target, and vice versa.
 * </ul>
 *
 * <p>Once you know what type of mapping you want, call {@link
 * Mappings#create(MappingType, int, int)} to create an efficient implementation
 * of that mapping.
 *
 * @author jhyde
 * @version $Id$
 * @since Mar 24, 2006
 */
public enum MappingType
{
    //            ordinal source target function inverse
    //            ======= ====== ====== ======== =================

    /**
     * A bijection is a mapping where every source has a target and every
     * target is a source.
     *
     * <p>The range and domain must have the same cardinality (if they are
     * finite).
     *
     * <p>If the range and domain are the same set, a bijection represents a
     * reordering of elements. For example, f: {0, 1, 2, 3} &rarr; {0, 1, 2, 3},
     * f(0) = 2, f(1) = 1, f(2) = 3, f(3) = 0.
     *
     * <p>Its inverse is also a bijection.
     *
     * <p>Infinite example: f: N &rarr N, f(n) = n - 1
     */
    //                  0      1      1 true     0 Bijection
    Bijection,

    /**
     * A surjection is a mapping where every source has a target, and a target
     * has no more than one source.
     *
     * <p>Example: f: {0, 1, 2} &rarr; {0, 1}, with f(0) = nothing, f(1) = 0,
     * f(2) = 1.
     */
    //                  1   <= 1      1 true     4 InverseSurjection
    Surjection,

    //                  2   >= 1      1 true     8 InverseInjection
    Injection,

    //                  3    any      1 true     12 InverseFunction
    Function,

    /**
     * An inverse surjection has a source for every target, and no source has
     * more than one target.
     */
    //                  4      1   <= 1 partial  1 Surjection
    InverseSurjection,

    /**
     * A partial surjection has no more than one source for any target, and no
     * more than one target for any source.
     *
     * <p>Its inverse is also a partial surjection.
     */
    //                  5   <= 1   <= 1 partial  5 PartialSurjection
    PartialSurjection,

    /**
     * A partial injection has no more than one target for any source, and
     * every source has at least one target.
     *
     * <p>Example: f: {0, 1, 2} &rarr; {0, 1}, with f(0) = nothing, f(1) = 0,
     * f(2) = 0.
     */
    //                  6   >= 1   <= 1 partial  9 InversePartialInjection
    PartialInjection,

    /**
     * A partial function has no more than one target for any source.
     *
     * <p>Example: f: {0, 1, 2} &rarr; {0, 1}, with f(0) = nothing, f(1) = 0,
     * f(2) = 0.
     */
    //                  7    any   <= 1 partial  13 InversePartialFunction
    PartialFunction,

    //                  8      1   >= 1 multi    2 Injection
    InverseInjection,

    //                  9   <= 1   >= 1 multi    6 PartialInjection
    InversePartialInjection,

    //                 10   >= 1   >= 1 multi    10
    Ten,

    //                 11    any   >= 1 multi    14
    Eleven,

    /**
     * An inverse function has a source for every target, but a source might
     * have 0, 1 or more targets.
     *
     * <p>Obeys the constaints {@link MappingType#isMandatorySource()}, {@link
     * MappingType#isSingleSource()}.
     *
     * <p>Similar types:
     *
     * <ul>
     * <li> {@link #InverseSurjection} is stronger (a source may not have
     * multiple targets);
     * <li>{@link #InversePartialFunction} is weaker (a target may have 0 or 1
     * sources).
     * </ul>
     */
    //                 12      1    any multi    3 Function
    InverseFunction,

    //                 13   <= 1    any multi    7 PartialFunction
    InversePartialFunction,

    //                 14   >= 1    any multi    11
    Fourteen,

    //                 15    any    any multi    15 MultiFunction
    MultiFunction;

    private final int inverseOrdinal;

    private MappingType()
    {
        this.inverseOrdinal = ((ordinal() & 3) << 2)
            | ((ordinal() & 12) >> 2);
    }

    public MappingType inverse()
    {
        return MappingType.values()[this.inverseOrdinal];
    }

    /**
     * Returns whether this mapping type is (possibly a weaker form of) a given
     * mapping type.
     *
     * <p>For example, a {@link #Bijection} is a {@link #Function}, but not
     * every {link #Function} is a {@link #Bijection}.
     */
    public boolean isA(MappingType mappingType)
    {
        return (ordinal() & mappingType.ordinal()) == ordinal();
    }

    /**
     * A mapping is a total function if every source has precisely one target.
     */
    public boolean isFunction()
    {
        return (ordinal() & (OptionalTarget | MultipleTarget)) == 0;
    }

    /**
     * A mapping is a partial function if every source has at most one target.
     */
    public boolean isPartialFunction()
    {
        return (ordinal() & MultipleTarget) == 0;
    }

    /**
     * A mapping is a surjection if it is a function and every target has at
     * least one source.
     */
    public boolean isSurjection()
    {
        return (ordinal() & (OptionalTarget | MultipleTarget | OptionalSource))
            == 0;
    }

    /**
     * A mapping is an injection if it is a function and no target has more than
     * one source. (In other words, every source has precisely one target.)
     */
    public boolean isInjection()
    {
        return (ordinal() & (OptionalTarget | MultipleTarget | MultipleSource))
            == 0;
    }

    /**
     * A mapping is a bijection if it is a surjection and it is an injection.
     * (In other words,
     */
    public boolean isBijection()
    {
        return (ordinal()
            & (OptionalTarget | MultipleTarget | OptionalSource
                | MultipleSource)) == 0;
    }

    /**
     * Constraint that every source has at least one target.
     */
    public boolean isMandatoryTarget()
    {
        return !((ordinal() & OptionalTarget) == OptionalTarget);
    }

    /**
     * Constraint that every source has at most one target.
     */
    public boolean isSingleTarget()
    {
        return !((ordinal() & MultipleTarget) == MultipleTarget);
    }

    /**
     * Constraint that every target has at least one source.
     */
    public boolean isMandatorySource()
    {
        return !((ordinal() & OptionalSource) == OptionalSource);
    }

    /**
     * Constraint that every target has at most one source.
     */
    public boolean isSingleSource()
    {
        return !((ordinal() & MultipleSource) == MultipleSource);
    }

    /**
     * Allow less than one source for a given target.
     */
    private static final int OptionalSource = 1;

    /**
     * Allow more than one source for a given target.
     */
    private static final int MultipleSource = 2;

    /**
     * Allow less than one target for a given source.
     */
    private static final int OptionalTarget = 4;

    /**
     * Allow more than one target for a given source.
     */
    private static final int MultipleTarget = 8;
}

// End MappingType.java
