#ifndef _SRIC_COMMON_H_
#define _SRIC_COMMON_H_

#include <stdlib.h>
#include <stdio.h>

#if !defined(SC_NO_CHECK) && !defined(SC_CHECK)

#ifdef _DEBUG
    #define SC_CHECK
#endif

#ifdef NDEBUG
    #define SC_NO_CHECK
#endif

#endif

namespace sric
{

#ifndef SC_CHECK
    #define sc_assert(c, m) 
#else
    #define sc_assert(c, msg) \
        if (!(c)) {\
            fprintf(stderr, "ERROR: %s\n", msg);\
            abort();\
        }
#endif // SC_CHECK


    class Noncopyable {
    public:
        Noncopyable() = default;
    protected:
        Noncopyable(const Noncopyable&) = delete;
        Noncopyable& operator=(const Noncopyable&) = delete;
    };

}

#endif