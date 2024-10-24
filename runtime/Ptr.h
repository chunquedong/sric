/*
 * Copyright (c) 2012-2016, chunquedong
 *
 * This file is part of cppfan project
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE version 3.0
 *
 * History:
 *   2012-12-23  Jed Young  Creation
 */
#ifndef _SRIC_PTR_H_
#define _SRIC_PTR_H_

#include <cstdio>
#include <cstdlib>
#include <type_traits>

#include "Refable.h"
#include "common.h"

namespace sric
{

class HeapRefable;

template<typename T>
class RefPtr;

template<typename U>
typename std::enable_if<std::is_polymorphic<U>::value, HeapRefable*>::type  getRefable(U* pointer) {
    void* mostTop = dynamic_cast<void*>(pointer);
    HeapRefable* p = (HeapRefable*)mostTop;
    --p;
    return p;
}

template<typename U>
typename std::enable_if<!std::is_polymorphic<U>::value, HeapRefable*>::type  getRefable(U* pointer) {
    void* mostTop = pointer;
    HeapRefable* p = (HeapRefable*)mostTop;
    --p;
    return p;
}

template<typename T>
class OwnPtr {
    T* pointer;
    template <class U> friend class OwnPtr;
public:
    OwnPtr() : pointer(nullptr) {
    }

    explicit OwnPtr(T* p) : pointer(p) {
    }

    ~OwnPtr() {
        clear();
    }

    OwnPtr(const OwnPtr& other) = delete;

    OwnPtr(OwnPtr&& other) {
        if (other.pointer) {
            pointer = other.pointer;
            other.pointer = nullptr;
        }
        else {
            pointer = nullptr;
        }
    }

    template <class U>
    OwnPtr(OwnPtr<U>&& other) {
        if (other.pointer) {
            pointer = other.pointer;
            other.pointer = nullptr;
        }
        else {
            pointer = nullptr;
        }
    }

    OwnPtr& operator=(const OwnPtr& other) = delete;

    OwnPtr& operator=(OwnPtr&& other) {
        T* toDelete = pointer;

        if (other.pointer) {
            pointer = other.pointer;
            other.pointer = nullptr;
        }
        else {
            pointer = nullptr;
        }

        if (toDelete) {
            doFree(toDelete);
        }
        return *this;
    }

    T* operator->() const { sc_assert(pointer != nullptr, "try deref null pointer"); return pointer; }

    T* operator->() { sc_assert(pointer != nullptr, "try deref null pointer"); return pointer; }

    T& operator*() { sc_assert(pointer != nullptr, "try deref null pointer"); return *pointer; }

    operator T* () { return pointer; }

    //template <class U>
    //operator RefPtr<U>() { return RefPtr<U>(this); }
    //operator RefPtr<T>() { return RefPtr<T>(this); }

    T* get() const { return pointer; }

    bool isNull() { return pointer == nullptr; }

    void clear() {
        if (pointer) {
            doFree(pointer);
            pointer = nullptr;
        }
    }

private:
    void doFree(T* pointer) {
        HeapRefable* p = getRefable(pointer);
        if (p->release()) {
            pointer->~T();
            ::operator delete(p);
        }
    }
public:
    T* take() {
        T* p = pointer;
        pointer = nullptr;
        return p;
    }

    void swap(OwnPtr& other) {
        T* p = pointer;
        pointer = other.pointer;
        other.pointer = p;
    }

    template <class U> OwnPtr<U> castTo()
    {
        OwnPtr<U> copy((U*)(take()));
        return copy;
    }

    template <class U> OwnPtr<U> dynamicCastTo()
    {
        OwnPtr<U> copy(dynamic_cast<U*>(take()));
        return copy;
    }

    OwnPtr<T> share() {
        if (pointer)
            getRefable(pointer)->addRef();
        return OwnPtr<T>(pointer);
    }
};



template<typename T>
OwnPtr<T> alloc() {
    HeapRefable* p = (HeapRefable*)::operator new(sizeof(HeapRefable) + sizeof(T));
    new (p) HeapRefable();
    void* m = (p + 1);
    T* t = new(m) T();
    return OwnPtr<T>(t);
}

template <class T>
OwnPtr<T> share(OwnPtr<T> p) {
    return p.share();
}

template <class T>
OwnPtr<T> rawToOwn(T* ptr) {
    getRefable(ptr)->addRef();
    return OwnPtr<T>(ptr);
}

/////////////////////////////////////////////////////////////////////////////////////////////////////
uint32_t generateCheckCode();

template<typename T>
struct StackRefable {
    int32_t checkCode;
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
    HeapRef, RawRef, NullRef, StackRef
};

template<typename T>
class RefPtr {
    T* pointer;
    int32_t checkCode;

    RefType type;

    template <class U> friend class RefPtr;

    template<typename T2> friend RefPtr<T2> refSafeCheck(RefPtr<T2> p);
private:
    void onDeref() const {
        sc_assert(pointer != nullptr, "try deref null pointer");
        if (type == RefType::HeapRef) {
            sc_assert(checkCode == getRefable(pointer)->getCheckCode(), "try deref error pointer");
        }
        else if (type == RefType::StackRef) {
            if (alignof(T) <= 4) {
                sc_assert(checkCode == *(((int32_t*)pointer) - 1), "try deref error pointer");
            }
            else {
                sc_assert(checkCode == *(((int64_t*)pointer) - 1), "try deref error pointer");
            }
        }
    }
public:
    RefPtr() : pointer(nullptr), checkCode(0), type(RefType::NullRef) {
    }

    RefPtr(T* p) : pointer(p), checkCode(0), type(RefType::RawRef) {
    }

    RefPtr(StackRefable<T>& p) : pointer(&p.value), checkCode(p.checkCode), type(RefType::StackRef) {
    }

    template <class U>
    RefPtr(OwnPtr<U>& p) : pointer(p.get()), checkCode(getRefable(pointer)->getCheckCode()), type(RefType::HeapRef) {
    }

    template <class U>
    RefPtr(RefPtr<U>& p) : pointer(p.pointer), checkCode(p.checkCode), type(p.type) {
    }

    T* operator->() const { onDeref(); return pointer; }

    T* operator->() { onDeref(); return pointer; }

    T& operator*() { onDeref(); return *pointer; }

    operator T* () { return pointer; }

    T* get() const { return pointer; }

    bool isNull() { return pointer == nullptr; }

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
    if (ptr.type != 0) {
        return OwnPtr<T>();
    }
    getRefable(ptr.get())->addRef();
    return OwnPtr<T>(ptr.get());
}

template <class T>
RefPtr<T> rawToRef(T* ptr) {
    return RefPtr<T>(ptr);
}

template<typename T>
RefPtr<T> refSafeCheck(RefPtr<T> p) {
    sric::sc_assert(p.type == RefPtr::RawRef, "Unsafe ref");
    return p;
}

/////////////////////////////////////////////////////////////////////////////////////////////////////

class WeakRefBlock;
template<typename T>
class WeakPtr {
    WeakRefBlock* pointer;
public:
    WeakPtr() : pointer(NULL) {
    }

    WeakPtr(OwnPtr<T>& p) : pointer(NULL) {
        HeapRefable* refp = dynamic_cast<HeapRefable*>(p.get());
        if (refp) {
            pointer = refp->getWeakRefBlock();
            pointer->addRef();
        }
    }

    void init(OwnPtr<T>& p) {
        if (pointer) {
            pointer->release();
        }

        HeapRefable* refp = dynamic_cast<HeapRefable*>(p.get());
        if (refp) {
            pointer = refp->getWeakRefBlock();
            pointer->addRef();
        }
    }

    WeakPtr(T* other) : pointer(NULL) {
        if (other) {
            HeapRefable* refp = dynamic_cast<HeapRefable*>(other);
            pointer = refp->getWeakRefBlock();
            pointer->addRef();
        }
    }

    WeakPtr(const WeakPtr& other) : pointer(other.pointer) {
        if (other.pointer) {
            other.pointer->addRef();
        }
    }

    virtual ~WeakPtr() {
        clear();
    }

    WeakPtr& operator=(const WeakPtr& other) {
        if (other.pointer) {
            other.pointer->addRef();
        }
        if (pointer) {
            pointer->release();
        }
        pointer = other.pointer;
        return *this;
    }

    OwnPtr<T> lock() {
        if (!pointer) {
            return OwnPtr<T>();
        }
        return OwnPtr<HeapRefable>(pointer->lock()).dynamicCastTo<T>();
    }

    void clear() {
        if (pointer) {
            pointer->release();
            pointer = nullptr;
        }
    }
};

}
#endif
