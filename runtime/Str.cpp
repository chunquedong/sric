#include "Str.h"
#include <string.h>
#include <stdlib.h>
#include <functional>
#include <stdarg.h>
//#include <varargs.h>
#include <cstdlib>

using namespace sric;
#define BUF_SIZE 1024

int String::hashCode() const {
    std::hash<std::string> hash_fn;
    return hash_fn(cpp_str());
}

int String::compare(const String& other) const {
    return strcmp(c_str(), other.c_str());
}

int String::find(const String& s, int start) const {
    auto it = str.find(s.str, start);
    if (it == std::string::npos) {
        return -1;
    }
    return it;
}

bool String::iequals(const String& other) const {
    size_t sz = this->size();
    if (other.size() != sz)
        return false;
    for (unsigned int i = 0; i < sz; ++i)
        if (tolower((*this)[i]) != tolower((other)[i]))
            return false;
    return true;
}

bool String::contains(const String& s) const {
    return strstr(this->c_str(), s.c_str()) != NULL;
}
bool String::startsWith(const String& s) const {
    return strstr(this->c_str(), s.c_str()) == this->c_str();
}
bool String::endsWith(const String& s) const {
    return (this->cpp_str()).rfind(s.cpp_str()) == (this->size() - s.size());
}

void String::replace(const String& src, const String& dst) {
    if (strcmp(src.c_str(), dst.c_str()) == 0) {
        return;
    }
    size_t srcLen = src.size();
    size_t desLen = dst.size();
    const std::string& self = this->cpp_str();
    const std::string& csrc = src.cpp_str();
    const std::string& cdst = dst.cpp_str();
    std::string::size_type pos = self.find(csrc);

    while ((pos != std::string::npos))
    {
        str.replace(pos, srcLen, cdst);
        pos = self.find(csrc, (pos + desLen));
    }
}

DArray<String> String::split(const String& sep) const {
    DArray<String> tokens;
    if (this->size() == 0)
        return tokens;
    std::size_t start = 0, end = 0;
    while ((end = find(sep, start)) != std::string::npos) {
        String ts = substr(start, end - start);
        tokens.add(std::move(ts));
        start = end + 1;
    }

    tokens.add(substr(start));
    return tokens;
}

String String::substr(int pos, int len) const {
    return str.substr(pos, len);
}

String& String::plus(const String& other) {
    str += other.str;
    return *this;
}
void String::add(const char* cstr) {
    str += cstr;
}

static bool isSpace(char ch) {
    return (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t');
}

void String::trimEnd() {
    int i = str.size() - 1;
    for (; i >= 0; --i) {
        if (!isSpace(str[i])) {
            break;
        }
    }

    if (i < str.size() - 1) {
        str.erase(str.begin() + i + 1, str.end());
    }
}
void String::trimStart() {
    int i = 0;
    for (; i < str.size(); ++i) {
        if (!isSpace(str[i])) {
            break;
        }
    }

    if (i > 0) {
        str.erase(str.begin(), str.begin() + i);
    }
}

void String::removeLastChar() {
    if (str.length() == 0) return;
    str.erase(str.length() - 1);
}

String String::toLower() const {
    std::string ret;
    char chrTemp;
    size_t i;
    for (i = 0; i < str.length(); ++i)
    {
        chrTemp = str[i];
        chrTemp = tolower(chrTemp);
        ret.push_back(chrTemp);
    }

    return ret;
}
String String::toUpper() const {
    std::string ret;
    char chrTemp;
    size_t i;
    for (i = 0; i < str.length(); ++i)
    {
        chrTemp = str[i];
        chrTemp = toupper(chrTemp);
        ret.push_back(chrTemp);
    }

    return ret;
}

int64_t String::toInt64() const {
    if (str.empty()) return 0;
    int64_t nValue = 0;
    sscanf(str.c_str(), "%lld", &nValue);
    return nValue;
}
int String::toInt32() const { return (int)std::stol(c_str(), NULL, 10); }
float String::toFloat32() const { return std::stof(c_str(), NULL); }
double String::toFloat64() const { return std::stod(c_str(), NULL); }

String String::fromInt32(int i) {
    char buf[BUF_SIZE];
    snprintf(buf, sizeof(buf), "%d", i);

    return buf;
}
String String::fromInt64(int64_t i) {
    char buf[BUF_SIZE];
    snprintf(buf, sizeof(buf), "%lld", i);

    return buf;
}
String String::fromFloat64(double f) {
    char buf[BUF_SIZE];
    snprintf(buf, sizeof(buf), "%f", f);

    return buf;
}
String String::fromFloat32(float f) {
    char buf[BUF_SIZE];
    snprintf(buf, sizeof(buf), "%f", f);

    return buf;
}

/**
* 'printf' style format
*/
String String::format(const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);

    char buf[BUF_SIZE];
    char* abuf = NULL;
    int i = vsnprintf(buf, sizeof(buf), fmt, args);

    if (i < 0) {
        va_end(args);
        return "";
    }
    if (i >= BUF_SIZE) {
        abuf = (char*)malloc(i + 1);
        i = vsnprintf(abuf, i, fmt, args);
        if (i < 0) {
            va_end(args);
            return "";
        }
        if (i > 0) {
            String str(abuf);
            free(abuf);
            va_end(args);
            return str;
        }
    }

    va_end(args);
    return String(buf);
}
