/*
// $Id$
// Farrago is an extensible data management system.
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
package net.sf.farrago.test;

import java.util.*;

import junit.framework.*;

import net.sf.farrago.query.*;
import net.sf.farrago.session.*;

import org.eigenbase.rel.*;
import org.eigenbase.rel.metadata.*;
import org.eigenbase.rel.rules.*;
import org.eigenbase.relopt.*;
import org.eigenbase.relopt.hep.*;


/**
 * FarragoMetadataTest tests the relational expression metadata queries that
 * require additional sql statement support in order to test, above and beyond
 * what can be tested in {@link org.eigenbase.test.RelMetadataTest}.
 *
 * @author Zelaine Fong
 * @version $Id$
 */
public class FarragoMetadataTest
    extends FarragoSqlToRelTestBase
{
    //~ Static fields/initializers ---------------------------------------------

    private static boolean doneStaticSetup;

    private static final double EPSILON = 1.0e-5;

    private static final double TAB_ROWCOUNT = 100.0;

    private static final double DEFAULT_EQUAL_SELECTIVITY = 0.15;

    private static final double DEFAULT_EQUAL_SELECTIVITY_SQUARED =
        DEFAULT_EQUAL_SELECTIVITY * DEFAULT_EQUAL_SELECTIVITY;

    private static final double DEFAULT_COMP_SELECTIVITY = 0.5;

    //~ Instance fields --------------------------------------------------------

    private HepProgram program;

    private RelNode rootRel;

    //~ Constructors -----------------------------------------------------------

    /**
     * Creates a new FarragoMetadataTest object.
     *
     * @param testName JUnit test name
     *
     * @throws Exception .
     */
    public FarragoMetadataTest(String testName)
        throws Exception
    {
        super(testName);
    }

    //~ Methods ----------------------------------------------------------------

    // implement TestCase
    public static Test suite()
    {
        return wrappedSuite(FarragoMetadataTest.class);
    }

    // implement TestCase
    protected void setUp()
        throws Exception
    {
        super.setUp();
        if (doneStaticSetup) {
            localSetUp();
            return;
        }
        doneStaticSetup = true;

        stmt.executeUpdate(
            "create schema farrago_metadata");
        stmt.executeUpdate(
            "set schema 'farrago_metadata'");

        stmt.executeUpdate(
            "create table tab("
            + "c0 int,"
            + "c1 int not null,"
            + "c2 int not null,"
            + "c3 int,"
            + "c4 int,"
            + "constraint primkey primary key(c0),"
            + "constraint unique_notnull unique(c1, c2),"
            + "constraint unique_null unique(c2, c3))");
        stmt.executeUpdate(
            "create index idx on tab(c4)");

        localSetUp();
    }

    public void tearDown()
        throws Exception
    {
        localTearDown();
        super.tearDown();
    }

    private void localSetUp()
    {
        repos.beginReposSession();
        repos.beginReposTxn(false);
    }

    private void localTearDown()
    {
        repos.endReposTxn(false);
        repos.endReposSession();
    }

    protected void checkAbstract(
        FarragoPreparingStmt stmt,
        RelNode relBefore)
        throws Exception
    {
        RelOptPlanner planner = stmt.getPlanner();
        planner.setRoot(relBefore);

        // NOTE jvs 11-Apr-2006: This is a little iffy, because the
        // superclass is going to yank a lot out from under us when we return,
        // but then we're going to keep using rootRel after that.  Seems
        // to work, but...
        rootRel = planner.findBestExp();
    }

    private void transformQueryWithoutImplementation(
        HepProgram program,
        String sql)
        throws Exception
    {
        this.program = program;

        String explainQuery = "EXPLAIN PLAN WITHOUT IMPLEMENTATION FOR " + sql;

        checkQuery(explainQuery);
    }

    private void transformQuery(
        HepProgram program,
        String sql)
        throws Exception
    {
        this.program = program;

        String explainQuery = "EXPLAIN PLAN FOR " + sql;

        checkQuery(explainQuery);
    }

    protected void initPlanner(FarragoPreparingStmt stmt)
    {
        FarragoSessionPlanner planner =
            new FarragoTestPlanner(
                program,
                stmt)
            {
                // TODO jvs 11-Apr-2006: eliminate this once we switch to Hep
                // permanently for LucidDB; this is to make sure that
                // LoptMetadataProvider gets used for the duration of this test
                // regardless of the LucidDbSessionFactory.USE_HEP flag setting.

                // implement RelOptPlanner
                public void registerMetadataProviders(
                    ChainedRelMetadataProvider chain)
                {
                    super.registerMetadataProviders(chain);
                }
            };
        stmt.setPlanner(planner);
    }

    private void checkPopulation(
        String sql,
        BitSet groupKey,
        Double expected)
        throws Exception
    {
        HepProgramBuilder programBuilder = new HepProgramBuilder();
        transformQuery(
            programBuilder.createProgram(),
            sql);

        Double result =
            RelMetadataQuery.getPopulationSize(
                rootRel,
                groupKey);
        if (expected != null) {
            assertEquals(
                expected,
                result.doubleValue());
        } else {
            assertEquals(expected, null);
        }
    }

    public void testPopulationTabPrimary()
        throws Exception
    {
        BitSet groupKey = new BitSet();

        // c0 has a primary key on it
        groupKey.set(0);
        groupKey.set(4);
        double expected = TAB_ROWCOUNT;
        checkPopulation("select * from tab", groupKey, expected);
    }

    public void testPopulationTabUniqueNotNull()
        throws Exception
    {
        BitSet groupKey = new BitSet();

        // c1, c2 have a unique constraint on them
        groupKey.set(1);
        groupKey.set(2);
        groupKey.set(3);
        double expected = TAB_ROWCOUNT;
        checkPopulation("select * from tab", groupKey, expected);
    }

    public void testPopulationTabUniqueNull()
        throws Exception
    {
        BitSet groupKey = new BitSet();

        // c2, c3 have a unique constraint on them, but c3 is null, so the
        // result should be null
        groupKey.set(2);
        groupKey.set(3);
        checkPopulation("select * from tab", groupKey, null);
    }

    public void testPopulationFilter()
        throws Exception
    {
        BitSet groupKey = new BitSet();

        // c1, c2 have a unique constraint on them
        groupKey.set(1);
        groupKey.set(2);
        groupKey.set(3);
        double expected = TAB_ROWCOUNT;
        checkPopulation(
            "select * from tab where c4 = 1",
            groupKey,
            expected);
    }

    public void testPopulationSort()
        throws Exception
    {
        BitSet groupKey = new BitSet();

        // c0 has a primary key on it
        groupKey.set(0);
        groupKey.set(4);
        double expected = TAB_ROWCOUNT;
        checkPopulation(
            "select * from tab order by c4",
            groupKey,
            expected);
    }

    public void testPopulationJoin()
        throws Exception
    {
        // this test will test both joins and semijoins
        HepProgramBuilder programBuilder = new HepProgramBuilder();
        programBuilder.addRuleInstance(PushFilterPastJoinRule.instance);
        programBuilder.addRuleInstance(AddRedundantSemiJoinRule.instance);
        transformQuery(
            programBuilder.createProgram(),
            "select * from tab t1, tab t2 where t1.c4 = t2.c4");
        BitSet groupKey = new BitSet();

        // primary key on c0, and unique constraint on c1, c2; set the mask
        // so c0 originates from t1 and c1, c2 originate from t2
        groupKey.set(0);
        groupKey.set(5 + 1);
        groupKey.set(5 + 2);
        Double result =
            RelMetadataQuery.getPopulationSize(
                rootRel,
                groupKey);
        double expected =
            RelMdUtil.numDistinctVals(
                TAB_ROWCOUNT * TAB_ROWCOUNT,
                TAB_ROWCOUNT * TAB_ROWCOUNT * DEFAULT_EQUAL_SELECTIVITY);
        assertEquals(
            expected,
            result.doubleValue());
    }

    public void testPopulationUnion()
        throws Exception
    {
        BitSet groupKey = new BitSet();
        groupKey.set(0);
        double expected = 2 * TAB_ROWCOUNT;
        checkPopulation(
            "select * from (select * from tab union all select * from tab)",
            groupKey,
            expected);
    }

    public void testPopulationAgg()
        throws Exception
    {
        BitSet groupKey = new BitSet();
        groupKey.set(0);
        groupKey.set(1);
        double expected = TAB_ROWCOUNT;
        checkPopulation(
            "select c0, count(*) from tab group by c0",
            groupKey,
            expected);
    }

    private void checkUniqueKeys(
        String sql,
        Set<BitSet> expected,
        Set<BitSet> nonUniqueKeys,
        Boolean nonUniqueExpected)
        throws Exception
    {
        HepProgramBuilder programBuilder = new HepProgramBuilder();
        transformQuery(
            programBuilder.createProgram(),
            sql);

        Set<BitSet> result = RelMetadataQuery.getUniqueKeys(rootRel);
        assertTrue(result.equals(expected));

        checkColumnUniqueness(expected, true);
        checkColumnUniqueness(nonUniqueKeys, nonUniqueExpected);
    }

    private void checkColumnUniqueness(Set<BitSet> keySet, Boolean expected)
    {
        for (BitSet key : keySet) {
            Boolean result = RelMetadataQuery.areColumnsUnique(rootRel, key);
            if (expected == null) {
                assertTrue(result == null);
            } else {
                assertTrue(result.equals(expected));
            }
        }
    }

    public void testUniqueKeysTab()
        throws Exception
    {
        Set<BitSet> expected = new HashSet<BitSet>();

        BitSet primKey = new BitSet();
        primKey.set(0);
        expected.add(primKey);

        BitSet uniqKey = new BitSet();
        uniqKey.set(1);
        uniqKey.set(2);
        expected.add(uniqKey);

        // this test case tests project, sort, filter, and table
        checkUniqueKeys(
            "select * from tab where c0 = 1 order by c1",
            expected,
            new HashSet<BitSet>(),
            null);
    }

    public void testUniqueKeysProj1()
        throws Exception
    {
        Set<BitSet> expected = new HashSet<BitSet>();

        BitSet primKey = new BitSet();
        primKey.set(1);
        expected.add(primKey);

        Set<BitSet> nonUniqueKey = new HashSet<BitSet>();
        BitSet key = new BitSet();
        key.set(0);

        // this test case tests project, sort, filter, and table
        checkUniqueKeys(
            "select c1, c0 from tab where c0 = 1 order by c1",
            expected,
            nonUniqueKey,
            false);
    }

    public void testUniqueKeysProj2()
        throws Exception
    {
        Set<BitSet> expected = new HashSet<BitSet>();

        BitSet uniqKey = new BitSet();
        uniqKey.set(0);
        uniqKey.set(2);
        expected.add(uniqKey);

        Set<BitSet> nonUniqueKey = new HashSet<BitSet>();
        BitSet key = new BitSet();
        key.set(1);

        // this test case tests project, sort, filter, and table
        checkUniqueKeys(
            "select c2, c3, c1 from tab where c0 = 1 order by c1",
            expected,
            nonUniqueKey,
            false);
    }

    public void testUniqueKeysProj3()
        throws Exception
    {
        Set<BitSet> expected = new HashSet<BitSet>();

        BitSet uniqKey = new BitSet();
        uniqKey.set(0);
        uniqKey.set(2);
        expected.add(uniqKey);

        Set<BitSet> nonUniqueKey = new HashSet<BitSet>();
        BitSet key = new BitSet();
        key.set(1);
        nonUniqueKey.add(key);

        // this test case tests project, sort, filter, and table
        checkUniqueKeys(
            "select c2, c3 + c0, c1 from tab where c0 = 1 order by c1",
            expected,
            nonUniqueKey,
            null);
    }

    public void testUniqueKeysAgg()
        throws Exception
    {
        Set<BitSet> expected = new HashSet<BitSet>();

        BitSet groupKey = new BitSet();
        groupKey.set(0);
        groupKey.set(1);
        expected.add(groupKey);

        Set<BitSet> nonUniqueKey = new HashSet<BitSet>();
        BitSet key = new BitSet();
        key.set(2);
        nonUniqueKey.add(key);

        checkUniqueKeys(
            "select c2, c4, count(*) from tab group by c2, c4",
            expected,
            nonUniqueKey,
            false);
    }

    public void testUniqueKeysFullTableAgg()
        throws Exception
    {
        String sql = "select count(*) from tab";
        HepProgramBuilder programBuilder = new HepProgramBuilder();
        transformQuery(
            programBuilder.createProgram(),
            sql);
        BitSet emptyKey = new BitSet();
        boolean result =
            RelMdUtil.areColumnsDefinitelyUnique(
                rootRel,
                emptyKey);
        assertTrue(result);
    }

    public void testUniqueKeysCorrelateRel()
        throws Exception
    {
        Set<BitSet> expected = new HashSet<BitSet>();

        BitSet groupKey = new BitSet();
        groupKey.set(0);
        expected.add(groupKey);

        HepProgramBuilder programBuilder = new HepProgramBuilder();
        transformQueryWithoutImplementation(
            programBuilder.createProgram(),
            "select t2.c0, (select sum(t1.c0) from tab t1 where t1.c1 = t2.c2) from tab t2");

        Set<BitSet> result = RelMetadataQuery.getUniqueKeys(rootRel);
        assertTrue(result.equals(expected));

        checkColumnUniqueness(expected, true);
    }

    public void testUniqueKeysWhenNullsFiltered()
        throws Exception
    {
        Set<BitSet> expected = new HashSet<BitSet>();

        BitSet primKey = new BitSet();
        primKey.set(0);
        expected.add(primKey);

        BitSet uniqKey = new BitSet();
        uniqKey.set(1);
        uniqKey.set(2);
        expected.add(uniqKey);

        uniqKey = new BitSet();
        uniqKey.set(2);
        uniqKey.set(3);
        expected.add(uniqKey);

        HepProgramBuilder programBuilder = new HepProgramBuilder();
        transformQuery(
            programBuilder.createProgram(),
            "select * from tab where c2 is not null and c3 is not null "
            + "order by c1");

        Set<BitSet> result =
            RelMetadataQuery.getUniqueKeys(rootRel, true);
        assertTrue(result.equals(expected));

        for (BitSet key : expected) {
            Boolean res =
                RelMetadataQuery.areColumnsUnique(rootRel, key, true);
            assertTrue(res.booleanValue());
        }
    }

    public void testAreColumnsUniqueWithLiteral()
        throws Exception
    {
        Set<BitSet> expected = new HashSet<BitSet>();

        BitSet primKey = new BitSet();
        primKey.set(0);
        primKey.set(5);
        expected.add(primKey);

        BitSet uniqKey = new BitSet();
        uniqKey.set(1);
        uniqKey.set(2);
        uniqKey.set(5);
        expected.add(uniqKey);

        HepProgramBuilder programBuilder = new HepProgramBuilder();
        transformQuery(
            programBuilder.createProgram(),
            "select *, true from tab where c0 = 1 order by c1");

        // Make sure the key sets are still unique even when they include a
        // literal column (offset 5).
        checkColumnUniqueness(expected, true);
    }

    public void testAreColumnsUniqueWithCast()
        throws Exception
    {
        Set<BitSet> expected = new HashSet<BitSet>();

        BitSet uniqKey = new BitSet();
        uniqKey.set(0);
        uniqKey.set(1);
        expected.add(uniqKey);

        // Make sure the cast to remove non nullability does not affect
        // uniqueness of the column.
        HepProgramBuilder programBuilder = new HepProgramBuilder();
        transformQuery(
            programBuilder.createProgram(),
            "select c1, cast(c2 as int) from tab where "
            + " c1 is not null and c2 is not null "
            + "order by c1");

        Boolean res =
            RelMetadataQuery.areColumnsUnique(rootRel, uniqKey, true);
        assertTrue(res.booleanValue());
    }

    private void checkUniqueKeysJoin(
        String sql,
        Set<BitSet> expected,
        Set<BitSet> nonUniqueKeySet)
        throws Exception
    {
        // tests that call this method will test both joins and semijoins
        HepProgramBuilder programBuilder = new HepProgramBuilder();
        programBuilder.addRuleInstance(PushFilterPastJoinRule.instance);
        programBuilder.addRuleInstance(AddRedundantSemiJoinRule.instance);
        transformQuery(
            programBuilder.createProgram(),
            sql);

        Set<BitSet> result = RelMetadataQuery.getUniqueKeys(rootRel);
        assertTrue(result.equals(expected));

        checkColumnUniqueness(expected, true);
        checkColumnUniqueness(nonUniqueKeySet, false);
    }

    private void addConcatUniqueKeys(
        Set<BitSet> keySet,
        Set<BitSet> nonUniqueKeySet)
    {
        // add concat unique keys
        // left: 0, (1, 2)
        // right 0, (1, 2)
        // left field length == 5
        // concatenated unqiue keys are
        // (0, 5), (0, 6, 7), (1, 2, 5), (1, 2, 6, 7)
        BitSet keys = new BitSet();
        keys.set(0);
        keys.set(5 + 0);
        keySet.add(keys);

        keys = new BitSet();
        keys.set(0);
        keys.set(5 + 1);
        keys.set(5 + 2);
        keySet.add(keys);

        keys = new BitSet();
        keys.set(1);
        keys.set(2);
        keys.set(5 + 0);
        keySet.add(keys);

        keys = new BitSet();
        keys.set(1);
        keys.set(2);
        keys.set(5 + 1);
        keys.set(5 + 2);
        keySet.add(keys);

        // put together a set of keys that aren't unique
        // (1, 6), (2, 3), (8)
        keys = new BitSet();
        keys.set(1);
        keys.set(6);
        nonUniqueKeySet.add(keys);

        keys = new BitSet();
        keys.set(2);
        keys.set(3);
        nonUniqueKeySet.add(keys);

        keys = new BitSet();
        keys.set(8);
        nonUniqueKeySet.add(keys);
    }

    public void testUniqueKeysJoinLeft()
        throws Exception
    {
        Set<BitSet> expected = new HashSet<BitSet>();

        // left side has a unique join key, so the right side unique keys
        // should be returned
        BitSet keys = new BitSet();
        keys.set(5 + 0);
        expected.add(keys);

        keys = new BitSet();
        keys.set(5 + 1);
        keys.set(5 + 2);
        expected.add(keys);

        Set<BitSet> nonUniqueKeySet = new HashSet<BitSet>();
        keys = new BitSet();
        keys.set(0);
        nonUniqueKeySet.add(keys);

        keys = new BitSet();
        keys.set(1);
        keys.set(2);
        nonUniqueKeySet.add(keys);

        addConcatUniqueKeys(expected, nonUniqueKeySet);

        checkUniqueKeysJoin(
            "select * from tab t1, tab t2 where t1.c0 = t2.c3",
            expected,
            nonUniqueKeySet);
    }

    public void testUniqueKeysJoinRight()
        throws Exception
    {
        Set<BitSet> expected = new HashSet<BitSet>();

        // right side has a unique join key, so the left side unique keys
        // should be returned
        BitSet keys = new BitSet();
        keys.set(0);
        expected.add(keys);

        keys = new BitSet();
        keys.set(1);
        keys.set(2);
        expected.add(keys);

        Set<BitSet> nonUniqueKeySet = new HashSet<BitSet>();
        keys = new BitSet();
        keys.set(5);
        nonUniqueKeySet.add(keys);

        keys = new BitSet();
        keys.set(6);
        keys.set(7);
        nonUniqueKeySet.add(keys);

        addConcatUniqueKeys(expected, nonUniqueKeySet);

        checkUniqueKeysJoin(
            "select * from tab t1, tab t2 where t1.c3 = t2.c1 and t1.c4 = t2.c2",
            expected,
            nonUniqueKeySet);
    }

    public void testUniqueKeysJoinNotUnique()
        throws Exception
    {
        // no equijoins on unique keys so there should be no unique keys
        // returned as a result of the join
        Set<BitSet> expected = new HashSet<BitSet>();

        Set<BitSet> nonUniqueKeySet = new HashSet<BitSet>();
        BitSet keys = new BitSet();
        keys = new BitSet();
        keys.set(0);
        nonUniqueKeySet.add(keys);

        keys = new BitSet();
        keys.set(1);
        keys.set(2);
        nonUniqueKeySet.add(keys);

        keys = new BitSet();
        keys.set(5);
        nonUniqueKeySet.add(keys);

        keys = new BitSet();
        keys.set(6);
        keys.set(7);
        nonUniqueKeySet.add(keys);

        addConcatUniqueKeys(expected, nonUniqueKeySet);

        checkUniqueKeysJoin(
            "select * from tab t1, tab t2 where t1.c3 = t2.c3",
            expected,
            nonUniqueKeySet);
    }

    public void testUniqueKeysCartesianProduct()
        throws Exception
    {
        // no equijoins on unique keys so there should be no unique keys
        // returned as a result of the join
        Set<BitSet> expected = new HashSet<BitSet>();

        Set<BitSet> nonUniqueKeySet = new HashSet<BitSet>();
        BitSet keys = new BitSet();
        keys = new BitSet();
        keys.set(0);
        nonUniqueKeySet.add(keys);

        keys = new BitSet();
        keys.set(1);
        keys.set(2);
        nonUniqueKeySet.add(keys);

        keys = new BitSet();
        keys.set(5);
        nonUniqueKeySet.add(keys);

        keys = new BitSet();
        keys.set(6);
        keys.set(7);
        nonUniqueKeySet.add(keys);

        addConcatUniqueKeys(expected, nonUniqueKeySet);

        checkUniqueKeysJoin(
            "select * from tab t1, tab t2",
            expected,
            nonUniqueKeySet);
    }

    public void testUniqueKeysLeftOuterJoin()
        throws Exception
    {
        // left side has a unique join key but the right hand side is null
        // generating, so no unique keys should be returned as a result of
        // the join

        Set<BitSet> expected = new HashSet<BitSet>();

        Set<BitSet> nonUniqueKeySet = new HashSet<BitSet>();
        BitSet keys = new BitSet();
        keys.set(5);
        nonUniqueKeySet.add(keys);

        keys = new BitSet();
        keys.set(6);
        keys.set(7);
        nonUniqueKeySet.add(keys);

        addConcatUniqueKeys(expected, nonUniqueKeySet);

        checkUniqueKeysJoin(
            "select * from tab t1 left outer join tab t2 on t1.c0 = t2.c3",
            expected,
            nonUniqueKeySet);
    }

    public void testUniqueKeysRightOuterJoin()
        throws Exception
    {
        // right side has a unique join key but the left hand side is null
        // generating, so no unique keys should be returned as a result of
        // the join
        Set<BitSet> expected = new HashSet<BitSet>();

        Set<BitSet> nonUniqueKeySet = new HashSet<BitSet>();
        BitSet keys = new BitSet();
        keys.set(0);
        nonUniqueKeySet.add(keys);

        keys = new BitSet();
        keys.set(1);
        keys.set(2);
        nonUniqueKeySet.add(keys);

        addConcatUniqueKeys(expected, nonUniqueKeySet);

        checkUniqueKeysJoin(
            "select * from tab t1 right outer join tab t2 "
            + "on t1.c3 = t2.c1 and t1.c4 = t2.c2",
            expected,
            nonUniqueKeySet);
    }

    private void checkDistinctRowCount(
        RelNode rel,
        BitSet groupKey,
        Double expected)
    {
        Double result =
            RelMetadataQuery.getDistinctRowCount(
                rel,
                groupKey,
                null);
        if (expected == null) {
            assertTrue(result == null);
        } else {
            assertTrue(result != null);
            assertEquals(
                expected,
                result.doubleValue(),
                EPSILON);
        }
    }

    public void testDistinctRowCountFilter()
        throws Exception
    {
        HepProgramBuilder programBuilder = new HepProgramBuilder();
        transformQuery(
            programBuilder.createProgram(),
            "select * from tab where c1 = 1");
        BitSet groupKey = new BitSet();
        groupKey.set(0);
        double expected = TAB_ROWCOUNT * DEFAULT_EQUAL_SELECTIVITY;
        checkDistinctRowCount(rootRel, groupKey, expected);
    }

    public void testDistinctRowCountSort()
        throws Exception
    {
        HepProgramBuilder programBuilder = new HepProgramBuilder();
        transformQuery(
            programBuilder.createProgram(),
            "select * from tab where c1 = 1 order by c2");
        BitSet groupKey = new BitSet();
        groupKey.set(0);
        double expected = TAB_ROWCOUNT * DEFAULT_EQUAL_SELECTIVITY;
        checkDistinctRowCount(rootRel, groupKey, expected);
    }

    public void testDistinctRowCountUnion()
        throws Exception
    {
        HepProgramBuilder programBuilder = new HepProgramBuilder();
        transformQuery(
            programBuilder.createProgram(),
            "select * from (select * from tab union all select * from tab) "
            + "where c1 = 10");
        BitSet groupKey = new BitSet();
        groupKey.set(0);

        double expected = 2 * TAB_ROWCOUNT * DEFAULT_EQUAL_SELECTIVITY;

        checkDistinctRowCount(rootRel, groupKey, expected);
    }

    public void testDistinctRowCountAgg()
        throws Exception
    {
        HepProgramBuilder programBuilder = new HepProgramBuilder();
        transformQuery(
            programBuilder.createProgram(),
            "select c0, count(*) from tab where c0 > 0 group by c0 "
            + "having count(*) = 0");
        BitSet groupKey = new BitSet();
        groupKey.set(0);
        groupKey.set(1);

        // number of distinct values from applying the filter and
        // having clause
        double expected =
            TAB_ROWCOUNT * DEFAULT_COMP_SELECTIVITY
            * DEFAULT_EQUAL_SELECTIVITY;
        checkDistinctRowCount(rootRel, groupKey, expected);
    }

    public void testDistinctRowCountJoin()
        throws Exception
    {
        HepProgramBuilder programBuilder = new HepProgramBuilder();
        programBuilder.addRuleInstance(PushFilterPastJoinRule.instance);
        programBuilder.addRuleInstance(AddRedundantSemiJoinRule.instance);
        transformQuery(
            programBuilder.createProgram(),
            "select * from tab t1, tab t2 where t1.c0 = t2.c0 and t2.c0 = 1");
        BitSet groupKey = new BitSet();
        groupKey.set(0);
        groupKey.set(5 + 0);
        Double result =
            RelMetadataQuery.getDistinctRowCount(
                rootRel,
                groupKey,
                null);

        // We need to multiply the selectivity three times to account for:
        // - table level filter on t2
        // - semijoin filter on t1
        // - join filter
        // Because this test does not exercise LucidDB logic that accounts
        // for the double counting of semijoins, that is why the selectivity
        // is multiplied three times

        // number of distinct rows from the join; first arg corresponds to
        // the number of rows from applying the table filter and semijoin;
        // second is the number of rows from applying all three filters
        double expected =
            RelMdUtil.numDistinctVals(
                TAB_ROWCOUNT * TAB_ROWCOUNT * DEFAULT_EQUAL_SELECTIVITY_SQUARED,
                TAB_ROWCOUNT * TAB_ROWCOUNT * DEFAULT_EQUAL_SELECTIVITY_SQUARED
                * DEFAULT_EQUAL_SELECTIVITY);
        assertTrue(result != null);
        assertEquals(
            expected,
            result.doubleValue(),
            EPSILON);
    }
}

// End FarragoMetadataTest.java
