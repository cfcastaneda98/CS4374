/*
// $Id$
// Package org.eigenbase is a class library of data management components.
// Copyright (C) 2005 The Eigenbase Project
// Copyright (C) 2002 SQLstream, Inc.
// Copyright (C) 2009 Dynamo BI Corporation
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
package org.eigenbase.relopt.volcano;

import junit.framework.*;

import openjava.ptree.*;

import org.eigenbase.oj.rel.*;
import org.eigenbase.rel.*;
import org.eigenbase.rel.convert.*;
import org.eigenbase.relopt.*;
import org.eigenbase.reltype.*;
import org.eigenbase.util.*;


/**
 * VolcanoPlannerTraitTest
 *
 * @author Stephan Zuercher
 * @version $Id$
 */
public class VolcanoPlannerTraitTest
    extends TestCase
{
    //~ Static fields/initializers ---------------------------------------------

    /**
     * Private calling convention representing a generic "physical" calling
     * convention.
     */
    private static final CallingConvention PHYS_CALLING_CONVENTION =
        new CallingConvention(
            "PHYS",
            CallingConvention.generateOrdinal(),
            RelNode.class);

    /**
     * Private trait definition for an alternate type of traits.
     */
    private static final AltTraitDef ALT_TRAIT_DEF = new AltTraitDef();

    /**
     * Private alternate trait.
     */
    private static final AltTrait ALT_TRAIT =
        new AltTrait(ALT_TRAIT_DEF, "ALT");

    /**
     * Private alternate trait.
     */
    private static final AltTrait ALT_TRAIT2 =
        new AltTrait(ALT_TRAIT_DEF, "ALT2");

    /**
     * Ordinal count for alternate traits (so they can implement equals() and
     * avoid being canonized into the same trait).
     */
    private static int altTraitOrdinal = 0;

    //~ Constructors -----------------------------------------------------------

    public VolcanoPlannerTraitTest(String name)
    {
        super(name);
    }

    //~ Methods ----------------------------------------------------------------

    public void testDoubleConversion()
    {
        VolcanoPlanner planner = new VolcanoPlanner();

        planner.addRelTraitDef(CallingConventionTraitDef.instance);
        planner.addRelTraitDef(ALT_TRAIT_DEF);

        planner.addRule(new PhysToIteratorConverterRule());
        planner.addRule(
            new AltTraitConverterRule(
                ALT_TRAIT,
                ALT_TRAIT2,
                "AltToAlt2ConverterRule"));
        planner.addRule(new PhysLeafRule());
        planner.addRule(new IterSingleRule());

        RelOptCluster cluster = VolcanoPlannerTest.newCluster(planner);

        NoneLeafRel noneLeafRel = new NoneLeafRel(cluster, "noneLeafRel");
        noneLeafRel.setTraits(noneLeafRel.getTraits().plus(ALT_TRAIT));

        NoneSingleRel noneRel = new NoneSingleRel(cluster, noneLeafRel);
        noneRel.setTraits(noneRel.getTraits().plus(ALT_TRAIT2));

        RelNode convertedRel =
            planner.changeTraits(
                noneRel,
                new RelTraitSet(CallingConvention.ITERATOR, ALT_TRAIT2));

        planner.setRoot(convertedRel);
        RelNode result = planner.chooseDelegate().findBestExp();

        assertTrue(result instanceof IterSingleRel);
        assertEquals(
            CallingConvention.ITERATOR,
            result.getTraits().getTrait(CallingConventionTraitDef.instance));
        assertEquals(
            ALT_TRAIT2,
            result.getTraits().getTrait(ALT_TRAIT_DEF));

        RelNode child = result.getInput(0);
        assertTrue(
            (child instanceof AltTraitConverter)
            || (child instanceof PhysToIteratorConverter));

        child = child.getInput(0);
        assertTrue(
            (child instanceof AltTraitConverter)
            || (child instanceof PhysToIteratorConverter));

        child = child.getInput(0);
        assertTrue(child instanceof PhysLeafRel);
    }

    public void testTraitPropagation()
    {
        VolcanoPlanner planner = new VolcanoPlanner();

        planner.addRelTraitDef(CallingConventionTraitDef.instance);
        planner.addRelTraitDef(ALT_TRAIT_DEF);

        planner.addRule(new PhysToIteratorConverterRule());
        planner.addRule(
            new AltTraitConverterRule(
                ALT_TRAIT,
                ALT_TRAIT2,
                "AltToAlt2ConverterRule"));
        planner.addRule(new PhysLeafRule());
        planner.addRule(new IterSingleRule2());

        RelOptCluster cluster = VolcanoPlannerTest.newCluster(planner);

        NoneLeafRel noneLeafRel = new NoneLeafRel(cluster, "noneLeafRel");
        noneLeafRel.setTraits(noneLeafRel.getTraits().plus(ALT_TRAIT));

        NoneSingleRel noneRel = new NoneSingleRel(cluster, noneLeafRel);
        noneRel.setTraits(noneRel.getTraits().plus(ALT_TRAIT2));

        RelNode convertedRel =
            planner.changeTraits(
                noneRel,
                new RelTraitSet(CallingConvention.ITERATOR, ALT_TRAIT2));

        planner.setRoot(convertedRel);
        RelNode result = planner.chooseDelegate().findBestExp();

        assertTrue(result instanceof IterSingleRel);
        assertEquals(
            CallingConvention.ITERATOR,
            result.getTraits().getTrait(CallingConventionTraitDef.instance));
        assertEquals(
            ALT_TRAIT2,
            result.getTraits().getTrait(ALT_TRAIT_DEF));

        RelNode child = result.getInput(0);
        assertTrue(child instanceof IterSingleRel);
        assertEquals(
            CallingConvention.ITERATOR,
            child.getTraits().getTrait(CallingConventionTraitDef.instance));
        assertEquals(
            ALT_TRAIT2,
            child.getTraits().getTrait(ALT_TRAIT_DEF));

        child = child.getInput(0);
        assertTrue(
            (child instanceof AltTraitConverter)
            || (child instanceof PhysToIteratorConverter));

        child = child.getInput(0);
        assertTrue(
            (child instanceof AltTraitConverter)
            || (child instanceof PhysToIteratorConverter));

        child = child.getInput(0);
        assertTrue(child instanceof PhysLeafRel);
    }

    //~ Inner Classes ----------------------------------------------------------

    private static class AltTrait
        implements RelTrait
    {
        private final AltTraitDef traitDef;
        private final int ordinal;
        private final String description;

        private AltTrait(AltTraitDef traitDef, String description)
        {
            this.traitDef = traitDef;
            this.description = description;
            this.ordinal = altTraitOrdinal++;
        }

        public RelTraitDef getTraitDef()
        {
            return traitDef;
        }

        public boolean equals(Object other)
        {
            if (other == null) {
                return false;
            }

            AltTrait that = (AltTrait) other;
            return this.ordinal == that.ordinal;
        }

        public int hashCode()
        {
            return ordinal;
        }

        public String toString()
        {
            return description;
        }
    }

    private static class AltTraitDef
        extends RelTraitDef
    {
        private MultiMap<RelTrait, Pair<RelTrait, ConverterRule>>
            conversionMap =
                new MultiMap<RelTrait, Pair<RelTrait, ConverterRule>>();

        public Class getTraitClass()
        {
            return AltTrait.class;
        }

        public String getSimpleName()
        {
            return "alt_phys";
        }

        public RelNode convert(
            RelOptPlanner planner,
            RelNode rel,
            RelTrait toTrait,
            boolean allowInfiniteCostConverters)
        {
            RelTrait fromTrait = rel.getTraits().getTrait(this);

            if (conversionMap.containsKey(fromTrait)) {
                for (
                    Pair<RelTrait, ConverterRule> traitAndRule
                    : conversionMap.getMulti(fromTrait))
                {
                    RelTrait trait = traitAndRule.left;
                    ConverterRule rule = traitAndRule.right;

                    if (trait == toTrait) {
                        RelNode converted = rule.convert(rel);
                        if ((converted != null)
                            && (!planner.getCost(converted).isInfinite()
                                || allowInfiniteCostConverters))
                        {
                            return converted;
                        }
                    }
                }
            }

            return null;
        }

        public boolean canConvert(
            RelOptPlanner planner,
            RelTrait fromTrait,
            RelTrait toTrait)
        {
            if (conversionMap.containsKey(fromTrait)) {
                for (
                    Pair<RelTrait, ConverterRule> traitAndRule
                    : conversionMap.getMulti(fromTrait))
                {
                    if (traitAndRule.left == toTrait) {
                        return true;
                    }
                }
            }

            return false;
        }

        public void registerConverterRule(
            RelOptPlanner planner,
            ConverterRule converterRule)
        {
            if (!converterRule.isGuaranteed()) {
                return;
            }

            RelTrait fromTrait = converterRule.getInTrait();
            RelTrait toTrait = converterRule.getOutTrait();

            conversionMap.putMulti(
                fromTrait,
                new Pair<RelTrait, ConverterRule>(toTrait, converterRule));
        }
    }

    private static abstract class TestLeafRel
        extends AbstractRelNode
    {
        private String label;

        protected TestLeafRel(
            RelOptCluster cluster,
            RelTraitSet traits,
            String label)
        {
            super(cluster, traits);
            this.label = label;
        }

        public String getLabel()
        {
            return label;
        }

        // implement RelNode
        public TestLeafRel clone()
        {
            return this;
        }

        // implement RelNode
        public RelOptCost computeSelfCost(RelOptPlanner planner)
        {
            return planner.makeInfiniteCost();
        }

        // implement RelNode
        protected RelDataType deriveRowType()
        {
            return getCluster().getTypeFactory().createStructType(
                new RelDataType[] {
                    getCluster().getTypeFactory().createJavaType(Void.TYPE)
                },
                new String[] { "this" });
        }

        public void explain(RelOptPlanWriter pw)
        {
            pw.explain(
                this,
                new String[] { "label" },
                new Object[] { label });
        }
    }

    private static class NoneLeafRel
        extends TestLeafRel
    {
        protected NoneLeafRel(
            RelOptCluster cluster,
            String label)
        {
            super(
                cluster,
                CallingConvention.NONE.singletonSet,
                label);
        }
    }

    private static class PhysLeafRel
        extends TestLeafRel
    {
        PhysLeafRel(
            RelOptCluster cluster,
            String label)
        {
            super(
                cluster,
                PHYS_CALLING_CONVENTION.singletonSet,
                label);
        }

        // implement RelNode
        public RelOptCost computeSelfCost(RelOptPlanner planner)
        {
            return planner.makeTinyCost();
        }

        // TODO: SWZ Implement clone?
    }

    private static abstract class TestSingleRel
        extends SingleRel
    {
        protected TestSingleRel(
            RelOptCluster cluster,
            RelTraitSet traits,
            RelNode child)
        {
            super(cluster, traits, child);
        }

        // implement RelNode
        public RelOptCost computeSelfCost(RelOptPlanner planner)
        {
            return planner.makeInfiniteCost();
        }

        // implement RelNode
        protected RelDataType deriveRowType()
        {
            return getChild().getRowType();
        }

        // TODO: SWZ Implement clone?
    }

    private static class NoneSingleRel
        extends TestSingleRel
    {
        protected NoneSingleRel(
            RelOptCluster cluster,
            RelNode child)
        {
            super(
                cluster,
                CallingConvention.NONE.singletonSet,
                child);
        }

        // implement RelNode
        public NoneSingleRel clone()
        {
            NoneSingleRel clone =
                new NoneSingleRel(
                    getCluster(),
                    getChild());
            clone.inheritTraitsFrom(this);
            return clone;
        }
    }

    private static class IterSingleRel
        extends TestSingleRel
        implements JavaRel
    {
        public IterSingleRel(RelOptCluster cluster, RelNode child)
        {
            super(
                cluster,
                CallingConvention.ITERATOR.singletonSet,
                child);
        }

        // implement RelNode
        public RelOptCost computeSelfCost(RelOptPlanner planner)
        {
            return planner.makeTinyCost();
        }

        public IterSingleRel clone()
        {
            IterSingleRel clone =
                new IterSingleRel(
                    getCluster(),
                    getInput(0));
            clone.inheritTraitsFrom(this);
            return clone;
        }

        public ParseTree implement(JavaRelImplementor implementor)
        {
            return null;
        }
    }

    private static class PhysLeafRule
        extends RelOptRule
    {
        PhysLeafRule()
        {
            super(new RelOptRuleOperand(NoneLeafRel.class, ANY));
        }

        // implement RelOptRule
        public CallingConvention getOutConvention()
        {
            return PHYS_CALLING_CONVENTION;
        }

        // implement RelOptRule
        public void onMatch(RelOptRuleCall call)
        {
            NoneLeafRel leafRel = (NoneLeafRel) call.rels[0];
            call.transformTo(
                new PhysLeafRel(
                    leafRel.getCluster(),
                    leafRel.getLabel()));
        }
    }

    private static class IterSingleRule
        extends RelOptRule
    {
        IterSingleRule()
        {
            super(new RelOptRuleOperand(NoneSingleRel.class, ANY));
        }

        // implement RelOptRule
        public CallingConvention getOutConvention()
        {
            return CallingConvention.ITERATOR;
        }

        public RelTrait getOutTrait()
        {
            return getOutConvention();
        }

        // implement RelOptRule
        public void onMatch(RelOptRuleCall call)
        {
            NoneSingleRel rel = (NoneSingleRel) call.rels[0];

            RelNode converted =
                convert(
                    rel.getInput(0),
                    rel.getTraits().plus(getOutTrait()));

            call.transformTo(
                new IterSingleRel(
                    rel.getCluster(),
                    converted));
        }
    }

    private static class IterSingleRule2
        extends RelOptRule
    {
        IterSingleRule2()
        {
            super(new RelOptRuleOperand(NoneSingleRel.class, ANY));
        }

        // implement RelOptRule
        public CallingConvention getOutConvention()
        {
            return CallingConvention.ITERATOR;
        }

        public RelTrait getOutTrait()
        {
            return getOutConvention();
        }

        // implement RelOptRule
        public void onMatch(RelOptRuleCall call)
        {
            NoneSingleRel rel = (NoneSingleRel) call.rels[0];

            RelNode converted =
                convert(
                    rel.getInput(0),
                    rel.getTraits().plus(getOutTrait()));

            IterSingleRel child =
                new IterSingleRel(
                    rel.getCluster(),
                    converted);

            call.transformTo(
                new IterSingleRel(
                    rel.getCluster(),
                    child));
        }
    }

    private static class AltTraitConverterRule
        extends ConverterRule
    {
        private final RelTrait toTrait;

        private AltTraitConverterRule(
            AltTrait fromTrait,
            AltTrait toTrait,
            String description)
        {
            super(
                RelNode.class,
                fromTrait,
                toTrait,
                description);

            this.toTrait = toTrait;
        }

        public RelNode convert(RelNode rel)
        {
            return new AltTraitConverter(
                rel.getCluster(),
                rel,
                toTrait);
        }

        public boolean isGuaranteed()
        {
            return true;
        }
    }

    private static class AltTraitConverter
        extends ConverterRelImpl
    {
        private final RelTrait toTrait;

        private AltTraitConverter(
            RelOptCluster cluster,
            RelNode child,
            RelTrait toTrait)
        {
            super(
                cluster,
                toTrait.getTraitDef(),
                child.getTraits().plus(toTrait),
                child);

            this.toTrait = toTrait;
        }

        // override Object (public, does not throw CloneNotSupportedException)
        public AltTraitConverter clone()
        {
            AltTraitConverter clone =
                new AltTraitConverter(
                    getCluster(),
                    getInput(0),
                    toTrait);
            clone.inheritTraitsFrom(this);
            return clone;
        }
    }

    private static class PhysToIteratorConverterRule
        extends ConverterRule
    {
        public PhysToIteratorConverterRule()
        {
            super(
                RelNode.class,
                PHYS_CALLING_CONVENTION,
                CallingConvention.ITERATOR,
                "PhysToIteratorRule");
        }

        public RelNode convert(RelNode rel)
        {
            return new PhysToIteratorConverter(
                rel.getCluster(),
                rel);
        }
    }

    private static class PhysToIteratorConverter
        extends ConverterRelImpl
    {
        public PhysToIteratorConverter(
            RelOptCluster cluster,
            RelNode child)
        {
            super(
                cluster,
                CallingConventionTraitDef.instance,
                child.getTraits().plus(CallingConvention.ITERATOR),
                child);
        }

        // implement RelNode
        public PhysToIteratorConverter clone()
        {
            PhysToIteratorConverter clone =
                new PhysToIteratorConverter(
                    getCluster(),
                    getChild());
            clone.inheritTraitsFrom(this);
            return clone;
        }
    }
}

// End VolcanoPlannerTraitTest.java
