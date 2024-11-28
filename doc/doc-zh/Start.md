

## 安装

需要:
- JDK 17+
- C++ 编译器 支持C++17
- Maven

构建:
1. 添加 "bin" 到你的环境变量
2. 运行 build.sh

## IDE

1. 在vscode插件市场中搜索'sric-language',并安装。
2. 在插件的设置页配置Language Server Path和Language Server Arguments。

## 用法

```
sric test.scm
```

其中.scm文件是模块定义文件，也是构建脚本。

生成的 C++ 代码位置在 "sric/output" 目录。


## 通过fmake来编译 (可选的)
```
fan fmake output/test.fmake -debug
```