/*
// $Id$
// Fennel is a library of data storage and processing components.
// Copyright (C) 2005 The Eigenbase Project
// Copyright (C) 2009 SQLstream, Inc.
// Copyright (C) 2004 Dynamo BI Corporation
// Portions Copyright (C) 2004 John V. Sichi
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
#include "fennel/sorter/ExternalSortOutput.h"
#include "fennel/sorter/ExternalSortInfo.h"
#include "fennel/common/ByteOutputStream.h"
#include "fennel/exec/ExecStreamBufAccessor.h"

FENNEL_BEGIN_CPPFILE("$Id$");

ExternalSortOutput::ExternalSortOutput(ExternalSortInfo &sortInfoIn)
    : sortInfo(sortInfoIn)
{
    pSubStream = NULL;
    pFetchArray = NULL;
    iCurrentTuple = 0;

    tupleAccessor.compute(sortInfo.tupleDesc);
}

ExternalSortOutput::~ExternalSortOutput()
{
    releaseResources();
}

void ExternalSortOutput::releaseResources()
{
}

void ExternalSortOutput::setSubStream(ExternalSortSubStream &subStream)
{
    iCurrentTuple = 0;

    pSubStream = &subStream;
    pFetchArray = &(subStream.bindFetchArray());
}

ExecStreamResult ExternalSortOutput::fetch(
    ExecStreamBufAccessor &bufAccessor)
{
    uint cbRemaining = bufAccessor.getProductionAvailable();
    PBuffer pOutBuf = bufAccessor.getProductionStart();
    PBuffer pNextTuple = pOutBuf;

    for (;;) {
        if (iCurrentTuple >= pFetchArray->nTuples) {
            ExternalSortRC rc = pSubStream->fetch(EXTSORT_FETCH_ARRAY_SIZE);
            if (rc == EXTSORT_ENDOFDATA) {
                goto done;
            }
            iCurrentTuple = 0;
        }

        while (iCurrentTuple < pFetchArray->nTuples) {
            PConstBuffer pSrcTuple =
                pFetchArray->ppTupleBuffers[iCurrentTuple];
            uint cbTuple = tupleAccessor.getBufferByteCount(pSrcTuple);
            if (cbTuple > cbRemaining) {
                if (pNextTuple == pOutBuf) {
                    bufAccessor.requestConsumption();
                    return EXECRC_BUF_OVERFLOW;
                }
                goto done;
            }
            memcpy(pNextTuple, pSrcTuple, cbTuple);
            cbRemaining -= cbTuple;
            pNextTuple += cbTuple;
            iCurrentTuple++;
        }
    }

 done:
    if (pNextTuple == pOutBuf) {
        return EXECRC_EOS;
    } else {
        bufAccessor.produceData(pNextTuple);
        bufAccessor.requestConsumption();
        // REVIEW:  sometimes should be EXECRC_EOS instead
        return EXECRC_BUF_OVERFLOW;
    }
}

FENNEL_END_CPPFILE("$Id$");

// End ExternalSortOutput.cpp
