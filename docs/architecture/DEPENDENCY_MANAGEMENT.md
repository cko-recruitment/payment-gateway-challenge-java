# Spring Boot Starter 冲突解决

## 1. 冲突场景

```
┌─────────────────────────────────────┐
│  spring-boot-starter-web            │
│  └── jackson-databind:2.15.3        │
│                                     │
│  spring-boot-starter-data-redis     │
│  └── jackson-databind:2.14.0  ← 冲突！│
│                                     │
│  Gradle 自动选择高版本：2.15.3        │
└─────────────────────────────────────┘
```

## 2. 查看冲突

```bash
# Gradle 查看依赖树
./gradlew dependencies --configuration runtimeClasspath

# 搜索特定依赖
./gradlew dependencies --configuration runtimeClasspath | grep jackson

# 查看冲突解析
./gradlew dependencyInsight --dependency jackson-databind
```

## 3. 解决冲突

### 方法一：强制指定版本（推荐）

```groovy
// build.gradle
ext {
    // 统一版本管理
    set('jackson.version', '2.15.3')
    set('slf4j.version', '2.0.9')
}

dependencies {
    // 或者强制约束
    constraints {
        implementation('com.fasterxml.jackson.core:jackson-databind:2.15.3') {
            because 'Security fix and compatibility'
        }
    }
}
```

### 方法二：排除传递依赖

```groovy
// build.gradle
dependencies {
    implementation('org.springframework.boot:spring-boot-starter-data-redis') {
        // 排除冲突的依赖
        exclude group: 'com.fasterxml.jackson.core', module: 'jackson-databind'
    }
    
    // 显式声明正确版本
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.15.3'
}
```

### 方法三：Spring Boot BOM 管理

```groovy
// build.gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.1.5'
    id 'io.spring.dependency-management' version '1.0.15.RELEASE'
}

// Spring Boot 自动管理版本
dependencies {
    // 不需要指定版本，Spring Boot BOM 统一管理
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-security'
}
```

## 4. 最佳实践

### 版本锁定文件

```groovy
// gradle.properties
jackson.version=2.15.3
redis.version=3.1.5
netty.version=4.1.100
```

### 依赖检查任务

```groovy
// build.gradle
task checkDependencies {
    doLast {
        configurations.runtimeClasspath.resolvedConfiguration.firstLevelModuleDependencies.each { dependency ->
            def module = dependency.moduleGroup + ':' + dependency.moduleName
            def version = dependency.moduleVersion
            println "✓ $module:$version"
        }
    }
}
```

### CI 中检测冲突

```yaml
# .github/workflows/ci.yml
- name: Check Dependencies
  run: |
    ./gradlew dependencies --configuration runtimeClasspath > deps.txt
    # 检查是否有多个版本
    if grep -E "\\+--- .*:\\d+\\.\\d+" deps.txt | sort | uniq -d; then
      echo "⚠️ Potential dependency conflicts found"
      exit 1
    fi
```

## 5. 常见冲突

| 依赖 | 冲突原因 | 解决 |
|-----|---------|------|
| **Jackson** | 不同 Starter 引入不同版本 | 用 Spring Boot BOM |
| **Netty** | WebFlux vs gRPC | 强制统一版本 |
| **SLF4J** | 1.x vs 2.x | 排除旧版本 |
| **Log4j** | log4j vs log4j2 | 用 spring-boot-starter-logging |

## 6. 面试回答

> "Starter 冲突主要通过三种方式解决：1）Spring Boot BOM 统一管理版本；2）Gradle constraints 强制约束；3）exclude 排除传递依赖。CI 中通过依赖树检查提前发现冲突。"
