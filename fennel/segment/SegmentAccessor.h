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

#ifndef Fennel_SegmentAccessor_Included
#define Fennel_SegmentAccessor_Included

FENNEL_BEGIN_NAMESPACE

/**
 * A SegmentAccessor combines a Segment with a CacheAccessor.
 */
struct FENNEL_SEGMENT_EXPORT SegmentAccessor
{
    SharedSegment pSegment;
    SharedCacheAccessor pCacheAccessor;

    explicit SegmentAccessor()
    {
    }

    explicit SegmentAccessor(
        SharedSegment pSegmentInit,
        SharedCacheAccessor pCacheAccessorInit)
        : pSegment(pSegmentInit), pCacheAccessor(pCacheAccessorInit)
    {
    }

    void reset()
    {
        pSegment.reset();
        pCacheAccessor.reset();
    }
};

FENNEL_END_NAMESPACE

#endif

// End SegmentAccessor.h
