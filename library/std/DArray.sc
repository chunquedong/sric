//@#include "DArray.h"
/**
* Dynamic Array
*/
extern noncopyable struct DArray$<T> {
    fun data(): raw* T;

    fun size() const : Int;

    fun add(d: T);

    operator fun get(i: Int): ref* T;

    fun getConst(i: Int): ref* const T;
    unsafe fun getUnchecked(i: Int): ref* T;

    operator fun set(i: Int, d: T);

    fun resize(size: Int);
    fun reserve(capacity: Int);
    fun removeAt(i: Int);

}
