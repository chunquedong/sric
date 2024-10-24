#ifndef _SRIC_REF_H_
#define _SRIC_REF_H_

#ifndef NO_THREAD_SAFE
#include <atomic>
#endif

namespace sric
{

class HeapRefable;

class WeakRefBlock {
    friend class HeapRefable;
#if NO_THREAD_SAFE
    unsigned int _weakRefCount;
#else
    std::atomic<unsigned int> _weakRefCount;
#endif

    HeapRefable* _pointer;
public:
    WeakRefBlock();
    ~WeakRefBlock();

    HeapRefable* lock();

    void addRef();
    void release();
};

/**
 * Defines the base class for game objects that require lifecycle management.
 *
 * This class provides reference counting support for game objects that
 * contain system resources or data that is normally long lived and
 * referenced from possibly several sources at the same time. The built-in
 * reference counting eliminates the need for programmers to manually
 * keep track of object ownership and having to worry about when to
 * safely delete such objects.
 */
class HeapRefable
{
public:

    /**
     * Increments the reference count of this object.
     *
     * The release() method must be called when the caller relinquishes its
     * handle to this object in order to decrement the reference count.
     */
    void addRef();

    /**
     * Decrements the reference count of this object.
     *
     * When an object is initially created, its reference count is set to 1.
     * Calling addRef() will increment the reference and calling release()
     * will decrement the reference count. When an object reaches a
     * reference count of zero, the object is destroyed.
     */
    bool release();

    /**
     * Returns the current reference count of this object.
     *
     * @return This object's reference count.
     */
    unsigned int getRefCount() const;

    void _setRefCount(int rc);

    WeakRefBlock* getWeakRefBlock();

    int32_t getCheckCode() { return _checkCode; }
public:

    /**
     * Constructor.
     */
    HeapRefable();

    /**
     * Copy constructor.
     * 
     * @param copy The HeapRefable object to copy.
     */
    //HeapRefable(const HeapRefable& copy);

    /**
     * Destructor.
     */
    virtual ~HeapRefable();

private:
    void disposeWeakRef();

    uint32_t _checkCode;
    bool _isUnique;

#if NO_THREAD_SAFE
    unsigned int _refCount;
#else
    std::atomic<unsigned int> _refCount;
#endif

    WeakRefBlock* _weakRefBlock;

    // Memory leak diagnostic data (only included when GP_USE_MEM_LEAK_DETECTION is defined)
#ifdef GP_USE_REF_TRACE
    friend void* trackRef(HeapRefable* ref);
    friend void untrackRef(HeapRefable* ref);
    HeapRefable* _next;
    HeapRefable* _prev;
public:
    static void printLeaks();
#endif
};

}

#endif
