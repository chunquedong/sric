
fun main1()
{
    var i = 1;
    var p: raw* Int = &i;
}

fun main2()
{
    var p: raw*? Int;
    if (true) {
        var a : Int = 1;
        p = &a;
    }
}
