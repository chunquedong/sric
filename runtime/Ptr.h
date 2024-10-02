/*
 * Copyright (c) 2012-2016, chunquedong
 *
 * This file is part of cppfan project
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE version 3.0
 *
 * History:
 *   2012-12-23  Jed Young  Creation
 */
#ifndef PTR_H_
#define PTR_H_

#include <cstdio>
#include <cstdlib>
#include <type_traits>

#include "Refable.h"

namespace sric
{

class Refable;

template<typename T>
class OwnPtr {
    T* pointer;

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
        Refable* p = getRefable(pointer);
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

    template <class U> OwnPtr<U> staticCastTo()
    {
        OwnPtr<U> copy(static_cast<U*>(take()));
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

inline Refable* getRefable(void *pointer) {
    Refable* p = (Refable*)pointer;
    --p;
    return p;
}

template<typename T>
OwnPtr<T> alloc() {
    Refable* p = (Refable*)::operator new(sizeof(Refable) + sizeof(T));
    new (p) Refable();
    void* m = (p + 1);
    T* t = new(m) T();
    return OwnPtr<T>(t);
}

template <class T>
OwnPtr<T> share(OwnPtr<T> p) {
    T* pointer = p.get();
    if (pointer)
        getRefable(pointer)->addRef();
    return OwnPtr<T>(pointer);
}

template<typename T>
T* ownToRaw(OwnPtr<T>& p) {
    return p.get();
}

template <class T>
OwnPtr<T> rawToOwn(T* ptr) {
    getRefable(ptr)->addRef();
    return OwnPtr<T>(ptr);
}

/////////////////////////////////////////////////////////////////////////////////////////////////////

template<typename T>
class RefPtr {
    T* pointer;
    int32_t checkCode;
    int32_t type;
private:
    void onDeref() {
        sc_assert(pointer != nullptr, "try deref null pointer");
        if (type == 0) {
            sc_assert(checkCode == getRefable(pointer)->getCheckCode(), "try deref error pointer");
        }
    }
public:
    RefPtr(T* p) : pointer(p), checkCode(0), type(1) {
    }

    RefPtr(OwnPtr<T>& p) : pointer(p.get()), checkCode(getRefable(pointer)->getCheckCode()), type(0) {
    }

    T* operator->() const { onDeref(); return pointer; }

    T* operator->() { onDeref(); return pointer; }

    T& operator*() { onDeref(); return *pointer; }

    T* get() const { onDeref(); return pointer; }

    bool isNull() { return pointer == nullptr; }
};


template<typename T>
RefPtr<T> ownToRef(OwnPtr<T>& p) {
    return RefPtr<T>(p);
}

template<typename T>
RefPtr<T> addressOf(T& p) {
    return RefPtr<T>(&p);
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
        Refable* refp = dynamic_cast<Refable*>(p.get());
        if (refp) {
            pointer = refp->getWeakRefBlock();
            pointer->addRef();
        }
    }

    void init(OwnPtr<T>& p) {
        if (pointer) {
            pointer->release();
        }

        Refable* refp = dynamic_cast<Refable*>(p.get());
        if (refp) {
            pointer = refp->getWeakRefBlock();
            pointer->addRef();
        }
    }

    WeakPtr(T* other) : pointer(NULL) {
        if (other) {
            Refable* refp = dynamic_cast<Refable*>(other);
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
        return OwnPtr<Refable>(pointer->lock()).dynamicCastTo<T>();
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