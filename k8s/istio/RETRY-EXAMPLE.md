# Retry 重试示例

## 代码 vs Sidecar

### 以前（代码里写）

```java
@RestController
public class PaymentController {
    
    @Autowired
    private BankClient bankClient;
    
    public Response processPayment() {
        // 重试逻辑写在代码里
        int retries = 3;
        for (int i = 0; i < retries; i++) {
            try {
                return bankClient.call();
            } catch (Exception e) {
                if (i == retries - 1) throw e;
                Thread.sleep(1000); // 退避
            }
        }
    }
}
```

### 现在（Sidecar 自动处理）

```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: bank-retry
spec:
  hosts:
    - bank-simulator
  http:
    - route:
        - destination:
            host: bank-simulator
      retries:
        attempts: 3              # 重试 3 次
        perTryTimeout: 2s        # 每次超时 2 秒
        retryOn: gateway-error,connect-failure,refused-stream
      timeout: 10s               # 总超时 10 秒
```

## 效果对比

| | 代码重试 | Sidecar 重试 |
|--|---------|-------------|
| **代码侵入** | 需要修改业务代码 | 零侵入 |
| **语言限制** | Java/Go/Python 各写一遍 | 统一配置 |
| **可观测性** | 自己埋点 | Envoy 自动记录 |
| **动态调整** | 改代码重新部署 | 改配置热生效 |

## 面试回答

> "熔断、重试、追踪以前要在代码里写，每个语言、每个服务都要实现一遍。Service Mesh 把这些下沉到 Sidecar，通过 YAML 配置即可，业务代码完全无感知。"
