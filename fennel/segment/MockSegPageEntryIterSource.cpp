/*
// $Id$
// Fennel is a library of data storage and processing components.
// Copyright (C) 2005 The Eigenbase Project
// Copyright (C) 2005 SQLstream, Inc.
// Copyright (C) 2005 Dynamo BI Corporation
// Portions Copyright (C) 1999 John V. Sichi
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
#include "fennel/segment/MockSegPageEntryIterSource.h"
#include "fennel/segment/Segment.h"

FENNEL_BEGIN_CPPFILE("$Id$");

MockSegPageEntryIterSource::MockSegPageEntryIterSource(
    SegmentAccessor const &segmentAccessorInit,
    PageId beginPageId)
    : SegPageEntryIterSource<int>()
{
    counter = 0;
    segmentAccessor = segmentAccessorInit;
    nextPageId = beginPageId;
}

MockSegPageEntryIterSource::~MockSegPageEntryIterSource()
{
}

PageId MockSegPageEntryIterSource::getNextPageForPrefetch(
    int &entry,
    bool &found)
{
    found = true;
    entry = counter++;
    // Only need to figure out the next page on every other iteration
    if (counter % 2) {
        return nextPageId;
    }
    PageId currPageId = nextPageId;
    nextPageId = segmentAccessor.pSegment->getPageSuccessor(nextPageId);
    if (nextPageId != NULL_PAGE_ID) {
        nextPageId = segmentAccessor.pSegment->getPageSuccessor(nextPageId);
    }
    return currPageId;
}

FENNEL_END_CPPFILE("$Id:");

// End MockSegPageEntryIterSource.cpp
