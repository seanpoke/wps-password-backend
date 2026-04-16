# UserContextHolder 使用说明

## 概述

本项目实现了基于 Token 的用户鉴权机制，通过拦截器解析 Token 并将用户信息存储在 ThreadLocal 中，方便在 Service 层随时获取当前登录用户信息。

## 核心组件

### 1. UserContext（用户上下文）
位置：`com.docauth.context.UserContext`

存储用户信息的对象，包含：
- `account`: 用户账号
- `name`: 用户名称

### 2. UserContextHolder（用户上下文持有者）
位置：`com.docauth.context.UserContextHolder`

提供 ThreadLocal 的存取操作：
- `setUserContext(UserContext)`: 设置用户上下文
- `getUserContext()`: 获取用户上下文
- `getCurrentAccount()`: 获取当前用户账号
- `getCurrentName()`: 获取当前用户名称
- `clear()`: 清除用户上下文（防止内存泄漏）

### 3. UserUtil（用户工具类）
位置：`com.docauth.util.UserUtil`

提供便捷的用户信息获取方法：
- `getCurrentAccount()`: 获取当前登录用户账号
- `getCurrentName()`: 获取当前登录用户名称
- `isLogin()`: 检查当前用户是否已登录

### 4. TokenInterceptor（Token 拦截器）
位置：`com.docauth.interceptor.TokenInterceptor`

在请求处理前：
1. 从请求头获取 token
2. 从 Redis 中获取用户账号
3. 创建 UserContext 并存储到 ThreadLocal
4. 刷新 token 过期时间

在请求完成后：
- 自动清除 ThreadLocal，防止内存泄漏

## 使用方式

### 在 Controller 中使用

```java
@GetMapping("/example")
public ApiResponse<?> exampleMethod() {
    // 直接获取当前用户账号
    String account = UserContextHolder.getCurrentAccount();
    
    // 或者使用工具类
    String account2 = UserUtil.getCurrentAccount();
    
    // 获取用户名称
    String name = UserContextHolder.getCurrentName();
    
    return ApiResponse.success("当前用户: " + account);
}
```

### 在 Service 中使用

```java
@Service
public class DocService {
    
    public void processDocument(String docId) {
        // 在任何地方都可以获取当前用户信息
        String currentAccount = UserContextHolder.getCurrentAccount();
        
        // 记录操作人
        DocInfo doc = new DocInfo();
        doc.setCreateBy(currentAccount);
        
        // 业务逻辑...
    }
}
```

### 在 Repository 或任何地方使用

```java
// 由于使用了 ThreadLocal，可以在任何地方获取当前用户信息
String currentUser = UserUtil.getCurrentAccount();
```

## 工作流程

```
客户端请求
    ↓
携带 Token (Header: token)
    ↓
TokenInterceptor.preHandle()
    ↓
从 Redis 获取用户信息
    ↓
创建 UserContext
    ↓
存储到 ThreadLocal
    ↓
Controller → Service → Repository
    ↓
(随时可以获取用户信息)
    ↓
TokenInterceptor.afterCompletion()
    ↓
清除 ThreadLocal (防止内存泄漏)
    ↓
返回响应
```

## 注意事项

### 1. 内存泄漏防护
- 拦截器的 `afterCompletion()` 方法会自动清除 ThreadLocal
- 不要手动调用 `UserContextHolder.clear()`，除非有特殊需求

### 2. 异步线程
- ThreadLocal 不能跨线程传递
- 如果需要在异步线程中获取用户信息，需要手动传递

```java
// 错误示例：异步线程中无法获取
@Async
public void asyncMethod() {
    String account = UserContextHolder.getCurrentAccount(); // null
}

// 正确示例：手动传递
String account = UserContextHolder.getCurrentAccount();
CompletableFuture.runAsync(() -> {
    // 使用传递的 account
    processWithAccount(account);
});
```

### 3. 登录接口豁免
- `/account/login` 接口不需要 Token
- 其他所有接口都需要携带有效的 Token

### 4. Token 传递方式
客户端需要在请求头中携带 Token：
```
Headers:
  token: your-token-here
```

## 示例代码

### 完整的 Service 示例

```java
@Service
public class DocumentService {
    
    @Autowired
    private DocInfoRepository docInfoRepository;
    
    /**
     * 创建文档
     */
    public DocInfo createDocument(String uid) {
        // 获取当前用户
        String account = UserContextHolder.getCurrentAccount();
        String name = UserContextHolder.getCurrentName();
        
        // 创建文档
        DocInfo doc = new DocInfo();
        doc.setUid(uid);
        doc.setAccount(account);
        doc.setName(name);
        doc.setCreateBy(account);
        
        return docInfoRepository.save(doc);
    }
    
    /**
     * 更新文档
     */
    public void updateDocument(String uid, String content) {
        // 获取当前用户
        String account = UserContextHolder.getCurrentAccount();
        
        // 查询文档
        DocInfo doc = docInfoRepository.findByUid(uid);
        if (doc == null) {
            throw new RuntimeException("文档不存在");
        }
        
        // 校验权限：只有创建者可以更新
        if (!doc.getAccount().equals(account)) {
            throw new RuntimeException("无权限更新此文档");
        }
        
        // 更新文档
        doc.setUpdateBy(account);
        // ... 其他更新逻辑
        docInfoRepository.save(doc);
    }
}
```

## 优势

1. **解耦**：Controller 不需要显式传递用户信息给 Service
2. **简洁**：Service 层可以直接获取当前用户，无需额外参数
3. **安全**：用户信息来自 Token 验证，不可伪造
4. **自动清理**：请求结束后自动清理，防止内存泄漏
