// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/hdr_abort.h>
#include <vespa/searchlib/attribute/enumattribute.h>
#include <vespa/searchlib/attribute/enumstore.hpp>

namespace search {

template <typename B>
EnumAttribute<B>::
EnumAttribute(const vespalib::string &baseFileName,
              const AttributeVector::Config &cfg)
    : B(baseFileName, cfg),
      _enumStore(0, cfg.fastSearch())
{
    this->setEnum(true);
}

template <typename B>
EnumAttribute<B>::~EnumAttribute()
{
}

template <typename B>
void EnumAttribute<B>::fillEnum(LoadedVector & loaded)
{
    if constexpr(!std::is_same_v<LoadedVector, NoLoadedVector>) {
        typename EnumStore::Builder builder;
        if (!loaded.empty()) {
            auto value = loaded.read();
            LoadedValueType prev = value.getValue();
            uint32_t prevRefCount(0);
            EnumIndex index = builder.insert(value.getValue(), value._pidx.ref());
            for (size_t i(0), m(loaded.size()); i < m; ++i, loaded.next()) {
                value = loaded.read();
                if (EnumStore::ComparatorType::compare(prev, value.getValue()) != 0) {
                    builder.updateRefCount(prevRefCount);
                    index = builder.insert(value.getValue(), value._pidx.ref());
                    prev = value.getValue();
                    prevRefCount = 1;
                } else {
                    prevRefCount++;
                }
                value.setEidx(index);
                loaded.write(value);
            }
            builder.updateRefCount(prevRefCount);
        }
        _enumStore.reset(builder);
    }
}


template <typename B>
void
EnumAttribute<B>::fillEnum0(const void *src,
                            size_t srcLen,
                            EnumIndexVector &eidxs)
{
    ssize_t sz = _enumStore.deserialize(src, srcLen, eidxs);
    assert(static_cast<size_t>(sz) == srcLen);
    (void) sz;
}


template <typename B>
void
EnumAttribute<B>::fixupEnumRefCounts(const EnumVector &enumHist)
{
    _enumStore.fixupRefCounts(enumHist);
}


template <typename B>
uint64_t
EnumAttribute<B>::getUniqueValueCount() const
{
    return _enumStore.getNumUniques();
}



template <typename B>
void
EnumAttribute<B>::insertNewUniqueValues(EnumStoreBatchUpdater& updater)
{
    UniqueSet newUniques;

    // find new unique strings
    for (const auto & data : this->_changes) {
        considerAttributeChange(data, newUniques);
    }

    uint64_t extraBytesNeeded = 0;
    for (const auto & data : newUniques) {
        extraBytesNeeded += _enumStore.getEntrySize(data.raw());
    }

    do {
        // perform compaction on EnumStore if necessary
        if (extraBytesNeeded > this->_enumStore.getRemaining() ||
            this->_enumStore.getPendingCompact())
        {
            this->logEnumStoreEvent("enumstorecompact", "reserve");
            this->removeAllOldGenerations();
            this->_enumStore.clearPendingCompact();
            EnumIndexMap old2New(this->_enumStore.getNumUniques()*3);
            this->logEnumStoreEvent("enumstorecompact", "start");
            if (!this->_enumStore.performCompaction(extraBytesNeeded, old2New)) {
                this->logEnumStoreEvent("enumstorecompact", "failed_compact");
                // fallback to resize strategy
                this->_enumStore.fallbackResize(extraBytesNeeded);
                this->logEnumStoreEvent("enumstorecompact", "fallbackresize_complete");
                if (extraBytesNeeded > this->_enumStore.getRemaining()) {
                    HDR_ABORT("Cannot fallbackResize enumStore");
                }
                break;  // fallback resize performed instead of compaction.
            }

            // update underlying structure with new EnumIndex values.
            reEnumerate(old2New);
            // Clear scratch enumeration
            for (auto & data : this->_changes) {
                data._enumScratchPad = ChangeBase::UNSET_ENUM;
            }
            this->logEnumStoreEvent("enumstorecompact", "complete");
        }
    } while (0);

    // insert new unique values in EnumStore
    for (const auto & data : newUniques) {
        updater.add(data.raw());
    }
}


template <typename B>
vespalib::AddressSpace
EnumAttribute<B>::getEnumStoreAddressSpaceUsage() const
{
    return _enumStore.getAddressSpaceUsage();
}

} // namespace search


