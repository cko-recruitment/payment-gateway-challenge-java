# 依赖冲突对比：JVM Classpath vs Maven vs Gradle

## 1. 冲突本质

```
┌─────────────────────────────────────────────────────────┐
│                    冲突本质相同                            │
│                                                         │
│  同一个类/库 的 多个版本 同时存在，JVM 只能选一个            │
│                                                         │
│  JVM: 先加载的胜出（Classpath 顺序）                      │
│  Maven: 最近原则（依赖树深度）                             │
│  Gradle: 默认最新版本胜出                                  │
└─────────────────────────────────────────────────────────┘
```

## 2. 三种冲突对比

### JVM Classpath（最原始）

```
java -cp "lib/a-1.0.jar:lib/a-2.0.jar" MyApp
                              ↑
                         两个版本同时存在！

JVM 加载顺序：
1. 按 Classpath 顺序加载
2. 先加载的 a-1.0.jar 胜出
3. a-2.0.jar 被忽略（但还在 classpath 里）

问题：
- 不可预测（顺序决定）
- 没有工具检查
- 运行时才发现 ClassNotFoundException
```

### Maven（树形解析）

```
依赖树：
┌─────────┐
│  你的项目 │
└────┬────┘
     │
     ├──► lib-a:1.0  ← 直接依赖（最近）
     │
     └──► lib-b:1.0
          └──► lib-a:2.0  ← 传递依赖（较远）

Maven 最近原则：
- 深度浅的优先（lib-a:1.0 深度=1）
- 深度深的被忽略（lib-a:2.0 深度=2）

问题：
- 1.0 胜出，但可能你需要 2.0 的功能
- 必须手动 exclude 或显式声明
```

### Gradle（默认最新版）

```
依赖树：
┌─────────┐
│  你的项目 │
└────┬────┘
     │
     ├──► lib-a:1.0
     │
     └──► lib-b:1.0
          └──► lib-a:2.0

Gradle 默认策略：
- 选择最新版本（lib-a:2.0）
- 自动升级

问题：
- 2.0 可能有 breaking change
- 1.0 的代码可能不兼容
```

## 3. 解决方式对比

| 场景 | JVM Classpath | Maven | Gradle |
|-----|--------------|-------|--------|
| **查看冲突** | 无工具，手动看 | `mvn dependency:tree` | `gradle dependencies` |
| **强制版本** | 调整顺序 | `<dependencyManagement>` | `constraints` / `force` |
| **排除依赖** | 删除 jar | `<exclusions>` | `exclude` |
| **版本锁定** | 无 | `<dependencyManagement>` | `platform` / `BOM` |

## 4. Spring Boot 的解决方式

```groovy
// Gradle + Spring Boot
plugins {
    id 'io.spring.dependency-management' version '1.0.15.RELEASE'
}

dependencies {
    // 不需要版本号，BOM 统一管理
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
}

// 等价于：
// jackson: 2.15.3（所有 Starter 都用这个）
// netty: 4.1.100（所有 Starter 都用这个）
// 不会冲突！
```

## 5. 实际例子

### 冲突场景

```groovy
// build.gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    // 引入 jackson:2.15.3
    
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.14.0'
    // 显式引入 2.14.0，冲突！
}
```

### Gradle 解决

```groovy
// 方法一：强制约束
constraints {
    implementation('com.fasterxml.jackson.core:jackson-databind:2.15.3') {
        because 'Spring Boot 3.1.5 requires this version'
    }
}

// 方法二：排除传递依赖
implementation('org.springframework.boot:spring-boot-starter-web') {
    exclude group: 'com.fasterxml.jackson.core', module: 'jackson-databind'
}
implementation 'com.fasterxml.jackson.core:jackson-databind:2.14.0'

// 方法三：用 Spring Boot BOM（推荐）
ext {
    set('jackson.version', '2.15.3')
}
```

## 6. 面试回答

> "三种冲突本质相同：同一个库的多个版本只能选一个。JVM Classpath 按顺序加载，不可控；Maven 按最近原则；Gradle 默认选最新版本。Spring Boot 用 BOM 统一管理 Starter 版本，从根本上避免冲突。"

## 7. 核心区别

| | JVM Classpath | Maven | Gradle |
|--|--------------|-------|--------|
| **冲突检测** | ❌ 无 | ✅ 有 | ✅ 有 |
| **解决策略** | 顺序 | 最近 | 最新 |
| **可预测性** | ❌ 低 | ✅ 中 | ✅ 高 |
| **工具支持** | ❌ 无 | ✅ 好 | ✅ 好 |
| **现代项目** | ❌ 不用 | ⚠️ 少用 | ✅ 主流 |
