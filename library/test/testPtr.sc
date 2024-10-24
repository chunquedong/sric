
import sric::*;

struct A {
    var i: Int = 1;

    operator fun compare(b: ref* A) : Int {
        return i - b.i;
    }
}

struct CP {
    var p: own*? Int;
}

fun testA(a: * A) {
    printf("%d\n", a.i);
}

fun testRefable() {
    var i: refable Int = 1;
    var a: refable A;
    a.i = i + 1;
    testA(&a);
    var b: A = a;
    var c: refable A = b;
    a = c;
    b = a;
}

fun testPtr() {
    var p: own* A = alloc$<A>();
    testA(p);

    if (p != null) {
        printf("%d\n", p.i);
    }

    var rp: ref* A = p;
    p = null;
    //printf("%d\n", rp.i);
}

fun testNullable() {
    var p: ref*? A;
    var i = p.i;
}

fun testCompare() {
    var a = A { .i = 1; };
    var b = A { .i = 2; };
    if (a < &b) {
        printf("compare OK\n");
    }
}

fun testMove() {
    var b: CP;
    var a = move b;
}

fun testRaw() {
    var a = 1;
    var p: raw* Int = &a;
    unsafe {
        var p1 = p + 1;
        p1 = p1 - 1;
        *p1 = 2;
    }
    printf("%d\n", a);
}