
## 和C++交互
sric可以很容易的和C++交互。sric编译为人类可读的C++代码，和已有C++代码一起编译，可以像C++代码一样被C++直接调用。

调用C++代码，只需要将C++代码的原型声明一下，即可调用。例如
```
externc fun printf(format: raw* const Int8, args: ...);

fun main() {
    printf("Hello World\n");
}
```
C语言代码使用externc来修改，C++代码使用extern来修改。支持类型。
```
//C++
class P {
    void foo();
};

//sric
extern struct P {
    fun foo();
}
```
如果碰到碰到C++支持，sric不支持的特性，就不能直接调用了，可能需要自己封装。

也可以用symbol指令来映射符号，例如
```
//C++
namespace test {
    void hi() {
    }
}

//sric
//@extern symbol: test::hi
extern fun hello();
```