Java port of [Mini Ruccola (vm2gol-v2)](https://github.com/sonota88/vm2gol-v2) compiler

素朴な自作言語のコンパイラをJavaに移植した  
https://memo88.hatenablog.com/entry/2020/08/30/121437

---

```
  $ java -version
openjdk version "21.0.5" 2024-10-15
OpenJDK Runtime Environment (build 21.0.5+11-Ubuntu-1ubuntu124.04)
OpenJDK 64-Bit Server VM (build 21.0.5+11-Ubuntu-1ubuntu124.04, mixed mode, sharing)
```

```
git clone --recursive https://github.com/sonota88/vm2gol-v2-java.git
cd vm2gol-v2-java

./docker.sh build
./test.sh all
```

```
  $ LANG=C wc -l src/main/java/vm2gol_v2/{Lexer,Parser,CodeGenerator}.java
  103 src/main/java/vm2gol_v2/Lexer.java
  493 src/main/java/vm2gol_v2/Parser.java
  402 src/main/java/vm2gol_v2/CodeGenerator.java
  998 total
```
