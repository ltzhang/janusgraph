#ifndef SLICE_QUERY_H
#define SLICE_QUERY_H

#include "StaticBuffer.h"

class SliceQuery {
private:
    StaticBuffer sliceStart;
    StaticBuffer sliceEnd;
    int limit;

public:
    SliceQuery(const StaticBuffer& start, const StaticBuffer& end, int lim = -1) 
        : sliceStart(start), sliceEnd(end), limit(lim) {}
    
    const StaticBuffer& getSliceStart() const {
        return sliceStart;
    }
    
    const StaticBuffer& getSliceEnd() const {
        return sliceEnd;
    }
    
    int getLimit() const {
        return limit;
    }
    
    bool hasLimit() const {
        return limit > 0;
    }
};

class KeySliceQuery {
private:
    StaticBuffer key;
    SliceQuery sliceQuery;

public:
    KeySliceQuery(const StaticBuffer& k, const SliceQuery& slice) 
        : key(k), sliceQuery(slice) {}
    
    const StaticBuffer& getKey() const {
        return key;
    }
    
    const SliceQuery& getSliceQuery() const {
        return sliceQuery;
    }
};

#endif