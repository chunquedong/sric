/*
 * Copyright (c) 2012-2016, chunquedong
 *
 * This file is part of cppfan project
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE version 3.0
 *
 * History:
 *   2012-12-23  Jed Young  Creation
 */
#ifndef _SRIC_REFPTR_H_
#define _SRIC_REFPTR_H_

#include "Ptr.h"

namespace sric
{

uint32_t generateCheckCode();

template<typename T>
struct StackRefable {
    uint32_t checkCode;
    T value;

    StackRefable(): checkCode(generateCheckCode()) {}

    StackRefable(const T& v): value(v), checkCode(generateCheckCode()) {
    }

    ~StackRefable() {
        checkCode = 0;
    }

    StackRefable& operator=(const T& v) {
        value = v;
    }

    T* operator->() const { return &value; }

    T* operator->() { return &value; }

    T& operator*() { return value; }

    operator T () { return value; }

    RefPtr<T> operator&() { return RefPtr<T>(*this); }

};

/////////////////////////////////////////////////////////////////////////////////////////////////////

enum struct RefType
{
    HeapRef, ArrayRef, StackRef, RawRef
};

template<typename T>
class RefPtr {
    T* pointer;
    uint32_t checkCode;
    uint32_t offset;
    RefType type;

    template <class U> friend class RefPtr;
    template <class U> friend RefPtr<U> rawToRef(U* ptr);
private:
#ifdef SC_NO_CHECK
#else
    void onDeref() const {
        sc_assert(pointer != nullptr, "try access null pointer");
        switch (type) {
        case RefType::HeapRef : {
            sc_assert(checkCode == getRefable(pointer)->_checkCode, "try access invalid pointer");
            break;
        }
        case RefType::StackRef: {
            if (alignof(T) <= 4) {
                sc_assert(checkCode == *(((int32_t*)pointer) - 1), "try access invalid pointer");
            }
            else {
                sc_assert(checkCode == *(((int64_t*)pointer) - 1), "try access invalid pointer");
            }
            break;
        }
        case RefType::ArrayRef: {
            T* first = (T*)(((char*)pointer) - offset);
            HeapRefable* refable = getRefable(first);
            sc_assert(checkCode == refable->_checkCode, "try access invalid array element pointer");
            sc_assert(offset < refable->_dataSize, "try access invalid array element pointer");
            break;
        }
        }
    }
#endif
private:
    RefPtr(T* p) : pointer(p), checkCode(0), offset(0), type(RefType::RawRef) {
    }
public:

    RefPtr() : pointer(nullptr), checkCode(0), offset(0), type(RefType::RawRef) {
    }

    RefPtr(T* p, uint32_t checkCode, uint32_t arrayOffset) : pointer(p), checkCode(checkCode), offset(arrayOffset), type(RefType::ArrayRef) {
    }

    RefPtr(StackRefable<T>& p) : pointer(&p.value), checkCode(p.checkCode), offset(0), type(RefType::StackRef) {
    }

    template <class U>
    RefPtr(const OwnPtr<U>& p) {
        if (p.isNull()) {
            pointer = nullptr;
            type = RefType::RawRef;
            checkCode = 0;
        }
        else {
            pointer = p.get();
            type = RefType::HeapRef;
            checkCode = getRefable(pointer)->_checkCode;
        }
    }

    template <class U>
    RefPtr(RefPtr<U>& p) : pointer(p.pointer), checkCode(p.checkCode), offset(p.offset), type(p.type) {
    }

    T* operator->() const {
#ifndef SC_NO_CHECK
        onDeref();
#endif
        return pointer;
    }

    T* operator->() {
#ifndef SC_NO_CHECK
        onDeref();
#endif
        return pointer;
    }

    T& operator*() { 
#ifndef SC_NO_CHECK
        onDeref();
#endif
        return *pointer;
    }

    operator T* () { return pointer; }

    T* get() const { return pointer; }

    bool isNull() { return pointer == nullptr; }

    bool operator==(const T* other) { return this->pointer == other; }
    bool operator==(const RefPtr<T>& other) { return this->pointer == other->pointer; }
    bool operator<(const RefPtr<T>& other) { return this->pointer < other->pointer; }

    template <class U> RefPtr<U> castTo()
    {
        RefPtr<U> copy((U*)(pointer));
        copy.checkCode = checkCode;
        copy.type = type;
        return copy;
    }

    template <class U> RefPtr<U> dynamicCastTo()
    {
        RefPtr<U> copy(dynamic_cast<U*>(pointer));
        copy.checkCode = checkCode;
        copy.type = type;
        return copy;
    }
};

template <class T>
OwnPtr<T> refToOwn(RefPtr<T> ptr) {
    if (ptr.type != RefType::HeapRef) {
        return OwnPtr<T>();
    }
    getRefable(ptr.get())->addRef();
    return OwnPtr<T>(ptr.get());
}

template <class T>
RefPtr<T> rawToRef(T* ptr) {
    return RefPtr<T>(ptr);
}

}
#endif