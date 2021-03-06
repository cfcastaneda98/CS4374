/*
// $Id$
// Fennel is a library of data storage and processing components.
// Copyright (C) 2006 The Eigenbase Project
// Copyright (C) 2010 SQLstream, Inc.
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

#ifndef Fennel_LcsRowScanBaseExecStream_Included
#define Fennel_LcsRowScanBaseExecStream_Included

#include "fennel/exec/ConfluenceExecStream.h"
#include "fennel/ftrs/BTreeExecStream.h"
#include "fennel/tuple/TupleDescriptor.h"
#include "fennel/tuple/TupleDataWithBuffer.h"
#include "fennel/tuple/UnalignedAttributeAccessor.h"
#include "fennel/common/CircularBuffer.h"
#include "fennel/lcs/LcsClusterReader.h"

FENNEL_BEGIN_NAMESPACE

/**
 * Represents a single cluster in a table cluster scan
 */
struct LcsClusterScanDef : public BTreeExecStreamParams
{
    /**
     * Tuple descriptor of columns that make up the cluster
     */
    TupleDescriptor clusterTupleDesc;
};

typedef std::vector<LcsClusterScanDef> LcsClusterScanDefList;

/**
 * Indicates the clustered indexes that need to be read to scan a table and
 * the columns from the clusters that need to be projected in the scan result.
 */
struct LcsRowScanBaseExecStreamParams : public ConfluenceExecStreamParams
{
    /**
     * Ordered list of cluster scans
     */
    LcsClusterScanDefList lcsClusterScanDefs;

    /**
     * projection from scan
     */
    TupleProjection outputProj;
};


/**
 * Implements basic elements required to scan clusters in an exec stream
 */
class FENNEL_LCS_EXPORT LcsRowScanBaseExecStream
    : public ConfluenceExecStream
{
protected:
    /**
     * Projection map that maps columns read from cluster to their position
     * in the output projection
     */
    VectorOfUint projMap;

    /**
     * Number of clusters to be scanned
     */
    uint nClusters;

    /**
     * Array containing cluster readers
     */
    boost::scoped_array<SharedLcsClusterReader> pClusters;

    /**
     * Tuple descriptor representing columns to be projected from scans
     */
    TupleDescriptor projDescriptor;

    /**
     * List of the non-cluster columns that need to be projected
     */
    std::vector<int> nonClusterCols;

    /**
     * True in the special case where we are only reading special columns.
     * I.e., we don't actually have to read the underlying cluster data.
     */
    bool allSpecial;

    /**
     * Circular buffer of rid runs
     */
    CircularBuffer<LcsRidRun> ridRuns;

    /**
     * Positions column readers based on new cluster reader position
     *
     * @param pScan cluster reader
     */
    void syncColumns(SharedLcsClusterReader &pScan);

    /**
     * Accessors used for loading actual column values.
     */
    std::vector<UnalignedAttributeAccessor> attrAccessors;

    /**
     * Reads column values based on current position of cluster reader
     *
     * @param pScan cluster reader
     * @param tupleData tupledata where data will be loaded
     * @param colStart starting column offset where first column will be
     * loaded
     *
     * @return false if column filters failed; true otherwise
     */
    bool readColVals(
        SharedLcsClusterReader &pScan,
        TupleDataWithBuffer &tupleData,
        uint colStart);

    /**
     * Builds outputProj from params.
     *
     * @param outputProj the projection to be built
     *
     * @param params the LcsRowScanBaseExecStreamParams
     *
     */
    virtual void buildOutputProj(
        TupleProjection &outputProj,
        LcsRowScanBaseExecStreamParams const &params);

public:
    explicit LcsRowScanBaseExecStream();
    virtual void prepare(LcsRowScanBaseExecStreamParams const &params);
    virtual void open(bool restart);
    virtual void getResourceRequirements(
        ExecStreamResourceQuantity &minQuantity,
        ExecStreamResourceQuantity &optQuantity);
    virtual void closeImpl();
};

/**
 * Column ordinals used to represent "special" columns, like rid
 */
enum LcsSpecialColumnId {
    LCS_RID_COLUMN_ID = 0x7FFFFF00
};

FENNEL_END_NAMESPACE

#endif

// End LcsRowScanBaseExecStream.h
