/*
// $Id$
// Fennel is a library of data storage and processing components.
// Copyright (C) 2006 The Eigenbase Project
// Copyright (C) 2009 SQLstream, Inc.
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

#include "fennel/common/CommonPreamble.h"
#include "fennel/test/ExecStreamUnitTestBase.h"
#include "fennel/hashexe/LhxAggExecStream.h"
#include "fennel/sorter/ExternalSortExecStream.h"
#include "fennel/tuple/StandardTypeDescriptor.h"
#include "fennel/exec/MockProducerExecStream.h"
#include "fennel/exec/ExecStreamEmbryo.h"
#include "fennel/cache/Cache.h"

#include <boost/test/test_tools.hpp>

using namespace fennel;

class LhxAggExecStreamTest : public ExecStreamUnitTestBase
{
    void testCountImpl(uint forcePartitionLevel);
    void testSumImpl(uint forcePartitionLevel);
    void testGroupCountImpl(uint forcePartitionLevel);
    void testSingleValueImpl(uint forcePartitionLevel);

public:
    explicit LhxAggExecStreamTest()
    {
        FENNEL_UNIT_TEST_CASE(LhxAggExecStreamTest, testCount);
        FENNEL_UNIT_TEST_CASE(LhxAggExecStreamTest, testSum);
        FENNEL_UNIT_TEST_CASE(LhxAggExecStreamTest, testGroupCount);
        FENNEL_UNIT_TEST_CASE(LhxAggExecStreamTest, testSingleValue);
        FENNEL_UNIT_TEST_CASE(LhxAggExecStreamTest, testCountPartition);
        FENNEL_UNIT_TEST_CASE(LhxAggExecStreamTest, testSumPartition);
        FENNEL_UNIT_TEST_CASE(LhxAggExecStreamTest, testGroupCountPartition);
        FENNEL_UNIT_TEST_CASE(LhxAggExecStreamTest, testSingleValuePartition);
    }

    void testCount();
    void testCountPartition();

    void testSum();
    void testSumPartition();

    void testGroupCount();
    void testGroupCountPartition();

    void testSingleValue();
    void testSingleValuePartition();
};

void LhxAggExecStreamTest::testCount()
{
    testCountImpl(0);
}

void LhxAggExecStreamTest::testCountPartition()
{
    testCountImpl(2);
}

void LhxAggExecStreamTest::testSum()
{
    testSumImpl(0);
}

void LhxAggExecStreamTest::testSumPartition()
{
    testSumImpl(2);
}

void LhxAggExecStreamTest::testGroupCount()
{
    testGroupCountImpl(0);
}

void LhxAggExecStreamTest::testGroupCountPartition()
{
    testGroupCountImpl(2);
}

void LhxAggExecStreamTest::testSingleValue()
{
    testSingleValueImpl(0);
}

void LhxAggExecStreamTest::testSingleValuePartition()
{
    testSingleValueImpl(2);
}

void LhxAggExecStreamTest::testCountImpl(uint forcePartitionLevel)
{
    StandardTypeDescriptorFactory stdTypeFactory;
    TupleAttributeDescriptor attrDesc(
        stdTypeFactory.newDataType(STANDARD_TYPE_INT_64));

    uint numRows = 10000;

    MockProducerExecStreamParams mockParams;
    mockParams.outputTupleDesc.push_back(attrDesc);
    mockParams.nRows = numRows;   // at least two buffers

    ExecStreamEmbryo mockStreamEmbryo;
    mockStreamEmbryo.init(new MockProducerExecStream(), mockParams);
    mockStreamEmbryo.getStream()->setName("MockProducerExecStream");

    // simulate SELECT COUNT(*) FROM t10k
    LhxAggExecStreamParams aggParams;
    aggParams.groupByKeyCount = 0;
    aggParams.outputTupleDesc.push_back(attrDesc);
    AggInvocation countInvocation;
    countInvocation.aggFunction = AGG_FUNC_COUNT;
    countInvocation.iInputAttr = -1; // interpreted as COUNT(*)
    aggParams.aggInvocations.push_back(countInvocation);

    aggParams.pCacheAccessor = pCache;
    aggParams.scratchAccessor =
        pSegmentFactory->newScratchSegment(pCache, 100);
    aggParams.pTempSegment = pRandomSegment;
    aggParams.cndGroupByKeys = numRows;
    aggParams.numRows = numRows;
    aggParams.forcePartitionLevel = forcePartitionLevel;
    aggParams.enableSubPartStat = true;

    ExecStreamEmbryo aggStreamEmbryo;
    aggStreamEmbryo.init(new LhxAggExecStream(), aggParams);
    aggStreamEmbryo.getStream()->setName("LhxAggExecStream");

    SharedExecStream pOutputStream = prepareTransformGraph(
        mockStreamEmbryo, aggStreamEmbryo);

    // set up a generator which can produce the expected output
    // (a count of 10000)
    RampExecStreamGenerator expectedResultGenerator(mockParams.nRows);

    verifyOutput(*pOutputStream, 1, expectedResultGenerator);
}

void LhxAggExecStreamTest::testSumImpl(uint forcePartitionLevel)
{
    StandardTypeDescriptorFactory stdTypeFactory;
    TupleAttributeDescriptor attrDesc(
        stdTypeFactory.newDataType(STANDARD_TYPE_INT_64));

    uint numRows = 10000;
    MockProducerExecStreamParams mockParams;
    mockParams.outputTupleDesc.push_back(attrDesc);
    mockParams.nRows = numRows;   // at least two buffers
    mockParams.pGenerator.reset(new RampExecStreamGenerator());

    ExecStreamEmbryo mockStreamEmbryo;
    mockStreamEmbryo.init(new MockProducerExecStream(), mockParams);
    mockStreamEmbryo.getStream()->setName("MockProducerExecStream");

    // simulate SELECT SUM(x) FROM t10k with x iterating from 0 to 9999
    LhxAggExecStreamParams aggParams;
    aggParams.groupByKeyCount = 0;
    attrDesc.isNullable = true;
    aggParams.outputTupleDesc.push_back(attrDesc);
    AggInvocation sumInvocation;
    sumInvocation.aggFunction = AGG_FUNC_SUM;
    sumInvocation.iInputAttr = 0;
    aggParams.aggInvocations.push_back(sumInvocation);

    aggParams.pCacheAccessor = pCache;
    aggParams.scratchAccessor =
        pSegmentFactory->newScratchSegment(pCache, 100);
    aggParams.pTempSegment = pRandomSegment;
    aggParams.cndGroupByKeys = numRows;
    aggParams.numRows = numRows;
    aggParams.forcePartitionLevel = forcePartitionLevel;
    aggParams.enableSubPartStat = true;

    ExecStreamEmbryo aggStreamEmbryo;
    aggStreamEmbryo.init(new LhxAggExecStream(), aggParams);
    aggStreamEmbryo.getStream()->setName("LhxAggExecStream");

    SharedExecStream pOutputStream = prepareTransformGraph(
        mockStreamEmbryo, aggStreamEmbryo);

    // set up a generator which can produce the expected output
    // (a count of 5000*9999)
    RampExecStreamGenerator expectedResultGenerator(
        (mockParams.nRows - 1) * mockParams.nRows / 2);

    verifyOutput(*pOutputStream, 1, expectedResultGenerator);
}

void LhxAggExecStreamTest::testGroupCountImpl(uint forcePartitionLevel)
{
    StandardTypeDescriptorFactory stdTypeFactory;
    TupleAttributeDescriptor attrDesc(
        stdTypeFactory.newDataType(STANDARD_TYPE_INT_64));

    uint numRows = 1000;
    // Create one column, with two duplicates per value.
    MockProducerExecStreamParams mockParams;
    mockParams.outputTupleDesc.push_back(attrDesc);
    mockParams.nRows = numRows;
    mockParams.pGenerator.reset(new RampDuplicateExecStreamGenerator());

    ExecStreamEmbryo mockStreamEmbryo;
    mockStreamEmbryo.init(new MockProducerExecStream(), mockParams);
    mockStreamEmbryo.getStream()->setName("MockProducerExecStream");

    TupleDescriptor outputDesc;
    outputDesc.push_back(attrDesc);
    outputDesc.push_back(attrDesc);

    // simulate SELECT col, COUNT(*) FROM t10k GROUP BY col;
    LhxAggExecStreamParams aggParams;
    aggParams.groupByKeyCount = 1;
    aggParams.outputTupleDesc = outputDesc;
    AggInvocation countInvocation;
    countInvocation.aggFunction = AGG_FUNC_COUNT;
    countInvocation.iInputAttr = -1; // interpreted as COUNT(*)
    aggParams.aggInvocations.push_back(countInvocation);

    aggParams.pCacheAccessor = pCache;
    aggParams.scratchAccessor =
        pSegmentFactory->newScratchSegment(pCache, 100);
    aggParams.pTempSegment = pRandomSegment;
    aggParams.cndGroupByKeys = numRows / 2;
    aggParams.numRows = numRows;
    aggParams.forcePartitionLevel = forcePartitionLevel;
    aggParams.enableSubPartStat = true;

    ExecStreamEmbryo aggStreamEmbryo;

    aggStreamEmbryo.init(new LhxAggExecStream(), aggParams);
    aggStreamEmbryo.getStream()->setName("LhxAggExecStream");

    ExternalSortExecStreamParams sortParams;
    sortParams.outputTupleDesc = outputDesc;
    sortParams.distinctness = DUP_ALLOW;
    sortParams.pTempSegment = pRandomSegment;
    sortParams.pCacheAccessor = pCache;
    sortParams.scratchAccessor =
        pSegmentFactory->newScratchSegment(pCache, 10);
    sortParams.keyProj.push_back(0);
    sortParams.storeFinalRun = false;
    sortParams.partitionKeyCount = 0;
    sortParams.estimatedNumRows = MAXU;
    sortParams.earlyClose = false;

    ExecStreamEmbryo sortStreamEmbryo;
    sortStreamEmbryo.init(
        ExternalSortExecStream::newExternalSortExecStream(), sortParams);
    sortStreamEmbryo.getStream()->setName("ExternalSortExecStream");

    std::vector<ExecStreamEmbryo> transforms;
    transforms.push_back(aggStreamEmbryo);
    transforms.push_back(sortStreamEmbryo);

    SharedExecStream pOutputStream = prepareTransformGraph(
        mockStreamEmbryo, transforms);

    // Result should be a sequence of values in the first column
    // and 2 for the second column
    vector<boost::shared_ptr<ColumnGenerator< ::int64_t > > > columnGenerators;

    SharedInt64ColumnGenerator col =
        SharedInt64ColumnGenerator(new SeqColumnGenerator());
    columnGenerators.push_back(col);

    col = SharedInt64ColumnGenerator(new ConstColumnGenerator(2));
    columnGenerators.push_back(col);

    CompositeExecStreamGenerator expectedResultGenerator(columnGenerators);

    verifyOutput(*pOutputStream, mockParams.nRows/2, expectedResultGenerator);
}

void LhxAggExecStreamTest::testSingleValueImpl(uint forcePartitionLevel)
{
    StandardTypeDescriptorFactory stdTypeFactory;
    TupleAttributeDescriptor attrDesc(
        stdTypeFactory.newDataType(STANDARD_TYPE_INT_64));
    TupleAttributeDescriptor attrDescNullable(
        stdTypeFactory.newDataType(STANDARD_TYPE_INT_64), true,
        sizeof(::int64_t));

    // Result should be a sequence of values in the first column
    // and 2 for the second column
    vector<boost::shared_ptr<ColumnGenerator< ::int64_t > > >
        columnGeneratorsIn;

    SharedInt64ColumnGenerator col =
        SharedInt64ColumnGenerator(new DupColumnGenerator(1));
    columnGeneratorsIn.push_back(col);

    uint numRows = 1000;

    // Create two columns, both with two duplicates per column.
    MockProducerExecStreamParams mockParams;
    mockParams.outputTupleDesc.push_back(attrDesc);
    mockParams.nRows = numRows;
    mockParams.pGenerator.reset(
        new CompositeExecStreamGenerator(columnGeneratorsIn));

    ExecStreamEmbryo mockStreamEmbryo;
    mockStreamEmbryo.init(new MockProducerExecStream(), mockParams);
    mockStreamEmbryo.getStream()->setName("MockProducerExecStream");

    TupleDescriptor outputDesc;
    outputDesc.push_back(attrDesc);
    outputDesc.push_back(attrDescNullable);

    // simulate SELECT x, single_value(x) FROM t10k group by x
    LhxAggExecStreamParams aggParams;
    aggParams.groupByKeyCount = 1;
    aggParams.outputTupleDesc = outputDesc;
    AggInvocation singleValueInvocation;
    singleValueInvocation.aggFunction = AGG_FUNC_SINGLE_VALUE;
    singleValueInvocation.iInputAttr = 0;
    aggParams.aggInvocations.push_back(singleValueInvocation);

    aggParams.pCacheAccessor = pCache;
    aggParams.scratchAccessor =
        pSegmentFactory->newScratchSegment(pCache, 100);
    aggParams.pTempSegment = pRandomSegment;
    aggParams.cndGroupByKeys = numRows;
    aggParams.numRows = numRows;
    aggParams.forcePartitionLevel = forcePartitionLevel;
    aggParams.enableSubPartStat = true;

    ExecStreamEmbryo aggStreamEmbryo;

    aggStreamEmbryo.init(new LhxAggExecStream(), aggParams);
    aggStreamEmbryo.getStream()->setName("LhxAggExecStream");

    ExternalSortExecStreamParams sortParams;
    sortParams.outputTupleDesc = outputDesc;
    sortParams.distinctness = DUP_ALLOW;
    sortParams.pTempSegment = pRandomSegment;
    sortParams.pCacheAccessor = pCache;
    sortParams.scratchAccessor =
        pSegmentFactory->newScratchSegment(pCache, 10);
    sortParams.keyProj.push_back(0);
    sortParams.storeFinalRun = false;
    sortParams.partitionKeyCount = 0;

    ExecStreamEmbryo sortStreamEmbryo;
    sortStreamEmbryo.init(
        ExternalSortExecStream::newExternalSortExecStream(), sortParams);
    sortStreamEmbryo.getStream()->setName("ExternalSortExecStream");

    std::vector<ExecStreamEmbryo> transforms;
    transforms.push_back(aggStreamEmbryo);
    transforms.push_back(sortStreamEmbryo);

    SharedExecStream pOutputStream = prepareTransformGraph(
        mockStreamEmbryo, transforms);

    // Result should be a sequence of values in both columns
    vector<boost::shared_ptr<ColumnGenerator< ::int64_t > > >
        columnGeneratorsOut;

    col =
        SharedInt64ColumnGenerator(new DupColumnGenerator(1));
    columnGeneratorsOut.push_back(col);

    col =
        SharedInt64ColumnGenerator(new DupColumnGenerator(1));
    columnGeneratorsOut.push_back(col);

    CompositeExecStreamGenerator expectedResultGenerator(columnGeneratorsOut);

    verifyOutput(*pOutputStream, mockParams.nRows, expectedResultGenerator);
}

FENNEL_UNIT_TEST_SUITE(LhxAggExecStreamTest);

// End LhxAggExecStreamTest.cpp
