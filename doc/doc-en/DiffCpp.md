## From C++ to Sric
### Types

| C++  | Sric  |
| ----- | ---- |
| int | Int |
| short | Int16 |
| int64_t | Int32 |
| unsigned int | UInt32 |
| int64_t | Int32 |
| float | Float32 |
| double | Float/Float64 |
| void | Void |
| char | Int8 |
| char[4] | [4]Int8 |
| int* | raw* Int8 |
| const int& | & const int |

### Defines
| C++  | Sric  |
| ----- | ---- |
| const char* str | var str: raw* Int8 |
| void foo(int i) {} | fun foo(i: Int) {} |

### Class

C++
```
#include <math.h>

class Point {
public:
    int x;
    int y;
    double dis(const Point &t) const {
        int dx = t.x - x;
        int dy = t.y - y;
        return sqrt(dx*dx + dy*dy);
    }
};
```
Sric:
```
import cstd::*;

struct Point {
    var x: Int;
    var y: Int;
    fun dis(t: & const Point) const: Float {
        var dx = t.x - x;
        var dy = t.y - y;
        return sqrt(dx*dx + dy*dy);
    }
};
```


## Features Compare

### Removed features from C++

- No function overload by params
- No header file
- No implicit copying of large objects
- No define multi var per statement
- No nested class, nested function
- No class, just struct
- No namespace
- No macro
- No forward declarations
- No three static
- No friend class
- No multiple inheritance
- No virtual,private inheritance
- No i++ just ++i
- No switch auto fallthrough
- No template specialization
- No various constructors

### More than C++

- Simple and easy
- Memory safe
- Modularization
- With block
- Non-nullable pointer
- Dynamic reflection
- Named args
